package xyz.plcliangpicup.phigrosscore.data

import android.content.Context

internal const val TIME_REWIND_P1_RULES = "p1-v2"

// Keep this order identical to handler/time_rewind.rs; titles and save order are not identities.
internal fun selectTimeRewindPhi(records: List<ScoreSnapshotEntry>): ScoreSnapshotEntry? = records
    .filter { it.score == 1_000_000 }
    .minWithOrNull(compareByDescending<ScoreSnapshotEntry> { it.chartConstant ?: 0.0 }
        .thenBy { it.songId }
        .thenBy { listOf("EZ", "HD", "IN", "AT").indexOf(it.difficulty.uppercase()).let { rank -> if (rank < 0) 4 else rank } })

/** An immutable, session-only overlay. Never written into the normal score/catalog cache. */
class TimeRewindConstants(context: Context) {
    val values: Map<String, Map<String, Double>> = context.assets.open("difficulty_20250214.csv")
        .bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).associate { line ->
                val cells = line.split(',')
                cells.first() to listOf("EZ", "HD", "IN", "AT").mapIndexedNotNull { i, difficulty ->
                    cells.getOrNull(i + 1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                        ?.let { difficulty to it }
                }.toMap()
            }
        }

    fun song(song: SongInfo): SongInfo {
        val constants = values[song.id].orEmpty()
        return song.copy(chartConstants = constants, charts = song.charts.mapNotNull { chart ->
            constants[chart.difficulty]?.let { chart.copy(chartConstant = it) }
        })
    }

    fun record(record: ScoreSnapshotEntry): ScoreSnapshotEntry? {
        val constant = values[record.songId]?.get(record.difficulty) ?: return null
        return record.copy(chartConstant = constant,
            rks = calculateChartRankingScore(record.accuracy, constant), pushAcc = null, pushAccHint = null)
    }

    fun snapshot(source: B30Snapshot, song: (String) -> SongInfo?): B30Snapshot {
        val records = source.scoreRecords.mapNotNull(::record)
        val best = selectBestCharts(records, 19)
        val phi = listOfNotNull(selectTimeRewindPhi(records))
        fun item(record: ScoreSnapshotEntry, index: Int, section: String) = B30Item(
            position = index + 1, section = section, songId = record.songId, songName = record.songName,
            composer = song(record.songId)?.composer.orEmpty(), difficulty = record.difficulty,
            chartConstant = record.chartConstant, score = record.score, accuracy = record.accuracy,
            rks = record.rks, isFullCombo = record.isFullCombo,
        )
        return source.copy(totalRks = calculateTimeRewindRks(records), scoreRecords = records,
            updatedScores = source.updatedScores.mapNotNull(::record),
            items = phi.mapIndexed { i, r -> item(r, i, "AP") } + best.mapIndexed { i, r -> item(r, i, "BEST") })
    }
}

internal fun calculateTimeRewindRks(records: List<ScoreSnapshotEntry>): Double =
    (selectBestCharts(records, 19).sumOf { it.rks } + (selectTimeRewindPhi(records)?.rks ?: 0.0)) / 20.0

internal fun calculateTimeRewindP30Rks(records: List<ScoreSnapshotEntry>): Double =
    calculateTimeRewindRks(records.filter { it.score == 1_000_000 })
