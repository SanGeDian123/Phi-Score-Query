package xyz.plcliangpicup.phigrosscore.data

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PracticeEmAtArchiveTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun resizedArchiveLoadsWithEveryOriginalNoteHoldAndLineEvent() {
        val sourcePath = System.getenv("PSQ_EM_AT_ORIGINAL")
        val resultPath = System.getenv("PSQ_EM_AT_REPACKED")
        assumeTrue(sourcePath != null && resultPath != null)
        val source = PracticeChartParser.loadIn(folder.newFolder("original"), File(sourcePath!!))
        val result = PracticeChartParser.loadIn(folder.newFolder("repacked"), File(resultPath!!))
        assertEquals(source.notes, result.notes)
        assertEquals(source.lines, result.lines)
        assertEquals(source.bpms, result.bpms)
        assertEquals(source.offsetSeconds, result.offsetSeconds, 0.0)
        assertEquals(2077, result.notes.size)
        assertEquals(211, result.notes.count { it.type == PracticeNoteType.HOLD })
        assertEquals("music.mp3", result.musicFile.name)
        assertTrue(result.musicFile.length() > 0)
        assertArrayEquals(source.illustrationFile.readBytes(), result.illustrationFile.readBytes())
    }
}
