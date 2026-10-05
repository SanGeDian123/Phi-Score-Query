package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.floor
import kotlin.math.roundToInt

/** Software form of BlockCompose pass 0; Android forbids RuntimeShader on bitmap canvases. */
internal class PracticeNoiseCompose(displacement: Bitmap, private val compatibilityMode: Boolean = false) {
    private val cloudWidth = displacement.width
    private val cloudHeight = displacement.height
    private val clouds = IntArray(cloudWidth * cloudHeight).also { displacement.getPixels(it, 0, cloudWidth, 0, 0, cloudWidth, cloudHeight) }
    private var normal = IntArray(0)
    private var subtract = IntArray(0)
    private var output = IntArray(0)
    fun draw(destination: Bitmap, normalMask: Bitmap, subtractMask: Bitmap, time: Double) {
        val w = destination.width; val h = destination.height
        if (output.size != w * h) { output = IntArray(w * h); normal = IntArray(w * h); subtract = IntArray(w * h) }
        normalMask.getPixels(normal, 0, w, 0, 0, w, h); subtractMask.getPixels(subtract, 0, w, 0, 0, w, h)
        val move = time.toFloat() / 20f * 2.59f * .7071067812f
        for (y in 0 until h) for (x in 0 until w) {
            val u = (x + .5f) / w; val v = 1f - (y + .5f) / h
            val a = cloud(u * 2.13f + move, v * 1.02f + move) - .5f
            val b = cloud(u * 2.13f - move, v * 1.02f + move) - .5f
            val shiftedX = u + (a - b) * .07071067812f
            val shiftedY = 1f - (v + (a + b) * .07071067812f)
            val n = sample(normal, w, h, shiftedX, shiftedY)
            val s = sample(subtract, w, h, shiftedX, shiftedY)
            output[y * w + x] = Color.rgb(kotlin.math.abs(n - s).roundToInt().coerceIn(0, 255), 0, 0)
        }
        destination.setPixels(output, 0, w, 0, 0, w, h)
    }
    private fun cloud(u: Float, v: Float): Float {
        if (compatibilityMode) {
            val x = u * cloudWidth - .5f; val y = (1f - v) * cloudHeight - .5f
            val ix = floor(x).toInt(); val iy = floor(y).toInt(); val fx = x - ix; val fy = y - iy
            fun value(dx: Int, dy: Int): Float = Color.red(clouds[Math.floorMod(iy + dy, cloudHeight) * cloudWidth +
                Math.floorMod(ix + dx, cloudWidth)]) / 255f
            return (value(0, 0) * (1 - fx) + value(1, 0) * fx) * (1 - fy) +
                (value(0, 1) * (1 - fx) + value(1, 1) * fx) * fy
        }
        val x = practiceNoiseMirrorTexel(u, cloudWidth)
        val y = practiceNoiseMirrorTexel(1f - v, cloudHeight)
        return Color.red(clouds[y * cloudWidth + x]) / 255f
    }
    private fun sample(pixels: IntArray, w: Int, h: Int, u: Float, v: Float): Float {
        if (compatibilityMode) {
            val x = u * w - .5f; val y = v * h - .5f
            val ix = floor(x).toInt(); val iy = floor(y).toInt(); val fx = x - ix; val fy = y - iy
            fun value(dx: Int, dy: Int): Float = Color.red(pixels[(iy + dy).coerceIn(0, h - 1) * w +
                (ix + dx).coerceIn(0, w - 1)]).toFloat()
            return (value(0, 0) * (1 - fx) + value(1, 0) * fx) * (1 - fy) +
                (value(0, 1) * (1 - fx) + value(1, 1) * fx) * fy
        }
        val x = kotlin.math.floor(u * w).toInt().coerceIn(0, w - 1)
        val y = kotlin.math.floor(v * h).toInt().coerceIn(0, h - 1)
        return Color.red(pixels[y * w + x]).toFloat()
    }
}
