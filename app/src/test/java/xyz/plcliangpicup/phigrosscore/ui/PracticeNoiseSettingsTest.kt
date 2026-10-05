package xyz.plcliangpicup.phigrosscore.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PracticeNoiseSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun resetNoisePreferences() {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit()
            .remove("noise_compatibility_mode").remove("noise_compatibility_notice_seen").commit()
    }

    @Test fun compatibilityDefaultsOffAndPersistsBothSwitchDirections() {
        assertFalse(PracticeNoiseSettings(context).compatibilityMode)
        PracticeNoiseSettings(context).setCompatibilityMode(true)
        assertTrue(PracticeNoiseSettings(context).compatibilityMode)
        PracticeNoiseSettings(context).setCompatibilityMode(false)
        assertFalse(PracticeNoiseSettings(context).compatibilityMode)
    }

    @Test fun noticeIncludesAndroid13AndExcludesNewerSystems() {
        for (sdk in listOf(26, 30, 32, 33)) assertTrue(practiceNoiseShouldShowCompatibilityNotice(sdk, true, false))
        for (sdk in listOf(34, 35, 36)) assertFalse(practiceNoiseShouldShowCompatibilityNotice(sdk, true, false))
    }

    @Test fun ordinaryChartsAndNewerDevicesDoNotConsumeTheFirstNoiseNotice() {
        val settings = PracticeNoiseSettings(context)
        assertFalse(settings.shouldShowNotice(33, false))
        assertFalse(settings.shouldShowNotice(34, true))
        assertTrue(PracticeNoiseSettings(context).shouldShowNotice(33, true))
    }

    @Test fun acknowledgingPersistsAcrossChartsAndDoesNotChangeTheSelectedStyle() {
        val settings = PracticeNoiseSettings(context)
        settings.setCompatibilityMode(true)
        assertTrue(settings.shouldShowNotice(33, true))
        settings.markNoticeSeen()
        val reopened = PracticeNoiseSettings(context)
        assertFalse(reopened.shouldShowNotice(33, true))
        assertTrue(reopened.compatibilityMode)
        reopened.setCompatibilityMode(false)
        assertFalse(PracticeNoiseSettings(context).shouldShowNotice(32, true))
    }
}
