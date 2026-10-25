package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TimeRewindApiTest {
    @Test fun rewindImagesRequireTheMatchingP1RuleVersion() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api = PhiApiClient(server.url("/").toString(), Json)
            for (rules in listOf(null, "p1-v1", TIME_REWIND_P1_RULES)) {
                val response = MockResponse().setHeader("X-PSQ-Time-Rewind", "20250214").setBody("image")
                if (rules != null) response.setHeader("X-PSQ-Time-Rewind-Rules", rules)
                server.enqueue(response)
                val result = runCatching { api.renderB30("fixture", 1440, B30ImageStyle.CLASSIC, false, timeRewind = true) }
                if (rules == TIME_REWIND_P1_RULES) assertArrayEquals("image".toByteArray(), result.getOrThrow())
                else assertTrue(result.exceptionOrNull() is ApiException)
                assertEquals("true", server.takeRequest().requestUrl!!.queryParameter("time_rewind"))
            }
        }
    }
}
