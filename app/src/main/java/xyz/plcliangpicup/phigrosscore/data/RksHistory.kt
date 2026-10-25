package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.Serializable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs

@Serializable
data class RksHistoryItem(val rks: Double, val rksJump: Double = 0.0, val createdAt: String) {
    @kotlinx.serialization.Transient
    val epochMs: Long? = runCatching { Instant.parse(createdAt).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(createdAt).toInstant().toEpochMilli() }.getOrNull()
}

@Serializable
data class RksHistoryResponse(
    val items: List<RksHistoryItem> = emptyList(),
    val total: Int = 0,
    val currentRks: Double = 0.0,
    val peakRks: Double = 0.0,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
)

enum class RksTrendRange(val label: String, val days: Int?) {
    WEEK("近7天", 7), MONTH("近30天", 30), QUARTER("近90天", 90), ALL("全部", null),
}

internal fun RksHistoryResponse.coversTrendRange(range: RksTrendRange, now: Long): Boolean {
    if (!hasMore) return true
    val since = range.days?.let { now - it * 86_400_000L } ?: return false
    // Equality is not enough: the next page may contain more records at the same timestamp.
    return items.lastOrNull()?.epochMs?.let { it < since } == true
}

/** Read every page in the selected range. The API remains paged; the user never has to expand it. */
internal suspend fun loadRksHistoryRange(
    range: RksTrendRange,
    now: Long,
    cached: RksHistoryResponse? = null,
    fetchPage: suspend (String?) -> RksHistoryResponse,
    onProgress: (RksHistoryResponse) -> Unit = {},
): RksHistoryResponse {
    var result = cached
    val cursors = mutableSetOf<String>()
    while (result == null || !result.coversTrendRange(range, now)) {
        currentCoroutineContext().ensureActive()
        val cursor = if (result == null) null else {
            result.nextCursor?.takeIf(String::isNotBlank)
                ?: throw IOException("历史记录分页信息无效，请刷新重试")
        }
        if (cursor != null && !cursors.add(cursor)) throw IOException("历史记录分页未前进，请刷新重试")
        val page = fetchPage(cursor)
        currentCoroutineContext().ensureActive()
        result = if (result == null) page else result.copy(
            items = result.items + page.items,
            total = maxOf(result.total, page.total),
            hasMore = page.hasMore,
            nextCursor = page.nextCursor,
        )
        onProgress(result)
        if (page.items.isEmpty() && page.hasMore) throw IOException("历史记录分页返回空数据，请刷新重试")
    }
    return result
}

/** Keep real timestamps and negative changes; never synthesize past achievements from today's save. */
internal fun List<RksHistoryItem>.trendPoints(range: RksTrendRange, now: Long): List<RksHistoryItem> {
    val since = range.days?.let { now - it * 86_400_000L }
    // API order is newest first, including ties resolved by its submission ID.
    return asReversed().filter { it.rks.isFinite() && it.rksJump.isFinite() && it.epochMs != null }
        .filter { since == null || it.epochMs!! >= since }
        .sortedBy { it.epochMs }
}

internal fun rksChangeLabel(value: Double): String = when {
    abs(value) < .00005 -> "±0.0000"
    value > 0 -> String.format(java.util.Locale.US, "+%.4f", value)
    else -> String.format(java.util.Locale.US, "%.4f", value)
}
