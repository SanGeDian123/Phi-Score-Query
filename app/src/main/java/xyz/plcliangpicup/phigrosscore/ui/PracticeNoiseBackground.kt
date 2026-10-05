package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.*
import android.os.Build

/** The same crop, artwork opacity and dimming as the normal Compose background.
 * Cached separately because a RenderEffect input only contains its node's content,
 * not the Compose layers behind the gameplay canvas.
 */
internal class PracticeNoiseBackground {
    private var source: Bitmap? = null
    private var bitmap: Bitmap? = null
    private var shader: BitmapShader? = null
    private var fieldLeft = Float.NaN
    private var fieldWidth = 0f
    private var fieldHeight = 0f
    private var screenWidth = 0f

    fun get(background: Bitmap, left: Float, width: Float, height: Float,
        fullWidth: Float, size: PracticeNoiseRenderSize): BitmapShader {
        if (source !== background || left != fieldLeft || width != fieldWidth || height != fieldHeight ||
            fullWidth != screenWidth || bitmap?.width != size.width || bitmap?.height != size.height) {
            bitmap?.recycle()
            val image = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            canvas.drawColor(Color.rgb(16, 17, 22))
            canvas.scale(size.width / width, size.height / height)
            canvas.translate(-left, 0f)
            val scale = maxOf(fullWidth / background.width, height / background.height)
            val bw = background.width * scale; val bh = background.height * scale
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 61 }
            canvas.drawBitmap(background, null, RectF((fullWidth - bw) / 2, (height - bh) / 2,
                (fullWidth + bw) / 2, (height + bh) / 2), paint)
            paint.color = Color.BLACK; paint.alpha = 128
            canvas.drawRect(left, 0f, left + width, height, paint)
            bitmap = image
            shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                if (Build.VERSION.SDK_INT >= 33) setFilterMode(BitmapShader.FILTER_MODE_NEAREST)
                setLocalMatrix(Matrix().apply { setScale(width / size.width, height / size.height) })
            }
            source = background; fieldLeft = left; fieldWidth = width; fieldHeight = height; screenWidth = fullWidth
        }
        return checkNotNull(shader)
    }

    fun release() { bitmap?.recycle(); bitmap = null; shader = null; source = null }

    fun bitmapInput(): Bitmap = checkNotNull(bitmap)
}
