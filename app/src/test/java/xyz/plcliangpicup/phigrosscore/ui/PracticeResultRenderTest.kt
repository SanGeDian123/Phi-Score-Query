package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.plcliangpicup.phigrosscore.data.calculatePlayScoreAndAccuracy
import xyz.plcliangpicup.phigrosscore.data.calculateChartRks
import xyz.plcliangpicup.phigrosscore.data.smoothArtworkBlur

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PracticeResultRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightStrictResult() = render(false, true, "result-light-strict.png")
    @Test fun darkStrictResult() = render(true, true, "result-dark-strict.png")
    @Test fun normalResultOmitsStrictBadge() = render(false, false, "result-light-normal.png")
    @Test @Config(qualifiers = "w854dp-h393dp-land-mdpi")
    fun compactStrictResult() = render(false, true, "result-phone-strict.png")
    @Test @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    fun tabletStrictResult() = render(true, true, "result-tablet-strict.png")

    private fun render(dark: Boolean, strict: Boolean, name: String) {
        val path = System.getenv("PSQ_EM_IN_PEZ")
        assumeTrue("Set PSQ_EM_IN_PEZ and PSQ_RESULT_OUTPUT to render result previews", path != null && System.getenv("PSQ_RESULT_OUTPUT") != null)
        val artwork = ZipFile(path).use { zip ->
            val entry = zip.entries().asSequence().first { it.name.endsWith(".jpg") }
            zip.getInputStream(entry).use { checkNotNull(BitmapFactory.decodeStream(it)) }
        }
        val blur = runBlocking { smoothArtworkBlur(artwork, 90) }
        val score = calculatePlayScoreAndAccuracy(2055, 1980, 51, 8, 16, 548)
        compose.setContent {
            PhigrosScoreTheme(dark) {
                PracticeResultPanel(artwork, blur, "Exoplanetary Mirage", "IN Lv.16.9",
                    score.score, score.accuracy, calculateChartRks(16.9, score.accuracy),
                    1980, 23, 28, 8, 16, 548, false, strict, 1f, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        compose.onNodeWithText("再来一局").assertExists()
        if (strict) compose.onNodeWithText("严判模式").assertExists()
        else compose.onNodeWithText("严判模式").assertDoesNotExist()
        val output = File(checkNotNull(System.getenv("PSQ_RESULT_OUTPUT")), name)
        checkNotNull(output.parentFile).mkdirs()
        val screenshot = compose.runOnIdle {
            val view = compose.activity.findViewById<View>(android.R.id.content)
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        output.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
