package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.plcliangpicup.phigrosscore.data.PracticeField

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PracticeHitEffectRenderTest {
    @Test fun officialSizeGrowsBeforeSettlingAndTheTravelSlowsDown() {
        assertEquals(.49884492f, PracticeHitMotion.size(0f), .000001f)
        assertEquals(.6941143f, PracticeHitMotion.size(1f), .000001f)
        assertTrue(PracticeHitMotion.size(.5f) > PracticeHitMotion.size(0f))
        assertTrue(PracticeHitMotion.size(.5f) > PracticeHitMotion.size(1f))
        val earlyTravel = PracticeHitMotion.distance(25f, .2f)
        val lateTravel = PracticeHitMotion.distance(25f, 1f) - PracticeHitMotion.distance(25f, .8f)
        assertTrue(lateTravel > 0f && lateTravel < earlyTravel)
    }

    @Test fun sameHitKeepsItsAnglesAndExportsTheProductionRendering() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val atlas = context.assets.open("practice/hit_fx.png").use { checkNotNull(BitmapFactory.decodeStream(it)) }
        val visual = PracticeHitVisual(Random(31))
        val size = PracticeField(1280f, 720f).noteWidth * .95f * 1.6f
        val tint = PRACTICE_HIT_PERFECT_TINT
        val filter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val source = Rect()
        val target = RectF()

        fun render(burst: PracticeHitVisual, time: Float = .14f): Bitmap {
            val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val saved = canvas.saveCount
            burst.draw(canvas, 320f, 320f, size, time, .5f, atlas, 6, 5, tint, filter, paint, source, target)
            assertEquals("Particle rotation must not leak into subsequent drawing", saved, canvas.saveCount)
            return bitmap
        }

        try {
            val first = render(visual)
            val again = render(visual)
            val other = render(PracticeHitVisual(Random(32)))
            val spriteFinished = render(visual, .51f)
            val finished = render(visual, .6f)
            val empty = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            try {
                assertFalse(first.sameAs(empty))
                assertTrue("Redrawing one hit must keep its particle angles", first.sameAs(again))
                assertFalse("Separate hits can have different angles", first.sameAs(other))
                assertFalse("Particles outlive the last central sprite frame", spriteFinished.sameAs(empty))
                assertTrue("Expired effects must disappear", finished.sameAs(empty))
            } finally { listOf(first, again, other, spriteFinished, finished, empty).forEach { it.recycle() } }

            System.getenv("PSQ_HIT_RENDER_OUTPUT")?.let { directory ->
                val preview = Bitmap.createBitmap(1920, 1130, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(preview)
                    canvas.drawColor(Color.rgb(17, 21, 29))
                    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(237, 240, 247)
                        textSize = 40f
                        typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    }
                    canvas.drawText("PSQ  /  HIT EFFECT", 64f, 72f, text)
                    val times = listOf(60, 160, 360)
                    for (row in 0..1) {
                        val color = if (row == 0) tint else PRACTICE_HIT_GOOD_TINT
                        val gradeFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                        val burst = if (row == 0) visual else PracticeHitVisual(Random(32), perfect = false)
                        val rowTop = 122f + row * 486f
                        text.textSize = 32f
                        text.color = color
                        canvas.drawText(if (row == 0) "Perfect" else "Good", 64f, rowTop + 38f, text)
                        for ((column, ms) in times.withIndex()) {
                            val left = 32f + column * 632f
                            val x = left + 296f
                            val y = rowTop + 280f
                            val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(22, 27, 37) }
                            canvas.drawRoundRect(left, rowTop + 72f, left + 592f, rowTop + 458f, 16f, 16f, panel)
                            panel.color = Color.rgb(71, 77, 88)
                            panel.strokeWidth = 2f
                            canvas.drawLine(left + 38f, y, left + 554f, y, panel)
                            burst.draw(canvas, x, y, size, ms / 1000f, .5f, atlas, 6, 5, color,
                                gradeFilter, paint, source, target)
                            text.textSize = 25f
                            text.color = Color.rgb(168, 177, 194)
                            canvas.drawText("$ms ms", left + 22f, rowTop + 107f, text)
                        }
                    }
                    val file = File(directory, "PSQ_Hit_Effect_Official_Preview_20261005.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { preview.recycle() }
            }
        } finally { atlas.recycle() }
    }
}
