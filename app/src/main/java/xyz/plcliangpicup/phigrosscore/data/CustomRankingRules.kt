package xyz.plcliangpicup.phigrosscore.data

const val CUSTOM_RANKING_SLOT_COUNT = 36

/** Main slots reserve top-three charts for automatic copies, including during partial entry. */
fun customRankingPickerUsedKeys(charts: List<CustomChartDraft>, sourceIndex: Int): Set<String> =
    charts.mapIndexedNotNull { index, chart ->
        chart.songId.takeIf { it.isNotBlank() && index != sourceIndex && (sourceIndex >= 3 || index < 3) }
            ?.let { "$it-${chart.difficulty.uppercase()}" }
    }.toSet()

data class CustomRankingRow(val sourceIndex: Int, val mirrored: Boolean = false) {
    val stableKey: String get() = "${if (mirrored) "ap-copy" else "input"}-$sourceIndex"
}

/** Keep editor sources fixed; only move their presentation, so typing retains its identity. */
fun customRankingRows(values: List<Double?>, ranking: String, chartKeys: List<String?>? = null): List<CustomRankingRow> {
    require(values.size == CUSTOM_RANKING_SLOT_COUNT)
    fun rks(row: CustomRankingRow) = values[row.sourceIndex]?.takeIf { it.isFinite() && it >= 0.0 }
    fun sortedFilled(rows: List<CustomRankingRow>): List<CustomRankingRow> {
        val sorted = rows.filter { rks(it) != null }.sortedByDescending { rks(it) }.iterator()
        return rows.map { if (rks(it) != null) sorted.next() else it }
    }
    val inputs = values.indices.map { CustomRankingRow(it) }
    require(ranking in setOf("b30", "p30"))
    val top = sortedFilled(inputs.take(3))
    val main = inputs.drop(3).toMutableList()
    if (top.all { rks(it) != null }) {
        top.forEach { ap ->
            // An existing copy is represented by its AP source, never counted twice in Best.
            val duplicate = if (chartKeys == null) -1 else main.indexOfFirst {
                !it.mirrored && chartKeys[it.sourceIndex] != null && chartKeys[it.sourceIndex] == chartKeys[ap.sourceIndex]
            }
            val reserved = ap.sourceIndex
            val empty = if (reserved < main.size && rks(main[reserved]) == null &&
                (chartKeys == null || chartKeys[main[reserved].sourceIndex] == null)) reserved
            else main.indexOfFirst { rks(it) == null && (chartKeys == null || chartKeys[it.sourceIndex] == null) }
            val copy = ap.copy(mirrored = true)
            when {
                duplicate >= 0 -> main[duplicate] = copy
                empty >= 0 -> main[empty] = copy
                else -> main.add(copy)
            }
        }
    }
    return top + sortedFilled(main).take(33)
}

fun resolvedCustomRankingCharts(charts: List<CustomChartDraft>, ranking: String): List<CustomChartDraft> {
    val values = charts.mapIndexed { i, chart -> customRankingChartRks(chart, ranking, i) }
    val keys = charts.map { chart -> chart.songId.takeIf { it.isNotBlank() }?.let { "$it-${chart.difficulty}" } }
    return customRankingRows(values, ranking, keys).map { row ->
        charts[row.sourceIndex].let { if (row.mirrored) it.copy(score = "1000000", accuracy = "100") else it }
    }
}

fun customRankingPerfectSlot(ranking: String, index: Int): Boolean = ranking == "p30" || index < 3

fun customRankingChartRks(chart: CustomChartDraft, ranking: String, index: Int): Double? {
    val constant = chart.chartConstant.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val accuracy = if (customRankingPerfectSlot(ranking, index)) 100.0 else chart.accuracy.toDoubleOrNull() ?: return null
    return runCatching { calculateChartRks(constant, accuracy) }.getOrNull()
}

/** The first three AP slots and the next 27 main slots contribute; empty slots count as zero. */
fun customRankingComposite(values: List<Double?>): Double? = values
    .takeIf { it.size == CUSTOM_RANKING_SLOT_COUNT }
    ?.take(30)
    ?.sumOf { value -> value?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0 }
    ?.div(30.0)

fun customRankingOrderValid(values: List<Double?>, ranking: String): Boolean {
    fun descending(group: List<Double?>) = group.zipWithNext().all { (a, b) -> a == null || b == null || a + 1e-9 >= b }
    return descending(values.take(3)) && descending(values.drop(3))
}

/** Shared by the editor and submission so the generate button matches the actual request. */
fun customRankingScores(charts: List<CustomChartDraft>, ranking: String): List<CustomRankingImageScore> {
    require(charts.size == CUSTOM_RANKING_SLOT_COUNT) { "请填满 36 张谱面" }
    val keys = charts.map { "${it.songId.trim()}-${it.difficulty.uppercase()}" }
    val groups = listOf(keys.take(3), keys.drop(3))
    require(groups.all { it.distinct().size == it.size }) { "同一区域内谱面不能重复" }
    val result = charts.mapIndexed { index, chart ->
        require(chart.source == "catalog" && chart.songId.isNotBlank() && chart.difficulty in setOf("EZ", "HD", "IN", "AT")) { "请选择第 ${index + 1} 张谱面" }
        require(customRankingChartRks(chart, ranking, index) != null) { "请补全第 ${index + 1} 张谱面的定数或 ACC" }
        val perfect = customRankingPerfectSlot(ranking, index)
        val score = if (perfect) 1_000_000 else chart.score.takeIf(String::isNotBlank)?.let {
            it.toIntOrNull()?.takeIf { value -> value in 0..1_000_000 }
                ?: throw IllegalArgumentException("第 ${index + 1} 张谱面分数无效")
        }
        CustomRankingImageScore(chart.songId, chart.difficulty, score, if (perfect) 100.0 else chart.accuracy.toDouble())
    }
    require(customRankingOrderValid(charts.mapIndexed { i, chart -> customRankingChartRks(chart, ranking, i) }, ranking)) { "请按单曲 RKS 降序排列" }
    return result
}

fun sortCustomRankingCharts(charts: List<CustomChartDraft>, ranking: String): List<CustomChartDraft> {
    fun sorted(group: List<CustomChartDraft>, start: Int) = group.sortedByDescending {
        customRankingChartRks(it, ranking, start) ?: -1.0
    }
    return sorted(charts.take(3), 0) + sorted(charts.drop(3), 3)
}
