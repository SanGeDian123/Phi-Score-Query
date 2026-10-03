package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class FeedbackApiTest {
    private val feedback = """{"id":"abc","title":"标题","body":"正文","status":"resolved","reply":"已修复","createdAt":"2026-09-08T00:00:00Z","updatedAt":"2026-09-08T00:01:00Z","revision":2,"readRevision":1,"imageCount":1}"""
    @Test fun `live feedback request sends cursor and bearer token`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"cursor":"new-cursor","items":[$feedback]}"""))
            val api = PhiApiClient(server.url("/").toString(), Json { ignoreUnknownKeys = true })
            val result = api.feedbackUpdates("live-token", "previous-cursor")
            assertEquals("new-cursor", result.cursor)
            assertEquals(2L, result.items.single().revision)
            val request = server.takeRequest()
            assertEquals("/api/v2/feedback/updates?cursor=previous-cursor", request.path)
            assertEquals("Bearer live-token", request.getHeader("Authorization"))
        }
    }
    @Test fun `feedback submission pagination and read use authenticated endpoints`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api=PhiApiClient(server.url("/").toString(),Json { ignoreUnknownKeys=true })
            server.enqueue(MockResponse().setBody(feedback))
            val result=api.createFeedback("test-token",FeedbackDraft("abc","标题","正文",listOf("aW1hZ2U=")))
            assertEquals("已处理",result.statusLabel)
            val submission=server.takeRequest()
            assertEquals("/api/v2/feedback",submission.path)
            assertEquals("Bearer test-token",submission.getHeader("Authorization"))
            assertTrue(submission.body.readUtf8().contains("aW1hZ2U="))
            server.enqueue(MockResponse().setBody("[$feedback]"))
            assertEquals(1,api.feedbackList("test-token",30).size)
            assertEquals("/api/v2/feedback/mine?offset=30",server.takeRequest().path)
            server.enqueue(MockResponse().setBody("[$feedback]"))
            assertEquals(2L,api.feedbackList("test-token",notifications=true).single().revision)
            assertEquals("/api/v2/feedback/notifications",server.takeRequest().path)
            server.enqueue(MockResponse().setBody("true"))
            assertTrue(api.feedbackRead("test-token","abc",2))
            val read=server.takeRequest()
            assertEquals("/api/v2/feedback/abc/read",read.path)
            assertEquals("{\"revision\":2}",read.body.readUtf8())
        }
    }
}
