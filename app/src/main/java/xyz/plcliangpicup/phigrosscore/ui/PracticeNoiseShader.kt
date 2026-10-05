package xyz.plcliangpicup.phigrosscore.ui

import android.content.Context
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import xyz.plcliangpicup.phigrosscore.data.PracticeNoisePoint
import kotlin.math.sin

/** Direct AGSL port of the 4.0.1 APK's ActiveBlock and DisabledBlock GLES3 formulas. */
@RequiresApi(33)
internal class PracticeNoiseShader(context: Context, compatibilityMode: Boolean = false) {
    val runtime = RuntimeShader(practiceNoiseFragmentSource(context, compatibilityMode))
    private val positions = FloatArray(20)

    /** Inputs use field-pixel coordinates for masks and native bitmap pixels for texture maps.
     * _SceneColor is supplied by RenderEffect.createRuntimeShaderEffect, not this method.
     * Touch points use field pixels with a bottom-left origin, as in the chart model.
     */
    fun update(time: Double, width: Int, height: Int, maskSizeW: Int, maskSizeH: Int,
               touches: Collection<PracticeNoisePoint>) {
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()
        val t = time.toFloat()
        runtime.setFloatUniform("_Time", t / 20f, t, t * 2f, t * 3f)
        runtime.setFloatUniform("_ScreenParams", w, h, 1f + 1f / w, 1f + 1f / h)
        // Use the actual bounded texture size for pixel-centred edge sampling.
        val effectW = maskSizeW.coerceAtLeast(1).toFloat()
        val effectH = maskSizeH.coerceAtLeast(1).toFloat()
        runtime.setFloatUniform("_EffectRT_TexelSize", 1f / effectW, 1f / effectH, effectW, effectH)
        val count = touches.size.coerceAtMost(10)
        positions.fill(0f)
        var index = 0
        for (point in touches) {
            if (index == count) break
            positions[index * 2] = point.x / h
            positions[index * 2 + 1] = point.y / h
            index++
        }
        runtime.setIntUniform("_TouchPosCount", count)
        runtime.setFloatUniform("_TouchPos", positions)
        runtime.setFloatUniform("_TouchPosShine", (0.63f + (1f - 0.63f) *
            (0.5f + 0.5f * sin(t * 43f))) * 2f)
    }

    fun scene(scene: Shader?, background: Shader, scaleX: Float = 1f, scaleY: Float = 1f,
        overlayOnly: Boolean = false) {
        if (scene != null) runtime.setInputShader("_SceneColor", scene)
        runtime.setInputShader("_BackgroundColor", background)
        runtime.setFloatUniform("_SceneRenderScale", scaleX, scaleY)
        runtime.setIntUniform("_OverlayOnly", if (overlayOnly) 1 else 0)
    }

    fun inputs(inputs: Map<String, Shader>) {
        for (name in INPUT_NAMES) {
            val input = inputs[name] ?: if (name == "_TouchDisplaceMap") inputs["_DisplaceMap"] else null
            requireNotNull(input) { "Missing noise shader input $name" }
            runtime.setInputShader(name, input)
        }
    }

    private companion object {
        val INPUT_NAMES = listOf("_ComposeRT", "_EffectRT", "_DisabledNormalBlockRT",
            "_DisabledSubtractBlockRT", "_ReadyComposeRT", "_TouchHoverRT", "_DisabledComposeRT",
            "_DisplaceMap", "_TouchDisplaceMap", "_SparkMap", "_NoiseMap")
    }
}
