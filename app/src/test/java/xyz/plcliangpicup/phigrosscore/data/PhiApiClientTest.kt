package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
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
    fun `random suggestion excludes current post`() = runTest {
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
        client.fetchRandomSuggestion("access", excludeId = "post-1")
        val request = server.takeRequest()

        assertEquals("/api/v2/suggestions/random?exclude=post-1", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
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
