package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class LeaderboardConsistencyTest {
    private fun snapshot(rks: Double) = B30Snapshot(rks, emptyList(), emptyMap(), cachedAtEpochMs = 1L)
    private fun MockWebServer.top(score: Double) = enqueue(MockResponse().setBody(
        """{"items":[{"rank":1,"nickname":"Player","score":$score}],"total":1}""",
    ))
    private fun MockWebServer.me(score: Double) = enqueue(MockResponse().setBody(
        """{"rank":1,"score":$score,"total":1,"percentile":100}""",
    ))
    private suspend fun load(server: MockWebServer, snapshot: B30Snapshot): LeaderboardSnapshot {
        val api = PhiApiClient(server.url("/").toString(), Json { ignoreUnknownKeys = true })
        return fetchConsistentLeaderboard(snapshot, { api.fetchLeaderboard("test-token") }, { api.fetchLeaderboardMe("test-token") })
    }

    @Test fun `lower current RKS waits for both ranking responses to match`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.top(16.1234); server.me(16.1234)
            server.top(16.0); server.me(16.0)
            val current = snapshot(16.0)
            val board = load(server, current)
            assertSame(current, board.scoreSnapshot)
            assertEquals(current.totalRks, board.me.score, 0.0)
            assertEquals(current.totalRks, board.entries.single().score, 0.0)
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun `improved RKS also waits for leaderboard refresh`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.top(16.0); server.me(16.0)
            server.top(16.2); server.me(16.2)
            val board = load(server, snapshot(16.2))
            assertEquals(16.2, board.me.score, 0.0)
            assertEquals(16.2, board.entries.single().score, 0.0)
        }
    }

    @Test fun `list and own card from different updates are retried`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.top(16.0); server.me(16.2)
            server.top(16.2); server.me(16.2)
            val board = load(server, snapshot(16.2))
            assertEquals(board.me.score, board.entries.single().score, 0.0)
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun `persistently stale server data is never presented as current ranking`() = runTest {
        MockWebServer().use { server ->
            server.start()
            repeat(3) { server.top(16.1234); server.me(16.1234) }
            try {
                load(server, snapshot(16.0))
                fail("Stale ranking must not be shown with the current personal snapshot")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("尚未同步"))
            }
            assertEquals(6, server.requestCount)
        }
    }

    @Test fun `consistent ranking returns immediately using the same personal snapshot`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.top(16.0); server.me(16.0)
            val current = snapshot(16.0)
            val board = load(server, current)
            assertSame(current, board.scoreSnapshot)
            assertEquals(2, server.requestCount)
            assertTrue(server.takeRequest().path.orEmpty().startsWith("/api/v2/leaderboard/rks/top"))
            val request = server.takeRequest()
            assertEquals("/api/v2/leaderboard/rks/me", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
        }
    }

    @Test fun `unranked own score is retained without making the user public`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"items":[],"total":0}"""))
            server.enqueue(MockResponse().setBody("""{"rank":0,"score":16.0,"total":0}"""))
            val board = load(server, snapshot(16.0))
            assertEquals(0, board.me.rank)
            assertEquals(16.0, board.me.score, 0.0)
            assertTrue(board.entries.isEmpty())
        }
    }
}
