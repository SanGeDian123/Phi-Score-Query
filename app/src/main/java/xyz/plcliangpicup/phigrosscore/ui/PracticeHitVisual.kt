package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal const val PRACTICE_HIT_LIFETIME_SECONDS = .6
internal val PRACTICE_HIT_PERFECT_TINT = Color.rgb(255, 236, 160)
internal val PRACTICE_HIT_GOOD_TINT = Color.rgb(180, 225, 255)

/** Phigros 4.0.1: perfect/ParticleSystem121 and good/ParticleSystem209.
 * Each hit keeps its own seed; the authored curves and motion table are shared.
 */
internal class PracticeHitVisual(random: Random = Random.Default, perfect: Boolean = true) {
    private val count = if (perfect) 4 else 3
    private val spriteAlpha = if (perfect) 225 else 235
    // Packed direction x/y, spawn radius and speed; no objects allocated while drawing.
    private val particles = FloatArray(count * 4)

    init {
        repeat(count) { index ->
            val angle = random.nextDouble() * Math.PI * 2
            val offset = index * 4
            particles[offset] = cos(angle).toFloat()
            particles[offset + 1] = sin(angle).toFloat()
            // Circle radius .2, outer 20% of the radius; uniformly sample its area.
            particles[offset + 2] = .2f * sqrt(.64f + random.nextFloat() * .36f)
            particles[offset + 3] = 15f + random.nextFloat() * 20f
        }
    }

    fun draw(
        canvas: Canvas, x: Float, y: Float, size: Float, elapsedSeconds: Float, spriteDuration: Float,
        atlas: Bitmap, columns: Int, rows: Int, tint: Int, tintFilter: ColorFilter,
        paint: Paint, source: Rect, target: RectF,
    ) {
        if (elapsedSeconds < 0f) return
        // NewHitFx switches 30 sprites at 60 Hz. Alpha belongs to the sprites and
        // the renderer (225/235); its Animator does not add another alpha fade.
        if (elapsedSeconds < spriteDuration) {
            val progress = elapsedSeconds / spriteDuration
            val frameWidth = atlas.width / columns
            val frameHeight = atlas.height / rows
            val frame = (progress * columns * rows).toInt().coerceAtMost(columns * rows - 1)
            val left = frame % columns * frameWidth
            val top = frame / columns * frameHeight
            source.set(left, top, left + frameWidth, top + frameHeight)
            target.set(x - size / 2, y - size / 2, x + size / 2, y + size / 2)
            paint.colorFilter = tintFilter
            paint.alpha = spriteAlpha
            canvas.drawBitmap(atlas, source, target, paint)
        }
        paint.colorFilter = null
        paint.color = tint
        // Both sprite and particle share their parent scale: sprite is 256/100
        // world units wide, and particle startSize is .3 world units.
        val worldToPixels = size / 2.56f
        repeat(count) { index ->
            // 100 particles/s, no burst; the cap is reached after 30/40 ms.
            val age = elapsedSeconds - (index + 1) * .01f
            if (age < 0f || age >= .5f) return@repeat
            val progress = age / .5f
            val offset = index * 4
            val distance = (particles[offset + 2] +
                PracticeHitMotion.distance(particles[offset + 3], progress)) * worldToPixels
            val cx = x + particles[offset] * distance
            val cy = y + particles[offset + 1] * distance
            val halfSide = .15f * PracticeHitMotion.size(progress) * worldToPixels
            paint.alpha = ((1 - progress) * 255).roundToInt()
            // Official startRotation is zero and RotationModule is disabled.
            canvas.drawRect(cx - halfSide, cy - halfSide, cx + halfSide, cy + halfSide, paint)
        }
    }
}

/** The serialized Hermite curves are exact. Unity's native drag integration is
 * approximated deterministically here, independent of render rate. A small shared
 * table avoids integration and logarithms for every active particle every frame.
 */
internal object PracticeHitMotion {
    private const val STEPS = 128
    private const val STRIDE = STEPS + 1
    private val distances = FloatArray(21 * STRIDE).also { table ->
        val dt = .5 / STEPS
        for (speedIndex in 0..20) {
            var velocity = 15.0 + speedIndex
            var distance = 0.0
            for (step in 1..STEPS) {
                val t = (step - .5) / STEPS
                // ClampVelocity: dampen=0, drag=5*curve, multiplyByVelocity=true.
                val drag = 5.0 * ((.2 * t - 1.4) * t + 2.2) * t
                val slowed = 1.0 + velocity * drag * dt
                distance += if (drag > 1e-8) ln(slowed) / drag else velocity * dt
                velocity /= slowed
                table[speedIndex * STRIDE + step] = distance.toFloat()
            }
        }
    }

    fun prepare() = Unit

    fun size(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * .49884492f +
            (t3 - 2 * t2 + t) * 1.6398785f +
            (-2 * t3 + 3 * t2) * .6941143f +
            (t3 - t2) * -1.0415096f
    }

    fun distance(startSpeed: Float, progress: Float): Float {
        val speed = (startSpeed - 15f).coerceIn(0f, 20f)
        val lowerSpeed = speed.toInt().coerceAtMost(19)
        val speedFraction = speed - lowerSpeed
        val sample = progress.coerceIn(0f, 1f) * STEPS
        val lowerTime = sample.toInt().coerceAtMost(STEPS - 1)
        val timeFraction = sample - lowerTime
        val first = lowerSpeed * STRIDE + lowerTime
        val second = first + STRIDE
        val slow = distances[first] + (distances[first + 1] - distances[first]) * timeFraction
        val fast = distances[second] + (distances[second + 1] - distances[second]) * timeFraction
        return slow + (fast - slow) * speedFraction
    }
}
