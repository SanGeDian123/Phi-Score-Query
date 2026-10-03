package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import xyz.plcliangpicup.phigrosscore.data.PracticeField
import kotlin.math.min

/**
 * Phigros 4.0.0 level12 has a separate TranslucentImage and TrueShadow on
 * each side of the gameplay field. The LevelStart animation changes their
 * blend from .5 to .8 and shadow opacity from 0 to .235294. The material also
 * records brightness .02, flatten .145, vibrancy 1; its source blur config is
 * radius 7.1875, iteration 4, maxDepth 6, strength 115. These Unity values do
 * not map directly to Android bitmap pixels, so the blur kernel remains an
 * approximation while the side structure and settled opacity follow the APK.
 */
internal object PracticeSideStyle {
    const val blend = .8f
    const val brightness = .02f
    const val flatten = .145f
    const val shadowOpacity = .23529412f
    const val shadowSize = 360.1f
    val shadowColor = Color(.31617f, .40713f, .46226f)
}

@Composable
internal fun PracticeSideBackground(bitmap: Bitmap, field: PracticeField) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxSize()) {
        val left = field.left
        if (left <= 0f) return@Canvas
        val right = left + field.width
        val screenWidth = size.width
        val screenHeight = size.height
        val sourceWidth = bitmap.width.toFloat()
        val sourceHeight = bitmap.height.toFloat()
        val sourceAspect = sourceWidth / sourceHeight
        val screenAspect = screenWidth / screenHeight
        val cropWidth = if (sourceAspect > screenAspect) sourceHeight * screenAspect else sourceWidth
        val cropHeight = if (sourceAspect > screenAspect) sourceHeight else sourceWidth / screenAspect
        val source = Rect(
            ((sourceWidth - cropWidth) / 2).toInt(), ((sourceHeight - cropHeight) / 2).toInt(),
            ((sourceWidth + cropWidth) / 2).toInt(), ((sourceHeight + cropHeight) / 2).toInt(),
        )
        val destination = RectF(0f, 0f, screenWidth, screenHeight)
        drawIntoCanvas { composeCanvas ->
            val canvas: AndroidCanvas = composeCanvas.nativeCanvas
            // The native shader mixes the captured blur with a dark base. Its
            // visible contribution is the complement of spriteBlending.
            paint.alpha = (255 * (1f - PracticeSideStyle.blend) * .45f).toInt()
            for ((start, end) in listOf(0f to left, right to screenWidth)) {
                val save = canvas.save()
                canvas.clipRect(start, 0f, end, screenHeight)
                canvas.drawBitmap(bitmap, source, destination, paint)
                canvas.restoreToCount(save)
            }
        }
        // The Unity TrueShadow is a softened rectangular caster. Its blur-size
        // unit depends on the scene canvas; the gradient reach below is scaled
        // to the same 1000-unit reference and clipped only by the screen.
        val reach = PracticeSideStyle.shadowSize / 1000f * field.height
        val edgeReach = min(reach * .36f, field.width * .12f)
        val tint = PracticeSideStyle.shadowColor
        // Only a softened edge of the large 1000-unit caster reaches the
        // screen; its sampled alpha is lower than the stored color alpha.
        val strongest = tint.copy(alpha = PracticeSideStyle.shadowOpacity * .40f)
        val middle = tint.copy(alpha = PracticeSideStyle.shadowOpacity * .40f * .42f)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to strongest,
                (left / (left + edgeReach)).coerceIn(0f, 1f) to middle,
                1f to Color.Transparent,
                startX = 0f, endX = left + edgeReach,
            ),
            topLeft = Offset.Zero,
            size = Size(left + edgeReach, screenHeight),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                (edgeReach / (screenWidth - right + edgeReach)).coerceIn(0f, 1f) to middle,
                1f to strongest,
                startX = right - edgeReach, endX = screenWidth,
            ),
            topLeft = Offset(right - edgeReach, 0f),
            size = Size(screenWidth - right + edgeReach, screenHeight),
        )
    }
}
