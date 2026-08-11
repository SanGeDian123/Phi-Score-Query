package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RksCalculatorTest {
    @Test
    fun `two known values solve the missing chart value`() {
        val rks = solveChartRks(15.0, 98.5, null)
        val acc = solveChartRks(15.0, null, rks.chartRks)
        val constant = solveChartRks(null, 98.5, rks.chartRks)

        assertEquals(14.0166666667, rks.chartRks, 1e-9)
        assertEquals(98.5, acc.accuracy, 1e-9)
        assertEquals(15.0, constant.chartConstant, 1e-9)
    }

    @Test
    fun `account projection updates an existing real B27 entry`() {
        val current = calculateChartRks(15.0, 98.0)
        val realB27 = (1..26).map { index -> b30Item(index, 20.0 - index / 10.0) } +
            b30Item(27, current, chartConstant = 15.0)
        val projection = projectAccountRksIncrease(
            chartConstant = 15.0,
            currentAccuracy = 98.0,
            targetAccuracy = 99.0,
            currentAccountRks = 15.5,
            b30Items = realB27,
        )
        val expected = (calculateChartRks(15.0, 99.0) - current) / 30.0
        assertEquals(expected, projection.accountIncrease, 1e-12)
        assertEquals(15.5 + expected, projection.projectedAccountRks, 1e-12)
        assertEquals(true, projection.wasInBest27)
        assertEquals(true, projection.entersBest27)
    }

    @Test
    fun `account projection uses the real B27 replacement line`() {
        val realB27 = (1..27).map { index -> b30Item(index, 28.0 - index) }
        val target = calculateChartRks(10.0, 99.0)
        val projection = projectAccountRksIncrease(
            chartConstant = 10.0,
            currentChartRks = 0.5,
            targetAccuracy = 99.0,
            currentAccountRks = 15.0,
            b30Items = realB27,
        )

        assertEquals((target - 1.0) / 30.0, projection.accountIncrease, 1e-12)
        assertEquals(false, projection.wasInBest27)
        assertEquals(true, projection.entersBest27)
        assertEquals(1.0, projection.best27Floor, 1e-12)
    }

    @Test
    fun `account projection returns zero below the real B27 replacement line`() {
        val realB27 = (1..27).map { index -> b30Item(index, 28.0 - index) }
        val projection = projectAccountRksIncrease(
            chartConstant = 1.0,
            currentChartRks = 0.1,
            targetAccuracy = 99.0,
            currentAccountRks = 15.0,
            b30Items = realB27,
        )

        assertEquals(0.0, projection.accountIncrease, 1e-12)
        assertEquals(false, projection.entersBest27)
    }

    @Test
    fun `account projection recalculates AP3 when target reaches Phi`() {
        val realB27 = (1..27).map { index -> b30Item(index, 28.0 - index) }
        val realAp3 = listOf(12.0, 11.0, 9.0).mapIndexed { index, rks ->
            b30Item(index + 1, rks, section = "AP")
        }
        val projection = projectAccountRksIncrease(
            chartConstant = 10.0,
            currentChartRks = 0.5,
            targetAccuracy = 100.0,
            currentAccountRks = 15.0,
            b30Items = realB27 + realAp3,
        )

        assertEquals(9.0 / 30.0, projection.best27Increase, 1e-12)
        assertEquals(1.0 / 30.0, projection.ap3Increase, 1e-12)
        assertEquals(10.0 / 30.0, projection.accountIncrease, 1e-12)
        assertEquals(true, projection.entersAp3)
    }

    @Test
    fun `custom composite keeps missing slots at zero`() {
        assertEquals(1.0, calculateCustomCompositeRks(listOf(10.0, 10.0, 10.0)), 1e-12)
        assertThrows(IllegalArgumentException::class.java) {
            calculateCustomCompositeRks(List(31) { 1.0 })
        }
    }

    @Test
    fun `calculator draft normalizes corrupt preferences and keeps thirty slots`() {
        val draft = RksCalculatorDraft(
            mode = "broken",
            threeAccuracy = "99.25",
            growthMetric = "broken",
            customRanking = "broken",
            b30Values = listOf("15.5", "not-a-number"),
            p30Values = List(35) { "14.0" },
        ).normalized()

        assertEquals("three_value", draft.mode)
        assertEquals("99.25", draft.threeAccuracy)
        assertEquals("acc", draft.growthMetric)
        assertEquals("b30", draft.customRanking)
        assertEquals(30, draft.b30Values.size)
        assertEquals("15.5", draft.b30Values[0])
        assertEquals("", draft.b30Values[1])
        assertEquals(30, draft.p30Values.size)
    }

    @Test
    fun `calculator draft survives app preference serialization`() {
        val original = RksCalculatorDraft(
            mode = "custom",
            threeConstant = "15.9",
            growthMetric = "rks",
            b30Values = List(30) { index -> if (index < 3) "15.${index + 1}" else "" },
        ).normalized()
        val encoded = Json.encodeToString(RksCalculatorDraft.serializer(), original)
        val restored = Json.decodeFromString(RksCalculatorDraft.serializer(), encoded).normalized()

        assertEquals(original, restored)
    }

    private fun b30Item(
        position: Int,
        rks: Double,
        chartConstant: Double? = null,
        section: String = "BEST",
    ) = B30Item(
        position = position,
        section = section,
        songId = "song-$section-$position",
        songName = "Song $position",
        difficulty = "IN",
        chartConstant = chartConstant,
        rks = rks,
    )
}
