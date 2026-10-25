package xyz.plcliangpicup.phigrosscore.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h850dp-port-mdpi")
class RankingImageNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var drawersOpened = 0

    private fun showPage() {
        compose.setContent {
            PhigrosScoreTheme(false) {
                ImagePage(
                    AppUiState(showImagePagerGuide = false), {}, {}, {},
                    onOpenDrawer = { drawersOpened++ }, onDismissGuide = {},
                )
            }
        }
    }

    private fun swipeBlank(toNext: Boolean, fromEdge: Boolean = false) {
        compose.onNodeWithTag("ranking-image-pager").performTouchInput {
            val startX = if (toNext) width * .85f else if (fromEdge) width * .04f else width * .15f
            val endX = if (toNext) width * .15f else width * .85f
            swipe(Offset(startX, height * .88f), Offset(endX, height * .88f), durationMillis = 400)
        }
        compose.waitForIdle()
    }

    @Test fun emptySpaceSwipesAcrossAllThreeTypesIncludingBackFromLeftEdge() {
        showPage()
        compose.onNodeWithTag("image-tab-0").assertIsSelected()
        swipeBlank(true)
        compose.onNodeWithTag("image-tab-1").assertIsSelected()
        swipeBlank(true)
        compose.onNodeWithTag("image-tab-2").assertIsSelected()
        compose.onNodeWithText("至多支持200张谱面").assertExists()
        swipeBlank(false, fromEdge = true)
        compose.onNodeWithTag("image-tab-1").assertIsSelected()
        swipeBlank(false, fromEdge = true)
        compose.onNodeWithTag("image-tab-0").assertIsSelected()
        assertEquals(0, drawersOpened)
    }

    @Test fun tabTapMovesIndicatorThroughIntermediatePositions() {
        showPage()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val start = compose.onNodeWithTag("image-tab-indicator").fetchSemanticsNode().boundsInRoot.left
        val target = compose.onNodeWithTag("image-tab-2").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("image-tab-2").performClick()
        compose.mainClock.advanceTimeBy(110)
        val middle = compose.onNodeWithTag("image-tab-indicator").fetchSemanticsNode().boundsInRoot.left
        assertTrue("indicator should travel between tabs", middle > start && middle < target)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("image-tab-2").assertIsSelected()
        val end = compose.onNodeWithTag("image-tab-indicator").fetchSemanticsNode().boundsInRoot.left
        assertEquals(target, end, 1f)
    }
}
