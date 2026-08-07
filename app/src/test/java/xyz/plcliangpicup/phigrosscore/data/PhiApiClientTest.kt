package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
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
}
