package xyz.plcliangpicup.phigrosscore.ui

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import xyz.plcliangpicup.phigrosscore.data.*
import kotlin.math.pow
import kotlin.math.roundToInt

/** Geometry stays in chart coordinates; only the visual mask is distorted. */
internal fun practiceNoiseUsesRuntimeShader(sdk: Int): Boolean = sdk >= 33

internal class PracticeNoiseRenderer(private val context: Context,
    useRuntimeShader: Boolean = practiceNoiseUsesRuntimeShader(Build.VERSION.SDK_INT),
    private val useSceneBitmap: Boolean = Build.VERSION.SDK_INT == 33,
    private val compatibilityMode: Boolean = false) {
    private val masks = Array(3) { Path() }
    private val normalMasks = Array(3) { Path().apply { fillType = Path.FillType.WINDING } }
    private val subtractMasks = Array(3) { Path().apply { fillType = Path.FillType.EVEN_ODD } }
    private val disabledNormal = Path().apply { fillType = Path.FillType.WINDING }
    private val disabledSubtract = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val shape = Path()
    private val touch = PracticeNoiseTouch()
    private var hovers: List<PracticeNoiseTouch.Hover> = emptyList()
    private var lastChartTime = Double.NaN
    private var effectTime = 0.0
    private val visualClock = PracticeNoiseVisualClock()
    private var refreshMasks = true
    private var previousRegions: List<PracticeNoiseRegion>? = null
    private var geometryHeight = 0f
    private var geometryVersion = 0
    private val fallback = lazy(LazyThreadSafetyMode.NONE) { PracticeNoiseFallback(context) }
    private val legacy = lazy(LazyThreadSafetyMode.NONE) { Legacy(context) }
    // Android 13 uses the same official shader through a regular Paint, avoiding
    // the nested runtime RenderEffect path without substituting its visual formula.
    private var gpu = if (!compatibilityMode && Build.VERSION.SDK_INT >= 33 && useRuntimeShader) try {
        Gpu(context)
    } catch (failure: RuntimeException) {
        Log.w("PSQ Noise", "RuntimeShader unavailable; using official GLES3", failure); null
    } catch (failure: LinkageError) {
        Log.w("PSQ Noise", "RuntimeShader unavailable; using official GLES3", failure); null
    } else null
    internal val usesOfficialShader: Boolean get() = gpu != null || legacy.isInitialized() && legacy.value.isReady
    internal val maskBuildCount: Int get() = gpu?.maskBuildCount ?: if (legacy.isInitialized()) legacy.value.maskBuildCount else geometryVersion
    internal val geometryBuildCount: Int get() = geometryVersion
    internal val visualSizes: List<PracticeNoiseRenderSize> get() = gpu?.sizes ?: if (legacy.isInitialized()) legacy.value.sizes else emptyList()

    private fun drawFallback(canvas: Canvas, frame: PracticeNoiseFrame, left: Float, width: Float, height: Float) {
        val saved = canvas.save()
        try {
            canvas.translate(left, 0f); canvas.clipRect(0f, 0f, width, height)
            fallback.value.draw(canvas, frame.timeSeconds, width, height, masks, hovers, effectTime)
        } finally { canvas.restoreToCount(saved) }
    }

    private fun disableGpu(failure: RuntimeException) {
        Log.w("PSQ Noise", "RuntimeShader failed; using official GLES3", failure)
        val failed = gpu; gpu = null
        if (Build.VERSION.SDK_INT >= 33) runCatching { failed?.release() }
    }

    private fun prepare(frame: PracticeNoiseFrame, width: Float, height: Float,
        touches: Map<Long, PracticeNoisePoint>, now: Double) {
        if (lastChartTime.isFinite() && (frame.timeSeconds < lastChartTime || frame.timeSeconds - lastChartTime > .5)) resetTouches()
        lastChartTime = frame.timeSeconds; effectTime = now
        hovers = touch.update(touches, now)
        refreshMasks = visualClock.refresh(now, width, height, frame.regions.isEmpty())
        if (!refreshMasks || (previousRegions == frame.regions && geometryHeight == height)) return
        previousRegions = frame.regions; geometryHeight = height; geometryVersion++
        masks.forEach { it.rewind() }
        normalMasks.forEach { it.rewind(); it.fillType = Path.FillType.WINDING }
        subtractMasks.forEach { it.rewind(); it.fillType = Path.FillType.EVEN_ODD }
        disabledNormal.rewind(); disabledSubtract.rewind()
        disabledNormal.fillType = Path.FillType.WINDING
        disabledSubtract.fillType = Path.FillType.EVEN_ODD
        for (region in frame.regions) {
            shape.rewind()
            region.corners.forEachIndexed { index, point ->
                if (index == 0) shape.moveTo(point.x, height - point.y) else shape.lineTo(point.x, height - point.y)
            }
            shape.close()
            val state = region.state.ordinal
            if (region.isSubtract) subtractMasks[state].addPath(shape) else normalMasks[state].addPath(shape)
            if (region.state != PracticeNoiseState.ACTIVE) {
                if (region.isSubtract) disabledSubtract.addPath(shape) else disabledNormal.addPath(shape)
            }
        }
        for (state in masks.indices) {
            if (subtractMasks[state].isEmpty) masks[state].set(normalMasks[state])
            else if (normalMasks[state].isEmpty) masks[state].set(subtractMasks[state])
            else masks[state].op(normalMasks[state], subtractMasks[state], Path.Op.XOR)
        }
    }

    /** Draw ordinary gameplay once at full resolution, then a bounded noise overlay. */
    fun drawScene(canvas: Canvas, frame: PracticeNoiseFrame, left: Float, width: Float, height: Float,
        touches: Map<Long, PracticeNoisePoint>, background: Bitmap, screenWidth: Float,
        warmUp: Boolean = false,
        scene: (Canvas) -> Unit) {
        prepare(frame, width, height, touches, System.nanoTime() / 1e9)
        val currentGpu = gpu
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated && currentGpu != null &&
            (warmUp || frame.regions.isNotEmpty() || hovers.isNotEmpty())) {
            currentGpu.drawOrdinaryScene(canvas, height, screenWidth, scene)
            try {
                currentGpu.update(frame, width, height, touches)
                currentGpu.drawOverlay(canvas, left, width, height, background, screenWidth)
            } catch (failure: RuntimeException) {
                disableGpu(failure)
                val renderer = legacy.value
                renderer.captureScene(height, screenWidth, scene)
                renderer.update(frame, width, height, touches)
                renderer.drawOverlay(canvas, frame, left, width, height, background, screenWidth, touches)
            }
        } else {
            if (warmUp || frame.regions.isNotEmpty() || hovers.isNotEmpty()) {
                val renderer = legacy.value
                renderer.captureScene(height, screenWidth, scene); renderer.drawOrdinaryScene(canvas)
                renderer.update(frame, width, height, touches)
                renderer.drawOverlay(canvas, frame, left, width, height, background, screenWidth, touches)
            } else scene(canvas)
        }
    }

    /** Software canvas entry point also exercises the official AGSL in native render tests. */
    fun draw(canvas: Canvas, frame: PracticeNoiseFrame, fieldLeft: Float, width: Float, height: Float,
        blockedTouches: Map<Long, PracticeNoisePoint> = emptyMap(),
        effectTimeSeconds: Double = System.nanoTime() / 1e9,
        sceneShader: Shader? = null, backgroundShader: Shader? = null) {
        prepare(frame, width, height, blockedTouches, effectTimeSeconds)
        val saved = canvas.save(); canvas.translate(fieldLeft, 0f); canvas.clipRect(0f, 0f, width, height)
        try {
            val currentGpu = gpu
            if (Build.VERSION.SDK_INT >= 33 && currentGpu != null && (canvas.isHardwareAccelerated || sceneShader != null)) {
                try {
                    currentGpu.update(frame, width, height, blockedTouches)
                    currentGpu.draw(canvas, width, height, sceneShader, backgroundShader)
                } catch (failure: RuntimeException) {
                    disableGpu(failure)
                    fallback.value.draw(canvas, frame.timeSeconds, width, height, masks, hovers, effectTime)
                }
            } else fallback.value.draw(canvas, frame.timeSeconds, width, height, masks, hovers, effectTime)
        } finally { canvas.restoreToCount(saved) }
    }

    fun resetTouches() {
        touch.reset(); hovers = emptyList(); lastChartTime = Double.NaN
        visualClock.reset(); previousRegions = null
    }
    fun release() {
        resetTouches(); if (fallback.isInitialized()) fallback.value.release()
        if (legacy.isInitialized()) legacy.value.release()
        if (Build.VERSION.SDK_INT >= 33) gpu?.release()
    }

    /** Detached bitmap copies for validating both shader languages against identical real masks. */
    internal fun officialInputsForValidation(frame: PracticeNoiseFrame, width: Float, height: Float,
        touches: Map<Long, PracticeNoisePoint>, now: Double): Map<String, Bitmap> {
        prepare(frame, width, height, touches, now)
        val raster = Raster(context)
        try {
            raster.update(frame, width, height, touches)
            return raster.validationInputs().mapValues { checkNotNull(it.value.copy(Bitmap.Config.ARGB_8888, false)) }
        } finally { raster.release() }
    }

    private open inner class Raster(private val context: Context) {
        private fun texture(name: String) = context.assets.open("practice/$name").use { checkNotNull(BitmapFactory.decodeStream(it)) }
        private val images = listOf(texture("noise_displace.png"), texture("noise_spark.png"), texture("noise_map.png"))
        private val hover = texture("noise_touch_hover.png")
        private val textureMode = if (compatibilityMode) Shader.TileMode.REPEAT else Shader.TileMode.MIRROR
        private fun textureShader(bitmap: Bitmap, mode: Shader.TileMode) = BitmapShader(bitmap, mode, mode).apply {
            if (Build.VERSION.SDK_INT >= 33) setFilterMode(if (compatibilityMode) BitmapShader.FILTER_MODE_LINEAR else BitmapShader.FILTER_MODE_NEAREST)
        }
        private val shaders = mapOf(
            "_DisplaceMap" to textureShader(images[0], textureMode),
            "_TouchDisplaceMap" to textureShader(images[0], textureMode),
            "_SparkMap" to textureShader(images[1], Shader.TileMode.REPEAT),
            "_NoiseMap" to textureShader(images[2], textureMode))
        private val compose = PracticeNoiseCompose(images[0], compatibilityMode)
        protected val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val buffers = linkedMapOf<String, Bitmap>()
        private val children = linkedMapOf<String, BitmapShader>()
        private val canvases = linkedMapOf<String, Canvas>()
        private val rect = RectF()
        private var fieldWidth = 0f; private var fieldHeight = 0f
        private var rasterVersion = -1
        private var composeTime = Double.NaN
        private var touchWasEmpty = true
        private val weights = FloatArray(6) { (6 - it).toFloat().pow(2.65f) }.let { values ->
            val sum = values.sum(); FloatArray(values.size) { values[it] / sum }
        }
        var maskBuildCount = 0; private set
        var sizes: List<PracticeNoiseRenderSize> = emptyList(); private set
        private var w = 0; private var h = 0
        private var baseW = 0; private var baseH = 0
        private var sourcePixels = IntArray(0)
        private var original = IntArray(0)
        private var dilation = IntArray(0)
        private var horizontal = IntArray(0)
        private var nextDilation = IntArray(0)
        private var glow = FloatArray(0)
        private var edge = IntArray(0)
        protected val transparent = LinearGradient(0f, 0f, 1f, 1f, Color.TRANSPARENT, Color.TRANSPARENT, Shader.TileMode.CLAMP)

        private fun buffer(name: String): Bitmap = checkNotNull(buffers[name])
        open fun update(frame: PracticeNoiseFrame, width: Float, height: Float, touches: Map<Long, PracticeNoisePoint>): Boolean {
            // Preserve the APK's 1/8 mask grid at 1080p. Four nearest reads
            // per Compose texel cost less than the former 16 bilinear reads
            // on a 128x72 mask; the edge/glow and expensive shader budgets stay fixed.
            val baseSize = practiceNoiseBaseSize(width, height, compatibilityMode)
            val effectSize = practiceNoiseRenderSize(width, height, 256 * 144, 4)
            val newW = effectSize.width; val newH = effectSize.height
            val resized = w != newW || h != newH || baseW != baseSize.width || baseH != baseSize.height ||
                fieldWidth != width || fieldHeight != height
            if (resized) {
                buffers.values.forEach { it.recycle() }; buffers.clear(); children.clear(); canvases.clear()
                w = newW; h = newH; baseW = baseSize.width; baseH = baseSize.height
                fieldWidth = width; fieldHeight = height
                sourcePixels = IntArray(w * h); original = IntArray(w * h); dilation = IntArray(w * h)
                horizontal = IntArray(w * h); nextDilation = IntArray(w * h); glow = FloatArray(w * h); edge = IntArray(w * h)
                for (name in listOf("_RawActiveNormal", "_RawActiveSubtract", "_ComposeRT", "_DisabledComposeRT",
                    "_ReadyComposeRT", "_DisabledNormalBlockRT", "_DisabledSubtractBlockRT", "_EffectRT", "_TouchHoverRT")) {
                    val image = Bitmap.createBitmap(if (name == "_EffectRT") w else baseW,
                        if (name == "_EffectRT") h else baseH, Bitmap.Config.ARGB_8888)
                    buffers[name] = image; canvases[name] = Canvas(image)
                    children[name] = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        // BlockRender.Start creates point-filtered masks. Only
                        // its doubled EffectRT uses bilinear glow interpolation.
                        if (Build.VERSION.SDK_INT >= 33) setFilterMode(if (compatibilityMode || name == "_EffectRT") BitmapShader.FILTER_MODE_LINEAR
                            else BitmapShader.FILTER_MODE_NEAREST)
                        setLocalMatrix(Matrix().apply { setScale(width / image.width, height / image.height) })
                    }
                }
                rasterVersion = -1; composeTime = Double.NaN; touchWasEmpty = true
                sizes = listOf(baseSize, PracticeNoiseRenderSize(w, h), practiceNoiseOverlaySize(width, height, compatibilityMode))
            }
            if (resized || refreshMasks) updateMasks(frame)
            if (!compatibilityMode || resized || refreshMasks) updateTouchMask(width, height, resized)
            return resized
        }

        private fun updateMasks(frame: PracticeNoiseFrame) {
            val geometryChanged = rasterVersion != geometryVersion
            if (!geometryChanged && (normalMasks[2].isEmpty && subtractMasks[2].isEmpty || composeTime == frame.timeSeconds)) return
            fun raster(name: String, path: Path, color: Int = Color.RED) {
                val bitmap = buffer(name); bitmap.eraseColor(Color.BLACK)
                val c = checkNotNull(canvases[name]); val saved = c.save()
                c.scale(bitmap.width / fieldWidth, bitmap.height / fieldHeight)
                paint.shader = null; paint.xfermode = null; paint.style = Paint.Style.FILL
                paint.color = color; paint.alpha = 255; c.drawPath(path, paint)
                c.restoreToCount(saved)
            }
            if (geometryChanged) {
                raster("_RawActiveNormal", normalMasks[2]); raster("_RawActiveSubtract", subtractMasks[2])
                raster("_DisabledComposeRT", masks[0]); raster("_ReadyComposeRT", masks[1])
                raster("_DisabledNormalBlockRT", disabledNormal)
                raster("_DisabledSubtractBlockRT", disabledSubtract, Color.GREEN)
                rasterVersion = geometryVersion
            }
            composeTime = frame.timeSeconds; maskBuildCount++
            val composed = buffer("_ComposeRT"); composed.eraseColor(Color.BLACK)
            val effect = buffer("_EffectRT"); effect.eraseColor(Color.BLACK)
            if (normalMasks[2].isEmpty && subtractMasks[2].isEmpty) return
            compose.draw(composed, buffer("_RawActiveNormal"), buffer("_RawActiveSubtract"), frame.timeSeconds)
            val c = checkNotNull(canvases["_EffectRT"])
            paint.shader = null; paint.xfermode = null; paint.colorFilter = null
            paint.color = Color.WHITE; paint.alpha = 255; paint.style = Paint.Style.FILL
            rect.set(0f, 0f, w.toFloat(), h.toFloat())
            paint.isFilterBitmap = compatibilityMode
            c.drawBitmap(composed, null, rect, paint)
            effect.getPixels(sourcePixels, 0, w, 0, 0, w, h)
            for (i in original.indices) { original[i] = Color.red(sourcePixels[i]); dilation[i] = original[i]; glow[i] = 0f }
            for (ring in 0 until 6) {
                val weight = weights[ring]
                if (weight < .01f) continue
                // The shader takes the maximum of all nine neighbouring mask texels.
                for (y in 0 until h) for (x in 0 until w) {
                    val i = y * w + x
                    horizontal[i] = maxOf(dilation[i], dilation[y * w + (x - 1).coerceAtLeast(0)],
                        dilation[y * w + (x + 1).coerceAtMost(w - 1)])
                }
                for (y in 0 until h) for (x in 0 until w) {
                    val i = y * w + x
                    nextDilation[i] = maxOf(horizontal[i], horizontal[(y - 1).coerceAtLeast(0) * w + x],
                        horizontal[(y + 1).coerceAtMost(h - 1) * w + x])
                    if (ring == 0) edge[i] = (nextDilation[i] - original[i]).coerceAtLeast(0)
                    glow[i] += (nextDilation[i] - dilation[i]).coerceAtLeast(0) * (1f - original[i] / 255f) * weight
                }
                val previous = dilation; dilation = nextDilation; nextDilation = previous
            }
            for (i in sourcePixels.indices) sourcePixels[i] = Color.rgb(edge[i], glow[i].roundToInt().coerceIn(0, 255), 0)
            effect.setPixels(sourcePixels, 0, w, 0, 0, w, h)
        }

        private fun updateTouchMask(width: Float, height: Float, resized: Boolean) {
            if (hovers.isEmpty() && touchWasEmpty && !resized) return
            touchWasEmpty = hovers.isEmpty()
            val touchMask = buffer("_TouchHoverRT"); touchMask.eraseColor(Color.BLACK)
            val tc = checkNotNull(canvases["_TouchHoverRT"]); val saved = tc.save()
            tc.scale(touchMask.width / width, touchMask.height / height)
            paint.shader = null; paint.colorFilter = null; paint.xfermode = null
            paint.style = Paint.Style.FILL; paint.alpha = 255; paint.color = Color.WHITE
            paint.isFilterBitmap = true
            hovers.forEach { slot ->
                val radius = height * .253f * slot.scale(effectTime)
                rect.set(slot.point.x - radius, height - slot.point.y - radius,
                    slot.point.x + radius, height - slot.point.y + radius)
                tc.drawBitmap(hover, null, rect, paint)
            }
            tc.restoreToCount(saved)
        }

        protected fun shaderInputs(): Map<String, Shader> = shaders + children
        protected fun bitmapInputs(): Map<String, Bitmap> = mapOf(
            "_DisplaceMap" to images[0], "_TouchDisplaceMap" to images[0],
            "_SparkMap" to images[1], "_NoiseMap" to images[2]) + buffers
        fun validationInputs(): Map<String, Bitmap> = bitmapInputs()

        open fun release() {
            buffers.values.forEach { it.recycle() }; buffers.clear(); canvases.clear(); children.clear()
            images.forEach { it.recycle() }; hover.recycle()
        }
    }

    /** Original GLES3 fragment formula for devices before Android's RuntimeShader. */
    private inner class Legacy(context: Context) : Raster(context) {
        private val gl = PracticeNoiseGles(context, compatibilityMode)
        private val picture = Picture()
        private val background = PracticeNoiseBackground()
        private val clock = PracticeNoiseVisualClock()
        private var sceneBitmap: Bitmap? = null
        private var sceneCanvas: Canvas? = null
        private var cachedOutput: Bitmap? = null
        private var lastGeometry = -1
        private var lastTime = Double.NaN
        private var unavailable = false
        private val destination = RectF()
        val isReady get() = gl.isReady

        fun captureScene(height: Float, screenWidth: Float, scene: (Canvas) -> Unit) {
            val recording = picture.beginRecording(screenWidth.roundToInt(), height.roundToInt())
            try { scene(recording) } finally { picture.endRecording() }
        }
        fun drawOrdinaryScene(canvas: Canvas) { canvas.drawPicture(picture) }

        fun drawOverlay(canvas: Canvas, frame: PracticeNoiseFrame, left: Float, width: Float, height: Float,
            artwork: Bitmap, screenWidth: Float, touches: Map<Long, PracticeNoisePoint>) {
            if (!unavailable) try {
                renderOverlay(canvas, frame, left, width, height, artwork, screenWidth, touches)
                return
            } catch (failure: RuntimeException) {
                Log.w("PSQ Noise", "Device GLES3 failed; using emergency bitmap renderer", failure)
                unavailable = true; gl.release(); cachedOutput = null
            }
            drawFallback(canvas, frame, left, width, height)
        }

        private fun renderOverlay(canvas: Canvas, frame: PracticeNoiseFrame, left: Float, width: Float, height: Float,
            artwork: Bitmap, screenWidth: Float, touches: Map<Long, PracticeNoisePoint>) {
            val size = sizes.last()
            val resized = sceneBitmap?.width != size.width || sceneBitmap?.height != size.height
            if (resized) {
                sceneBitmap?.recycle()
                sceneBitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                sceneCanvas = Canvas(checkNotNull(sceneBitmap)); clock.reset()
            }
            if (lastGeometry != geometryVersion || frame.timeSeconds < lastTime || frame.timeSeconds - lastTime > .5) clock.reset()
            if (clock.refresh(effectTime, width, height, frame.regions.isEmpty())) {
                val backdrop = background.get(artwork, left, width, height, screenWidth, size)
                val source = checkNotNull(sceneCanvas); val saved = source.save()
                try {
                    source.scale(size.width / width, size.height / height)
                    paint.shader = backdrop; paint.alpha = 255; paint.style = Paint.Style.FILL; paint.isFilterBitmap = false
                    source.drawRect(0f, 0f, width, height, paint); paint.shader = null
                    source.translate(-left, 0f); source.drawPicture(picture)
                } finally { paint.shader = null; source.restoreToCount(saved) }
                val scene = checkNotNull(sceneBitmap)
                cachedOutput = gl.render(size, width, height, frame.timeSeconds, sizes[1], touches.values,
                    bitmapInputs() + mapOf("_SceneColor" to scene, "_BackgroundColor" to background.bitmapInput()))
                lastGeometry = geometryVersion; lastTime = frame.timeSeconds
            }
            val saved = canvas.save()
            try {
                canvas.translate(left, 0f); canvas.clipRect(0f, 0f, width, height)
                paint.shader = null; paint.colorFilter = null; paint.xfermode = null
                paint.style = Paint.Style.FILL; paint.alpha = 255; paint.isFilterBitmap = true
                destination.set(0f, 0f, width, height)
                canvas.drawBitmap(checkNotNull(cachedOutput), null, destination, paint)
            } finally { canvas.restoreToCount(saved) }
        }
        override fun release() {
            gl.release(); cachedOutput = null; sceneBitmap?.recycle(); sceneBitmap = null; sceneCanvas = null
            background.release(); super.release()
        }
    }

    @RequiresApi(33)
    private inner class Gpu(context: Context) : Raster(context) {
        private val shader = PracticeNoiseShader(context)
        private val sceneNode = RenderNode("PSQ Gameplay")
        private val scenePicture = Picture()
        private var sceneBitmap: Bitmap? = null
        private var sceneBitmapCanvas: Canvas? = null
        private var sceneBitmapShader: BitmapShader? = null
        private val node = RenderNode("PSQ Noise Overlay")
        private val background = PracticeNoiseBackground()

        override fun update(frame: PracticeNoiseFrame, width: Float, height: Float, touches: Map<Long, PracticeNoisePoint>): Boolean {
            val resized = super.update(frame, width, height, touches)
            if (resized) shader.inputs(shaderInputs())
            shader.update(frame.timeSeconds, width.roundToInt(), height.roundToInt(), sizes[1].width, sizes[1].height, touches.values)
            return resized
        }

        fun draw(canvas: Canvas, width: Float, height: Float, scene: Shader?, backdrop: Shader?) {
            shader.scene(scene ?: transparent, backdrop ?: transparent, overlayOnly = backdrop != null)
            paint.shader = shader.runtime; paint.alpha = 255; paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, width, height, paint); paint.shader = null
        }
        fun drawOrdinaryScene(canvas: Canvas, height: Float, screenWidth: Float, scene: (Canvas) -> Unit) {
            if (useSceneBitmap) {
                val ordinary = scenePicture.beginRecording(screenWidth.roundToInt(), height.roundToInt())
                try { scene(ordinary) } finally { scenePicture.endRecording() }
                canvas.drawPicture(scenePicture)
                return
            }
            sceneNode.setPosition(0, 0, screenWidth.roundToInt(), height.roundToInt())
            val ordinary = sceneNode.beginRecording(screenWidth.roundToInt(), height.roundToInt())
            try { scene(ordinary) } finally { sceneNode.endRecording() }
            canvas.drawRenderNode(sceneNode)
        }
        fun drawOverlay(canvas: Canvas, left: Float, width: Float, height: Float, artwork: Bitmap,
            screenWidth: Float) {
            val size = sizes.last()
            val sx = size.width / width; val sy = size.height / height
            val backdrop = background.get(artwork, left, width, height, screenWidth, size)
            if (useSceneBitmap) {
                if (sceneBitmap?.width != size.width || sceneBitmap?.height != size.height) {
                    sceneBitmap?.recycle()
                    val image = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                    sceneBitmap = image; sceneBitmapCanvas = Canvas(image)
                    sceneBitmapShader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setFilterMode(BitmapShader.FILTER_MODE_NEAREST)
                    }
                }
                val source = checkNotNull(sceneBitmapCanvas); val saved = source.save()
                try {
                    source.scale(sx, sy)
                    paint.shader = backdrop; paint.alpha = 255; paint.style = Paint.Style.FILL
                    source.drawRect(0f, 0f, width, height, paint); paint.shader = null
                    source.translate(-left, 0f); source.drawPicture(scenePicture)
                } finally { paint.shader = null; source.restoreToCount(saved) }
                shader.scene(checkNotNull(sceneBitmapShader), backdrop, sx, sy, true)
            } else shader.scene(null, backdrop, sx, sy, true)
            node.setPosition(0, 0, size.width, size.height)
            val recording = node.beginRecording(size.width, size.height)
            try {
                if (useSceneBitmap) {
                    // This is the unchanged official AGSL with a bounded bitmap
                    // SceneColor child, rendered as a Paint rather than RenderEffect.
                    paint.shader = shader.runtime; paint.alpha = 255; paint.style = Paint.Style.FILL
                    recording.drawRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), paint)
                } else {
                    // Opaque artwork establishes the complete input bounds even with
                    // no notes. A transparent clear can be culled by the GPU driver.
                    recording.scale(sx, sy)
                    paint.shader = backdrop; paint.alpha = 255; paint.style = Paint.Style.FILL
                    recording.drawRect(0f, 0f, width, height, paint); paint.shader = null
                    recording.translate(-left, 0f); recording.drawRenderNode(sceneNode)
                }
            } finally { paint.shader = null; node.endRecording() }
            // RenderEffect snapshots shader uniforms: recreate after updates, not a stale cached effect.
            node.setRenderEffect(if (useSceneBitmap) null else RenderEffect.createRuntimeShaderEffect(shader.runtime, "_SceneColor"))
            val saved = canvas.save()
            try {
                canvas.translate(left, 0f); canvas.clipRect(0f, 0f, width, height)
                canvas.scale(1f / sx, 1f / sy); canvas.drawRenderNode(node)
            } finally { canvas.restoreToCount(saved) }
        }
        override fun release() {
            node.discardDisplayList(); sceneNode.discardDisplayList(); background.release()
            sceneBitmap?.recycle(); sceneBitmap = null; sceneBitmapCanvas = null; sceneBitmapShader = null
            super.release()
        }
    }
}
