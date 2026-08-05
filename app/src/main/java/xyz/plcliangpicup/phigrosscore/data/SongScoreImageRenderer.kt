package xyz.plcliangpicup.phigrosscore.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.BuildConfig
import xyz.plcliangpicup.phigrosscore.R
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Local renderer for the single-song score image shown in the design. */
class SongScoreImageRenderer(context: Context) {
    private val appContext = context.applicationContext
    private val imageLoader = appContext.imageLoader
    private val appTypeface = ResourcesCompat.getFont(appContext, R.font.source_han_sans_saira_hybrid)
        ?: Typeface.DEFAULT
    private val chapterAssetNames = lazy {
        appContext.assets.list(CHAPTER_ASSET_DIR).orEmpty().toList()
    }
    private val gradeIcons by lazy {
        mapOf(
            "AP" to BitmapFactory.decodeResource(appContext.resources, R.drawable.grade_ap),
            "FC" to BitmapFactory.decodeResource(appContext.resources, R.drawable.grade_fc),
            "V" to BitmapFactory.decodeResource(appContext.resources, R.drawable.grade_v),
            "S" to BitmapFactory.decodeResource(appContext.resources, R.drawable.grade_s),
            "A" to BitmapFactory.decodeResource(appContext.resources, R.drawable.grade_a),
            "B" to BitmapFactory.decodeResource(appContext.resources, R.drawable.grade_b),
            "C" to BitmapFactory.decodeResource(appContext.resources, R.drawable.grade_c),
            "F" to BitmapFactory.decodeResource(appContext.resources, R.drawable.grade_f),
        )
    }

    fun cachedFile(songId: String, style: SongScoreImageStyle): File = File(
        File(appContext.filesDir, SONG_IMAGE_DIR),
        "song-$CACHE_RENDER_VERSION-${style.preferenceValue}-${stableKey(songId)}.png",
    )

    suspend fun render(song: SongScoreResult, style: SongScoreImageStyle): File = withContext(Dispatchers.IO) {
        val artwork = loadBitmap(
            urls = listOf(illustrationUrl(song.songId), fallbackIllustrationUrl(song.songId)),
            cacheKey = "song-score-art-${song.songId}",
            width = if (style == SongScoreImageStyle.DEFAULT) MODERN_IMAGE_WIDTH else 1_280,
            height = if (style == SongScoreImageStyle.DEFAULT) MODERN_IMAGE_HEIGHT else 720,
        )
        val chapterArtwork = if (style == SongScoreImageStyle.LEGACY) loadChapterArtwork(song.chapter) else null

        val image = if (style == SongScoreImageStyle.DEFAULT) {
            createBitmap(MODERN_IMAGE_WIDTH, MODERN_IMAGE_HEIGHT)
        } else {
            createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT)
        }
        val canvas = Canvas(image)
        if (style == SongScoreImageStyle.DEFAULT) {
            drawModernDesign(canvas, song, artwork)
        } else {
            drawBackground(canvas, artwork)
            canvas.save()
            canvas.translate(IMAGE_WIDTH / 2f, IMAGE_HEIGHT / 2f)
            canvas.scale(CONTENT_SCALE, CONTENT_SCALE)
            canvas.translate(-CONTENT_CENTER_X, -CONTENT_CENTER_Y)
            drawDesignHeader(canvas, song, chapterArtwork)
            drawDesignArtwork(canvas, artwork)
            drawDesignDifficultyRows(canvas, song)
            canvas.restore()
            drawWatermark(canvas)
        }
        chapterArtwork?.recycle()

        val target = cachedFile(song.songId, style)
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

    private fun drawModernDesign(canvas: Canvas, song: SongScoreResult, artwork: Bitmap?) {
        canvas.drawColor(Color.rgb(25, 25, 28))
        if (artwork != null) {
            drawCover(
                canvas,
                artwork,
                RectF(0f, 0f, MODERN_IMAGE_WIDTH.toFloat(), MODERN_IMAGE_HEIGHT.toFloat()),
            )
        }
        canvas.drawColor(Color.argb(MODERN_OVERLAY_ALPHA, 24, 24, 27), PorterDuff.Mode.SRC_OVER)

        drawModernArtwork(canvas, artwork)
        drawModernSongInfo(canvas, song)
        drawModernScoreCards(canvas, song)
        canvas.drawText(
            "Phi Score Query · ${BuildConfig.VERSION_NAME}",
            2_490f,
            1_425f,
            textPaint(Color.argb(185, 255, 255, 255), 19f).apply { textAlign = Paint.Align.RIGHT },
        )
    }

    private fun drawModernArtwork(canvas: Canvas, artwork: Bitmap?) {
        val destination = RectF(72f, 188f, 1_287f, 872f)
        val artworkPath = Path().apply { addRoundRect(destination, 72f, 72f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(artworkPath)
        if (artwork != null) {
            drawCover(canvas, artwork, destination)
        } else {
            canvas.drawRect(destination, Paint().apply { color = Color.rgb(45, 45, 48) })
        }
        canvas.restore()
        canvas.drawRoundRect(
            destination,
            72f,
            72f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = MODERN_BORDER_WIDTH
                color = Color.BLACK
            },
        )
    }

    private fun drawModernSongInfo(canvas: Canvas, song: SongScoreResult) {
        drawTextFit(
            canvas,
            song.songName.ifBlank { "Song Name" },
            94f,
            990f,
            1_170f,
            textPaint(Color.WHITE, 82f),
        )
        drawTextFit(
            canvas,
            song.composer.ifBlank { "Artist" },
            94f,
            1_073f,
            1_170f,
            textPaint(Color.WHITE, 47f),
        )

        val difficulties = buildList {
            addAll(listOf("EZ", "HD", "IN"))
            val hasAt = song.charts.any { it.difficulty.equals("AT", ignoreCase = true) } ||
                song.records.any { it.difficulty.equals("AT", ignoreCase = true) }
            if (hasAt) add("AT")
        }
        val left = 72f
        val right = 1_287f
        val gap = 27f
        val width = (right - left - gap * (difficulties.size - 1)) / difficulties.size
        difficulties.forEachIndexed { index, difficulty ->
            val chart = song.charts.firstOrNull { it.difficulty.equals(difficulty, ignoreCase = true) }
            val record = song.records.firstOrNull { it.difficulty.equals(difficulty, ignoreCase = true) }
            val constant = record?.chartConstant ?: chart?.chartConstant
            val card = RectF(
                left + index * (width + gap),
                1_110f,
                left + index * (width + gap) + width,
                1_252f,
            )
            canvas.drawRoundRect(card, 29f, 29f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(188, 17, 18, 20)
            })
            canvas.drawText(
                difficulty,
                card.left + 22f,
                card.top + 43f,
                textPaint(difficultyColor(difficulty), 34f),
            )
            drawTextCenteredFit(
                canvas,
                constant?.let { "%.1f".format(Locale.US, it) } ?: "--",
                RectF(card.left, card.top + 42f, card.right, card.bottom),
                textPaint(Color.WHITE, 54f),
                minTextSize = 38f,
            )
        }
    }

    private fun drawModernScoreCards(canvas: Canvas, song: SongScoreResult) {
        val difficulties = buildList {
            addAll(listOf("EZ", "HD", "IN"))
            val hasAt = song.charts.any { it.difficulty.equals("AT", ignoreCase = true) } ||
                song.records.any { it.difficulty.equals("AT", ignoreCase = true) }
            if (hasAt) add("AT")
        }
        val cardLeft = 1_355f
        val cardRight = 2_488f
        val cardHeight = 307f
        val rowStep = 353f
        difficulties.forEachIndexed { index, difficulty ->
            val top = 36f + index * rowStep
            val bounds = RectF(cardLeft, top, cardRight, top + cardHeight)
            canvas.drawRoundRect(bounds, 43f, 43f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(205, 13, 15, 18)
            })
            canvas.drawRoundRect(bounds, 43f, 43f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = MODERN_BORDER_WIDTH
                color = Color.BLACK
            })
            canvas.drawText(
                difficulty,
                bounds.left + 34f,
                bounds.top + 64f,
                textPaint(difficultyColor(difficulty), 48f),
            )

            val record = song.records.firstOrNull { it.difficulty.equals(difficulty, ignoreCase = true) }
            val chart = song.charts.firstOrNull { it.difficulty.equals(difficulty, ignoreCase = true) }
            if (record == null) {
                drawTextCenteredFit(
                    canvas,
                    "NO DATA",
                    bounds,
                    textPaint(Color.WHITE, 116f),
                    minTextSize = 82f,
                    horizontalPadding = 90f,
                )
            } else {
                drawModernScoreData(canvas, bounds, record, chart)
            }
        }
    }

    private fun drawModernScoreData(
        canvas: Canvas,
        bounds: RectF,
        record: SongDifficultyScore,
        chart: SongChartInfo?,
    ) {
        drawTextFit(
            canvas,
            record.score.toString(),
            bounds.left + 34f,
            bounds.top + 220f,
            420f,
            textPaint(Color.WHITE, 103f),
        )

        val isAllPerfect = record.score >= 1_000_000 || record.accuracy >= 100.0
        val currentAccuracy = "%.2f%%".format(Locale.US, record.accuracy)
        val targetAccuracy = record.pushAcc?.takeIf { !isAllPerfect && it > record.accuracy + 0.0001 }
        if (isAllPerfect) {
            drawTextCenteredFit(
                canvas,
                currentAccuracy,
                RectF(bounds.left + 440f, bounds.top + 125f, bounds.left + 665f, bounds.top + 245f),
                textPaint(MODERN_GOLD, 42f),
                minTextSize = 31f,
                horizontalPadding = 5f,
            )
        } else if (targetAccuracy != null) {
            drawTextCenteredFit(
                canvas,
                currentAccuracy,
                RectF(bounds.left + 440f, bounds.top + 116f, bounds.left + 665f, bounds.top + 172f),
                textPaint(Color.WHITE, 34f),
                minTextSize = 27f,
                horizontalPadding = 5f,
            )
            drawTextCenteredFit(
                canvas,
                "→ %.2f%%".format(Locale.US, targetAccuracy),
                RectF(bounds.left + 430f, bounds.top + 170f, bounds.left + 675f, bounds.top + 238f),
                textPaint(MODERN_GOLD, 39f),
                minTextSize = 30f,
                horizontalPadding = 5f,
            )
        } else {
            drawTextCenteredFit(
                canvas,
                currentAccuracy,
                RectF(bounds.left + 440f, bounds.top + 125f, bounds.left + 665f, bounds.top + 245f),
                textPaint(Color.WHITE, 39f),
                minTextSize = 30f,
                horizontalPadding = 5f,
            )
        }

        val grade = gradeFor(record)
        gradeIcons[grade]?.let { icon ->
            drawBitmapFit(
                canvas,
                icon,
                RectF(bounds.left + 675f, bounds.top + 103f, bounds.left + 875f, bounds.top + 255f),
            )
        }

        val constant = record.chartConstant ?: chart?.chartConstant
        drawTextCenteredFit(
            canvas,
            constant?.let { "%.2f".format(Locale.US, it) } ?: "--",
            RectF(bounds.left + 900f, bounds.top + 87f, bounds.right - 20f, bounds.top + 150f),
            textPaint(Color.WHITE, 44f),
            minTextSize = 33f,
            horizontalPadding = 4f,
        )
        drawTextCenteredFit(
            canvas,
            "↓",
            RectF(bounds.left + 900f, bounds.top + 145f, bounds.right - 20f, bounds.top + 195f),
            textPaint(Color.WHITE, 40f),
            minTextSize = 32f,
            horizontalPadding = 4f,
        )
        drawTextCenteredFit(
            canvas,
            "%.2f".format(Locale.US, record.rankingScore),
            RectF(bounds.left + 900f, bounds.top + 190f, bounds.right - 20f, bounds.top + 257f),
            textPaint(Color.WHITE, 44f),
            minTextSize = 33f,
            horizontalPadding = 4f,
        )
    }

    private fun gradeFor(record: SongDifficultyScore): String = when {
        record.score >= 1_000_000 || record.accuracy >= 100.0 -> "AP"
        record.isFullCombo -> "FC"
        record.score >= 960_000 -> "V"
        record.score >= 920_000 -> "S"
        record.score >= 880_000 -> "A"
        record.score >= 820_000 -> "B"
        record.score >= 700_000 -> "C"
        else -> "F"
    }

    private fun difficultyColor(difficulty: String): Int = when (difficulty.uppercase(Locale.US)) {
        "EZ" -> Color.rgb(0, 203, 103)
        "HD" -> Color.rgb(0, 188, 239)
        "IN" -> Color.rgb(255, 22, 22)
        else -> Color.rgb(150, 150, 150)
    }

    private fun drawBitmapFit(canvas: Canvas, bitmap: Bitmap, bounds: RectF) {
        val scale = minOf(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val destination = RectF(
            bounds.centerX() - width / 2f,
            bounds.centerY() - height / 2f,
            bounds.centerX() + width / 2f,
            bounds.centerY() + height / 2f,
        )
        canvas.drawBitmap(bitmap, null, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
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

    private fun drawBackground(canvas: Canvas, source: Bitmap?) {
        canvas.drawColor(Color.rgb(22, 26, 37))
        if (source != null) {
            val background = blurBitmap(source, 360, 172)
            drawCover(canvas, background, RectF(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat()))
            background.recycle()
        }
        canvas.drawColor(Color.argb(105, 255, 255, 255), PorterDuff.Mode.SRC_OVER)
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
            lineTo(927f, 354f)
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
        drawTextCenteredFit(
            canvas,
            song.chapter.ifBlank { "Chapter Name" },
            RectF(445f, 303f, 921f, 355f),
            textPaint(Color.WHITE, 29f),
            minTextSize = 18f,
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
        val columns = floatArrayOf(left, 1_094f, 1_203f, 1_332f, 1_537f, 1_665f, right)
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

            val constant = record?.chartConstant ?: chart?.chartConstant
            val values = listOf(
                difficulty,
                constant?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                chart?.noteCount?.let { "%,d".format(Locale.US, it) }.orEmpty(),
                record?.let { "%,d".format(Locale.US, it.score) }.orEmpty(),
                record?.let { "%.2f%%".format(Locale.US, it.accuracy) }.orEmpty(),
                record?.let { "%.2f".format(Locale.US, it.rankingScore) }.orEmpty(),
            )
            val sizes = floatArrayOf(40f, 35f, 35f, 35f, 23f, 24f)
            columns.drop(1).dropLast(1).forEach { x ->
                canvas.drawLine(x, top + 15f, x, top + height - 15f, separatorPaint())
            }
            values.forEachIndexed { columnIndex, value ->
                if (value.isNotEmpty()) {
                    drawTextCenteredFit(
                        canvas = canvas,
                        value = value,
                        bounds = RectF(columns[columnIndex], top, columns[columnIndex + 1], top + height),
                        paint = textPaint(Color.BLACK, sizes[columnIndex]),
                        minTextSize = 17f,
                        horizontalPadding = if (columnIndex == 0 || columnIndex == values.lastIndex) 7f else 10f,
                    )
                }
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
        Canvas(small).drawBitmap(
            source,
            null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        val temporary = IntArray(pixels.size)
        repeat(2) {
            boxBlurHorizontal(pixels, temporary, width, height, BLUR_RADIUS)
            boxBlurVertical(temporary, pixels, width, height, BLUR_RADIUS)
        }
        small.setPixels(pixels, 0, width, 0, 0, width, height)
        return small
    }

    private fun boxBlurHorizontal(
        input: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val divisor = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = input[row + offset.coerceIn(0, width - 1)]
                alpha += Color.alpha(color)
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
            }
            for (x in 0 until width) {
                output[row + x] = Color.argb(alpha / divisor, red / divisor, green / divisor, blue / divisor)
                val leaving = input[row + (x - radius).coerceIn(0, width - 1)]
                val entering = input[row + (x + radius + 1).coerceIn(0, width - 1)]
                alpha += Color.alpha(entering) - Color.alpha(leaving)
                red += Color.red(entering) - Color.red(leaving)
                green += Color.green(entering) - Color.green(leaving)
                blue += Color.blue(entering) - Color.blue(leaving)
            }
        }
    }

    private fun boxBlurVertical(
        input: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val divisor = radius * 2 + 1
        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = input[offset.coerceIn(0, height - 1) * width + x]
                alpha += Color.alpha(color)
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
            }
            for (y in 0 until height) {
                output[y * width + x] = Color.argb(alpha / divisor, red / divisor, green / divisor, blue / divisor)
                val leaving = input[(y - radius).coerceIn(0, height - 1) * width + x]
                val entering = input[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                alpha += Color.alpha(entering) - Color.alpha(leaving)
                red += Color.red(entering) - Color.red(leaving)
                green += Color.green(entering) - Color.green(leaving)
                blue += Color.blue(entering) - Color.blue(leaving)
            }
        }
    }

    private fun separatorPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(10, 40, 54)
        strokeWidth = 2f
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(appTypeface, Typeface.BOLD) else appTypeface
    }

    private fun drawTextFit(canvas: Canvas, value: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        while (paint.measureText(value) > maxWidth && paint.textSize > 15f) {
            paint.textSize -= 1f
        }
        canvas.drawText(ellipsizeToWidth(value, maxWidth, paint), x, y, paint)
    }

    private fun drawTextCenteredFit(
        canvas: Canvas,
        value: String,
        bounds: RectF,
        paint: Paint,
        minTextSize: Float,
        horizontalPadding: Float = 12f,
    ) {
        val availableWidth = (bounds.width() - horizontalPadding * 2f).coerceAtLeast(1f)
        while (paint.measureText(value) > availableWidth && paint.textSize > minTextSize) {
            paint.textSize = (paint.textSize - 1f).coerceAtLeast(minTextSize)
        }
        val displayed = ellipsizeToWidth(value, availableWidth, paint)
        val metrics = paint.fontMetrics
        val baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f
        val saveCount = canvas.save()
        canvas.clipRect(bounds)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(displayed, bounds.centerX(), baseline, paint)
        canvas.restoreToCount(saveCount)
    }

    private fun ellipsizeToWidth(value: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(value) <= maxWidth) return value
        var text = value
        while (text.length > 1 && paint.measureText("$text…") > maxWidth) text = text.dropLast(1)
        return "$text…"
    }

    private fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

    private fun stableKey(value: String): String = value.toByteArray(Charsets.UTF_8).fold(
        0xcbf29ce484222325uL,
    ) { hash, byte ->
        (hash xor (byte.toInt() and 0xff).toULong()) * 0x100000001b3uL
    }.toString(16)

    private fun illustrationUrl(songId: String): String =
        "${BuildConfig.API_BASE_URL.trimEnd('/')}/_ill/ill/${android.net.Uri.encode(songId)}.png"

    private fun fallbackIllustrationUrl(songId: String): String =
        "https://raw.githubusercontent.com/Catrong/phi-plugin-ill/main/ill/${android.net.Uri.encode(songId)}.png"

    private companion object {
        const val CACHE_RENDER_VERSION = "v3"
        const val MODERN_IMAGE_WIDTH = 2_560
        const val MODERN_IMAGE_HEIGHT = 1_440
        const val MODERN_OVERLAY_ALPHA = 154
        const val MODERN_BORDER_WIDTH = 3f
        val MODERN_GOLD: Int = Color.rgb(255, 191, 0)
        const val IMAGE_WIDTH = 2_025
        const val IMAGE_HEIGHT = 963
        const val CONTENT_SCALE = 1.12f
        const val CONTENT_CENTER_X = 924.5f
        const val CONTENT_CENTER_Y = 555.5f
        const val BLUR_RADIUS = 5
        const val SONG_IMAGE_DIR = "song-images"
        const val CHAPTER_ASSET_DIR = "chapter_backgrounds"
    }
}
