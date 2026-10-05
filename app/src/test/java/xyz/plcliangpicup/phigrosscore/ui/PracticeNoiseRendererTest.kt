package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RenderNode
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.BitmapShader
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.plcliangpicup.phigrosscore.data.PracticeBlockArea
import xyz.plcliangpicup.phigrosscore.data.PracticeNoisePoint
import xyz.plcliangpicup.phigrosscore.data.PracticeNoiseRuntime
import xyz.plcliangpicup.phigrosscore.data.PracticeNoiseState
import xyz.plcliangpicup.phigrosscore.data.PracticeNoiseFrame
import xyz.plcliangpicup.phigrosscore.data.PracticeChartParser
import xyz.plcliangpicup.phigrosscore.data.smoothArtworkBlur

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PracticeNoiseRendererTest {
    @Test fun explicitCompatibilityUsesBoundedV100MasksAndBypassesRuntimeShader() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext(), compatibilityMode = true)
        try {
            val frame = PracticeNoiseRuntime(listOf(area(.1f, .1f, .9f, .9f), area(.4f, .3f, .6f, .7f, true)))
                .frame(2.0, 1920f, 1080f)
            assertFalse(renderer.usesOfficialShader)
            val inputs = renderer.officialInputsForValidation(frame, 1920f, 1080f, emptyMap(), 1.0)
            try {
                assertEquals(128, inputs.getValue("_ComposeRT").width)
                assertEquals(72, inputs.getValue("_ComposeRT").height)
                assertEquals(256, inputs.getValue("_EffectRT").width)
                assertEquals(144, inputs.getValue("_EffectRT").height)
            } finally { inputs.values.forEach { it.recycle() } }
            for ((w, h) in listOf(1920f to 1080f, 1080f to 1920f, 4096f to 2048f)) {
                val size = practiceNoiseOverlaySize(w, h, true)
                assertTrue(size.width * size.height <= 512 * 288)
            }
        } finally { renderer.release() }
    }

    @Test fun android13KeepsTheOfficialRuntimeShader() {
        assertFalse(practiceNoiseUsesRuntimeShader(26))
        assertTrue(practiceNoiseUsesRuntimeShader(33))
        assertTrue(practiceNoiseUsesRuntimeShader(34))
        assertTrue(practiceNoiseUsesRuntimeShader(35))
    }

    @Test fun directShaderSceneKeepsOfficialEffectsAndRecordsGameplayOnce() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext(), useSceneBitmap = true)
        val parent = RenderNode("noise-direct-shader-test").apply { setPosition(0, 0, 2400, 1080) }
        val artwork = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        var calls = 0
        try {
            assertTrue(renderer.usesOfficialShader)
            renderer.drawScene(parent.beginRecording(2400, 1080),
                PracticeNoiseRuntime(listOf(area(.1f, .1f, .9f, .9f), area(.4f, .3f, .6f, .7f, true))).frame(2.0, 1920f, 1080f),
                240f, 1920f, 1080f, emptyMap(), artwork, 2400f) { scene ->
                calls++
                scene.drawRect(1190f, 400f, 1210f, 500f, Paint().apply { color = Color.WHITE })
            }
            parent.endRecording()
            assertEquals(1, calls)
            assertTrue(parent.hasDisplayList())
            val sizes = renderer.visualSizes
            assertEquals(PracticeNoiseRenderSize(240, 135), sizes[0])
            assertTrue(sizes[1].width * sizes[1].height <= 256 * 144)
            assertTrue(sizes[2].width * sizes[2].height <= 640 * 360)
        } finally { parent.discardDisplayList(); renderer.release(); artwork.recycle() }
    }

    @Test fun directShaderSceneHasFullBoundsEvenWhenNoNotesAreDrawn() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext(), useSceneBitmap = true)
        val parent = RenderNode("noise-direct-empty-scene").apply { setPosition(0, 0, 1200, 540) }
        val artwork = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        var calls = 0
        try {
            for ((width, height) in listOf(960f to 540f, 540f to 960f)) {
                renderer.drawScene(parent.beginRecording(1200, 960),
                    PracticeNoiseRuntime(listOf(area(.0f, .0f, 1f, 1f))).frame(2.0, width, height),
                    120f, width, height, emptyMap(), artwork, 1200f) { calls++ }
                parent.endRecording()
                assertTrue(renderer.usesOfficialShader)
                assertTrue(parent.hasDisplayList())
                assertTrue(renderer.visualSizes.last().let { it.width * it.height <= 640 * 360 })
            }
            assertEquals(2, calls)
        } finally { parent.discardDisplayList(); renderer.release(); artwork.recycle() }
    }

    private fun canvasFor(bitmap: Bitmap) = Canvas(bitmap).also { canvas ->
        Class.forName("android.graphics.BaseCanvas").getMethod("setHwFeaturesInSwModeEnabled", Boolean::class.javaPrimitiveType)
            .invoke(canvas, true)
    }
    private fun save(bitmap: Bitmap, name: String) {
        System.getenv("PSQ_NOISE_RENDER_OUTPUT")?.let { directory ->
            val file = File(directory, name); file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun area(left: Float, bottom: Float, right: Float, top: Float, subtract: Boolean = false) =
        PracticeBlockArea(PracticeNoisePoint(right, top), PracticeNoisePoint(left, bottom),
            appearTime = 0.0, enableTime = 1.0, disableTime = 3.0, disappearTime = 4.0, isSubtract = subtract)

    /** Raster equivalent of the production 640x360 overlay over a 1920x1080 field.
     * Canvas scaling supplies the same field coordinates as _SceneRenderScale.
     * The final bitmap is bilinearly enlarged like a transformed RenderNode;
     * this does not silently substitute a full-resolution expensive shader.
     */
    private fun boundedPreview(renderer: PracticeNoiseRenderer, frame: PracticeNoiseFrame,
        artwork: Bitmap, touches: Map<Long, PracticeNoisePoint> = emptyMap(), effectTime: Double = 0.0): Bitmap {
        val width = 1920f; val height = 1080f
        val size = practiceNoiseRenderSize(width, height, 640 * 360)
        val overlay = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val backdrop = PracticeNoiseBackground()
        val output = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        try {
            val transparent = LinearGradient(0f, 0f, 1f, 1f, Color.TRANSPARENT, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            val reduced = canvasFor(overlay)
            reduced.scale(size.width / width, size.height / height)
            renderer.draw(reduced, frame, 0f, width, height, touches, effectTime, transparent,
                backdrop.get(artwork, 0f, width, height, width, size))
            assertEquals(size, renderer.visualSizes.last())
            val canvas = Canvas(output)
            canvas.drawColor(Color.rgb(16, 17, 22))
            val cropScale = maxOf(width / artwork.width, height / artwork.height)
            val bw = artwork.width * cropScale; val bh = artwork.height * cropScale
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 61 }
            canvas.drawBitmap(artwork, null, RectF((width - bw) / 2, (height - bh) / 2,
                (width + bw) / 2, (height + bh) / 2), paint)
            paint.color = Color.BLACK; paint.alpha = 128
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.alpha = 255
            canvas.drawBitmap(overlay, null, RectF(0f, 0f, width, height), paint)
        } finally { overlay.recycle(); backdrop.release() }
        return output
    }

    @Test fun pointFilteringAndMirrorWrappingAgreeBetweenNativeAndCpuSampling() {
        val texture = Bitmap.createBitmap(intArrayOf(Color.RED, Color.BLUE, Color.GREEN, Color.WHITE),
            2, 2, Bitmap.Config.ARGB_8888)
        val output = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        try {
            val shader = practiceNoiseTextureShader(texture, Shader.TileMode.MIRROR).apply {
                setLocalMatrix(Matrix().apply { setScale(4f, 4f) })
            }
            val canvas = Canvas(output)
            canvas.translate(8f, 4f)
            canvas.drawRect(-8f, -4f, 24f, 20f, Paint().apply { this.shader = shader })
            for (y in 0 until output.height) for (x in 0 until output.width) {
                val tx = practiceNoiseMirrorTexel((x + .5f - 8f) / 8f, 2)
                val ty = practiceNoiseMirrorTexel((y + .5f - 4f) / 8f, 2)
                assertEquals("Nearest mirrored texel at ($x,$y)", texture.getPixel(tx, ty), output.getPixel(x, y))
            }
        } finally { texture.recycle(); output.recycle() }
    }

    @Test fun interiorPointNoiseStaysVisibleAndAnimatesWithinTheProductionPixelBudget() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val runtime = PracticeNoiseRuntime(listOf(area(.12f, .12f, .88f, .88f)))
        val artwork = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888)
        Canvas(artwork).drawRect(0f, 0f, 96f, 54f, Paint().apply {
            shader = LinearGradient(0f, 0f, 96f, 54f, Color.rgb(28, 57, 86),
                Color.rgb(139, 105, 69), Shader.TileMode.CLAMP)
        })
        try {
            val first = boundedPreview(renderer, runtime.frame(2.0, 1920f, 1080f), artwork)
            val second = boundedPreview(renderer, runtime.frame(2.1, 1920f, 1080f), artwork, effectTime = .11)
            var sparks = 0; var changed = 0
            // Stay well away from the perimeter: bright pixels here are PointNoise,
            // not edge glow or finger feedback. Sample enlarged texel centres.
            for (y in 270 until 810 step 3) for (x in 480 until 1440 step 3) {
                val pixel = first.getPixel(x + 1, y + 1)
                if (Color.red(pixel) > 100) sparks++
                if (pixel != second.getPixel(x + 1, y + 1)) changed++
            }
            assertTrue("Interior sparks must survive bounded rendering: $sparks", sparks > 30)
            assertTrue("Interior sparks/clouds must evolve: $changed", changed > 100)
            assertTrue(Color.blue(first.getPixel(30, 30)) > 0)
            save(first, "noise-pixel-active.png"); save(second, "noise-pixel-active-next.png")
            val points = mapOf(1L to PracticeNoisePoint(1000f, 540f))
            boundedPreview(renderer, runtime.frame(2.11, 1920f, 1080f), artwork, points, .12).recycle()
            val touched = boundedPreview(renderer, runtime.frame(2.22, 1920f, 1080f), artwork, points, .23)
            assertTrue(Color.red(touched.getPixel(1000, 540)) > Color.red(second.getPixel(1000, 540)))
            save(touched, "noise-pixel-touch.png")
            val ready = boundedPreview(renderer, runtime.frame(.75, 1920f, 1080f), artwork)
            val disabled = boundedPreview(renderer, runtime.frame(.1, 1920f, 1080f), artwork)
            save(ready, "noise-pixel-ready.png"); save(disabled, "noise-pixel-disabled.png")
            first.recycle(); second.recycle(); touched.recycle(); ready.recycle(); disabled.recycle()
        } finally { renderer.release(); artwork.recycle() }
    }

    @Test fun exportActualChartNoisePreviewWhenLocalDeliveryIsAvailable() {
        val path = System.getenv("PSQ_NOISE_PREVIEW_PEZ") ?: return
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        var artwork: Bitmap? = null
        try {
            ZipFile(path).use { zip ->
                val chartJson = zip.getInputStream(zip.getEntry("chart.json")).reader().use { it.readText() }
                val chart = PracticeChartParser.parse(chartJson, File("music"), File("artwork"))
                val imageEntry = zip.entries().asSequence().first { it.name.endsWith(".jpg", ignoreCase = true) }
                val original = zip.getInputStream(imageEntry).use { checkNotNull(BitmapFactory.decodeStream(it)) }
                val radius = (minOf(original.width, original.height) / 12).coerceIn(2, 128)
                val background = runBlocking { smoothArtworkBlur(original, radius) }
                artwork = background; original.recycle()
                val runtime = PracticeNoiseRuntime(chart.blockAreas)
                for (time in listOf(62.0, 83.0, 110.0)) {
                    val frame = runtime.frame(time, 1920f, 1080f)
                    assertTrue(frame.regions.isNotEmpty())
                    val output = boundedPreview(renderer, frame, background)
                    save(output, "noise-pixel-hate-at-${time.toInt()}s.png")
                    output.recycle()
                }
            }
        } finally { renderer.release(); artwork?.recycle() }
    }

    private fun render(areas: List<PracticeBlockArea>, time: Double, name: String): Bitmap {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val bitmap = Bitmap.createBitmap(960, 360, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(renderer.usesOfficialShader)
            val canvas = Canvas(bitmap)
            // Native Skia supports RuntimeEffect on raster surfaces; this hidden
            // test-only flag bypasses Android's Java hardware-canvas restriction.
            Class.forName("android.graphics.BaseCanvas").getMethod("setHwFeaturesInSwModeEnabled", Boolean::class.javaPrimitiveType)
                .invoke(canvas, true)
            renderer.draw(canvas, PracticeNoiseRuntime(areas).frame(time, 640f, 360f), 160f, 640f, 360f,
                sceneShader = LinearGradient(0f, 0f, 640f, 360f, Color.BLACK, Color.BLACK, Shader.TileMode.CLAMP))
            System.getenv("PSQ_NOISE_RENDER_OUTPUT")?.let { directory ->
                val file = File(directory, name)
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { renderer.release() }
        return bitmap
    }

    @Test fun subtractHoleAndFieldMarginsRemainTransparent() {
        val areas = listOf(area(.1f, .1f, .9f, .9f), area(.2f, .2f, .8f, .8f, true))
        val bitmap = render(areas, 2.0, "noise-active-hole.png")
        assertTrue(Color.red(bitmap.getPixel(250, 180)) > 0)
        assertEquals(Color.BLACK, bitmap.getPixel(480, 180))
        assertEquals(0, Color.alpha(bitmap.getPixel(80, 180)))
        assertEquals(0, Color.alpha(bitmap.getPixel(880, 180)))
        assertFalse(PracticeNoiseRuntime(areas).frame(2.0, 160f, 120f).blocksAt(80f, 60f))
    }

    @Test fun overlappingSubtractAreasRestoreTheFilledMask() {
        val subtract = area(.4f, .4f, .6f, .6f, true)
        val areas = listOf(area(.1f, .1f, .9f, .9f), subtract, subtract)
        val bitmap = render(areas, 2.0, "noise-active-parity.png")
        assertTrue(Color.red(bitmap.getPixel(480, 180)) > 0)
        assertTrue(PracticeNoiseRuntime(areas).frame(2.0, 160f, 120f).blocksAt(80f, 60f))
    }

    @Test fun readyStateIsVisibleWithoutBlockingTouch() {
        val areas = listOf(area(.1f, .1f, .9f, .9f))
        val frame = PracticeNoiseRuntime(areas).frame(.75, 160f, 120f)
        assertEquals(PracticeNoiseState.READY, frame.regions.single().state)
        assertFalse(frame.blocksAt(80f, 60f))
        val bitmap = render(areas, .75, "noise-ready.png")
        assertTrue(Color.alpha(bitmap.getPixel(480, 180)) > 0)
        assertTrue(Color.red(bitmap.getPixel(480, 180)) > 0)
    }

    @Test fun officialTouchNoiseShowsFollowsFadesAndClearsOnSeek() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val scene = LinearGradient(0f, 0f, 640f, 360f, Color.BLACK, Color.BLACK, Shader.TileMode.CLAMP)
        fun frame(time: Double, effect: Double, touches: Map<Long, PracticeNoisePoint>): Bitmap {
            val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
            renderer.draw(canvasFor(bitmap), PracticeNoiseFrame(time, emptyList()), 0f, 640f, 360f,
                touches, effect, scene)
            return bitmap
        }
        fun redPixels(bitmap: Bitmap): Int {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels.count { Color.red(it) > 20 }
        }
        try {
            val points = mapOf(1L to PracticeNoisePoint(160f, 180f), 2L to PracticeNoisePoint(480f, 180f))
            assertEquals(0, redPixels(frame(2.0, 0.0, points)))
            val shown = frame(2.01, .11, points)
            assertTrue(redPixels(shown) > 500); save(shown, "noise-touch-two-fingers.png")
            val moved = frame(2.02, .12, mapOf(1L to PracticeNoisePoint(260f, 180f), 2L to points.getValue(2L)))
            assertTrue(redPixels(moved) > 500); save(moved, "noise-touch-follow.png")
            frame(2.03, .13, emptyMap())
            val fading = frame(2.04, .18, emptyMap())
            assertTrue(redPixels(fading) in 1 until redPixels(shown))
            assertEquals(0, redPixels(frame(2.05, .24, emptyMap())))
            frame(2.06, .25, points); frame(2.07, .36, points)
            assertEquals(0, redPixels(frame(.1, .37, emptyMap())))
        } finally { renderer.release() }
    }

    @Test fun sceneSamplingAndTimeChangeTheOfficialActiveTexture() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val scene = LinearGradient(0f, 0f, 640f, 360f, Color.rgb(24, 45, 72), Color.rgb(78, 103, 151), Shader.TileMode.CLAMP)
        val runtime = PracticeNoiseRuntime(listOf(area(.1f, .1f, .9f, .9f)))
        fun frame(time: Double): Bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).also {
            renderer.draw(canvasFor(it), runtime.frame(time, 640f, 360f), 0f, 640f, 360f, sceneShader = scene)
        }
        try {
            val first = frame(2.0); val second = frame(2.1)
            assertFalse(first.sameAs(second)); save(first, "noise-active-scene.png")
        } finally { renderer.release() }
    }

    @Test fun fallbackTouchUsesNoiseAndSdfWithoutRuntimeShader() {
        val fallback = PracticeNoiseFallback(ApplicationProvider.getApplicationContext())
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val touches = listOf(PracticeNoiseTouch.Hover(1L, PracticeNoisePoint(320f, 180f), 0f, 1f, 0.0))
        try {
            fallback.draw(Canvas(bitmap), 2.0, 640f, 360f, Array(3) { android.graphics.Path() }, touches, .11)
            val pixels = IntArray(640 * 360); bitmap.getPixels(pixels, 0, 640, 0, 0, 640, 360)
            assertTrue(pixels.count { Color.red(it) > 20 } > 500)
            assertEquals(Color.BLACK, bitmap.getPixel(0, 0)); save(bitmap, "noise-touch-fallback.png")
        } finally { fallback.release() }
    }

    @Test fun hardwareSceneRecordsWithTheOfficialRuntimeRenderEffect() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val parent = RenderNode("noise-test-parent")
        parent.setPosition(0, 0, 640, 360)
        val background = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val canvas = parent.beginRecording(640, 360)
        var recorded = false
        try {
            assertTrue(canvas.isHardwareAccelerated)
            val frame = PracticeNoiseRuntime(listOf(area(.1f, .1f, .9f, .9f))).frame(2.0, 640f, 360f)
            renderer.drawScene(canvas, frame, 0f, 640f, 360f, emptyMap(), background, 640f) { scene ->
                assertTrue(scene.isHardwareAccelerated); scene.drawColor(Color.BLACK); recorded = true
            }
            parent.endRecording()
            assertTrue(recorded); assertTrue(parent.hasDisplayList())
        } finally { parent.discardDisplayList(); renderer.release(); background.recycle() }
    }

    @Test fun artworkAndNotesSurviveOutsideNoiseAndInSubtractHoles() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val backdrop = PracticeNoiseBackground()
        val artwork = Bitmap.createBitmap(96, 36, Bitmap.Config.ARGB_8888)
        Canvas(artwork).drawRect(0f, 0f, 96f, 36f, Paint().apply {
            shader = LinearGradient(0f, 0f, 96f, 36f, Color.BLUE, Color.GREEN, Shader.TileMode.CLAMP)
        })
        val output = Bitmap.createBitmap(960, 360, Bitmap.Config.ARGB_8888)
        val c = canvasFor(output)
        c.drawColor(Color.rgb(16, 17, 22))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 61 }
        c.drawBitmap(artwork, null, RectF(0f, 0f, 960f, 360f), paint)
        paint.color = Color.BLACK; paint.alpha = 128; c.drawRect(0f, 0f, 960f, 360f, paint)
        paint.color = Color.WHITE; paint.alpha = 255; c.drawRect(450f, 170f, 510f, 190f, paint)
        val before = output.copy(Bitmap.Config.ARGB_8888, false)
        val transparent = LinearGradient(0f, 0f, 1f, 1f, Color.TRANSPARENT, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        try {
            val background = backdrop.get(artwork, 160f, 640f, 360f, 960f, PracticeNoiseRenderSize(640, 360))
            // The scene child contains notes only; the artwork lives in the explicit backdrop.
            renderer.draw(c, PracticeNoiseRuntime(listOf(area(.1f, .1f, .9f, .9f),
                area(.2f, .2f, .8f, .8f, true))).frame(2.0, 640f, 360f),
                160f, 640f, 360f, effectTimeSeconds = 0.0, sceneShader = transparent, backgroundShader = background)
            for ((x, y) in listOf(80 to 180, 180 to 30, 480 to 140, 480 to 180, 880 to 180)) {
                assertEquals("Artwork/note at ($x,$y) must survive", before.getPixel(x, y), output.getPixel(x, y))
            }
            assertTrue(Color.blue(output.getPixel(480, 140)) > 0)
            assertTrue(Color.red(output.getPixel(250, 180)) > Color.red(before.getPixel(250, 180)))
            save(output, "noise-background-preserved.png")
        } finally { renderer.release(); backdrop.release(); artwork.recycle(); before.recycle() }
    }

    @Test fun disabledNoiseUsesTheCroppedArtworkInsteadOfBlackSceneInput() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val backdrop = PracticeNoiseBackground()
        val artwork = Bitmap.createBitmap(96, 36, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val transparent = LinearGradient(0f, 0f, 1f, 1f, Color.TRANSPARENT, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        try {
            val output = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
            renderer.draw(canvasFor(output), PracticeNoiseRuntime(listOf(area(.1f, .1f, .9f, .9f))).frame(.1, 640f, 360f),
                0f, 640f, 360f, effectTimeSeconds = 0.0, sceneShader = transparent,
                backgroundShader = backdrop.get(artwork, 0f, 640f, 360f, 640f, PracticeNoiseRenderSize(640, 360)))
            assertTrue(Color.blue(output.getPixel(320, 180)) >= 35)
            assertTrue(Color.red(output.getPixel(320, 180)) > 20)
            assertEquals(0, Color.alpha(output.getPixel(0, 0)))
            save(output, "noise-disabled-artwork.png")
        } finally { renderer.release(); backdrop.release(); artwork.recycle() }
    }

    @Test fun maskUpdatesAreBoundedWhileAnimationAndSeekKeepWorking() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val runtime = PracticeNoiseRuntime(listOf(area(.1f, .1f, .9f, .9f)))
        val black = LinearGradient(0f, 0f, 1f, 1f, Color.BLACK, Color.BLACK, Shader.TileMode.CLAMP)
        val output = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        try {
            repeat(61) { index ->
                renderer.draw(canvasFor(output), runtime.frame(2.0 + index / 600.0, 160f, 90f),
                    0f, 160f, 90f, effectTimeSeconds = index / 60.0, sceneShader = black)
            }
            assertTrue(renderer.maskBuildCount in 30..31)
            assertEquals(1, renderer.geometryBuildCount)
            renderer.draw(canvasFor(output), runtime.frame(.1, 160f, 90f), 0f, 160f, 90f,
                effectTimeSeconds = 1.001, sceneShader = black)
            assertEquals(2, renderer.geometryBuildCount)
            assertTrue(Color.red(output.getPixel(80, 45)) > 0)
            renderer.draw(canvasFor(output), PracticeNoiseFrame(.11, emptyList()), 0f, 160f, 90f,
                effectTimeSeconds = 1.002, sceneShader = black)
            assertEquals(Color.BLACK, output.getPixel(80, 45))
        } finally { renderer.release() }
    }

    @Test fun fullHdHardwareUsesBoundedEffectsAndRecordsGameplayOnceAtOriginalResolution() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val parent = RenderNode("noise-budget-test").apply { setPosition(0, 0, 2400, 1080) }
        val artwork = Bitmap.createBitmap(96, 36, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val canvas = parent.beginRecording(2400, 1080)
        var calls = 0
        try {
            renderer.drawScene(canvas, PracticeNoiseRuntime(listOf(area(.1f, .1f, .9f, .9f))).frame(2.0, 1920f, 1080f),
                240f, 1920f, 1080f, emptyMap(), artwork, 2400f) { scene ->
                assertEquals(2400, scene.width); assertEquals(1080, scene.height); calls++
                scene.drawRect(1190f, 400f, 1210f, 500f, Paint().apply { color = Color.WHITE })
            }
            parent.endRecording()
            assertEquals(1, calls)
            val sizes = renderer.visualSizes
            assertEquals(PracticeNoiseRenderSize(240, 135), sizes[0])
            assertTrue(sizes[0].width * sizes[0].height <= 240 * 135)
            assertTrue(sizes[1].width * sizes[1].height <= 256 * 144)
            assertTrue(sizes[2].width * sizes[2].height <= 640 * 360)
        } finally { parent.discardDisplayList(); renderer.release(); artwork.recycle() }
    }

    @Test fun fallbackCachesEachFingerAndFollowsMovementWithoutRebuildingEveryFrame() {
        val fallback = PracticeNoiseFallback(ApplicationProvider.getApplicationContext())
        val output = Bitmap.createBitmap(960, 360, Bitmap.Config.ARGB_8888)
        val touches = (1L..10L).map { PracticeNoiseTouch.Hover(it, PracticeNoisePoint(200f + it * 30, 180f), 1f, 1f, 0.0) }
        val masks = Array(3) { android.graphics.Path() }
        try {
            fallback.draw(Canvas(output), 2.0, 960f, 360f, masks, touches, .11)
            assertEquals(10, fallback.touchBuildCount)
            touches.forEach { it.point = PracticeNoisePoint(it.point.x + 20f, it.point.y) }
            fallback.draw(Canvas(output), 2.01, 960f, 360f, masks, touches, .12)
            assertEquals(10, fallback.touchBuildCount)
            fallback.draw(Canvas(output), 2.02, 960f, 360f, masks, touches, .14)
            assertEquals(20, fallback.touchBuildCount)
            fallback.draw(Canvas(output), 2.03, 960f, 360f, masks, emptyList(), .15)
            fallback.draw(Canvas(output), 2.04, 960f, 360f, masks, touches, .16)
            assertEquals(30, fallback.touchBuildCount)
        } finally { fallback.release() }
    }

    @Test fun reducedSceneCoordinatesSampleTheArtworkAndNotesAtTheCorrectPosition() {
        val shader = PracticeNoiseShader(ApplicationProvider.getApplicationContext())
        fun solid(color: Int): Shader = LinearGradient(0f, 0f, 1f, 1f, color, color, Shader.TileMode.CLAMP)
        val black = solid(Color.BLACK)
        shader.inputs(listOf("_ComposeRT", "_EffectRT", "_DisabledNormalBlockRT", "_DisabledSubtractBlockRT",
            "_ReadyComposeRT", "_TouchHoverRT", "_DisplaceMap", "_TouchDisplaceMap", "_SparkMap", "_NoiseMap")
            .associateWith { black } + mapOf("_DisabledComposeRT" to solid(Color.RED)))
        shader.update(2.0, 1920, 1080, 256, 144, emptyList())
        val source = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        Canvas(source).drawRect(300f, 150f, 340f, 170f, Paint().apply { color = Color.WHITE })
        val sourceShader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val backdrop = LinearGradient(0f, 0f, 1920f, 1080f, Color.BLUE, Color.GREEN, Shader.TileMode.CLAMP)
        val output = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        try {
            shader.scene(sourceShader, backdrop, 1f / 3, 1f / 3, true)
            canvasFor(output).drawRect(0f, 0f, 640f, 360f, Paint().apply { this.shader = shader.runtime })
            assertEquals(Color.WHITE, output.getPixel(320, 160))
            assertTrue(Color.blue(output.getPixel(100, 100)) > 150)
            assertTrue(Color.green(output.getPixel(540, 260)) > Color.green(output.getPixel(100, 100)))
            save(output, "noise-reduced-scene-artwork.png")
        } finally { source.recycle() }
    }

    @Test fun warmupAllocatesNoiseResourcesBeforeTheFirstAreaAppears() {
        val renderer = PracticeNoiseRenderer(ApplicationProvider.getApplicationContext())
        val parent = RenderNode("noise-warmup-test").apply { setPosition(0, 0, 1920, 1080) }
        val artwork = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        var calls = 0
        try {
            renderer.drawScene(parent.beginRecording(1920, 1080), PracticeNoiseFrame(0.0, emptyList()),
                0f, 1920f, 1080f, emptyMap(), artwork, 1920f, warmUp = true) { calls++ }
            parent.endRecording()
            assertEquals(1, calls)
            assertEquals(3, renderer.visualSizes.size)
            assertEquals(1, renderer.maskBuildCount)
            assertTrue(parent.hasDisplayList())
        } finally { parent.discardDisplayList(); renderer.release(); artwork.recycle() }
    }
}
