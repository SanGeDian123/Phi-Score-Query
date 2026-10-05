package xyz.plcliangpicup.phigrosscore.ui

import android.content.Context

internal const val PRACTICE_NOISE_COMPATIBILITY_NOTICE =
    "检测到您的设备Android版本小于或等于13，本谱面中含有噪域，相关内容显示可能出现问题，建议您前往设置，打开“噪域兼容模式”。"

internal fun practiceNoiseShouldShowCompatibilityNotice(sdk: Int, hasNoise: Boolean, seen: Boolean): Boolean =
    sdk <= 33 && hasNoise && !seen

internal class PracticeNoiseSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    val compatibilityMode: Boolean get() = preferences.getBoolean("noise_compatibility_mode", false)

    fun setCompatibilityMode(enabled: Boolean) {
        preferences.edit().putBoolean("noise_compatibility_mode", enabled).apply()
    }

    fun shouldShowNotice(sdk: Int, hasNoise: Boolean): Boolean = practiceNoiseShouldShowCompatibilityNotice(
        sdk, hasNoise, preferences.getBoolean("noise_compatibility_notice_seen", false))

    fun markNoticeSeen() {
        preferences.edit().putBoolean("noise_compatibility_notice_seen", true).apply()
    }
}
