package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class CustomRankingRulesTest {
    @Test fun `main picker excludes automatic top copies and other manual charts`() {
        val draft = charts()
        for (index in listOf(3, 6, 29, 35)) {
            val used = customRankingPickerUsedKeys(draft, index)
            for (top in 0..2) assertTrue("song-$top-IN" in used)
            assertFalse("song-$index-IN" in used)
            assertTrue("song-4-IN" in used)
            assertFalse("song-0-AT" in used)
        }
        val partial = MutableList(36) { CustomChartDraft() }.apply { this[0] = draft[0] }
        assertTrue("song-0-IN" in customRankingPickerUsedKeys(partial, 6))
        partial[0] = CustomChartDraft()
        assertFalse("song-0-IN" in customRankingPickerUsedKeys(partial, 6))
    }

    @Test fun `top picker allows moving a main chart into automatic top copies`() {
        val draft = charts()
        val used = customRankingPickerUsedKeys(draft, 0)
        assertEquals(setOf("song-1-IN", "song-2-IN"), used)
    }

    @Test fun `P30 repeats top three at P4 through P6 and tracks later edits`() {
        val values = (listOf(16.4, 16.3, 16.2) + List<Double?>(33) { null }).toMutableList()
        var rows = customRankingRows(values, "p30")
        assertEquals(listOf(3, 4, 5), rows.indices.filter { rows[it].mirrored })
        assertEquals(listOf(0, 1, 2), rows.drop(3).take(3).map { it.sourceIndex })
        assertEquals(36, rows.size)
        assertEquals(3.26, customRankingComposite(rows.map { values[it.sourceIndex] })!!, 1e-10)
        val source = rows[6].sourceIndex
        values[source] = 16.5
        rows = customRankingRows(values, "p30")
        assertEquals(CustomRankingRow(source), rows[3])
        assertTrue(customRankingOrderValid(rows.map { values[it.sourceIndex] }, "p30"))
        values[source] = 16.25
        rows = customRankingRows(values, "p30")
        assertEquals(listOf(0, 1, source, 2), rows.drop(3).take(4).map { it.sourceIndex })
        values[0] = null
        assertFalse(customRankingRows(values, "p30").any { it.mirrored })
    }

    @Test fun `P30 chart mirrors can be submitted but duplicates within main are rejected`() {
        val original = charts().toMutableList()
        original[3] = original[0]
        val resolved = resolvedCustomRankingCharts(original, "p30")
        assertEquals(original.take(3).map { it.songId }, resolved.drop(3).take(3).map { it.songId })
        val scores = customRankingScores(resolved, "p30")
        assertEquals(36, scores.size)
        assertEquals(1, resolved.drop(3).count { it.songId == original[0].songId })
        assertThrows(IllegalArgumentException::class.java) {
            customRankingScores(resolved.toMutableList().apply { this[35] = this[3] }, "p30")
        }
    }

    @Test fun `AP mirrors start at Best1 through Best3 and participate twice`() {
        val values = listOf(16.4, 16.3, 16.2) + List<Double?>(33) { null }
        val rows = customRankingRows(values, "b30")
        assertEquals(listOf(3, 4, 5), rows.indices.filter { rows[it].mirrored })
        assertFalse(rows[6].mirrored) // Best4 is the first editable row.
        assertEquals((16.4 + 16.3 + 16.2) * 2 / 30, customRankingComposite(rows.map { values[it.sourceIndex] })!!, 1e-10)
    }

    @Test fun `typing into Best4 moves AP copies while retaining input identity`() {
        val values = (listOf(16.4, 16.3, 16.2) + List<Double?>(33) { null }).toMutableList()
        val source = customRankingRows(values, "b30")[6].sourceIndex
        values[source] = 16.5
        var rows = customRankingRows(values, "b30")
        assertEquals(CustomRankingRow(source), rows[3])
        assertEquals(listOf(4, 5, 6), rows.indices.filter { rows[it].mirrored })
        values[source] = 16.25
        rows = customRankingRows(values, "b30")
        assertEquals(listOf(0, 1, source, 2), rows.drop(3).take(4).map { it.sourceIndex })
        values[source] = null
        rows = customRankingRows(values, "b30")
        assertEquals(listOf(3, 4, 5), rows.indices.filter { rows[it].mirrored })
        assertEquals(CustomRankingRow(source), rows[6])
    }

    @Test fun `AP enters Best24 and changing or clearing sources never leaves stale copies`() {
        val values = (listOf<Double?>(16.4, 16.3, 16.2) + List(23) { 17.0 - it * .02 } + listOf(16.39, 16.38, 16.37, 16.36, 16.35, 16.34, 16.33, 16.32, 16.31, 16.30)).toMutableList()
        val rows = customRankingRows(values, "b30")
        assertEquals(36, rows.size)
        assertEquals(CustomRankingRow(0, true), rows[26]) // Best24
        assertEquals(24, rows.drop(3).indexOfFirst { it.sourceIndex == 26 })
        assertTrue(customRankingOrderValid(rows.map { values[it.sourceIndex] }, "b30"))
        values[0] = 10.0
        assertFalse(customRankingRows(values, "b30").drop(3).any { it.sourceIndex == 0 })
        values[0] = null
        assertFalse(customRankingRows(values, "b30").any { it.mirrored })
    }

    @Test fun `explicit Best copy is merged but distinct charts with equal RKS remain`() {
        val original = charts().toMutableList()
        original[3] = original[0].copy(score = "1000000", accuracy = "100")
        val result = resolvedCustomRankingCharts(original, "b30")
        assertEquals(1, result.drop(3).count { it.songId == original[0].songId })
        assertEquals(36, customRankingScores(result, "b30").size)
        val equal = customRankingRows(List(36) { 16.4 }, "b30")
        assertEquals(36, equal.size)
        assertEquals(36, equal.map { it.stableKey }.distinct().size)
    }

    private fun charts() = List(36) { index ->
        CustomChartDraft(source = "catalog", songId = "song-$index", songName = "Song $index", difficulty = "IN",
            chartConstant = (18.0 - index * .1).toString(), accuracy = "99", score = "")
    }

    @Test fun `all modes require 36 values while overflow does not change composite`() {
        val values = List(36) { 18.0 - it * .1 }
        val expected = values.take(30).sum() / 30.0
        assertEquals(expected, customRankingComposite(values)!!, 1e-12)
        assertEquals(expected, customRankingComposite(values.take(30) + List(6) { 0.0 })!!, 1e-12)
        assertNull(customRankingComposite(values.take(30)))
        assertEquals(values.take(30).sum() / 30.0, customRankingComposite(values.take(35) + listOf(null))!!, 1e-12)
        assertEquals(0.0, customRankingComposite(List(36) { null })!!, 0.0)
        for (style in B30ImageStyle.entries) for (ranking in listOf("b30", "p30")) {
            assertEquals(36, customRankingSlotCount(ranking, style))
        }
    }

    @Test fun `AP slots are fixed and B30 main scores may be omitted`() {
        val scores = customRankingScores(charts(), "b30")
        assertEquals(36, scores.size)
        scores.take(3).forEach { assertEquals(1_000_000, it.score); assertEquals(100.0, it.accuracy, 0.0) }
        scores.drop(3).forEach { assertNull(it.score); assertEquals(99.0, it.accuracy, 0.0) }
        customRankingScores(charts(), "p30").forEach {
            assertEquals(1_000_000, it.score); assertEquals(100.0, it.accuracy, 0.0)
        }
    }

    @Test fun `B30 sorts independent groups and permits Best1 above P3`() {
        val values = listOf(14.0, 13.0, 12.0) + List(33) { 17.0 - it * .1 }
        assertTrue(customRankingOrderValid(values, "b30"))
        assertTrue(customRankingOrderValid(values, "p30"))
        val original = charts()
        val reversed = original.take(3).reversed() + original.drop(3).reversed()
        assertThrows(IllegalArgumentException::class.java) { customRankingScores(reversed, "b30") }
        assertEquals(original, sortCustomRankingCharts(reversed, "b30"))
    }

    @Test fun `duplicates missing overflow and invalid ACC block submission`() {
        val original = charts()
        assertThrows(IllegalArgumentException::class.java) { customRankingScores(original.take(35), "b30") }
        assertThrows(IllegalArgumentException::class.java) { customRankingScores(original.toMutableList().apply { this[35] = this[3] }, "b30") }
        for (accuracy in listOf("", "100.1", "NaN")) {
            assertThrows(IllegalArgumentException::class.java) {
                customRankingScores(original.toMutableList().apply { this[35] = this[35].copy(accuracy = accuracy) }, "b30")
            }
        }
    }
}
