package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.io.IOException

class RksHistoryTest {
    @Test fun bearerHistoryRequestUsesCursorAndPreservesServerDelta() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"items":[{"rks":15.81,"rksJump":-0.0217,"createdAt":"2026-10-25T08:30:00Z"}],"total":241,"currentRks":15.81,"peakRks":15.9,"hasMore":true,"nextCursor":"2026-10-25T08:30:00Z|42"}"""))
            val response = PhiApiClient(server.url("/").toString(), Json { ignoreUnknownKeys = true })
                .fetchRksHistory("fixture-token", "2026-10-25T09:30:00Z|50")
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v2/rks/history", request.path)
            assertEquals("Bearer fixture-token", request.getHeader("Authorization"))
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertTrue(body["auth"]!!.jsonObject.isEmpty())
            assertEquals("200", body["limit"]!!.jsonPrimitive.content)
            assertEquals("2026-10-25T09:30:00Z|50", body["cursor"]!!.jsonPrimitive.content)
            assertEquals(-.0217, response.items.single().rksJump, 0.0)
            assertEquals(15.9, response.peakRks, 0.0)
            assertEquals("2026-10-25T08:30:00Z|42", response.nextCursor)
        }
    }

    @Test fun rangesUseRealTimeExcludeInvalidDataAndKeepSameTimeApiOrder() {
        val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
        val points = listOf(
            RksHistoryItem(15.9, .1, "2026-10-25T08:00:00Z"),
            RksHistoryItem(15.8, -.1, "2026-10-25T08:00:00Z"),
            RksHistoryItem(Double.NaN, 0.0, "2026-10-24T08:00:00Z"),
            RksHistoryItem(15.7, 0.0, "invalid"),
            RksHistoryItem(15.5, .1, "2026-10-01T08:00:00Z"),
        )
        assertEquals(listOf(15.8, 15.9), points.trendPoints(RksTrendRange.WEEK, now).map { it.rks })
        assertEquals(listOf(15.5, 15.8, 15.9), points.trendPoints(RksTrendRange.MONTH, now).map { it.rks })
        assertTrue(emptyList<RksHistoryItem>().trendPoints(RksTrendRange.ALL, now).isEmpty())
        assertEquals("-0.0100", rksChangeLabel(-.01))
        assertEquals("±0.0000", rksChangeLabel(-.000001))
        assertEquals("+0.0100", rksChangeLabel(.01))
    }

    @Test fun loadsAllRecordsInRangeAcross200ItemPagesThenReusesThemForOtherRanges() = runTest {
        val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
        val records = List(1_000) { index ->
            RksHistoryItem(15.0 - index * .0001, .0001, Instant.ofEpochMilli(now - index * 3_600_000L).toString())
        }
        val requested = mutableListOf<String?>()
        val progress = mutableListOf<Int>()
        val fetch: suspend (String?) -> RksHistoryResponse = { cursor ->
            requested += cursor
            val start = cursor?.toInt() ?: 0
            val end = (start + 200).coerceAtMost(records.size)
            RksHistoryResponse(records.subList(start, end), records.size, 15.0, 15.0,
                end < records.size, end.takeIf { it < records.size }?.toString())
        }
        val month = loadRksHistoryRange(RksTrendRange.MONTH, now, fetchPage = fetch,
            onProgress = { progress += it.items.size })
        assertEquals(listOf(null, "200", "400", "600"), requested)
        assertEquals(listOf(200, 400, 600, 800), progress)
        assertEquals(721, month.items.trendPoints(RksTrendRange.MONTH, now).size)
        assertTrue(month.coversTrendRange(RksTrendRange.MONTH, now))
        val week = loadRksHistoryRange(RksTrendRange.WEEK, now, month, fetch)
        assertSame(month, week)
        assertEquals(4, requested.size)
        val all = loadRksHistoryRange(RksTrendRange.ALL, now, month, fetch)
        assertEquals(listOf(null, "200", "400", "600", "800"), requested)
        assertEquals(1_000, all.items.size)
        assertFalse(all.hasMore)
    }

    @Test fun keepsLoadingWhenPageEndsExactlyAtTheRangeBoundary() = runTest {
        val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
        val boundary = now - 7 * 86_400_000L
        val at = Instant.ofEpochMilli(boundary).toString()
        var requests = 0
        val result = loadRksHistoryRange(RksTrendRange.WEEK, now, fetchPage = { cursor ->
            requests++
            if (cursor == null) RksHistoryResponse(listOf(RksHistoryItem(15.9, .1, at)), hasMore = true, nextCursor = "older")
            else RksHistoryResponse(listOf(RksHistoryItem(15.8, .1, at),
                RksHistoryItem(15.7, .1, Instant.ofEpochMilli(boundary - 1).toString())))
        })
        assertEquals(2, requests)
        assertEquals(listOf(15.8, 15.9), result.items.trendPoints(RksTrendRange.WEEK, now).map { it.rks })
    }

    @Test fun paginationFailureKeepsProgressAndDoesNotPretendItReachedTheEnd() = runTest {
        var partial: RksHistoryResponse? = null
        var requests = 0
        try {
            loadRksHistoryRange(RksTrendRange.ALL, System.currentTimeMillis(), fetchPage = {
                requests++
                if (requests > 1) throw IOException("offline")
                RksHistoryResponse(listOf(RksHistoryItem(15.8, .1, "2026-10-25T08:00:00Z")),
                    hasMore = true, nextCursor = "next")
            }, onProgress = { partial = it })
            fail("Partial results must not be reported as complete")
        } catch (expected: IOException) {
            assertEquals("offline", expected.message)
        }
        assertEquals(2, requests)
        assertEquals(1, partial!!.items.size)
        assertFalse(partial!!.coversTrendRange(RksTrendRange.ALL, System.currentTimeMillis()))
    }

    @Test fun repeatedCursorCannotCauseAnInfiniteAutomaticLoad() = runTest {
        var requests = 0
        try {
            loadRksHistoryRange(RksTrendRange.ALL, System.currentTimeMillis(), fetchPage = {
                requests++
                RksHistoryResponse(listOf(RksHistoryItem(15.8, .1, "2026-10-25T08:00:00Z")),
                    hasMore = true, nextCursor = "repeated")
            })
            fail("Repeated cursor should stop with an error")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("分页未前进"))
        }
        assertEquals(2, requests)
    }
}
