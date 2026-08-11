package xyz.plcliangpicup.phigrosscore.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class CommunityTimeTest {
    @Test
    fun `utc community time is displayed in china time`() {
        assertEquals(
            "2026-08-11 08:00",
            formatCommunityTime("2026-08-11T00:00:00Z", ZoneId.of("Asia/Shanghai")),
        )
    }

    @Test
    fun `offset timestamp keeps the same instant`() {
        assertEquals(
            "2026-08-11 08:00",
            formatCommunityTime("2026-08-11T02:00:00+02:00", ZoneId.of("Asia/Shanghai")),
        )
    }
}
