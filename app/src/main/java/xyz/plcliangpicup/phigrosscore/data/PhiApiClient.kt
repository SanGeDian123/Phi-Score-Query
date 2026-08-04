package xyz.plcliangpicup.phigrosscore.data

import xyz.plcliangpicup.phigrosscore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class PhiApiClient(
    private val baseUrl: String,
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun exchangeSession(sessionToken: String): SessionExchangeResponse {
        val body = buildJsonObject {
            put("sessionToken", sessionToken)
            put("taptapVersion", "cn")
        }.toString()
        return executeJson(
            Request.Builder()
                .url(url("api/v2/auth/session/exchange"))
                .post(body.toRequestBody(jsonMediaType))
                .build(),
        )
    }

    suspend fun refreshSession(accessToken: String): SessionExchangeResponse = executeJson(
        Request.Builder()
            .url(url("api/v2/auth/session/refresh"))
            .header("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(jsonMediaType))
            .build(),
        retryNetwork = false,
    )

    suspend fun logout(accessToken: String) {
        executeBytes(
            Request.Builder()
                .url(url("api/v2/auth/session/logout"))
                .header("Authorization", "Bearer $accessToken")
                .post("{\"scope\":\"current\"}".toRequestBody(jsonMediaType))
                .build(),
            retryNetwork = false,
        )
    }

    suspend fun createQrCode(): QrCodeCreateResponse = executeJson(
        Request.Builder()
            .url(url("api/v2/auth/qrcode?taptapVersion=cn"))
            .post("{}".toRequestBody(jsonMediaType))
            .build(),
    )

    suspend fun qrStatus(qrId: String): QrCodeStatusResponse = executeJson(
        Request.Builder()
            .url(url("api/v2/auth/qrcode/$qrId/status"))
            .get()
            .build(),
        retryNetwork = false,
    )

    suspend fun fetchB30(accessToken: String): SaveAndRksResponse = executeJson(
        Request.Builder()
            .url(url("api/v2/save?calculate_rks=true"))
            .header("Authorization", "Bearer $accessToken")
            .post("{\"taptapVersion\":\"cn\"}".toRequestBody(jsonMediaType))
            .build(),
    )

    suspend fun searchSongs(accessToken: String, query: String): SongSearchPage {
        val searchUrl = url("api/v2/songs/search").toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("limit", "100")
            ?.build()
            ?: throw IOException("歌曲搜索地址无效")
        return executeJson(
            Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build(),
        )
    }

    suspend fun fetchSongCatalog(currentVersion: String?): RemoteSongCatalog? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url("api/v2/songs/catalog"))
                .apply {
                    currentVersion
                        ?.takeIf(String::isNotBlank)
                        ?.let { header("If-None-Match", "\"$it\"") }
                }
                .get()
                .build()
            var lastError: IOException? = null
            repeat(3) { attempt ->
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.code == 304) return@withContext null
                        val bytes = response.body?.bytes() ?: ByteArray(0)
                        if (!response.isSuccessful) {
                            val detail = runCatching {
                                json.decodeFromString<ProblemDetails>(bytes.decodeToString())
                            }.getOrNull()
                            val message = detail?.detail ?: detail?.message ?: detail?.title
                                ?: "曲库同步失败：HTTP ${response.code}"
                            throw ApiException(response.code, message)
                        }
                        return@withContext json.decodeFromString<RemoteSongCatalog>(bytes.decodeToString())
                    }
                } catch (error: IOException) {
                    lastError = error
                    if (attempt < 2) delay(if (attempt == 0) 600 else 1_800)
                }
            }
            throw IOException("无法连接曲库服务器，请稍后重试", lastError)
        }

    suspend fun fetchLeaderboard(accessToken: String): LeaderboardResponse = executeJson(
        Request.Builder()
            .url(url("api/v2/leaderboard/rks/top?limit=1000&lite=true"))
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build(),
    )

    suspend fun fetchLeaderboardMe(accessToken: String): LeaderboardMe = executeJson(
        Request.Builder()
            .url(url("api/v2/leaderboard/rks/me"))
            .header("Authorization", "Bearer $accessToken")
            .post("{\"taptapVersion\":\"cn\"}".toRequestBody(jsonMediaType))
            .build(),
    )

    suspend fun renderB30(
        accessToken: String,
        width: Int,
        style: B30ImageStyle,
        isDarkTheme: Boolean,
    ): ByteArray {
        val requestBody = buildJsonObject {
            put("n", 27)
            put("theme", if (isDarkTheme) "black" else "white")
            put("embedImages", true)
            put("appVersion", BuildConfig.VERSION_NAME)
        }.toString()
        val templateQuery = if (style == B30ImageStyle.MINIMAL) "&template=minimal" else ""
        return executeBytes(
            Request.Builder()
                .url(url("api/v2/image/bn?format=png&width=$width$templateQuery"))
                .header("Authorization", "Bearer $accessToken")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build(),
            // A render timeout must not immediately start a second full render;
            // the server-side image cache will be used by the user's next tap.
            retryNetwork = false,
        )
    }

    suspend fun fetchAppUpdate(): AppUpdateManifest = executeJson(
        Request.Builder()
            .url(url("app-update/latest.json"))
            .header("Cache-Control", "no-cache")
            .get()
            .build(),
        retryNetwork = false,
    )

    suspend fun downloadAppUpdate(
        update: AppUpdateManifest,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val base = baseUrl.toHttpUrlOrNull() ?: throw IOException("更新服务器地址无效")
        val downloadUrl = update.apkUrl.toHttpUrlOrNull() ?: throw IOException("安装包下载地址无效")
        if (!downloadUrl.isHttps || downloadUrl.host != base.host) {
            throw SecurityException("安装包必须来自官方 HTTPS 更新服务器")
        }
        val expectedHash = update.sha256.trim().lowercase()
        if (!expectedHash.matches(Regex("[0-9a-f]{64}"))) {
            throw SecurityException("服务器返回的安装包校验值无效")
        }

        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, "${destination.name}.download")
        temp.delete()
        try {
            client.newCall(Request.Builder().url(downloadUrl).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code, "安装包下载失败：HTTP ${response.code}")
                val finalUrl = response.request.url
                if (!finalUrl.isHttps || finalUrl.host != base.host) {
                    throw SecurityException("安装包下载被重定向到非官方服务器")
                }
                val body = response.body ?: throw IOException("服务器未返回安装包内容")
                val totalBytes = body.contentLength()
                if (totalBytes > 200L * 1024L * 1024L) throw IOException("安装包大小异常")
                if (update.sizeBytes != null && totalBytes >= 0 && update.sizeBytes != totalBytes) {
                    throw IOException("安装包大小与更新清单不一致")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var downloadedBytes = 0L
                body.byteStream().use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloadedBytes += count
                            onProgress(downloadedBytes, totalBytes)
                        }
                    }
                }
                if (update.sizeBytes != null && downloadedBytes != update.sizeBytes) {
                    throw IOException("安装包下载不完整")
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (actualHash != expectedHash) throw SecurityException("安装包 SHA-256 校验失败")
            }
            destination.delete()
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
            destination
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun url(path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private suspend inline fun <reified T> executeJson(
        request: Request,
        retryNetwork: Boolean = true,
    ): T = json.decodeFromString(executeBytes(request, retryNetwork).decodeToString())

    private suspend fun executeBytes(request: Request, retryNetwork: Boolean = true): ByteArray =
        withContext(Dispatchers.IO) {
            var lastError: IOException? = null
            val attempts = if (retryNetwork) 3 else 1
            repeat(attempts) { attempt ->
                try {
                    client.newCall(request).execute().use { response ->
                        val bytes = response.body?.bytes() ?: ByteArray(0)
                        if (!response.isSuccessful) {
                            val detail = runCatching {
                                json.decodeFromString<ProblemDetails>(bytes.decodeToString())
                            }.getOrNull()
                            val message = detail?.detail ?: detail?.message ?: detail?.title
                                ?: "服务器返回 HTTP ${response.code}"
                            throw ApiException(response.code, message)
                        }
                        return@withContext bytes
                    }
                } catch (error: IOException) {
                    lastError = error
                    if (attempt < attempts - 1) delay(if (attempt == 0) 600 else 1_800)
                }
            }
            throw IOException("无法连接查分服务器，请稍后重试", lastError)
        }
}
