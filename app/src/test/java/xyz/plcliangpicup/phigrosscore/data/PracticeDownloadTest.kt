package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import okhttp3.mockwebserver.*
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.util.zip.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException

class PracticeDownloadTest {
    @get:Rule val folder = TemporaryFolder()
    private fun archive(payloadSize: Int = 9 * 1024 * 1024): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in listOf("chart.json" to "{\"META\":{}}".toByteArray(),
                "illustration.png" to byteArrayOf(1), "music.wav" to ByteArray(payloadSize) { (it % 251).toByte() })) {
                val crc = CRC32().apply { update(bytes) }
                zip.putNextEntry(ZipEntry(name).apply { method=ZipEntry.STORED; size=bytes.size.toLong(); compressedSize=size; this.crc=crc.value })
                zip.write(bytes); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
    @Test fun parallelPauseRetainsChunksAndResumesWithoutZeroProgress() = checkResume(false, false)
    @Test fun changedEtagReplacesOldChunks() = checkResume(true, false)
    @Test fun unsupportedRangeFallsBackToSingleDownload() = checkResume(false, true)
    @Test fun smallChartsUseTwoRangesAndVerifiedLocalCopyNeedsNoNetwork() {
        val bytes = archive(3 * 1024 * 1024)
        val server = MockWebServer()
        val ranges = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = MockResponse().setHeader("ETag", "\"small\"")
                if (request.method == "HEAD") return response.setHeader("Content-Length", bytes.size)
                val range = request.getHeader("Range") ?: return response.setBody(Buffer().write(bytes))
                ranges.add(range)
                val parts = range.removePrefix("bytes=").split("-")
                val start = parts[0].toInt(); val end = parts[1].toInt()
                return response.setResponseCode(206).setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                    .setBody(Buffer().write(bytes, start, end-start+1))
                    .throttleBody(128 * 1024, 30, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        }
        server.start()
        try {
            val dir = folder.newFolder()
            val chart = PracticeChartSource("test", "Test", "IN", 16.0, "small.pez", bytes.size.toLong(), "v1")
            val url = server.url("/small").toString()
            val first = PracticeChartDownloader.ensureDownloadedIn(dir, chart, url, {})
            assertArrayEquals(bytes, first.readBytes())
            assertEquals(2, ranges.size)
            val requests = server.requestCount
            PracticeChartDownloader.ensureDownloadedIn(dir, chart, url, {})
            assertEquals(requests, server.requestCount)
            PracticeChartDownloader.ensureDownloadedIn(dir, chart.copy(revision = "v2"), url, {})
            assertEquals(4, ranges.size)
        } finally { server.shutdown() }
    }

    @Test fun tinyKnownFileSkipsMetadataRoundTrip() {
        val bytes = archive(1024)
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        server.start()
        try {
            val chart = PracticeChartSource("test", "Test", "IN", 16.0, "tiny.pez", bytes.size.toLong())
            val file = PracticeChartDownloader.ensureDownloadedIn(folder.newFolder(), chart, server.url("/tiny").toString(), {})
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(1, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
        } finally { server.shutdown() }
    }
    private fun checkResume(changeTag: Boolean, rejectRange: Boolean) {
        val bytes = archive()
        val server = MockWebServer()
        var tag = "\"v1\""
        val requests = CopyOnWriteArrayList<Long>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = MockResponse().setHeader("ETag", tag)
                if (request.method == "HEAD") return response.setHeader("Content-Length", bytes.size)
                val range = request.getHeader("Range")
                if (range == null || rejectRange) return response.setBody(Buffer().write(bytes))
                val parts = range.removePrefix("bytes=").split("-")
                val start = parts[0].toInt(); val end = parts[1].toIntOrNull() ?: bytes.lastIndex
                requests.add(start.toLong())
                return response.setResponseCode(206).setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                    .setBody(Buffer().write(bytes, start, end-start+1))
                    .throttleBody(128 * 1024, 30, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        }
        server.start()
        try {
            val dir = folder.newFolder()
            val chart = PracticeChartSource("test.id", "Test", "IN Lv.16", 16.0, "Test_IN_16.pez")
            val stop = AtomicBoolean(false)
            if (!rejectRange) {
                try {
                    PracticeChartDownloader.ensureDownloadedIn(dir, chart, server.url("/chart").toString(), {
                        if (it.receivedBytes >= 512 * 1024) stop.set(true)
                    }, { stop.get() })
                    fail("Expected pause")
                } catch (_: CancellationException) { }
                val saved = PracticeChartDownloader.savedProgressIn(dir, chart)
                assertTrue(saved.receivedBytes > 0)
                assertTrue(saved.receivedBytes < bytes.size)
                requests.clear()
                if (changeTag) tag = "\"v2\""
            }
            val saved = PracticeChartDownloader.savedProgressIn(dir, chart).receivedBytes
            val progress = CopyOnWriteArrayList<Long>()
            val result = PracticeChartDownloader.ensureDownloadedIn(dir, chart, server.url("/chart").toString(), { progress.add(it.receivedBytes) })
            assertArrayEquals(bytes, result.readBytes())
            if (!changeTag && !rejectRange) {
                assertTrue(progress.all { it >= saved })
                assertTrue(requests.any { start -> (0..3).none { start == bytes.size.toLong()*it/4 } })
            }
            assertFalse(dir.listFiles()!!.any { it.name.contains(".part") })
        } finally { server.shutdown() }
    }
}
