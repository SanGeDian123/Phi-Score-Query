package xyz.plcliangpicup.phigrosscore.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PracticeHoldGeometryTest {
    private fun note(speed: Float = 1f, offset: Float = 0f, official: Float? = null) = PracticeNote(
        0, 0, PracticeNoteType.HOLD, 1.0, 3.0, .5, 1.5, 0f, 1f, 255, true, false,
        speed = speed, yOffset = offset, phigrosHoldSpeed = official)
    private val factor = 1080.0 * 10.0 / 45.0 / .83175 / 2.0
    private val varying = PracticeTravel(listOf(PracticeEvent(0.0, 1.0, 4.5f, 4.5f, 1),
        PracticeEvent(1.0, 2.0, 9f, 9f, 1)))
    private fun distances(n: PracticeNote, time: Double, travel: PracticeTravel = varying) =
        practiceNoteDistances(n, time, travel.at(n.startSeconds), travel.at(n.endSeconds), travel.at(time), 1080f)

    @Test fun ordinaryRpeHoldStillIntegratesEndpointsAcrossSpeedChanges() {
        val n = note(speed = 2f)
        val before = distances(n, .25)
        assertEquals((6.75 * 2 * factor).toFloat(), before.tail - before.head, .001f)
        val holding = distances(n, 1.0)
        assertEquals(0f, holding.head, 0f)
        assertEquals((4.5 * 2 * factor).toFloat(), holding.tail, .001f)
    }

    @Test fun officialHoldOwnSpeedIsIndependentOfLineSpeedAndNoteMultiplier() {
        val n = note(speed = .5f, official = 3f)
        for (t in listOf(.1, .25, .5, .75, 1.0, 1.25, 1.5)) {
            val d = distances(n, t)
            val remaining = (n.endSeconds - maxOf(n.startSeconds, t)).coerceAtLeast(0.0)
            assertEquals((remaining * 3 * 4.5 * factor).toFloat(), d.tail - d.head, .001f)
        }
    }

    @Test fun officialHoldDoesNotCollapseWhenLineStops() {
        val stop = PracticeTravel(listOf(PracticeEvent(0.0, 1.0, 9f, 9f, 1),
            PracticeEvent(1.0, 3.0, 0f, 0f, 1)))
        assertEquals(0f, distances(note(), 1.25, stop).tail, .001f)
        assertEquals((.25 * 3 * 4.5 * factor).toFloat(), distances(note(official = 3f), 1.25, stop).tail, .001f)
    }

    @Test fun heldHeadRetainsOffsetWithoutAddingOffsetToBodyLength() {
        val n = note(speed = 2f, offset = 100f)
        val d = distances(n, 1.0)
        assertEquals(240f, d.head, .0001f)
        assertEquals((4.5 * 2 * factor).toFloat(), d.tail - d.head, .001f)
        val official = distances(n.copy(phigrosHoldSpeed = 3f), 1.0)
        assertEquals(240f, official.head, .0001f)
        assertEquals((.5 * 3 * 4.5 * factor).toFloat(), official.tail - official.head, .001f)
    }

    @Test fun ordinaryRpeEndpointIntegrationSupportsAccelerationAndReversal() {
        val reversed = PracticeTravel(listOf(PracticeEvent(0.0, 2.0, 9f, -9f, 1)))
        val n = note(speed = .5f)
        val d = distances(n, 1.0, reversed)
        assertEquals((-1.125 * .5 * factor).toFloat(), d.tail, .001f)
        assertEquals(0f, d.head, 0f)
        val before = distances(n, .25, reversed)
        assertEquals(0f, before.tail - before.head, .001f)
    }

    @Test fun optInParsingUsesSecondsAfterBpmChangesAndPreservesOrdinarySpeed() {
        val chart = PracticeChartParser.parse("""{"META":{"offset":500},
          "BPMList":[{"startTime":[0,0,1],"bpm":120},{"startTime":[2,0,1],"bpm":60}],
          "judgeLineList":[{"notes":[{"type":2,"startTime":[1,0,1],"endTime":[3,0,1],
            "speed":0.5,"phigrosHoldSpeed":3,"yOffset":25}]}]}""", File("m"), File("i"))
        val n = chart.notes.single()
        assertEquals(.5, n.startSeconds, 0.0)
        assertEquals(2.0, n.endSeconds, 0.0)
        assertEquals(.5f, n.speed, 0f)
        assertEquals(3f, n.phigrosHoldSpeed!!, 0f)
        val d = practiceNoteDistances(n, .5, 10.0, 9999.0, 10.0, 1080f)
        assertEquals((1.5 * 3 * 4.5 * factor).toFloat(), d.tail - d.head, .001f)
        assertEquals(.5, chart.offsetSeconds, 0.0)
    }

    @Test fun missingModeIsCompatibleAndInvalidOptInSpeedIsRejected() {
        val base = """{"judgeLineList":[{"notes":[{"type":2,"startTime":[0,0,1],"endTime":[1,0,1]%s}]}]}"""
        for (field in listOf("", ",\"phigrosHoldSpeed\":null")) {
            assertNull(PracticeChartParser.parse(base.format(field), File("m"), File("i")).notes.single().phigrosHoldSpeed)
        }
        assertThrows(Exception::class.java) {
            PracticeChartParser.parse(base.format(",\"phigrosHoldSpeed\":\"invalid\""), File("m"), File("i"))
        }
    }
}
