package xyz.plcliangpicup.phigrosscore.data

import android.content.Context
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile
import java.util.zip.CRC32
import kotlinx.coroutines.CancellationException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@kotlinx.serialization.Serializable
data class PracticeChartSource(
    val songId: String,
    val title: String,
    val difficulty: String,
    val chartConstant: Double,
    val fileName: String,
    val sizeBytes: Long? = null,
    val revision: String? = null,
) {
    val url: String get() = "${xyz.plcliangpicup.phigrosscore.BuildConfig.API_BASE_URL.trimEnd('/')}/app-update/practice-charts/${java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")}"
}

data class PracticeDownloadProgress(
    val receivedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long = 0L,
    val attempt: Int = 1,
    val verified: Boolean = false,
) {
    val fraction: Float? get() = totalBytes?.takeIf { it > 0L }?.let {
        (receivedBytes.toFloat() / it).coerceIn(0f, if (verified) 1f else .99f)
    }
}

object PracticeChartDownloader {
    private const val MAX_ATTEMPTS = 4
    private val chartLocks = ConcurrentHashMap<String, Any>()
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()
    private val parallelCalls = ConcurrentHashMap<String, MutableSet<okhttp3.Call>>()
    private fun transferNames(name: String) = listOf("$name.part", "$name.part.etag", "$name.part.etag.tmp",
        "$name.part.total", "$name.part.ranges", "$name.part.ranges.tmp") + (0..3).map { "$name.part.chunk$it" }

    fun savedProgress(context: Context, chart: PracticeChartSource): PracticeDownloadProgress =
        savedProgressIn(File(context.filesDir, "practice-charts"), chart)

    internal fun savedProgressIn(directory: File, chart: PracticeChartSource): PracticeDownloadProgress {
        val partial = File(directory, "${chart.fileName}.part")
        val ranges = File(directory, "${partial.name}.ranges")
        val manifest = runCatching { ranges.readLines() }.getOrDefault(emptyList())
        val total = manifest.firstOrNull()?.toLongOrNull()
        val count = manifest.getOrNull(2)?.toIntOrNull()?.takeIf { it in 2..4 } ?: 4
        if (total != null && total > 0) {
            val received = (0 until count).sumOf { index ->
                File(directory, "${partial.name}.chunk$index").length().coerceIn(0L, total * (index + 1) / count - total * index / count)
            }
            return PracticeDownloadProgress(received, total)
        }
        return PracticeDownloadProgress(partial.length(), runCatching { File(directory, "${partial.name}.total").readText().toLong() }.getOrNull())
    }
    fun stopDownload(chart: PracticeChartSource) {
        activeCalls[chart.fileName]?.cancel()
        parallelCalls[chart.fileName]?.forEach { it.cancel() }
    }
    private val client = ResourceHttp.builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        // Chart files are large and may take several minutes on mobile networks.
        // A total-call deadline discards otherwise healthy, slow downloads.
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    data class StoredResource(
        val chart: PracticeChartSource,
        val downloadedBytes: Long,
        val temporaryBytes: Long,
        val extractedBytes: Long,
    ) {
        val totalBytes: Long get() = downloadedBytes + temporaryBytes + extractedBytes
        val isDownloaded: Boolean get() = downloadedBytes > 0L
        val hasStoredData: Boolean get() = totalBytes > 0L
    }

    fun storedResources(context: Context): List<StoredResource> = PracticeCharts.storedCharts(context).map { chart ->
        val directory = File(context.filesDir, "practice-charts")
        val extracted = File(context.cacheDir, "practice-chart-assets/${chart.fileName.removeSuffix(".pez")}")
        StoredResource(
            chart = chart,
            downloadedBytes = File(directory, chart.fileName).takeIf { it.isFile }?.length() ?: 0L,
            temporaryBytes = transferNames(chart.fileName)
                .sumOf { name -> File(directory, name).takeIf { it.isFile }?.length() ?: 0L },
            extractedBytes = extracted.walkTopDown().filter { it.isFile }.sumOf { it.length() },
        )
    }

    fun deleteStoredResources(context: Context, charts: Collection<PracticeChartSource>) {
        val directory = File(context.filesDir, "practice-charts")
        for (chart in charts.distinct().filter { PracticeCharts.valid(it) }) {
            val names = listOf(chart.fileName, "${chart.fileName}.verified") + transferNames(chart.fileName)
            for (name in names) {
                val file = File(directory, name)
                if (file.exists() && !file.delete()) throw IOException("无法删除 ${chart.title} 的谱面文件")
            }
            val extracted = File(context.cacheDir, "practice-chart-assets/${chart.fileName.removeSuffix(".pez")}")
            if (extracted.exists() && !extracted.deleteRecursively()) {
                throw IOException("无法删除 ${chart.title} 的临时资源")
            }
        }
    }

    fun ensureDownloaded(context: Context, chart: PracticeChartSource, onProgress: (PracticeDownloadProgress) -> Unit,
                         isCancelled: () -> Boolean = { false }): File =
        ensureDownloadedIn(File(context.filesDir, "practice-charts"), chart, chart.url, onProgress, isCancelled)

    internal fun ensureDownloadedIn(directory: File, chart: PracticeChartSource, url: String,
        onProgress: (PracticeDownloadProgress) -> Unit, isCancelled: () -> Boolean = { false }): File =
        synchronized(chartLocks.getOrPut(chart.fileName) { Any() }) {
        fun checkCancelled() { if (isCancelled()) throw CancellationException("谱面下载已暂停") }
        checkCancelled()
        directory.mkdirs()
        val target = File(directory, chart.fileName)
        val partial = File(directory, "${chart.fileName}.part")
        val etagFile = File(directory, "${chart.fileName}.part.etag")
        val verified = File(directory, "${chart.fileName}.verified")
        if (target.isFile && runCatching { verified.readText() }.getOrNull() == verificationStamp(target, chart)) {
            return@synchronized target
        }
        // Older catalogues have no revision. Validate a legacy local file once before caching it.
        if (chart.revision == null && isValidChart(target)) {
            verified.writeText(verificationStamp(target, chart))
            return@synchronized target
        }
        verified.delete()
        target.delete()
        onProgress(savedProgressIn(directory, chart))

        var lastFailure: IOException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            checkCancelled()
            try {
                downloadAttempt(chart, url, partial, etagFile, onProgress, attempt, isCancelled)
                checkCancelled()
                finishDownload(partial, etagFile, target)
                verified.writeText(verificationStamp(target, chart))
                onProgress(PracticeDownloadProgress(target.length(), target.length(), attempt = attempt, verified = true))
                return@synchronized target
            } catch (failure: IOException) {
                checkCancelled()
                lastFailure = failure
                if (failure.message?.contains("谱面文件无效") == true) {
                    transferNames(chart.fileName).forEach { File(directory, it).delete() }
                }
                if (attempt == MAX_ATTEMPTS) break
                onProgress(savedProgressIn(directory, chart).copy(attempt = attempt + 1))
                try {
                    Thread.sleep(750L * attempt)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("下载已取消，已接收的数据已保留。", interrupted)
                }
            }
        }
        val savedMiB = savedProgressIn(directory, chart).receivedBytes / (1024.0 * 1024.0)
        throw IOException(
            "网络下载多次中断，已保留 ${"%.1f".format(savedMiB)} MB；点击重试会从断点继续。",
            lastFailure,
        )
    }

    private fun verificationStamp(file: File, chart: PracticeChartSource): String =
        "${file.length()}:${file.lastModified()}:${chart.revision.orEmpty()}"

    private fun downloadAttempt(
        chart: PracticeChartSource,
        url: String,
        partial: File,
        etagFile: File,
        onProgress: (PracticeDownloadProgress) -> Unit,
        attempt: Int,
        isCancelled: () -> Boolean,
    ) {
        var offset = partial.length()
        var savedValidator = etagFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        if (offset > 0L && savedValidator == null) {
            partial.delete()
            offset = 0L
        }
        if (offset == 0L && (attempt == 1 || File(partial.parentFile, "${partial.name}.ranges").isFile)) {
            if (tryParallelDownload(chart, url, partial, onProgress, attempt, isCancelled)) return
            offset = partial.length()
            savedValidator = etagFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        }
        if (isCancelled()) throw CancellationException("谱面下载已暂停")

        val requestBuilder = Request.Builder().url(url).header("Accept-Encoding", "identity").get()
        if (offset > 0L && savedValidator != null) {
            requestBuilder.header("Range", "bytes=$offset-")
            requestBuilder.header("If-Range", savedValidator)
        }
        onProgress(savedProgressIn(partial.parentFile!!, chart).copy(attempt = attempt))

        val call = client.newCall(requestBuilder.build())
        activeCalls[chart.fileName] = call
        try {
        call.execute().use { response ->
            if (response.code == 416) {
                val total = Regex("bytes \\*/(\\d+)").find(response.header("Content-Range").orEmpty())
                    ?.groupValues?.getOrNull(1)?.toLongOrNull()
                if (offset > 0L && total == offset && isValidChart(partial)) return
                partial.delete()
                etagFile.delete()
                throw IOException("服务器谱面版本已变化，正在重新下载。")
            }
            if (!response.isSuccessful) throw IOException("谱面下载失败（HTTP ${response.code}）")
            val body = response.body ?: throw IOException("服务器没有返回谱面文件")
            val appending = response.code == 206
            if (appending) {
                val range = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")
                    .find(response.header("Content-Range").orEmpty())
                    ?: throw IOException("服务器返回了无效的续传范围")
                val rangeStart = range.groupValues[1].toLongOrNull()
                if (offset == 0L || rangeStart != offset) throw IOException("服务器返回的续传位置不匹配")
                val responseValidator = if (savedValidator?.startsWith('"') == true)
                    response.header("ETag") else response.header("Last-Modified")
                if (savedValidator != null && savedValidator != responseValidator) {
                    partial.delete()
                    etagFile.delete()
                    throw IOException("服务器谱面版本已变化，正在重新下载。")
                }
            }

            val responseValidator = response.header("ETag")?.takeIf { !it.startsWith("W/") }
                ?: response.header("Last-Modified")
            if (responseValidator != null) {
                val temporaryTag = File(etagFile.parentFile, "${etagFile.name}.tmp")
                temporaryTag.writeText(responseValidator)
                if (!temporaryTag.renameTo(etagFile)) {
                    temporaryTag.delete()
                    etagFile.writeText(responseValidator)
                }
            } else {
                etagFile.delete()
            }

            val startOffset = if (appending) offset else 0L
            val contentRange = response.header("Content-Range")
                ?.let { Regex("bytes \\d+-\\d+/(\\d+)").find(it)?.groupValues?.getOrNull(1)?.toLongOrNull() }
            val total = contentRange ?: body.contentLength().takeIf { it > 0L }?.let { startOffset + it }
            if (total != null) File(partial.parentFile, "${partial.name}.total").writeText(total.toString())
            body.byteStream().use { input ->
                FileOutputStream(partial, appending).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var received = startOffset
                    var lastReportAt = System.nanoTime() / 1_000_000L
                    var rateWindowStart = lastReportAt
                    var rateWindowBytes = 0L
                    var currentRate = 0L
                    onProgress(PracticeDownloadProgress(received, total, currentRate, attempt))
                    while (true) {
                        if (isCancelled()) throw CancellationException("谱面下载已暂停")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        rateWindowBytes += count
                        val now = System.nanoTime() / 1_000_000L
                        if (now - rateWindowStart >= 500L) {
                            currentRate = (rateWindowBytes * 1000L / (now - rateWindowStart)).coerceAtLeast(0L)
                            rateWindowBytes = 0L
                            rateWindowStart = now
                        }
                        if (now - lastReportAt >= 250L || total == received) {
                            onProgress(PracticeDownloadProgress(received, total, currentRate, attempt))
                            lastReportAt = now
                        }
                    }
                    output.fd.sync()
                }
            }
            if (total != null && partial.length() != total) {
                throw IOException("下载数据不完整（${partial.length()} / $total 字节），将从断点继续。")
            }
        }
        } finally { activeCalls.remove(chart.fileName, call) }
    }

    private class RangeUnsupported : IOException("服务器不支持分段续传")

    private fun tryParallelDownload(chart: PracticeChartSource, url: String, partial: File,
        onProgress: (PracticeDownloadProgress) -> Unit, attempt: Int, isCancelled: () -> Boolean): Boolean {
        val manifest = File(partial.parentFile, "${partial.name}.ranges")
        val saved = runCatching { manifest.readLines() }.getOrDefault(emptyList())
        if (saved.isEmpty() && chart.sizeBytes != null && chart.sizeBytes < 2L * 1024 * 1024) return false
        val head = client.newCall(Request.Builder().url(url).header("Accept-Encoding", "identity").head().build())
        activeCalls[chart.fileName] = head
        val metadata = try {
            head.execute().use { response ->
                val length = response.header("Content-Length")?.toLongOrNull()
                val validator = response.header("ETag")?.takeIf { it.isNotBlank() && !it.startsWith("W/") }
                    ?: response.header("Last-Modified")
                if (!response.isSuccessful || length == null || validator == null) {
                    if (saved.isNotEmpty()) throw IOException("暂时无法确认续传资源，已保留下载进度")
                    return false
                }
                if (length < 2L * 1024 * 1024 && saved.isEmpty()) return false
                length to validator
            }
        } catch (failure: IOException) {
            if (saved.isNotEmpty() || isCancelled()) throw failure
            return false
        } finally { activeCalls.remove(chart.fileName, head) }
        if (isCancelled()) throw CancellationException("谱面下载已暂停")
        val (length, validator) = metadata
        val sameResource = saved.getOrNull(0) == length.toString() && saved.getOrNull(1) == validator
        val count = if (sameResource) saved.getOrNull(2)?.toIntOrNull()?.takeIf { it in 2..4 } ?: 4
            else if (length < 4L * 1024 * 1024) 2 else 4
        val chunks = (0 until count).map { File(partial.parentFile, "${partial.name}.chunk$it") }
        if (!sameResource) {
            (0..3).forEach { File(partial.parentFile, "${partial.name}.chunk$it").delete() }
        }
        manifest.writeText("$length\n$validator\n$count")
        chunks.forEachIndexed { index, chunk ->
            if (chunk.length() > length * (index + 1) / count - length * index / count) chunk.delete()
        }
        val received = AtomicLong(chunks.sumOf { it.length() })
        val calls = ConcurrentHashMap.newKeySet<okhttp3.Call>()
        parallelCalls[chart.fileName] = calls
        val pool = Executors.newFixedThreadPool(count)
        val completion = ExecutorCompletionService<Unit>(pool)
        val reportLock = Any()
        var lastReport = received.get()
        var lastReportTime = System.nanoTime()
        var failure: Throwable? = null
        try {
            onProgress(PracticeDownloadProgress(received.get(), length, attempt = attempt))
            chunks.forEachIndexed { index, chunk ->
                completion.submit(java.util.concurrent.Callable {
                    val base = length * index / count
                    val end = length * (index + 1) / count - 1
                    var chunkBytes = chunk.length()
                    val start = base + chunkBytes
                    if (start > end) return@Callable Unit
                    if (isCancelled()) throw CancellationException("谱面下载已暂停")
                    val call = client.newCall(Request.Builder().url(url)
                        .header("Accept-Encoding", "identity").header("Range", "bytes=$start-$end")
                        .header("If-Range", validator).get().build())
                    calls.add(call)
                    try {
                        call.execute().use { response ->
                            val responseValidator = if (validator.startsWith('"')) response.header("ETag") else response.header("Last-Modified")
                            if (response.code != 206 || responseValidator != validator ||
                                response.header("Content-Range") != "bytes $start-$end/$length") throw RangeUnsupported()
                            val body = response.body ?: throw IOException("服务器没有返回谱面文件")
                            FileOutputStream(chunk, true).use { output ->
                                body.byteStream().use { input ->
                                    val buffer = ByteArray(128 * 1024)
                                    while (true) {
                                        if (isCancelled()) throw CancellationException("谱面下载已暂停")
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        if (chunkBytes + count > end - base + 1) throw RangeUnsupported()
                                        output.write(buffer, 0, count)
                                        chunkBytes += count
                                        received.addAndGet(count.toLong())
                                        synchronized(reportLock) {
                                            val total = received.get()
                                            val now = System.nanoTime()
                                            if (now - lastReportTime >= 200_000_000L || total == length) {
                                                val rate = ((total - lastReport) * 1e9 / (now - lastReportTime).coerceAtLeast(1)).toLong()
                                                onProgress(PracticeDownloadProgress(total, length, rate, attempt))
                                                lastReport = total; lastReportTime = now
                                            }
                                        }
                                    }
                                }
                            }
                            if (chunk.length() != end - base + 1) throw IOException("谱面分段下载不完整")
                        }
                    } finally { calls.remove(call) }
                })
            }
            try { repeat(count) { completion.take().get() } }
            catch (caught: Exception) { failure = caught.cause ?: caught }
        } finally {
            calls.forEach { it.cancel() }
            pool.shutdownNow()
            // Never release the chart lock while a cancelled worker can still write.
            while (!pool.awaitTermination(100, TimeUnit.MILLISECONDS)) calls.forEach { it.cancel() }
            parallelCalls.remove(chart.fileName, calls)
        }
        if (isCancelled()) throw CancellationException("谱面下载已暂停")
        if (failure != null && failure !is RangeUnsupported) throw IOException("下载中断，分段进度已保留", failure)
        if (failure is RangeUnsupported) {
            // Preserve the contiguous prefix when falling back to one connection.
            FileOutputStream(partial, false).use { output ->
                for (index in chunks.indices) {
                    val chunk = chunks[index]
                    if (chunk.isFile) chunk.inputStream().use { it.copyTo(output, 128 * 1024) }
                    if (chunk.length() < length * (index + 1) / count - length * index / count) break
                }
            }
            File(partial.parentFile, "${partial.name}.etag").writeText(validator)
            File(partial.parentFile, "${partial.name}.total").writeText(length.toString())
            chunks.forEach { it.delete() }; manifest.delete()
            return false
        }
        FileOutputStream(partial, false).use { output ->
            chunks.forEach { chunk -> chunk.inputStream().use { it.copyTo(output, 128 * 1024) } }
            output.fd.sync()
        }
        if (partial.length() != length) throw IOException("谱面合并不完整，分段进度已保留")
        onProgress(PracticeDownloadProgress(length, length, attempt = attempt))
        return true
    }
    private fun finishDownload(partial: File, etagFile: File, target: File) {
        if (!isValidChart(partial)) throw IOException("下载的谱面文件无效，重试将重新校验。")
        if (target.exists() && !target.delete()) throw IOException("无法替换旧谱面文件")
        if (!partial.renameTo(target)) throw IOException("无法保存谱面文件")
        transferNames(target.name).forEach { File(target.parentFile, it).delete() }
    }

    internal fun isValidChart(file: File): Boolean {
        return try {
            if (!file.isFile || file.length() <= 0L) return false
            ZipFile(file).use { archive ->
                val chartEntry = archive.getEntry("chart.json") ?: return@use false
                val meta = archive.getInputStream(chartEntry).bufferedReader(Charsets.UTF_8).use {
                    PracticeChartParser.readHeader(it).meta
                }
                if (archive.getEntry(meta.stringAt("song", "music.wav")) == null ||
                    archive.getEntry(meta.stringAt("background", "illustration.png")) == null) return@use false
                val entries = archive.entries()
                val buffer = ByteArray(128 * 1024)
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val crc = CRC32()
                    var inflated = 0L
                    archive.getInputStream(entry).use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            crc.update(buffer, 0, count)
                            inflated += count
                            if (inflated > 512L * 1024 * 1024) break
                        }
                    }
                    if (inflated > 512L * 1024 * 1024 || inflated != entry.size || crc.value != entry.crc) return@use false
                }
                true
            }
        } catch (_: Exception) { false }
    }
}

enum class PracticeNoteType { TAP, HOLD, FLICK, DRAG }

data class PracticeNote(
    val id: Int,
    val lineIndex: Int,
    val type: PracticeNoteType,
    val startBeat: Double,
    val endBeat: Double,
    val startSeconds: Double,
    val endSeconds: Double,
    val positionX: Float,
    val size: Float,
    val alpha: Int,
    val above: Boolean,
    val isMulti: Boolean,
    val speed: Float = 1f,
    val yOffset: Float = 0f,
    val visibleTime: Double = Double.POSITIVE_INFINITY,
    val fake: Boolean = false,
    // Explicit opt-in for converted official holds. Ordinary RPE uses line-travel integration.
    val phigrosHoldSpeed: Float? = null,
)

data class PracticeEvent(
    val startBeat: Double,
    val endBeat: Double,
    val start: Float,
    val end: Float,
    val easingType: Int,
    val layer: Int = 0,
    val easingLeft: Float = 0f,
    val easingRight: Float = 1f,
    val bezier: List<Float> = emptyList(),
)

data class PracticeJudgeLine(
    val group: Int,
    val alpha: List<PracticeEvent>,
    val moveX: List<PracticeEvent>,
    val moveY: List<PracticeEvent>,
    val rotate: List<PracticeEvent>,
    val speed: List<PracticeEvent>,
    val parent: Int = -1,
    val cover: Boolean = true,
    val scaleX: List<PracticeEvent> = emptyList(),
    val scaleY: List<PracticeEvent> = emptyList(),
) {
    val alphaLayers = alpha.groupBy { it.layer }.values.toList()
    val xLayers = moveX.groupBy { it.layer }.values.toList()
    val yLayers = moveY.groupBy { it.layer }.values.toList()
    val rotationLayers = rotate.groupBy { it.layer }.values.toList()
    val travel = PracticeTravel(speed)
}

data class PracticeBpmEvent(val startBeat: Double, val bpm: Double)

data class PracticeChart(
    val name: String,
    val level: String,
    val offsetSeconds: Double,
    val bpms: List<PracticeBpmEvent>,
    val lines: List<PracticeJudgeLine>,
    val notes: List<PracticeNote>,
    val durationSeconds: Double,
    val musicFile: File,
    val illustrationFile: File,
    val blockAreas: List<PracticeBlockArea> = emptyList(),
)

object PracticeChartParser {
    internal data class Header(val meta: JsonObject, val bpms: List<PracticeBpmEvent>)

    // First pass skips events without allocating them. JSON member order is arbitrary.
    internal fun readHeader(reader: Reader): Header = practiceJsonReader(reader).use { input ->
        var meta = JsonObject(emptyMap())
        val bpms = mutableListOf<PracticeBpmEvent>()
        input.beginObject()
        while (input.hasNext()) when (input.nextName()) {
            "META" -> meta = input.practiceObject()
            "BPMList" -> input.practiceArray {
                val event = input.practiceObject()
                val bpm = event.doubleAt("bpm", 0.0)
                if (bpm.isFinite() && bpm > 0.0) bpms += PracticeBpmEvent(event.timeAt("startTime"), bpm)
            }
            else -> input.skipValue()
        }
        input.endObject()
        input.practiceEndDocument()
        Header(meta, bpms.sortedBy(PracticeBpmEvent::startBeat).ifEmpty { listOf(PracticeBpmEvent(0.0, 120.0)) })
    }

    fun load(context: Context, pezFile: File): PracticeChart =
        loadIn(File(context.cacheDir, "practice-chart-assets/${pezFile.nameWithoutExtension}"), pezFile)

    internal fun loadIn(extracted: File, pezFile: File): PracticeChart {
        check(extracted.isDirectory || extracted.mkdirs()) { "无法创建谱面资源目录" }
        return ZipFile(pezFile).use { zip ->
            val entry = checkNotNull(zip.getEntry("chart.json")) { "谱面文件缺少 chart.json" }
            val header = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use(::readHeader)
            val music = extractEntry(pezFile, extracted, header.meta.stringAt("song", "music.wav"))
            val illustration = extractEntry(pezFile, extracted, header.meta.stringAt("background", "illustration.png"))
            zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { parse(it, header, music, illustration) }
        }
    }

    fun parse(chartJson: String, musicFile: File, illustrationFile: File): PracticeChart =
        parse(chartJson.reader(), readHeader(chartJson.reader()), musicFile, illustrationFile)

    private fun parse(reader: Reader, header: Header, musicFile: File, illustrationFile: File): PracticeChart {
        val meta = header.meta
        val rawBpms = header.bpms

        fun secondsAt(targetBeat: Double): Double {
            if (targetBeat < 0.0) return targetBeat * 60.0 / rawBpms.first().bpm
            var elapsed = 0.0
            var cursor = 0.0
            var bpm = rawBpms.first().bpm
            for (event in rawBpms) {
                if (event.startBeat <= cursor) {
                    bpm = event.bpm
                    continue
                }
                if (event.startBeat >= targetBeat) break
                elapsed += (event.startBeat - cursor) * 60.0 / bpm
                cursor = event.startBeat
                bpm = event.bpm
            }
            elapsed += (targetBeat - cursor).coerceAtLeast(0.0) * 60.0 / bpm
            return elapsed
        }

        val lines = mutableListOf<PracticeJudgeLine>()
        val ungroupedNotes = mutableListOf<PracticeNote>()
        val blockAreas = mutableListOf<PracticeBlockArea>()
        practiceJsonReader(reader).use { input ->
            input.beginObject()
            while (input.hasNext()) {
                when (input.nextName()) {
                    "blockAreaList" -> { input.practiceArray { blockAreas += readPracticeBlockArea(input) }; continue }
                    "judgeLineList" -> Unit
                    else -> { input.skipValue(); continue }
                }
                input.practiceArray {
                    val lineIndex = lines.size
                    var group = 0
                    var parent = -1
                    var cover = true
                    val events = mutableMapOf<String, MutableList<PracticeEvent>>()
                    input.beginObject()
                    while (input.hasNext()) when (input.nextName()) {
                        "Group" -> group = input.nextInt()
                        "father" -> parent = input.nextInt()
                        "isCover" -> cover = input.nextInt() != 0
                        "eventLayers" -> {
                            var layerIndex = 0
                            input.practiceArray {
                                if (input.peek() == JsonToken.NULL) input.nextNull() else {
                                    input.beginObject()
                                    while (input.hasNext()) {
                                        val eventKey = input.nextName()
                                        if (eventKey !in setOf("alphaEvents", "moveXEvents", "moveYEvents", "rotateEvents", "speedEvents")) {
                                            input.skipValue()
                                            continue
                                        }
                                        val destination = events.getOrPut(eventKey) { mutableListOf() }
                                        input.practiceArray {
                                            val event = input.practiceObject()
                                            destination += PracticeEvent(
                                                startBeat = secondsAt(event.timeAt("startTime")),
                                                endBeat = secondsAt(event.timeAt("endTime")),
                                                start = event.floatAt("start", 0f),
                                                end = event.floatAt("end", 0f),
                                                easingType = event.intAt("easingType", 1),
                                                layer = layerIndex,
                                                easingLeft = event.floatAt("easingLeft", 0f),
                                                easingRight = event.floatAt("easingRight", 1f),
                                                bezier = if (event.intAt("bezier", 0) != 0) event.arrayAt("bezierPoints").map { it.jsonPrimitive.doubleOrNull?.toFloat() ?: 0f } else emptyList(),
                                            )
                                        }
                                    }
                                    input.endObject()
                                }
                                layerIndex++
                            }
                        }
                        "notes" -> input.practiceArray {
                            val item = input.practiceObject()
                            val type = when (item.intAt("type", 1)) {
                                2 -> PracticeNoteType.HOLD
                                3 -> PracticeNoteType.FLICK
                                4 -> PracticeNoteType.DRAG
                                else -> PracticeNoteType.TAP
                            }
                            val startBeat = item.timeAt("startTime")
                            val endBeat = item.timeAt("endTime").coerceAtLeast(startBeat)
                            ungroupedNotes += PracticeNote(
                                id = ungroupedNotes.size,
                                lineIndex = lineIndex,
                                type = type,
                                startBeat = startBeat,
                                endBeat = endBeat,
                                startSeconds = secondsAt(startBeat),
                                endSeconds = secondsAt(endBeat),
                                positionX = item.floatAt("positionX", 0f),
                                size = item.floatAt("size", 1f),
                                alpha = item.intAt("alpha", 255).coerceIn(0, 255),
                                above = item.intAt("above", 1) == 1,
                                isMulti = false,
                                speed = item.floatAt("speed", 1f),
                                yOffset = item.floatAt("yOffset", 0f),
                                visibleTime = item.doubleAt("visibleTime", Double.POSITIVE_INFINITY),
                                fake = item.intAt("isFake", 0) != 0,
                                phigrosHoldSpeed = if (type == PracticeNoteType.HOLD) item.optionalFiniteFloatAt("phigrosHoldSpeed") else null,
                            )
                        }
                        else -> input.skipValue()
                    }
                    input.endObject()
                    fun ordered(key: String): List<PracticeEvent> = events[key]?.apply {
                        sortWith(compareBy(PracticeEvent::startBeat, PracticeEvent::endBeat))
                    } ?: emptyList()
                    lines += PracticeJudgeLine(group, ordered("alphaEvents"), ordered("moveXEvents"),
                        ordered("moveYEvents"), ordered("rotateEvents"), ordered("speedEvents"), parent, cover)
                }
            }
            input.endObject()
            input.practiceEndDocument()
        }
        val multiTimes = ungroupedNotes.filterNot { it.fake }.groupingBy { kotlin.math.round(it.startSeconds * 1000000).toLong() }.eachCount()
        val notes = ungroupedNotes.map { note -> note.copy(isMulti = (multiTimes[kotlin.math.round(note.startSeconds * 1000000).toLong()] ?: 0) > 1) }
            .sortedWith(compareBy(PracticeNote::startSeconds, PracticeNote::lineIndex, PracticeNote::positionX))
            .mapIndexed { index, note -> note.copy(id = index) }

        return PracticeChart(
            name = meta.stringAt("name", "Unknown"),
            level = meta.stringAt("level", "AT"),
            offsetSeconds = meta.doubleAt("offset", 0.0) / 1000.0,
            bpms = rawBpms,
            lines = lines,
            notes = notes,
            durationSeconds = notes.maxOfOrNull(PracticeNote::endSeconds) ?: 0.0,
            musicFile = musicFile,
            illustrationFile = illustrationFile,
            blockAreas = blockAreas,
        )
    }

    private fun extractEntry(pezFile: File, directory: File, entryName: String): File {
        val safeName = File(entryName).name
        val output = File(directory, safeName)
        ZipFile(pezFile).use { zip ->
            val entry = checkNotNull(zip.getEntry(entryName)) { "谱面文件缺少 $entryName" }
            val stamp = File(directory, "$safeName.verified")
            fun version() = "${pezFile.length()}:${pezFile.lastModified()}:${entry.crc}:${entry.size}:${output.lastModified()}"
            if (output.isFile && output.length() == entry.size &&
                runCatching { stamp.readText() }.getOrNull() == version()) return output
            val cachedValid = output.isFile && output.length() == entry.size && runCatching {
                val crc = CRC32()
                output.inputStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) { val count = input.read(buffer); if (count < 0) break; crc.update(buffer, 0, count) }
                }
                crc.value == entry.crc
            }.getOrDefault(false)
            if (!cachedValid) {
                val temporary = File(directory, "$safeName.tmp")
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(temporary).use { out -> input.copyTo(out) }
                }
                if (temporary.length() != entry.size) {
                    temporary.delete()
                    throw IOException("谱面资源解压不完整：$safeName")
                }
                if (output.exists() && !output.delete()) throw IOException("无法替换谱面资源：$safeName")
                if (!temporary.renameTo(output)) throw IOException("无法保存谱面资源：$safeName")
            }
            stamp.writeText(version())
        }
        return output
    }
}

fun PracticeChart.secondsToBeat(seconds: Double): Double {
    var remaining = seconds.coerceAtLeast(0.0)
    var cursor = 0.0
    var bpm = bpms.firstOrNull()?.bpm ?: 120.0
    for (event in bpms) {
        if (event.startBeat <= cursor) {
            bpm = event.bpm
            continue
        }
        val segmentSeconds = (event.startBeat - cursor) * 60.0 / bpm
        if (remaining <= segmentSeconds) return cursor + remaining * bpm / 60.0
        remaining -= segmentSeconds
        cursor = event.startBeat
        bpm = event.bpm
    }
    return cursor + remaining * bpm / 60.0
}

fun PracticeChart.beatToSeconds(beat: Double): Double {
    var elapsed = 0.0
    var cursor = 0.0
    var bpm = bpms.firstOrNull()?.bpm ?: 120.0
    for (event in bpms) {
        if (event.startBeat <= cursor) {
            bpm = event.bpm
            continue
        }
        if (event.startBeat >= beat) break
        elapsed += (event.startBeat - cursor) * 60.0 / bpm
        cursor = event.startBeat
        bpm = event.bpm
    }
    return elapsed + (beat - cursor).coerceAtLeast(0.0) * 60.0 / bpm
}

fun evaluatePracticeEvents(events: List<PracticeEvent>, beat: Double, default: Float): Float {
    if (events.isEmpty()) return default
    var low = 0
    var high = events.lastIndex
    var candidate = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (events[mid].startBeat <= beat) {
            candidate = mid
            low = mid + 1
        } else high = mid - 1
    }
    if (candidate < 0) return events.first().start
    val event = events[candidate]
    if (event.endBeat <= event.startBeat || beat >= event.endBeat) return event.end
    val amount = ((beat - event.startBeat) / (event.endBeat - event.startBeat)).toFloat().coerceIn(0f, 1f)
    fun curve(x: Float) = if (event.bezier.size == 4) practiceBezier(event.bezier, x) else practiceEasing(event.easingType, x)
    val first = curve(event.easingLeft)
    val last = curve(event.easingRight)
    val progress = if (kotlin.math.abs(last - first) < 1e-7f) amount else
        (curve(event.easingLeft + (event.easingRight - event.easingLeft) * amount) - first) / (last - first)
    return event.start + (event.end - event.start) * progress
}

private fun JsonObject.arrayAt(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())
private fun JsonObject.objectAt(key: String): JsonObject = (this[key] as? JsonObject) ?: JsonObject(emptyMap())
private fun JsonObject.stringAt(key: String, fallback: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: fallback
private fun JsonObject.doubleAt(key: String, fallback: Double): Double =
    this[key]?.jsonPrimitive?.doubleOrNull ?: fallback
private fun JsonObject.floatAt(key: String, fallback: Float): Float =
    this[key]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: fallback
private fun JsonObject.intAt(key: String, fallback: Int): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: fallback
private fun JsonObject.optionalFiniteFloatAt(key: String): Float? {
    val value = this[key] ?: return null
    if (value == kotlinx.serialization.json.JsonNull) return null
    val parsed = value.jsonPrimitive.doubleOrNull?.toFloat()
    require(parsed != null && parsed.isFinite()) { "谱面 $key 数据无效" }
    return parsed
}
private fun JsonObject.timeAt(key: String): Double {
    val values = this[key]?.jsonArray ?: return 0.0
    val beat = values.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: 0.0
    val numerator = values.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: 0.0
    val denominator = values.getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: 1.0
    return beat + if (denominator == 0.0) 0.0 else numerator / denominator
}
