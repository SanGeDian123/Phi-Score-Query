package xyz.plcliangpicup.phigrosscore.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import xyz.plcliangpicup.phigrosscore.BuildConfig

/** Visibility is exclusively server-owned; remembered metadata is only for local resource cleanup. */
object PracticeCharts {
    private val json = Json { ignoreUnknownKeys = true }
    private val current = MutableStateFlow<List<PracticeChartSource>>(emptyList())
    val entries = current.asStateFlow()
    private val client = ResourceHttp.builder().callTimeout(8, TimeUnit.SECONDS).build()
    private val refreshLock = Mutex()
    private var etag: String? = null
    private var lastCatalog: List<PracticeChartSource>? = null
    @Serializable private data class Catalog(val practiceCharts: List<PracticeChartSource>)
    internal fun valid(chart: PracticeChartSource): Boolean = chart.songId.isNotBlank() &&
        chart.title.isNotBlank() && chart.chartConstant.isFinite() && chart.chartConstant in 0.0..100.0 &&
        chart.fileName.endsWith(".pez", true) && chart.fileName.length <= 240 &&
        chart.fileName.none { it in "/\\:\u0000" || it.isISOControl() } &&
        chart.fileName != ".pez" && (chart.sizeBytes == null || chart.sizeBytes > 0L) &&
        chart.difficulty.substringBefore(' ') in listOf("EZ", "HD", "IN", "AT")

    internal fun parseCatalog(body: String): List<PracticeChartSource> =
        json.decodeFromString<Catalog>(body).practiceCharts.filter(::valid).distinctBy { it.fileName }
            .sortedWith(compareBy<PracticeChartSource> { it.songId }
                .thenBy { listOf("EZ", "HD", "IN", "AT").indexOf(it.difficulty.substringBefore(' ')) }
                .thenBy { it.chartConstant }.thenBy { it.fileName })

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) { refreshLock.withLock {
        try {
            val request = Request.Builder().url("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/v2/songs/catalog?practice=true")
                .header("Cache-Control", "no-cache")
                .apply { if (lastCatalog != null) etag?.let { header("If-None-Match", it) } }.build()
            val charts = client.newCall(request).execute().use { response ->
                if (response.code == 304) return@use checkNotNull(lastCatalog)
                check(response.isSuccessful) { "谱面清单暂不可用" }
                val parsed = parseCatalog(response.body?.string() ?: error("谱面清单为空"))
                etag = response.header("ETag")
                lastCatalog = parsed
                parsed
            }
            rememberCharts(context, charts)
            current.value = charts
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never resurrect removed entries from a bundled list or an old disk cache.
            current.value = emptyList()
        }
    } }

    private fun known(context: Context): List<PracticeChartSource> = runCatching {
        json.decodeFromString<List<PracticeChartSource>>(context.getSharedPreferences("practice_catalog", 0)
            .getString("known", "[]")!!).filter(::valid)
    }.getOrDefault(emptyList())

    @Synchronized private fun rememberCharts(context: Context, charts: List<PracticeChartSource>) {
        val merged = (charts + known(context)).distinctBy { it.fileName }
        context.getSharedPreferences("practice_catalog", 0).edit().putString("known", json.encodeToString(merged)).apply()
    }

    fun storedCharts(context: Context): List<PracticeChartSource> {
        val known = known(context)
        // Also retain cleanup access for archives downloaded by older APKs and removed from the server.
        val local = File(context.filesDir, "practice-charts").listFiles().orEmpty().mapNotNull { file ->
            val name = file.name.substringBefore(".pez") + ".pez"
            if (!file.name.contains(".pez")) return@mapNotNull null
            known.firstOrNull { it.fileName == name } ?: PracticeChartSource(
                "local", name.removeSuffix(".pez"), "IN", 0.0, name)
        }
        return (known + local).filter(::valid).distinctBy { it.fileName }
    }
    fun encodeSource(chart: PracticeChartSource): String = json.encodeToString(chart)
    fun decodeSource(value: String): PracticeChartSource? = runCatching {
        json.decodeFromString<PracticeChartSource>(value).takeIf(::valid)
    }.getOrNull()
}
