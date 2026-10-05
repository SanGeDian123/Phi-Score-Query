package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CustomRankingImportTest {
    @Test
    fun `B30 imports actual scores and complete Best33 even when AP3 is weaker`() {
        val best = List(40) { record("best-$it", 18.0 - it * .05, 99.0, 987654 - it) }
        val ap = List(3) { record("ap-$it", 12.0 - it * .1) }
        val records = best + ap
        val original = RksCalculatorDraft(p30Values = List(36) { "17.5" })

        val imported = original.importOwnRanking(snapshot(records), "b30").normalized()
        val resolved = resolvedCustomRankingCharts(imported.customB30Charts, "b30")
        val submitted = customRankingScores(resolved, "b30")

        assertEquals("catalog", imported.customInputMode)
        assertEquals(original.p30Values, imported.p30Values)
        assertEquals(original.customP30Charts, imported.customP30Charts)
        assertEquals(ap.map { it.songId }, resolved.take(3).map { it.songId })
        assertEquals(best.take(33).map { it.songId }, resolved.drop(3).map { it.songId })
        assertEquals(best[0].score, submitted[3].score)
        assertEquals(best[0].accuracy, submitted[3].accuracy, 0.0)
        val expected = (ap.sumOf { it.rks } + best.take(27).sumOf { it.rks }) / 30.0
        assertEquals(expected, composite(resolved, "b30"), 1e-8)
    }

    @Test
    fun `P30 imports only real AP charts and preserves top copies and overflow`() {
        val ap = List(36) { record("ap-$it", 18.0 - it * .1) }
        val nonAp = record("non-ap", 19.0, 99.9, 999999)
        val original = RksCalculatorDraft(b30Values = List(36) { "14" })

        val imported = original.importOwnRanking(snapshot(ap + nonAp), "p30")
        val resolved = resolvedCustomRankingCharts(imported.customP30Charts, "p30")

        assertEquals(original.b30Values, imported.b30Values)
        assertEquals(original.customB30Charts, imported.customB30Charts)
        assertEquals(ap.take(3).map { it.songId }, resolved.take(3).map { it.songId })
        assertEquals(ap.take(33).map { it.songId }, resolved.drop(3).map { it.songId })
        assertFalse(resolved.any { it.songId == "non-ap" })
        assertEquals(36, customRankingScores(resolved, "p30").size)
        assertEquals(calculateP30Rks(ap), composite(resolved, "p30"), 1e-8)
    }

    @Test
    fun `editing imported AP source updates its automatic copy`() {
        val imported = RksCalculatorDraft().importOwnRanking(snapshot(List(36) { record("ap-$it", 18.0 - it * .1) }), "p30")
        val edited = imported.customP30Charts.toMutableList().apply {
            this[0] = this[0].copy(songId = "edited-ap", chartConstant = "19")
        }
        val resolved = resolvedCustomRankingCharts(edited, "p30")

        assertEquals("edited-ap", resolved[0].songId)
        assertEquals("edited-ap", resolved[3].songId)
        assertEquals(19.0, customRankingChartRks(resolved[3], "p30", 3)!!, 0.0)
        assertEquals(1, resolved.drop(3).count { it.songId == "edited-ap" })
        assertEquals(36, customRankingScores(resolved, "p30").size)
    }

    @Test
    fun `partial accounts preserve missing slots and do not invent AP results`() {
        val records = listOf(record("only-ap", 15.0), record("non-ap", 16.0, 98.0, 980000))
        val imported = RksCalculatorDraft().importOwnRanking(snapshot(records), "b30")
        val resolved = resolvedCustomRankingCharts(imported.customB30Charts, "b30")

        assertEquals(36, resolved.size)
        assertEquals(1, resolved.take(3).count { it.songId.isNotBlank() })
        assertEquals(2, resolved.drop(3).count { it.songId.isNotBlank() })
        assertTrue(resolved[1].songId.isBlank())
        assertEquals((records[0].rks + records.sumOf { it.rks }) / 30.0, composite(resolved, "b30"), 1e-8)
        assertThrows(IllegalArgumentException::class.java) { customRankingScores(resolved, "b30") }
    }

    @Test
    fun `legacy B30 items can be imported without duplicating chart sources`() {
        val records = List(3) { record("ap-$it", 16.0 - it * .1) }
        val items = records.mapIndexed { i, chart ->
            B30Item(position = i + 1, section = "AP", songId = chart.songId, songName = chart.songName,
                difficulty = chart.difficulty, chartConstant = chart.chartConstant,
                score = chart.score, accuracy = chart.accuracy, rks = chart.rks)
        }
        val legacy = snapshot(emptyList()).copy(items = items + items.map { it.copy(section = "BEST") })
        val imported = RksCalculatorDraft().importOwnRanking(legacy, "b30")
        val resolved = resolvedCustomRankingCharts(imported.customB30Charts, "b30")

        assertEquals(3, imported.customB30Charts.count { it.songId.isNotBlank() })
        assertEquals(6, resolved.count { it.songId.isNotBlank() })
        assertEquals(records.map { it.songId }, resolved.drop(3).take(3).map { it.songId })
    }

    @Test
    fun `imported decimals persist across non English locale and draft normalization`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val imported = RksCalculatorDraft().importOwnRanking(snapshot(listOf(record("decimal", 18.4, 99.123456789, 987654))), "b30").normalized()
            val restored = Json.decodeFromString(RksCalculatorDraft.serializer(), Json.encodeToString(RksCalculatorDraft.serializer(), imported))

            assertEquals("18.4", restored.customB30Charts[3].chartConstant)
            assertEquals("99.12345679", restored.customB30Charts[3].accuracy)
            assertEquals("987654", restored.customB30Charts[3].score)
            assertEquals(imported, restored)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `empty AP data cannot overwrite an existing P30 draft`() {
        val original = RksCalculatorDraft(p30Values = List(36) { "15" })
        assertThrows(IllegalArgumentException::class.java) {
            original.importOwnRanking(snapshot(listOf(record("non-ap", 16.0, 99.0, 999999))), "p30")
        }
        assertEquals(List(36) { "15" }, original.p30Values)
    }

    private fun record(id: String, constant: Double, accuracy: Double = 100.0, score: Int = 1_000_000) =
        ScoreSnapshotEntry(songId = id, songName = id, difficulty = "IN", score = score,
            accuracy = accuracy, rks = calculateChartRks(constant, accuracy), isFullCombo = true, chartConstant = constant)

    private fun snapshot(records: List<ScoreSnapshotEntry>) = B30Snapshot(
        totalRks = 0.0, items = emptyList(), gradeCounts = emptyMap(), cachedAtEpochMs = 0, scoreRecords = records,
    )

    private fun composite(charts: List<CustomChartDraft>, ranking: String) =
        customRankingComposite(charts.mapIndexed { i, chart -> customRankingChartRks(chart, ranking, i) })!!
}
