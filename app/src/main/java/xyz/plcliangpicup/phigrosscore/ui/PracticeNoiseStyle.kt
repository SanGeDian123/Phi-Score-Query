package xyz.plcliangpicup.phigrosscore.ui

import android.content.Context

/** v100 used interpolated repeat sampling before the later video/pixel correction. */
internal fun practiceNoiseFragmentSource(context: Context, compatibilityMode: Boolean = false): String {
    val source = context.assets.open("practice/noise_domain.agsl").bufferedReader().use { it.readText() }
    return if (compatibilityMode) source.replace(
        "floor(position * _SceneRenderScale) + float2(0.5)", "position * _SceneRenderScale") else source
}

internal fun practiceNoiseBaseSize(width: Float, height: Float, compatibilityMode: Boolean): PracticeNoiseRenderSize =
    if (compatibilityMode) practiceNoiseRenderSize(width, height, 128 * 72)
    else practiceNoiseRenderSize(width, height, 240 * 135, 8)

internal fun practiceNoiseOverlaySize(width: Float, height: Float, compatibilityMode: Boolean): PracticeNoiseRenderSize =
    practiceNoiseRenderSize(width, height, if (compatibilityMode) 512 * 288 else 640 * 360)
