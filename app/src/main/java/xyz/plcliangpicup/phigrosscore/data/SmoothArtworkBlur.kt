package xyz.plcliangpicup.phigrosscore.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Three separable box passes approximate a Gaussian without downsampling or block artifacts. */
internal suspend fun smoothArtworkBlur(source: Bitmap, radiusOverride: Int? = null): Bitmap {
    val width = source.width
    val height = source.height
    val radius = radiusOverride?.coerceIn(1, minOf(width, height).coerceAtLeast(1))
        ?: (minOf(width, height) / 90).coerceIn(2, 18)
    var input = IntArray(width * height)
    var output = IntArray(input.size)
    source.getPixels(input, 0, width, 0, 0, width, height)
    val divisor = radius * 2 + 1
    repeat(6) { pass ->
        val horizontal = pass % 2 == 0
        val length = if (horizontal) width else height
        val lines = if (horizontal) height else width
        val step = if (horizontal) 1 else width
        for (line in 0 until lines) {
            if (line % 32 == 0) currentCoroutineContext().ensureActive()
            val base = if (horizontal) line * width else line
            var a = 0; var r = 0; var g = 0; var b = 0
            for (offset in -radius..radius) {
                val pixel = input[base + offset.coerceIn(0, length - 1) * step]
                a += Color.alpha(pixel); r += Color.red(pixel); g += Color.green(pixel); b += Color.blue(pixel)
            }
            for (position in 0 until length) {
                output[base + position * step] = Color.argb(a / divisor, r / divisor, g / divisor, b / divisor)
                val old = input[base + (position - radius).coerceIn(0, length - 1) * step]
                val next = input[base + (position + radius + 1).coerceIn(0, length - 1) * step]
                a += Color.alpha(next) - Color.alpha(old); r += Color.red(next) - Color.red(old)
                g += Color.green(next) - Color.green(old); b += Color.blue(next) - Color.blue(old)
            }
        }
        val swap = input; input = output; output = swap
    }
    return Bitmap.createBitmap(input, width, height, Bitmap.Config.ARGB_8888)
}
