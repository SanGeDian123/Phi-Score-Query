package xyz.plcliangpicup.phigrosscore.data

import java.io.File
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PracticeStreamingTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun memberOrderNullLayersAndBeatTimingRemainCompatible() {
        val chart = PracticeChartParser.parse("""{
          "judgeLineList":[{"notes":[
            {"type":2,"startTime":[2,1,2],"endTime":[4,0,1],"above":0,"speed":1.25},
            {"type":3,"startTime":[2,1,2],"endTime":[2,1,2]}],
            "eventLayers":[null,{"moveXEvents":[
              {"startTime":[2,0,1],"endTime":[4,0,1],"start":1.25,"end":2.5,
               "easingType":5,"easingLeft":0.25,"easingRight":0.75,"bezier":1,"bezierPoints":[0.1,0.2,0.3,0.4]}],
              "alphaEvents":null}],"Group":2,"father":-1,"isCover":0},
            {"notes":null,"eventLayers":null}],
          "META":{"name":"Streaming","offset":125},
          "BPMList":[{"bpm":60,"startTime":[2,0,1]},{"bpm":120,"startTime":[0,0,1]}],
          "unknown":{"nested":[1,null,true,"ignored"]}
        }""", File("music"), File("image"))
        assertEquals("Streaming", chart.name)
        assertEquals(0.125, chart.offsetSeconds, 0.0)
        assertEquals(2, chart.lines.size)
        val line = chart.lines.first()
        assertFalse(line.cover)
        assertEquals(2, line.group)
        val event = line.moveX.single()
        assertEquals(1, event.layer)
        assertEquals(1.0, event.startBeat, 0.0)
        assertEquals(3.0, event.endBeat, 0.0)
        assertEquals(listOf(.1f, .2f, .3f, .4f), event.bezier)
        assertEquals(.25f, event.easingLeft, 0f)
        assertEquals(.75f, event.easingRight, 0f)
        val hold = chart.notes.first()
        assertEquals(PracticeNoteType.HOLD, hold.type)
        assertEquals(1.5, hold.startSeconds, 0.0)
        assertEquals(3.0, hold.endSeconds, 0.0)
        assertEquals(1.25f, hold.speed, 0f)
        assertFalse(hold.above)
        assertTrue(chart.notes.all { it.isMulti })
    }

    @Test fun truncatedDocumentIsRejected() {
        assertThrows(Exception::class.java) {
            PracticeChartParser.readHeader("""{"META":{},"judgeLineList":[{"notes":[""".reader())
        }
    }

    // Run with a 192 MiB test heap and the actual converted EM IN archive.
    @Test fun actualEmInDownloadsOnceAndLoadsWithinLimitedHeap() {
        val path = System.getenv("PSQ_EM_IN_PEZ")
        assumeTrue("Set PSQ_EM_IN_PEZ for the real EM IN regression", path != null)
        val source = File(path!!)
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(405)) // exercise the single-connection fallback
        server.enqueue(MockResponse().setBody(Buffer().write(source.readBytes())))
        server.start()
        try {
            val chart = PracticeChartSource("em", "Exoplanetary Mirage", "IN Lv.16.9", 16.9,
                "ExoplanetaryMirage_IN_16.9.pez", source.length(), "regression")
            val dir = folder.newFolder()
            val attempts = mutableListOf<Int>()
            val file = PracticeChartDownloader.ensureDownloadedIn(dir, chart, server.url("/em").toString(), {
                attempts += it.attempt
            })
            assertTrue(attempts.all { it == 1 })
            assertEquals(2, server.requestCount)
            val loaded = PracticeChartParser.loadIn(folder.newFolder(), file)
            assertEquals(24, loaded.lines.size)
            assertEquals(2055, loaded.notes.size)
            assertEquals(217, loaded.notes.count { it.type == PracticeNoteType.HOLD })
            assertEquals(213790, loaded.lines.sumOf { it.alpha.size + it.moveX.size + it.moveY.size + it.rotate.size + it.speed.size })
            assertEquals(192.0, loaded.bpms.single().bpm, 0.0)
            assertTrue(loaded.musicFile.length() > 0)
            assertTrue(loaded.illustrationFile.length() > 0)
            PracticeChartDownloader.ensureDownloadedIn(dir, chart, server.url("/em").toString(), {})
            assertEquals("Opening the same chart must not make another request", 2, server.requestCount)
        } finally { server.shutdown() }
    }
}
