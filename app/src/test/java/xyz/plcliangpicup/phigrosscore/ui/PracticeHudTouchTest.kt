package xyz.plcliangpicup.phigrosscore.ui

import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.plcliangpicup.phigrosscore.data.PracticeField
import xyz.plcliangpicup.phigrosscore.data.practicePauseTarget

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-mdpi")
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class PracticeHudTouchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hudPauseAreaDoesNotStealAnyFingerFromDoubleOrTripleChords() {
        val events = mutableListOf<Pair<Int, Int>>()
        var pauses = 0
        val field = PracticeField(960f, 540f)
        val target = practicePauseTarget(field)
        compose.setContent {
            PhigrosScoreTheme(true) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().pointerInteropFilter {
                        events += it.actionMasked to it.pointerCount
                        true
                    }) { }
                    PracticeGameplayHud(field, 0, 0, false, songName = "Rrhar'il", difficulty = "AT Lv.17",
                        progress = { 0f }, onPause = { pauses++ })
                }
            }
        }
        compose.onRoot().performTouchInput {
            down(0, Offset(480f, 260f))
            down(1, Offset(target.centerX, target.centerY))
            up(1)
            up(0)
            down(0, Offset(target.centerX, target.centerY))
            down(1, Offset(480f, 260f))
            down(2, Offset(720f, 260f))
            up(2)
            up(1)
            up(0)
        }
        compose.runOnIdle {
            assertEquals(0, pauses)
            assertFalse(events.any { it.first == MotionEvent.ACTION_CANCEL })
            assertEquals(2, events.count { it.first == MotionEvent.ACTION_DOWN })
            assertEquals(2, events.count { it.first == MotionEvent.ACTION_UP })
            assertTrue(events.contains(MotionEvent.ACTION_POINTER_DOWN to 3))
            assertEquals(3, events.count { it.first == MotionEvent.ACTION_POINTER_DOWN })
        }
    }
}
