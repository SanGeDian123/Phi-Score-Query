package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.plcliangpicup.phigrosscore.data.B30ImageStyle
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-port-mdpi")
class BestNImagePresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun generatedImageHasNoStyleOrQuantityCaptionAndKeepsGenerationControls() {
        val file = File(compose.activity.cacheDir, "best-n-preview.png")
        file.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.setContent { PhigrosScoreTheme(false) {
            BestNImagePanel(AppUiState(bestNImageFile = file, generatedBestNCount = 50,
                generatedBestNStyle = B30ImageStyle.MINIMAL), {}, {}, {})
        } }
        compose.onNodeWithText("Best 50 · 简约").assertDoesNotExist()
        compose.onNodeWithContentDescription("Best 50 成绩图").assertExists()
        compose.onNodeWithText("谱面数量").assertExists()
        compose.onNodeWithText("生成 Best 30 图片").assertExists()
        compose.onNodeWithText("保存或分享").performScrollTo().assertIsDisplayed()
    }
}
