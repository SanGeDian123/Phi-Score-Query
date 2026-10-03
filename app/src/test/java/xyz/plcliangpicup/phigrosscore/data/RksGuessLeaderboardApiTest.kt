package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RksGuessLeaderboardApiTest {
    @Test fun `leaderboard uses bearer and decodes win counters`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"items":[{"rank":1,"nickname":"玩家","totalWins":12,"singleWins":7,"publicWins":5,"isMe":true}],"myWins":12}"""))
            val api = PhiApiClient(server.url("/").toString(), Json { ignoreUnknownKeys = true })
            val result = api.fetchRksGuessLeaderboard("test-token")
            assertEquals(12L, result.myWins)
            assertEquals(7L, result.items.single().singleWins)
            assertEquals(5L, result.items.single().publicWins)
            assertTrue(result.items.single().isMe)
            val request = server.takeRequest()
            assertEquals("/api/v2/games/rks-guess/leaderboard", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
        }
    }
}
