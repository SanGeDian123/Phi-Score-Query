package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
