package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class PracticeCatalogTest {
    private fun entry(file: String, difficulty: String = "IN Lv.16.1") =
        """{"songId":"new.song","title":"New song","difficulty":"$difficulty","chartConstant":16.1,"fileName":"$file"}"""
    @Test fun discoversUnbundledSongsAndSortsInBeforeAt() {
        val body = """{"practiceCharts":[${entry("at.pez", "AT Lv.17.6")},${entry("in.pez")}]}"""
        val charts = PracticeCharts.parseCatalog(body)
        assertEquals(listOf("in.pez", "at.pez"), charts.map { it.fileName })
        assertEquals("new.song", charts[0].songId)
        assertTrue(PracticeCharts.parseCatalog("""{"practiceCharts":[]}""").isEmpty())
    }
    @Test fun rejectsUnsafeFilenamesAndInvalidMetadata() {
        val body = """{"practiceCharts":[${entry("../bad.pez")},${entry("ok.pez")},${entry("ok.pez")},${entry("bad.pez", "???")}]}"""
        assertEquals(listOf("ok.pez"), PracticeCharts.parseCatalog(body).map { it.fileName })
    }
    @Test fun activityMetadataRoundTripsWithoutAClientRegistry() {
        val source = PracticeCharts.parseCatalog("""{"practiceCharts":[${entry("new.pez") }]}""").single()
        assertEquals(source, PracticeCharts.decodeSource(PracticeCharts.encodeSource(source)))
    }
}
