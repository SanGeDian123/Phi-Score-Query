package xyz.plcliangpicup.phigrosscore.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.createBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Local renderer for the single-song score image shown in the design. */
class SongScoreImageRenderer(context: Context) {
    private val appContext = context.applicationContext
    private val imageLoader = appContext.imageLoader
    private val chapterAssetNames = lazy {
        appContext.assets.list(CHAPTER_ASSET_DIR).orEmpty().toList()
    }

    fun cachedFile(songId: String): File = File(
        File(appContext.filesDir, SONG_IMAGE_DIR),
        "${stableKey(songId)}.png",
    )

    suspend fun render(song: SongScoreResult): File = withContext(Dispatchers.IO) {
        val artwork = loadBitmap(
            urls = listOf(illustrationUrl(song.songId), fallbackIllustrationUrl(song.songId)),
            cacheKey = "song-score-art-${song.songId}",
            width = 1_280,
            height = 860,
        )
        val blurredArtwork = loadBitmap(
            urls = listOf(blurredIllustrationUrl(song.songId), fallbackBlurredIllustrationUrl(song.songId)),
            cacheKey = "song-score-blur-${song.songId}",
            width = 480,
            height = 260,
        )
        val chapterArtwork = loadChapterArtwork(song.chapter)

        val image = createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT)
        val canvas = Canvas(image)
        drawBackground(canvas, blurredArtwork ?: artwork, needsBlur = blurredArtwork == null)
        drawDesignHeader(canvas, song, chapterArtwork)
        drawDesignArtwork(canvas, artwork)
        drawDesignDifficultyRows(canvas, song)
        drawWatermark(canvas)

        val target = cachedFile(song.songId)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temporary).use { output ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output)) { "无法写入单曲成绩图" }
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        image.recycle()
        target
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val directory = File(appContext.filesDir, SONG_IMAGE_DIR)
        if (directory.exists()) directory.deleteRecursively()
    }

    private suspend fun loadBitmap(
        urls: List<String>,
        cacheKey: String,
        width: Int,
        height: Int,
    ): Bitmap? {
        urls.forEach { url ->
            val result = runCatching {
                imageLoader.execute(
                    ImageRequest.Builder(appContext)
                        .data(url)
                        .size(width, height)
                        .allowHardware(false)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .build(),
                )
            }.getOrNull()
            val drawable = (result as? SuccessResult)?.drawable as? BitmapDrawable
            if (drawable != null) return drawable.bitmap
        }
        return null
    }

    private fun loadChapterArtwork(chapter: String): Bitmap? {
        val normalizedChapter = normalize(chapter)
        val aliases = when (normalizedChapter) {
            "single" -> listOf("单曲")
            "sidestory4" -> listOf("extrastory")
            else -> emptyList()
        }
        val matched = chapterAssetNames.value.firstOrNull { name ->
            val base = normalize(name.substringBeforeLast('.'))
            (normalizedChapter.isNotBlank() && base.contains(normalizedChapter)) ||
                aliases.any(base::contains)
        } ?: chapterAssetNames.value.firstOrNull { it.startsWith("AllSong.", ignoreCase = true) }
        return matched?.let { assetName ->
            runCatching {
                appContext.assets.open("$CHAPTER_ASSET_DIR/$assetName").use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }

    private fun drawBackground(canvas: Canvas, source: Bitmap?, needsBlur: Boolean) {
        canvas.drawColor(Color.rgb(22, 26, 37))
        if (source != null) {
            val background = if (needsBlur) blurBitmap(source, 360, 172) else source
            drawCover(canvas, background, RectF(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat()))
            if (needsBlur) background.recycle()
        }
        canvas.drawColor(Color.argb(150, 255, 255, 255), PorterDuff.Mode.SRC_OVER)
        canvas.drawRect(
            0f,
            0f,
            IMAGE_WIDTH.toFloat(),
            IMAGE_HEIGHT.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    IMAGE_HEIGHT.toFloat(),
                    Color.argb(26, 255, 255, 255),
                    Color.argb(75, 20, 30, 40),
                    Shader.TileMode.CLAMP,
                )
            },
        )
    }

    private fun drawDesignHeader(canvas: Canvas, song: SongScoreResult, chapterArtwork: Bitmap?) {
        val titlePath = Path().apply {
            moveTo(125f, 354f)
            lineTo(941f, 354f)
            lineTo(904f, 442f)
            lineTo(104f, 442f)
            close()
        }
        canvas.drawPath(titlePath, Paint().apply { color = Color.BLACK })

        val chapterPath = Path().apply {
            moveTo(435f, 301f)
            lineTo(941f, 301f)
            lineTo(927f, 357f)
            lineTo(421f, 357f)
            close()
        }
        canvas.save()
        canvas.clipPath(chapterPath)
        chapterArtwork?.let { artwork ->
            val blurred = blurBitmap(artwork, 360, 90)
            drawCover(canvas, blurred, RectF(412f, 298f, 945f, 360f))
            blurred.recycle()
        } ?: canvas.drawColor(Color.rgb(92, 91, 91))
        canvas.drawColor(Color.argb(96, 0, 0, 0), PorterDuff.Mode.SRC_OVER)
        canvas.restore()

        val songName = song.songName.ifBlank { "Song Name" }
        drawTextFit(canvas, songName, 140f, 393f, 665f, textPaint(Color.WHITE, 32f, bold = true))
        val artist = song.composer.ifBlank { "Artist" }
        drawTextFit(canvas, artist, 140f, 425f, 665f, textPaint(Color.WHITE, 22f))
        drawTextFit(
            canvas,
            song.chapter.ifBlank { "Chapter Name" },
            930f,
            340f,
            365f,
            textPaint(Color.WHITE, 29f).apply { textAlign = Paint.Align.RIGHT },
        )
    }

    private fun drawDesignArtwork(canvas: Canvas, artwork: Bitmap?) {
        val destination = RectF(105f, 442f, 905f, 811f)
        if (artwork != null) {
            drawCover(canvas, artwork, destination)
        } else {
            canvas.drawRect(destination, Paint().apply { color = Color.rgb(231, 236, 241) })
            canvas.drawText("Illustration", 505f, 635f, textPaint(Color.rgb(25, 33, 43), 38f).apply { textAlign = Paint.Align.CENTER })
        }
        canvas.drawRect(
            destination,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.rgb(10, 40, 54)
            },
        )
    }

    private fun drawDesignDifficultyRows(canvas: Canvas, song: SongScoreResult) {
        val left = 1_012f
        val right = 1_745f
        val height = 89f
        val rows = listOf(300f, 438f, 583f, 722f)
        val difficulties = listOf("EZ", "HD", "IN", "AT")
        rows.forEachIndexed { index, top ->
            val difficulty = difficulties[index]
            val chart = song.charts.firstOrNull { it.difficulty.equals(difficulty, ignoreCase = true) }
            val record = song.records.firstOrNull { it.difficulty.equals(difficulty, ignoreCase = true) }
            val card = RectF(left, top, right, top + height)
            canvas.drawRoundRect(card, 17f, 17f, Paint().apply { color = Color.argb(224, 253, 254, 255) })
            canvas.drawRoundRect(
                card,
                17f,
                17f,
                Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    color = Color.rgb(10, 40, 54)
                },
            )

            val valuePaint = textPaint(Color.BLACK, 39f)
            canvas.drawText(difficulty, 1_037f, top + 59f, textPaint(Color.BLACK, 42f))
            canvas.drawLine(1_110f, top + 14f, 1_110f, top + height - 14f, separatorPaint())

            val constant = record?.chartConstant ?: chart?.chartConstant
            constant?.let {
                canvas.drawText("%.1f".format(Locale.US, it), 1_133f, top + 59f, valuePaint)
                canvas.drawLine(1_225f, top + 14f, 1_225f, top + height - 14f, separatorPaint())
            }
            chart?.noteCount?.let {
                canvas.drawText("%,d".format(Locale.US, it), 1_248f, top + 59f, valuePaint)
                canvas.drawLine(1_337f, top + 14f, 1_337f, top + height - 14f, separatorPaint())
            }
            record?.let {
                canvas.drawText("%,d".format(Locale.US, it.score), 1_363f, top + 59f, valuePaint)
                canvas.drawText("%.2f%%".format(Locale.US, it.accuracy), 1_592f, top + 59f, textPaint(Color.BLACK, 24f))
                canvas.drawText("%.2f".format(Locale.US, it.rankingScore), 1_704f, top + 59f, textPaint(Color.BLACK, 27f))
            }
        }
    }

    private fun drawWatermark(canvas: Canvas) {
        canvas.drawText(
            "Phi Score Query · ${BuildConfig.VERSION_NAME}",
            1_970f,
            943f,
            textPaint(Color.argb(175, 28, 41, 50), 15f).apply { textAlign = Paint.Align.RIGHT },
        )
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val destinationRatio = destination.width() / destination.height()
        val source = if (sourceRatio > destinationRatio) {
            val width = (bitmap.height * destinationRatio).toInt().coerceAtLeast(1)
            Rect((bitmap.width - width) / 2, 0, (bitmap.width + width) / 2, bitmap.height)
        } else {
            val height = (bitmap.width / destinationRatio).toInt().coerceAtLeast(1)
            Rect(0, (bitmap.height - height) / 2, bitmap.width, (bitmap.height + height) / 2)
        }
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun blurBitmap(source: Bitmap, width: Int, height: Int): Bitmap {
        val small = createBitmap(width, height)
        Canvas(small).drawBitmap(source, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(2) {
            val copy = pixels.copyOf()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var red = 0
                    var green = 0
                    var blue = 0
                    var alpha = 0
                    var count = 0
                    for (dy in -3..3) {
                        val py = (y + dy).coerceIn(0, height - 1)
                        for (dx in -3..3) {
                            val px = (x + dx).coerceIn(0, width - 1)
                            val color = copy[py * width + px]
                            red += Color.red(color)
                            green += Color.green(color)
                            blue += Color.blue(color)
                            alpha += Color.alpha(color)
                            count++
                        }
                    }
                    pixels[y * width + x] = Color.argb(alpha / count, red / count, green / count, blue / count)
                }
            }
        }
        small.setPixels(pixels, 0, width, 0, 0, width, height)
        return small
    }

    private fun separatorPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(10, 40, 54)
        strokeWidth = 2f
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun drawTextFit(canvas: Canvas, value: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        if (paint.measureText(value) <= maxWidth) {
            canvas.drawText(value, x, y, paint)
            return
        }
        var text = value
        while (text.length > 1 && paint.measureText("$text…") > maxWidth) text = text.dropLast(1)
        canvas.drawText("$text…", x, y, paint)
    }

    private fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

    private fun stableKey(value: String): String = value.toByteArray(Charsets.UTF_8).fold(
        0xcbf29ce484222325uL,
    ) { hash, byte ->
        (hash xor (byte.toInt() and 0xff).toULong()) * 0x100000001b3uL
    }.toString(16)

    private fun illustrationUrl(songId: String): String =
        "${BuildConfig.API_BASE_URL.trimEnd('/')}/_ill/ill/${android.net.Uri.encode(songId)}.png"

    private fun blurredIllustrationUrl(songId: String): String =
        "${BuildConfig.API_BASE_URL.trimEnd('/')}/_ill/illBlur/${android.net.Uri.encode(songId)}.png"

    private fun fallbackIllustrationUrl(songId: String): String =
        "https://raw.githubusercontent.com/Catrong/phi-plugin-ill/main/ill/${android.net.Uri.encode(songId)}.png"

    private fun fallbackBlurredIllustrationUrl(songId: String): String =
        "https://raw.githubusercontent.com/Catrong/phi-plugin-ill/main/illBlur/${android.net.Uri.encode(songId)}.png"

    private companion object {
        const val IMAGE_WIDTH = 2_025
        const val IMAGE_HEIGHT = 963
        const val SONG_IMAGE_DIR = "song-images"
        const val CHAPTER_ASSET_DIR = "chapter_backgrounds"
    }
}
