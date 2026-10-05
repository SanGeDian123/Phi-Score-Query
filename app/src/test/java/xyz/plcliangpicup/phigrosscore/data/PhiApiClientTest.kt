package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhiApiClientTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun closeServer() {
        server.close()
    }

    @Test
    fun `player leaderboard combines legacy pages up to rank 1500`() = runTest {
        enqueueLeaderboardPage(1, 1000, 1756)
        enqueueLeaderboardPage(1001, 500, 1756)
        server.start()

        val result = PhiApiClient(server.url("/").toString(), json).fetchLeaderboard("access")

        assertEquals(1756, result.total)
        assertEquals((1..1500).toList(), result.items.map { it.rank })
        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("1000", first.requestUrl?.queryParameter("limit"))
        assertEquals(null, first.requestUrl?.queryParameter("offset"))
        assertEquals("500", second.requestUrl?.queryParameter("limit"))
        assertEquals("1000", second.requestUrl?.queryParameter("offset"))
        listOf(first, second).forEach { request ->
            assertEquals("/api/v2/leaderboard/rks/top", request.requestUrl?.encodedPath)
            assertEquals("true", request.requestUrl?.queryParameter("lite"))
            assertEquals("Bearer access", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `player leaderboard prefers encoded server cursor over offset`() = runTest {
        val cursor = "next+page/with=chars"
        enqueueLeaderboardPage(1, 1000, 1500, cursor)
        enqueueLeaderboardPage(1001, 500, 1500)
        server.start()

        val result = PhiApiClient(server.url("/").toString(), json).fetchLeaderboard("access")

        assertEquals((1..1500).toList(), result.items.map { it.rank })
        server.takeRequest()
        val second = server.takeRequest()
        assertEquals(cursor, second.requestUrl?.queryParameter("cursor"))
        assertEquals(null, second.requestUrl?.queryParameter("offset"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `player leaderboard stops when all public players have been read`() = runTest {
        enqueueLeaderboardPage(1, 8, 8)
        server.start()

        val result = PhiApiClient(server.url("/").toString(), json).fetchLeaderboard("access")

        assertEquals(8, result.items.size)
        assertEquals(8, result.total)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `player leaderboard stops on an empty continuation page`() = runTest {
        enqueueLeaderboardPage(1, 1000, 1756)
        enqueueLeaderboardPage(1001, 0, 1756)
        server.start()

        val result = PhiApiClient(server.url("/").toString(), json).fetchLeaderboard("access")

        assertEquals(1000, result.items.size)
        assertEquals(2, server.requestCount)
    }

    private fun enqueueLeaderboardPage(firstRank: Int, count: Int, total: Int, cursor: String? = null) {
        val page = LeaderboardResponse(
            items = List(count) { index ->
                val rank = firstRank + index
                LeaderboardEntry(rank = rank, score = 18.0 - rank * .001)
            },
            total = total,
            nextCursor = cursor,
        )
        server.enqueue(MockResponse().setBody(json.encodeToString(page)))
    }

    @Test
    fun `update check retries when response body is interrupted`() = runTest {
        val manifest = """
            {
              "versionCode": 33,
              "versionName": "Pre-0.9.7.5",
              "apkUrl": "https://example.com/app.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setBody(manifest)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        server.enqueue(MockResponse().setBody(manifest))
        server.start()

        val client = PhiApiClient(server.url("/").toString(), json)
        val update = client.fetchAppUpdate()

        assertEquals("Pre-0.9.7.5", update.versionName)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `B30 and P30 rendering use independent endpoints`() = runTest {
        server.enqueue(MockResponse().setBody("b30-png"))
        server.enqueue(MockResponse().setBody("p30-png"))
        server.start()

        val client = PhiApiClient(server.url("/").toString(), json)
        client.renderB30("access", 1440, B30ImageStyle.CLASSIC, isDarkTheme = true)
        val b30Request = server.takeRequest()
        client.renderP30("access", 1440, B30ImageStyle.CLASSIC, isDarkTheme = true)
        val p30Request = server.takeRequest()

        assertEquals("/api/v2/image/bn?format=png&width=1440", b30Request.path)
        assertEquals("/api/v2/image/p30?format=png&width=1440", p30Request.path)
        assertFalse(b30Request.body.readUtf8().contains("\"mode\""))
        val p30Body = p30Request.body.readUtf8()
        assertFalse(p30Body.contains("\"mode\""))
        assertFalse(p30Body.contains("\"n\""))
    }

    @Test
    fun `song search sends aliases to backend search endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                    {
                      "items":[{
                        "id":"Anomaly.D_AAN",
                        "name":"Anomaly",
                        "composer":"D_AAN",
                        "illustrator":"dummy",
                        "chartConstants":{"in":14.0}
                      }],
                      "total":1
                    }
                """.trimIndent(),
            ),
        )
        server.start()

        val client = PhiApiClient(server.url("/").toString(), json)
        val result = client.searchSongs("access", "异常")
        val request = server.takeRequest()

        assertEquals("异常", request.requestUrl?.queryParameter("q"))
        assertEquals("100", request.requestUrl?.queryParameter("limit"))
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals("Anomaly.D_AAN", result.items.single().id)
    }

    @Test
    fun `Phi Plugin B30 requests Best 33 without changing P30 semantics`() = runTest {
        server.enqueue(MockResponse().setBody("b30-png"))
        server.enqueue(MockResponse().setBody("p30-png"))
        server.start()

        val client = PhiApiClient(server.url("/").toString(), json)
        client.renderB30("access", 1260, B30ImageStyle.PHI_PLUGIN, isDarkTheme = true)
        val b30Request = server.takeRequest()
        client.renderP30("access", 1260, B30ImageStyle.PHI_PLUGIN, isDarkTheme = true)
        val p30Request = server.takeRequest()

        assertEquals(
            "/api/v2/image/bn?format=png&width=1260&template=phi-plugin",
            b30Request.path,
        )
        assertTrue(b30Request.body.readUtf8().contains("\"n\":33"))
        assertEquals(
            "/api/v2/image/p30?format=png&width=1260&template=phi-plugin",
            p30Request.path,
        )
        assertFalse(p30Request.body.readUtf8().contains("\"n\""))
    }

    @Test
    fun `announcement uses the static latest endpoint and decodes content`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                    {
                      "id": "notice-20260809",
                      "title": "测试公告",
                      "body": "公告正文",
                      "publishedAt": "2026-08-09 22:30"
                    }
                """.trimIndent(),
            ),
        )
        server.start()

        val client = PhiApiClient(server.url("/").toString(), json)
        val announcement = client.fetchAppAnnouncement()
        val request = server.takeRequest()

        assertEquals("/app-announcement/latest.json", request.path)
        assertEquals("no-cache", request.getHeader("Cache-Control"))
        assertEquals("notice-20260809", announcement.id)
        assertEquals("测试公告", announcement.title)
        assertEquals("公告正文", announcement.body)
    }

    @Test
    fun `announcement history uses the static index endpoint and preserves newest first order`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                    {
                      "items": [
                        {
                          "id": "notice-2",
                          "title": "最新公告",
                          "body": "第二条正文",
                          "publishedAt": "2026-08-30 20:00:00 +08:00"
                        },
                        {
                          "id": "notice-1",
                          "title": "历史公告",
                          "body": "第一条正文",
                          "publishedAt": "2026-08-29 20:00:00 +08:00"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        server.start()

        val client = PhiApiClient(server.url("/").toString(), json)
        val history = client.fetchAppAnnouncementHistory()
        val request = server.takeRequest()

        assertEquals("/app-announcement/index.json", request.path)
        assertEquals("no-cache", request.getHeader("Cache-Control"))
        assertEquals(listOf("notice-2", "notice-1"), history.items.map(AppAnnouncement::id))
    }

    @Test
    fun `suggestion upload is authenticated multipart and resolves media urls`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                    {
                      "id":"post-1",
                      "description":"求建议！",
                      "imageUrl":"/suggestion-media/score.png",
                      "author":{"nickname":"Alice","rks":15.5},
                      "createdAt":"2026-08-11T00:00:00Z",
                      "comments":[]
                    }
                """.trimIndent(),
            ),
        )
        server.start()

        val client = PhiApiClient(server.url("/").toString(), json)
        val post = client.createSuggestionPost(
            accessToken = "access",
            description = "求建议！",
            imageBytes = byteArrayOf(0x01, 0x02, 0x03),
            imageMimeType = "image/png",
        )
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("/api/v2/suggestions/posts", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")?.startsWith("multipart/form-data") == true)
        assertTrue(body.contains("name=\"description\""))
        assertTrue(body.contains("求建议！"))
        assertTrue(body.contains("filename=\"score.png\""))
        assertEquals(server.url("/suggestion-media/score.png").toString(), post.imageUrl)
    }

    @Test
    fun `random suggestion excludes recently viewed posts`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                    {
                      "id":"post-2",
                      "description":"建议",
                      "imageUrl":"/suggestion-media/next.webp",
                      "author":{"nickname":"Bob","rks":14.0},
                      "createdAt":"2026-08-11T00:00:00Z",
                      "comments":[]
                    }
                """.trimIndent(),
            ),
        )
        server.start()

        val client = PhiApiClient(server.url("/").toString(), json)
        client.fetchRandomSuggestion(
            "access",
            excludedIds = listOf("post-1", " post-2 ", "post-1", ""),
        )
        val request = server.takeRequest()

        assertEquals("/api/v2/suggestions/random?exclude_ids=post-1%2Cpost-2", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
    }

    @Test
    fun `chart achievement rates encode chart identity and decode exclusive grades`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                    {
                      "songId":"song.with space",
                      "songName":"Test Song",
                      "difficulty":"AT",
                      "total":8,
                      "rates":[
                        {"grade":"F","count":1,"rate":0.125},
                        {"grade":"C","count":1,"rate":0.125},
                        {"grade":"B","count":1,"rate":0.125},
                        {"grade":"A","count":1,"rate":0.125},
                        {"grade":"S","count":1,"rate":0.125},
                        {"grade":"V","count":1,"rate":0.125},
                        {"grade":"FC","count":1,"rate":0.125},
                        {"grade":"AP","count":1,"rate":0.125}
                      ]
                    }
                """.trimIndent(),
            ),
        )
        server.start()

        val response = PhiApiClient(server.url("/").toString(), json)
            .fetchChartAchievementRates("access", "song.with space", "at")
        val request = server.takeRequest()

        assertEquals(
            "/api/v2/songs/achievement-rates?song_id=song.with%20space&difficulty=at",
            request.path,
        )
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals(8, response.total)
        assertEquals(listOf("F", "C", "B", "A", "S", "V", "FC", "AP"), response.rates.map { it.grade })
        assertTrue(response.rates.all { it.count == 1 && it.rate == 0.125 })
    }

    @Test
    fun `blank comment image is treated as absent`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                    {
                      "id":"post-3",
                      "description":"求建议！",
                      "imageUrl":"/suggestion-media/post.png",
                      "author":{"nickname":"Alice","rks":15.5},
                      "createdAt":"2026-08-11T00:00:00Z",
                      "comments":[{
                        "id":"comment-1",
                        "text":"先练短板",
                        "imageUrl":"   ",
                        "author":{"nickname":"Bob","rks":14.0},
                        "createdAt":"2026-08-11T00:01:00Z",
                        "canDelete":true
                      }]
                    }
                """.trimIndent(),
            ),
        )
        server.start()

        val post = PhiApiClient(server.url("/").toString(), json)
            .fetchSuggestionPost("access", "post-3")

        assertEquals(null, post.comments.single().imageUrl)
        assertTrue(post.comments.single().canDelete)
    }

    @Test
    fun `suggestion delete and notification routes are authenticated`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(
            MockResponse().setBody(
                """
                    {
                      "checkedAt":"2026-08-11T00:15:00Z",
                      "items":[{
                        "postId":"post-1",
                        "postTitle":"求建议！",
                        "commentCount":1,
                        "latestCommentAt":"2026-08-11T00:10:00Z"
                      }]
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val client = PhiApiClient(server.url("/").toString(), json)

        client.deleteSuggestionComment("access", "comment-1")
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
        assertEquals("/api/v2/suggestions/comments/comment-1", deleteRequest.path)
        assertEquals("Bearer access", deleteRequest.getHeader("Authorization"))

        val response = client.fetchSuggestionNotifications("access", "2026-08-11T00:00:00Z")
        val notificationRequest = server.takeRequest()
        assertTrue(notificationRequest.path.orEmpty().startsWith("/api/v2/suggestions/notifications?after="))
        assertEquals(1, response.items.single().commentCount)
    }
}
