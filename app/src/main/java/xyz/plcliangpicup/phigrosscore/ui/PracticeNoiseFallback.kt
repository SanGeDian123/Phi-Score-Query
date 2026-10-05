package xyz.plcliangpicup.phigrosscore.ui

import android.content.Context
import android.graphics.*
import kotlin.math.*

/** Canvas compatibility path; TouchEffect's noise/SDF arithmetic also runs before API 33. */
internal class PracticeNoiseFallback(private val context: Context) {
    private fun load(name: String) = context.assets.open("practice/$name").use { checkNotNull(BitmapFactory.decodeStream(it)) }
    private val hover = load("noise_touch_hover.png")
    private val noise = load("noise_map.png")
    private val clouds = load("noise_displace.png")
    private val sparks = load("noise_spark.png")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val tileSize = 48
    private val pixels = IntArray(tileSize * tileSize)
    private class TouchTile(val bitmap: Bitmap, var tick: Long = Long.MIN_VALUE,
        var width: Float = 0f, var height: Float = 0f)
    private val tiles = linkedMapOf<Long, TouchTile>()
    private class Texture(val bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }
    private val hoverTexture = Texture(hover)
    private val noiseTexture = Texture(noise)
    private val cloudTexture = Texture(clouds)
    private val cloudShader = BitmapShader(clouds, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    private val sparkShader = BitmapShader(sparks, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val cloudTint = PorterDuffColorFilter(Color.rgb(255, 46, 46), PorterDuff.Mode.MULTIPLY)
    private val sparkTint = PorterDuffColorFilter(Color.rgb(255, 73, 73), PorterDuff.Mode.MULTIPLY)
    private val matrix = Matrix()
    private val rect = RectF()
    internal var touchBuildCount = 0; private set
    fun draw(canvas: Canvas, time: Double, width: Float, height: Float, masks: Array<Path>,
        hovers: List<PracticeNoiseTouch.Hover>, effectTime: Double) {
        paint.shader = null; paint.colorFilter = null; paint.style = Paint.Style.FILL
        paint.color = Color.rgb(51, 14, 14); paint.alpha = 255; paint.xfermode = add
        canvas.drawPath(masks[0], paint)
        paint.xfermode = null; paint.color = Color.rgb(182, 60, 60); paint.alpha = 170
        canvas.drawPath(masks[2], paint)
        val clipped = canvas.save(); canvas.clipPath(masks[2])
        matrix.setScale(width / (clouds.width * .8f), height / (clouds.height * .3f))
        matrix.postTranslate((time / 20 * 1.5 * width / sqrt(2.0)).toFloat(), (time / 20 * 1.5 * height / sqrt(2.0)).toFloat())
        cloudShader.setLocalMatrix(matrix); paint.shader = cloudShader; paint.alpha = 72
        paint.isFilterBitmap = false
        paint.colorFilter = cloudTint
        canvas.drawRect(0f, 0f, width, height, paint)
        matrix.setScale(width / (sparks.width * 3f), height / (sparks.height * 1.2f))
        matrix.postTranslate((time / 20 * width * 1.5).toFloat(), (time / 20 * height * 1.5).toFloat())
        sparkShader.setLocalMatrix(matrix); paint.shader = sparkShader; paint.alpha = 255; paint.xfermode = add
        paint.colorFilter = sparkTint
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null; paint.colorFilter = null; paint.xfermode = null; canvas.restoreToCount(clipped)
        paint.isFilterBitmap = true
        paint.style = Paint.Style.STROKE; paint.color = Color.rgb(255, 46, 46)
        paint.alpha = 45; paint.strokeWidth = height * .022f; canvas.drawPath(masks[2], paint)
        paint.alpha = 204; paint.color = Color.rgb(255, 84, 84); paint.strokeWidth = height / 270f
        canvas.drawPath(masks[2], paint)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.xfermode = add
        paint.alpha = (255 * .12 * (1 + .5 * sin(time * 37.9))).roundToInt()
        canvas.drawPath(masks[1], paint); paint.alpha = 255
        tiles.entries.removeAll { entry ->
            if (hovers.none { it.id == entry.key }) { entry.value.bitmap.recycle(); true } else false
        }
        if (hovers.isEmpty()) { paint.xfermode = null; return }
        val t = time.toFloat(); val directionTime = t * 60f
        val phase = floor(directionTime); val interpolation = smooth(fract(directionTime))
        val dirX = mix(hash(phase, .381470025f), hash(phase + 1, .381470025f), interpolation) * 2f - 1
        val dirY = mix(hash(phase, .93821007f), hash(phase + 1, .93821007f), interpolation) * 2f - 1
        val movePhase = floor(t * 9.3f); val moveMix = smooth(fract(t * 9.3f))
        val tick = floor(effectTime * 30).toLong()
        val step = t / 20 * 2.9f * .70710678f
        hovers.forEach { slot ->
            val radius = height * .253f * slot.scale(effectTime)
            if (radius <= 0f) return@forEach
            val px = slot.point.x; val py = height - slot.point.y
            val tile = tiles.getOrPut(slot.id) { TouchTile(Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)) }
            // Every finger owns a tile; movement follows at display rate without
            // regenerating the SDF tile more than 30 times per second.
            if (tick != tile.tick || width != tile.width || height != tile.height) {
                for (y in 0 until tileSize) for (x in 0 until tileSize) {
                    val fieldX = px + ((x + .5f) / tileSize * 2 - 1) * radius
                    val fieldY = py + ((y + .5f) / tileSize * 2 - 1) * radius
                    val u = fieldX / width; val v = 1f - fieldY / height
                    val a = red(cloudTexture, u * .55f + step, v * .3f + step) - .5f
                    val b = red(cloudTexture, u * .55f - step, v * .3f + step) - .5f
                    val dx = (a - b) * .70710678f * .08f * width
                    val dy = (a + b) * .70710678f * .08f * height
                    val hu = (fieldX + dx - (px - radius)) / (radius * 2)
                    val hv = (fieldY - dy - (py - radius)) / (radius * 2)
                    if (hu !in 0f..1f || hv !in 0f..1f) { pixels[y * tileSize + x] = 0; continue }
                    val mask = alpha(hoverTexture, hu, hv)
                    if (mask < .0001f) { pixels[y * tileSize + x] = 0; continue }
                    val color = sample(noiseTexture, u * 1.5f + dirX * .03f, v * 1.46f + dirY * .03f)
                    val gx = fieldX / height / .11f + (Color.red(color) / 255f - .5f) * 2
                    val gy = (height - fieldY) / height / .11f + (Color.green(color) / 255f - .5f) * 2
                    val cx = floor(gx) + movePhase; val cy = floor(gy) + movePhase
                    val centerX = mix(hash(cx, cy), hash(cy + 1, cx + 1), moveMix)
                    val centerY = mix(hash(cy + 17.17f, cx + 17.17f), hash(cx + 18.17f, cy + 18.17f), moveMix)
                    val distance = hypot(fract(gx) - centerX, fract(gy) - centerY)
                    val sdf = smooth(((distance - .34f) / .63f).coerceIn(0f, 1f))
                    val noiseAmount = smooth(((mask + .52f) / 2f).coerceIn(0f, 1f))
                    val glow = smooth(mask.coerceIn(0f, 1f))
                    val r = overlay((sdf * 2).coerceAtMost(1f) * noiseAmount, glow) + glow * .5f
                    val gb = overlay(sdf * noiseAmount, 0f)
                    pixels[y * tileSize + x] = Color.rgb((r.coerceIn(0f, 1f) * 255).roundToInt(),
                        (gb.coerceIn(0f, 1f) * 255).roundToInt(), (gb.coerceIn(0f, 1f) * 255).roundToInt())
                }
                tile.bitmap.setPixels(pixels, 0, tileSize, 0, 0, tileSize, tileSize)
                tile.tick = tick; tile.width = width; tile.height = height; touchBuildCount++
            }
            rect.set(px - radius, py - radius, px + radius, py + radius)
            canvas.drawBitmap(tile.bitmap, null, rect, paint)
        }
        paint.xfermode = null
    }
    private fun sample(texture: Texture, u: Float, v: Float): Int {
        val w = texture.bitmap.width; val h = texture.bitmap.height
        return texture.pixels[practiceNoiseMirrorTexel(1 - v, h) * w + practiceNoiseMirrorTexel(u, w)]
    }
    private fun red(texture: Texture, u: Float, v: Float) = Color.red(sample(texture, u, v)) / 255f
    private fun alpha(texture: Texture, u: Float, v: Float): Float {
        val w = texture.bitmap.width; val h = texture.bitmap.height
        return Color.alpha(texture.pixels[(v * (h - 1)).roundToInt() * w + (u * (w - 1)).roundToInt()]) / 255f
    }
    private fun fract(value: Float) = value - floor(value)
    private fun smooth(value: Float) = value * value * (3 - 2 * value)
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun overlay(a: Float, b: Float) = if (a < .5f) 2 * a * b else 1 - 2 * (1 - a) * (1 - b)
    private fun hash(x: Float, y: Float): Float {
        val px = fract(x * .1031f); val py = fract(y * .1031f)
        val d = px * (py + 33.33f) + py * (px + 33.33f) + px * (px + 33.33f)
        return fract((px + d) * (px + py + d * 2))
    }
    fun release() {
        tiles.values.forEach { it.bitmap.recycle() }; tiles.clear()
        hover.recycle(); noise.recycle(); clouds.recycle(); sparks.recycle()
    }
}
