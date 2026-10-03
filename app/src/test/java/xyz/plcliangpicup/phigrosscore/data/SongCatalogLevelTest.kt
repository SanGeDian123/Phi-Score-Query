package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongCatalogLevelTest {
    private fun entries(vararg constants: Double) = buildConstantTableEntries(
        constants.mapIndexed { index, constant ->
            SongInfo(
                id = "song-$index",
                name = "Song $index",
                composer = "Composer",
                illustrator = "Illustrator",
                charts = listOf(SongChartInfo("AT", constant)),
            )
        },
    )

    @Test
    fun level18ChartSurvivesCatalogFilteringAndHasAnEntry() {
        val charts = entries(17.9, 18.0)
        assertEquals(listOf(18.0, 17.9), charts.map { it.chart.chartConstant })
        assertEquals(1, charts.count { it.chart.chartConstant?.toInt() == 18 })
        assertEquals(18, constantTableLevels(charts).first())
        assertTrue(18 in constantTableLevels(emptyList()))
    }

    @Test
    fun catalogLevelsExpandAbove18AndIncludePositiveSubOneCharts() {
        val charts = entries(18.0, 19.2, 0.5)
        assertEquals(listOf(19.2, 18.0, 0.5), charts.map { it.chart.chartConstant })
        assertEquals((19 downTo 0).toList(), constantTableLevels(charts))
    }

    @Test
    fun invalidConstantsDoNotBecomeChartsOrLevelEntries() {
        val charts = entries(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 18.0)
        assertEquals(listOf(18.0), charts.map { it.chart.chartConstant })
        assertEquals((18 downTo 1).toList(), constantTableLevels(charts))
    }
}
