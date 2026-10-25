package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class BestNImageTest {
    @Test fun `all styles send exact N and use an independent Best N endpoint`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api = PhiApiClient(server.url("/").toString(), Json { ignoreUnknownKeys = true })
            for (style in B30ImageStyle.entries) {
                for (n in listOf(1, 30, 200)) {
                    server.enqueue(MockResponse().setBody("image"))
                    assertArrayEquals("image".toByteArray(), api.renderBestN("fixture", n, 1440, style, false))
                    val request = server.takeRequest()
                    assertEquals("/api/v2/image/bestn", request.requestUrl!!.encodedPath)
                    assertEquals(if (style == B30ImageStyle.CLASSIC) null else style.preferenceValue, request.requestUrl!!.queryParameter("template"))
                    val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                    assertEquals(n.toString(), body.getValue("n").jsonPrimitive.content)
                    assertEquals("white", body.getValue("theme").jsonPrimitive.content)
                }
            }
        }
    }

    @Test fun `out of range N is rejected before any network request`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api = PhiApiClient(server.url("/").toString(), Json)
            for (n in listOf(Int.MIN_VALUE, -1, 0, 201, Int.MAX_VALUE)) {
                try {
                    api.renderBestN("fixture", n, 1440, B30ImageStyle.CLASSIC, false)
                    fail("Expected invalid N to be rejected: $n")
                } catch (error: IllegalArgumentException) {
                    assertTrue(error.message!!.contains("1–200"))
                }
            }
            assertEquals(0, server.requestCount)
        }
    }
}
