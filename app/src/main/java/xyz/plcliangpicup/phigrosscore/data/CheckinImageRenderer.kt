package xyz.plcliangpicup.phigrosscore.data

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import xyz.plcliangpicup.phigrosscore.BuildConfig
import xyz.plcliangpicup.phigrosscore.R
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** Landscape card follows the supplied layout, omitting its explanatory annotations. */
data class CheckinRenderResult(val file: File, val artworkMissing: Boolean = false)

class CheckinImageRenderer(private val context: Context) {
    private suspend fun loadArtwork(songId: String): Bitmap? = coroutineScope {
        val originalUrl = "${BuildConfig.API_BASE_URL.trimEnd('/')}/_ill/ill/${Uri.encode(songId)}.png"
        suspend fun fetch(url: String, key: String): Bitmap? = withTimeoutOrNull(10_000L) {
            val request = ImageRequest.Builder(context).data(url)
                .diskCacheKey(key).memoryCacheKey("checkin-software-$key")
                .allowHardware(false).allowRgb565(false).size(1280, 720).build()
            ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }
        val preview = async { fetch("$originalUrl?preview=1", "checkin-preview-v1-$songId") }
        // Hedge a stalled preview, while retaining the original disk cache and old-server support.
        val original = async { delay(1_200L); fetch(originalUrl, "illustration-full-$songId") }
        try {
            select<Bitmap?> {
                preview.onAwait { it ?: original.await() }
                original.onAwait { it ?: preview.await() }
            }
        } finally { preview.cancel(); original.cancel() }
    }

    suspend fun render(record: CheckinRecord): CheckinRenderResult = withContext(Dispatchers.IO) {
        val dir=File(context.cacheDir,"checkin").apply { mkdirs() }
        val key=java.security.MessageDigest.getInstance("SHA-256").digest(record.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val completeFile=File(dir,"checkin-v7-$key.png")
        if(completeFile.isFile && completeFile.length()>0) return@withContext CheckinRenderResult(completeFile)
        val artwork = record.chart?.let { loadArtwork(it.songId) }
        val missing = record.chart != null && artwork == null
        // A placeholder never becomes the final cached card; retry can still restore the artwork.
        val file = if (missing) File(dir, "checkin-v7-$key-pending.png") else completeFile
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.typeface = ResourcesCompat.getFont(context, R.font.source_han_sans_saira_hybrid) ?: Typeface.DEFAULT
        canvas.drawColor(Color.rgb(29, 31, 34))
        fun cover(image: Bitmap, rect: RectF) {
            val scale = maxOf(rect.width() / image.width, rect.height() / image.height)
            val w = image.width * scale; val h = image.height * scale
            canvas.drawBitmap(image, null, RectF(rect.centerX()-w/2,rect.centerY()-h/2,rect.centerX()+w/2,rect.centerY()+h/2), paint)
        }
        if (artwork != null) {
            // Blur real pixels at full decoded resolution; never enlarge a pixelated thumbnail.
            val softened = smoothArtworkBlur(artwork)
            cover(softened, RectF(0f,0f,1920f,1080f))
            softened.recycle()
            paint.color=Color.argb(154,24,24,27)
            canvas.drawRect(0f,0f,1920f,1080f,paint)
        }
        fun text(value: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, maxWidth: Float = 1750f) {
            paint.color=color; paint.textSize=size
            while(paint.measureText(value)>maxWidth && paint.textSize>18f) paint.textSize-=1f
            canvas.drawText(value,x,y,paint)
        }
        val date = LocalDate.parse(record.date)
        val monthText = String.format(Locale.US, "%02d", date.monthValue)
        val dayText = String.format(Locale.US, "%02d", date.dayOfMonth)
        val slashTopX = 218f
        val slashTopY = 96f
        val slashBottomX = 132f
        val slashBottomY = 326f
        val slashWidth = 7f
        // Reserve room for the stroke, rounded caps and antialiased glyph edges.
        val clearance = slashWidth / 2f + 12f
        fun slashX(y: Float): Float {
            val fraction = ((y - slashTopY) / (slashBottomY - slashTopY)).coerceIn(0f, 1f)
            return slashTopX + (slashBottomX - slashTopX) * fraction
        }
        val monthBounds = Rect()
        val dayBounds = Rect()
        var dateTextSize = 112f
        var dayX = 157f
        while (true) {
            paint.textSize = dateTextSize
            paint.getTextBounds(monthText, 0, monthText.length, monthBounds)
            paint.getTextBounds(dayText, 0, dayText.length, dayBounds)
            // Month stays left of the slash across its full ink height; day stays right.
            val monthRightLimit = slashX(174f + monthBounds.bottom) - clearance
            dayX = maxOf(157f, slashX(329f + dayBounds.top) + clearance - dayBounds.left)
            val monthFits = 68f + monthBounds.right <= monthRightLimit
            val dayFits = dayX + dayBounds.right <= 321f // Keep clear of the text starting at x=345.
            if (monthFits && dayFits) break
            if (dateTextSize <= 18f) break
            dateTextSize -= 1f
        }
        text(monthText,68f,174f,dateTextSize)
        paint.color=Color.WHITE;paint.strokeWidth=slashWidth;paint.strokeCap=Paint.Cap.ROUND
        canvas.drawLine(slashTopX,slashTopY,slashBottomX,slashBottomY,paint)
        text(dayText,dayX,329f,dateTextSize)
        text("签到成功",345f,164f,67f,Color.rgb(0,188,230))
        text("Sign Successful",345f,213f,35f)
        text("Coin + ${record.coin}",345f,330f,64f)
        text("今日幸运值",1060f,135f,40f)
        text(record.luck.toString(),1060f,270f,132f)
        val luckColor = when { record.luck>=85 -> Color.rgb(255,206,35);record.luck>=50 -> Color.WHITE;else -> Color.rgb(179,184,191) }
        text(checkinLuckText(record.luck),1060f,340f,42f,luckColor,760f)
        text("今日谱面",110f,420f,43f)
        val rect=RectF(110f,450f,960f,928.125f)
        if(artwork!=null) {
            canvas.save();val path=Path();path.addRoundRect(rect,62f,62f,Path.Direction.CW);canvas.clipPath(path);paint.color=Color.WHITE;cover(artwork,rect);canvas.restore()
        } else {
            paint.color=Color.rgb(42,49,59);canvas.drawRoundRect(rect,62f,62f,paint)
            text(if (missing) "曲绘暂未加载" else "今日暂无可推分谱面",180f,650f,48f,maxWidth=760f)
            if (!missing) text("继续享受音乐，明日再会",180f,710f,30f,maxWidth=760f)
        }
        record.chart?.let {
            text("${it.songName} [${it.difficulty}]",1060f,545f,46f,maxWidth=760f)
            text("宜推分",1060f,625f,45f)
            text(it.targetScore.toString(),1060f,735f,92f)
            text(String.format(Locale.US,"%.2f%%",it.targetAccuracy),1065f,790f,46f)
        }
        text(checkinPoem(record.quoteIndex),80f,979f,37f,maxWidth=1760f)
        text(record.date,80f,1040f,23f,Color.LTGRAY)
        val watermark = "Phi Score Query · ${BuildConfig.VERSION_NAME}"
        paint.color=Color.argb(190,255,255,255);paint.textSize=23f
        canvas.drawText(watermark,1840f-paint.measureText(watermark),1040f,paint)
        val temporary = File.createTempFile("checkin-", ".tmp", dir)
        try {
            temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete(); bitmap.recycle() }
        CheckinRenderResult(file, missing)
    }
}
