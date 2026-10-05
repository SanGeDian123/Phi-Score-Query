package xyz.plcliangpicup.phigrosscore.ui

import android.content.Context
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.sin
import xyz.plcliangpicup.phigrosscore.data.PracticeNoisePoint

/** Same fragment arithmetic as AGSL; only child sampling and fragment coordinates change. */
internal fun practiceNoiseGlesFragment(source: String): String {
    val names = Regex("uniform shader (\\w+);").findAll(source).map { it.groupValues[1] }.toList()
    val helpers = names.joinToString("\n") { name ->
        val divisor = when (name) {
            "_DisplaceMap", "_TouchDisplaceMap", "_NoiseMap" -> "vec2(256.0)"
            "_SparkMap" -> "vec2(128.0)"
            "_SceneColor" -> "_OutputSize"
            else -> "_ScreenParams.xy"
        }
        "vec4 psqEval$name(vec2 point) { return texture($name, point / $divisor); }"
    }
    var body = source.replace("uniform shader", "uniform sampler2D")
        .replace("half4 main(float2 coord)", "vec4 noiseFragment(vec2 coord)")
    for (name in names) body = body.replace("$name.eval(", "psqEval$name(")
    body = Regex("\\b(?:float|half)([234])\\b").replace(body) { "vec" + it.groupValues[1] }
    body = Regex("\\bbool([234])\\b").replace(body) { "bvec" + it.groupValues[1] }
    body = Regex("\\bint([234])\\b").replace(body) { "ivec" + it.groupValues[1] }
    body = Regex("\\bactive\\b").replace(body, "psqActive")
    val start = body.indexOf("vec2 androidPosition")
    require(start >= 0 && names.size == 13) { "Unexpected official noise shader inputs" }
    return "#version 300 es\nprecision highp float;\nprecision highp int;\nout vec4 psqColor;\nuniform vec2 _OutputSize;\n" +
        body.substring(0, start) + helpers + "\n" + body.substring(start) +
        "\nvoid main() { psqColor = noiseFragment(vec2(gl_FragCoord.x, _OutputSize.y - gl_FragCoord.y)); }\n"
}

internal const val PRACTICE_NOISE_GLES_VERTEX = """#version 300 es
layout(location = 0) in vec2 position;
void main() { gl_Position = vec4(position, 0.0, 1.0); }
"""

/** GLES3 works before RuntimeShader's API 33. Work/readback is bounded and capped at 30 Hz. */
internal class PracticeNoiseGles(context: Context, private val compatibilityMode: Boolean = false) {
    private val fragment = practiceNoiseGlesFragment(practiceNoiseFragmentSource(context, compatibilityMode))
    private val names = Regex("uniform sampler2D (\\w+);").findAll(fragment).map { it.groupValues[1] }.toList()
    private val staticInputs = setOf("_DisplaceMap", "_TouchDisplaceMap", "_NoiseMap", "_SparkMap")
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private var program = 0
    private var vertexBuffer = 0
    private var output: Bitmap? = null
    private var readback = ByteBuffer.allocateDirect(0)
    private var pixels = IntArray(0)
    private val textures = linkedMapOf<String, Int>()
    private val textureSizes = linkedMapOf<String, Pair<Int, Int>>()
    private val textureSources = linkedMapOf<String, Bitmap>()
    private val locations = linkedMapOf<String, Int>()
    private val positions = FloatArray(20)
    private val triangle = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)); position(0)
    }
    internal val isReady get() = program != 0

    private inline fun <T> current(block: () -> T): T {
        val oldDisplay = EGL14.eglGetCurrentDisplay()
        val oldContext = EGL14.eglGetCurrentContext()
        val oldRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        val oldDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        check(EGL14.eglMakeCurrent(display, surface, surface, eglContext)) { "Noise EGL make-current failed" }
        try { return block() } finally {
            if (oldDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglMakeCurrent(oldDisplay, oldDraw, oldRead, oldContext)
            else EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        }
    }
    private fun resize(size: PracticeNoiseRenderSize) {
        if (display == EGL14.EGL_NO_DISPLAY) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val versions = IntArray(2)
            check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, versions, 0, versions, 1)) { "Noise EGL initialization failed" }
            val configs = arrayOfNulls<EGLConfig>(1); val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0) && count[0] > 0) {
                "OpenGL ES 3 is required for the official noise effect"
            }
            config = checkNotNull(configs[0])
            eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "Noise GLES3 context unavailable" }
        }
        if (output?.width == size.width && output?.height == size.height) return
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        surface = EGL14.eglCreatePbufferSurface(display, config,
            intArrayOf(EGL14.EGL_WIDTH, size.width, EGL14.EGL_HEIGHT, size.height, EGL14.EGL_NONE), 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "Noise EGL surface unavailable" }
        output?.recycle(); output = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        readback = ByteBuffer.allocateDirect(size.width * size.height * 4).order(ByteOrder.nativeOrder())
        pixels = IntArray(size.width * size.height)
    }
    private fun compile(type: Int, source: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, source); GLES30.glCompileShader(id)
        val status = IntArray(1); GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES30.glGetShaderInfoLog(id); GLES30.glDeleteShader(id)
            error("Official noise shader compilation failed: $message")
        }
        return id
    }
    private fun initializeProgram() {
        val limit = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_IMAGE_UNITS, limit, 0)
        check(limit[0] >= names.size) { "Insufficient GLES3 texture units for official noise" }
        val vertex = compile(GLES30.GL_VERTEX_SHADER, PRACTICE_NOISE_GLES_VERTEX)
        var frag = 0
        var linked = 0
        try {
            frag = compile(GLES30.GL_FRAGMENT_SHADER, fragment)
            linked = GLES30.glCreateProgram(); GLES30.glAttachShader(linked, vertex); GLES30.glAttachShader(linked, frag)
            GLES30.glLinkProgram(linked)
            val status = IntArray(1); GLES30.glGetProgramiv(linked, GLES30.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { "Official noise shader link failed: ${GLES30.glGetProgramInfoLog(linked)}" }
            program = linked
            val buffers = IntArray(1); GLES30.glGenBuffers(1, buffers, 0); vertexBuffer = buffers[0]
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer)
            triangle.position(0); GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 6 * 4, triangle, GLES30.GL_STATIC_DRAW)
        } finally {
            GLES30.glDeleteShader(vertex)
            if (frag != 0) GLES30.glDeleteShader(frag)
            if (linked != 0 && program != linked) GLES30.glDeleteProgram(linked)
        }
    }
    private fun location(name: String) = locations.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }

    fun render(size: PracticeNoiseRenderSize, width: Float, height: Float, time: Double,
        effectSize: PracticeNoiseRenderSize, touches: Collection<PracticeNoisePoint>, inputs: Map<String, Bitmap>): Bitmap {
        resize(size)
        current {
            if (program == 0) initializeProgram()
            GLES30.glUseProgram(program); GLES30.glViewport(0, 0, size.width, size.height)
            GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDisable(GLES30.GL_DITHER)
            names.forEachIndexed { unit, name ->
                val bitmap = checkNotNull(inputs[name]) { "Missing GLES noise input $name" }
                val newTexture = name !in textures
                val texture = textures.getOrPut(name) { IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0] }
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
                val wrap = if (compatibilityMode && name in staticInputs) GLES30.GL_REPEAT else when (name) {
                    "_DisplaceMap", "_TouchDisplaceMap", "_NoiseMap" -> GLES30.GL_MIRRORED_REPEAT
                    "_SparkMap" -> GLES30.GL_REPEAT
                    else -> GLES30.GL_CLAMP_TO_EDGE
                }
                val filter = if (compatibilityMode || name == "_EffectRT") GLES30.GL_LINEAR else GLES30.GL_NEAREST
                if (newTexture) {
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
                }
                val dimensions = bitmap.width to bitmap.height
                val static = name in staticInputs
                if (textureSizes[name] != dimensions) {
                    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0); textureSizes[name] = dimensions
                } else if (!static || textureSources[name] !== bitmap) GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bitmap)
                textureSources[name] = bitmap; GLES30.glUniform1i(location(name), unit)
            }
            val w = width.roundToInt().coerceAtLeast(1).toFloat(); val h = height.roundToInt().coerceAtLeast(1).toFloat()
            val t = time.toFloat(); val ew = effectSize.width.toFloat(); val eh = effectSize.height.toFloat()
            GLES30.glUniform4f(location("_Time"), t / 20f, t, t * 2f, t * 3f)
            GLES30.glUniform4f(location("_ScreenParams"), w, h, 1f + 1f / w, 1f + 1f / h)
            GLES30.glUniform4f(location("_EffectRT_TexelSize"), 1f / ew, 1f / eh, ew, eh)
            GLES30.glUniform2f(location("_SceneRenderScale"), size.width / width, size.height / height)
            GLES30.glUniform2f(location("_OutputSize"), size.width.toFloat(), size.height.toFloat())
            GLES30.glUniform1i(location("_OverlayOnly"), 1)
            positions.fill(0f)
            val count = touches.size.coerceAtMost(10)
            touches.take(count).forEachIndexed { i, point -> positions[i * 2] = point.x / h; positions[i * 2 + 1] = point.y / h }
            GLES30.glUniform1i(location("_TouchPosCount"), count)
            GLES30.glUniform2fv(location("_TouchPos[0]"), 10, positions, 0)
            GLES30.glUniform1f(location("_TouchPosShine"), (0.63f + 0.37f * (0.5f + 0.5f * sin(t * 43f))) * 2f)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0); GLES30.glEnableVertexAttribArray(0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            readback.position(0); GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
            GLES30.glReadPixels(0, 0, size.width, size.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readback)
            check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "Official noise GLES draw failed" }
            for (y in 0 until size.height) for (x in 0 until size.width) {
                val index = ((size.height - 1 - y) * size.width + x) * 4
                val r = readback.get(index).toInt() and 255; val g = readback.get(index + 1).toInt() and 255
                val b = readback.get(index + 2).toInt() and 255; val a = readback.get(index + 3).toInt() and 255
                pixels[y * size.width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            checkNotNull(output).setPixels(pixels, 0, size.width, 0, 0, size.width, size.height)
        }
        return checkNotNull(output)
    }

    fun release() {
        if (surface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) runCatching { current {
            if (textures.isNotEmpty()) GLES30.glDeleteTextures(textures.size, textures.values.toIntArray(), 0)
            if (program != 0) GLES30.glDeleteProgram(program)
            if (vertexBuffer != 0) GLES30.glDeleteBuffers(1, intArrayOf(vertexBuffer), 0)
        } }
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
        // HWUI shares the process display. Release our context, without terminating its display.
        surface = EGL14.EGL_NO_SURFACE; eglContext = EGL14.EGL_NO_CONTEXT; display = EGL14.EGL_NO_DISPLAY; config = null; program = 0; vertexBuffer = 0
        textures.clear(); locations.clear(); textureSizes.clear(); textureSources.clear()
        output?.recycle(); output = null
    }
}
