package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class CustomRankingEditOrderTest {
    @Test fun `changing 99 to 100 keeps the edited chart visible through intermediate input`() {
        val values = (listOf<Double?>(17.3, 17.2, 17.1) + List(33) { 16.8 - it * .05 }).toMutableList()
        values[3] = calculateChartRks(18.0, 99.0)
        val original = customRankingRows(values, "b30")
        assertTrue(original.any { it.sourceIndex == 3 })
        var order = CustomRankingEditOrder().begin(original)
        order = order.advance(imeVisible = false, inputFocused = true)
        assertEquals(original, order.rows) // IME has not opened yet.
        order = order.advance(imeVisible = true, inputFocused = true)
        for (input in listOf("", "1", "10", "100")) {
            values[3] = input.toDoubleOrNull()?.let { calculateChartRks(18.0, it) }
            order = order.advance(imeVisible = true, inputFocused = true)
            assertEquals(original, order.rows)
            assertTrue(order.rows!!.any { it.sourceIndex == 3 })
            if (input == "1") assertFalse(customRankingRows(values, "b30").any { it.sourceIndex == 3 })
        }
        order = order.advance(imeVisible = false, inputFocused = true) // Back hides IME but can retain focus.
        assertNull(order.rows)
        assertEquals(CustomRankingRow(3), customRankingRows(values, "b30")[3])
    }

    @Test fun `moving between score and ACC does not release order while IME remains open`() {
        val rows = customRankingRows(List(36) { 18.0 - it * .1 }, "b30")
        var order = CustomRankingEditOrder().begin(rows).advance(true, true)
        order = order.advance(true, false)
        order = order.begin(rows.reversed()).advance(true, true)
        assertEquals(rows, order.rows)
        assertNull(order.advance(false, false).rows)
    }

    @Test fun `hardware keyboard commits when input focus leaves`() {
        val rows = customRankingRows(List(36) { 18.0 - it * .1 }, "p30")
        val order = CustomRankingEditOrder().begin(rows)
        assertEquals(rows, order.advance(false, true).rows)
        assertNull(order.advance(false, false).rows)
    }
}
