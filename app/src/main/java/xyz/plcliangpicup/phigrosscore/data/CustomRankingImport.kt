package xyz.plcliangpicup.phigrosscore.data

import java.util.Locale

/** Import editable sources; presentation continues to use the shared top-three copy rules. */
internal fun RksCalculatorDraft.importOwnRanking(snapshot: B30Snapshot, ranking: String = customRanking): RksCalculatorDraft {
    require(ranking == "b30" || ranking == "p30") { "请选择 B30 或 P30" }
    val fallback = snapshot.items.map { item ->
        ScoreSnapshotEntry(
            songId = item.songId,
            songName = item.songName,
            difficulty = item.difficulty,
            score = item.score,
            accuracy = item.accuracy,
            rks = item.rks,
            isFullCombo = item.isFullCombo,
            chartConstant = item.chartConstant,
        )
    }
    fun key(record: ScoreSnapshotEntry) = "${record.songId}-${record.difficulty.uppercase()}"
    val records = (snapshot.scoreRecords + fallback)
        .filter { it.songId.isNotBlank() && it.difficulty.uppercase() in setOf("EZ", "HD", "IN", "AT") }
        .distinctBy(::key)
    val perfect = selectPerfectCharts(records, limit = 3)
    val eligible = if (ranking == "p30") records.filter { it.score == 1_000_000 } else records
    require(eligible.isNotEmpty()) {
        if (ranking == "p30") "暂无可导入的 P30 成绩" else "暂无可导入的 B30 成绩"
    }
    // With three AP sources, shared rows insert their copies into Best/P4-P36 automatically.
    // Partial accounts keep real AP entries in the lower group until all top sources exist.
    val mirroredKeys = if (perfect.size == 3) perfect.map(::key).toSet() else emptySet()
    val main = selectBestCharts(eligible.filter { key(it) !in mirroredKeys }, limit = 33)
    fun ScoreSnapshotEntry.toDraft() = CustomChartDraft(
        source = "catalog",
        songId = songId,
        songName = songName,
        difficulty = difficulty.uppercase(),
        chartConstant = chartConstant?.let(::importDecimal).orEmpty(),
        score = score.toString(),
        accuracy = importDecimal(accuracy),
    )
    val charts = List(CUSTOM_RANKING_SLOT_COUNT) { index ->
        (if (index < 3) perfect.getOrNull(index) else main.getOrNull(index - 3))
            ?.toDraft() ?: CustomChartDraft()
    }
    val values = charts.mapIndexed { index, chart ->
        customRankingChartRks(chart, ranking, index)?.let(::importDecimal).orEmpty()
    }
    return if (ranking == "b30") {
        copy(customRanking = ranking, customInputMode = "catalog", customB30Charts = charts, b30Values = values)
    } else {
        copy(customRanking = ranking, customInputMode = "catalog", customP30Charts = charts, p30Values = values)
    }
}

private fun importDecimal(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.') else ""
