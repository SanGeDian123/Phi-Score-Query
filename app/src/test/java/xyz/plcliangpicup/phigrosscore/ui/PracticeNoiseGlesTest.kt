package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.plcliangpicup.phigrosscore.data.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PracticeNoiseGlesTest {
    @Test fun glesUsesTheSameOfficialFragmentBodyAndThirteenInputs() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val agsl = context.assets.open("practice/noise_domain.agsl").bufferedReader().use { it.readText() }
        val gles = practiceNoiseGlesFragment(agsl)
        assertTrue(gles.startsWith("#version 300 es"))
        assertEquals(13, Regex("uniform sampler2D").findAll(gles).count())
        assertFalse(gles.contains(".eval(")); assertFalse(gles.contains("uniform shader"))
        assertFalse(Regex("\\b(?:float|half|bool|int)[234]\\b").containsMatchIn(gles))
        for (constant in listOf("_FillStrength", "_EdgeOpacity", "_SDFMoveSpeed", "_TouchPosShine", "D_SparkMapOpacity")) {
            assertTrue(gles.contains(constant))
        }
    }

    @Test fun exportNativeAgslReferenceAndSharedMasksForActualGlesParityChecks() {
        exportReferences(false)
    }

    @Test fun exportV100CompatibilityReferenceForActualGlesParityChecks() {
        exportReferences(true)
    }

    private fun exportReferences(compatibilityMode: Boolean) {
        val output = System.getenv("PSQ_NOISE_RENDER_OUTPUT") ?: return
        val directory = File(output, if (compatibilityMode) "gles-parity-compatibility" else "gles-parity").apply { mkdirs() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val agsl = practiceNoiseFragmentSource(context, compatibilityMode)
        val size = practiceNoiseOverlaySize(1920f, 1080f, compatibilityMode)
        val w = size.width; val h = size.height
        val sx = w / 1920f; val sy = h / 1080f
        File(directory, "style.json").writeText("""{"compatibilityMode":$compatibilityMode,"width":$w,"height":$h}""")
        File(directory, "fragment.glsl").writeText(practiceNoiseGlesFragment(agsl))
        File(directory, "vertex.glsl").writeText(PRACTICE_NOISE_GLES_VERTEX)
        fun area(left: Float, bottom: Float, right: Float, top: Float, subtract: Boolean = false) =
            PracticeBlockArea(PracticeNoisePoint(right, top), PracticeNoisePoint(left, bottom),
                appearTime = 0.0, enableTime = 1.0, disableTime = 3.0, disappearTime = 4.0, isSubtract = subtract)
        val runtime = PracticeNoiseRuntime(listOf(area(.12f, .12f, .88f, .88f), area(.4f, .3f, .6f, .7f, true)))
        val renderer = PracticeNoiseRenderer(context, compatibilityMode = compatibilityMode)
        val scene = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(scene).drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.rgb(9, 20, 36), Color.rgb(37, 22, 12), Shader.TileMode.CLAMP)
        })
        fun save(bitmap: Bitmap, file: File) { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        try {
            for ((name, time) in listOf("active" to 2.0, "disabled" to .1, "ready" to .75, "touch" to 2.2)) {
                renderer.resetTouches()
                val points = if (name == "touch") mapOf(1L to PracticeNoisePoint(550f, 540f), 2L to PracticeNoisePoint(1400f, 500f)) else emptyMap()
                val frame = runtime.frame(time, 1920f, 1080f)
                renderer.officialInputsForValidation(frame, 1920f, 1080f, points, 0.0).values.forEach { it.recycle() }
                val inputs = renderer.officialInputsForValidation(frame, 1920f, 1080f, points, .11)
                val folder = File(directory, name).apply { mkdirs() }
                for ((key, image) in inputs) save(image, File(folder, "$key.png"))
                save(scene, File(folder, "_SceneColor.png")); save(scene, File(folder, "_BackgroundColor.png"))
                val shader = PracticeNoiseShader(context, compatibilityMode)
                val children = inputs.filterKeys { !it.startsWith("_Raw") }.mapValues { (key, image) ->
                    val mode = when (key) {
                        "_DisplaceMap", "_TouchDisplaceMap", "_NoiseMap" -> if (compatibilityMode) Shader.TileMode.REPEAT else Shader.TileMode.MIRROR
                        "_SparkMap" -> Shader.TileMode.REPEAT
                        else -> Shader.TileMode.CLAMP
                    }
                    BitmapShader(image, mode, mode).apply {
                        setFilterMode(if (compatibilityMode || key == "_EffectRT") BitmapShader.FILTER_MODE_LINEAR else BitmapShader.FILTER_MODE_NEAREST)
                        if (key !in setOf("_DisplaceMap", "_TouchDisplaceMap", "_NoiseMap", "_SparkMap"))
                            setLocalMatrix(Matrix().apply { setScale(1920f / image.width, 1080f / image.height) })
                    }
                }
                shader.inputs(children)
                shader.update(time, 1920, 1080, 256, 144, points.values)
                shader.scene(BitmapShader(scene, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setFilterMode(if (compatibilityMode) BitmapShader.FILTER_MODE_LINEAR else BitmapShader.FILTER_MODE_NEAREST)
                }, BitmapShader(scene, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setFilterMode(if (compatibilityMode) BitmapShader.FILTER_MODE_LINEAR else BitmapShader.FILTER_MODE_NEAREST)
                    setLocalMatrix(Matrix().apply { setScale(1f / sx, 1f / sy) })
                }, sx, sy, true)
                val reference = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(reference)
                Class.forName("android.graphics.BaseCanvas").getMethod("setHwFeaturesInSwModeEnabled", Boolean::class.javaPrimitiveType).invoke(canvas, true)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.shader = shader.runtime })
                save(reference, File(folder, "reference-agsl.png"))
                assertTrue(Color.red(reference.getPixel(w * 28 / 100, h / 2)) > 0)
                assertEquals(Color.TRANSPARENT, reference.getPixel(4, 4))
                reference.recycle(); inputs.values.forEach { it.recycle() }
            }
        } finally { renderer.release(); scene.recycle() }
    }
}
