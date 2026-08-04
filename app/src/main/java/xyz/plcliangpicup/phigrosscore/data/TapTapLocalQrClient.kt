package xyz.plcliangpicup.phigrosscore.data

import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class LocalQrCode(
    val display: QrCodeCreateResponse,
    internal val deviceCode: String,
    internal val deviceId: String,
    internal val intervalSeconds: Long,
    internal val expiresAtEpochMs: Long,
)

sealed interface LocalQrPollResult {
    data object Pending : LocalQrPollResult
    data object Scanned : LocalQrPollResult
    data class Confirmed(val sessionToken: String) : LocalQrPollResult
}

/** Direct TapTap device-code flow; only the final SessionToken goes to the backend. */
internal class TapTapLocalQrClient(
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun create(): LocalQrCode = withContext(Dispatchers.IO) {
        val deviceId = UUID.randomUUID().toString().replace("-", "")
        val response = execute(
            Request.Builder()
                .url(DEVICE_CODE_URL)
                .headers(tapHeaders())
                .post(
                    FormBody.Builder()
                        .add("client_id", LEANCLOUD_APP_ID)
                        .add("response_type", "device_code")
                        .add("scope", "basic_info")
                        .add("version", "1.2.0")
                        .add("platform", "unity")
                        .add("info", "{\"device_id\":\"$deviceId\"}")
                        .build(),
                )
                .build(),
        )
        val envelope = runCatching { json.decodeFromString<DeviceCodeEnvelope>(response) }
            .getOrElse { throw IOException("TapTap 二维码响应格式无效") }
        if (!envelope.success) throw IOException(tapTapError(envelope.error, "TapTap 二维码申请失败"))
        val data = envelope.data ?: throw IOException("TapTap 未返回 device_code")
        val deviceCode = data.deviceCode?.takeIf(String::isNotBlank)
            ?: throw IOException("TapTap 未返回 device_code")
        val verificationUrl = data.verificationUrl?.takeIf(String::isNotBlank)
            ?: throw IOException("TapTap 未返回 verification_url")
        val scanUrl = when {
            !data.qrcodeUrl.isNullOrBlank() -> data.qrcodeUrl
            !data.userCode.isNullOrBlank() -> {
                val separator = if (verificationUrl.contains('?')) '&' else '?'
                "$verificationUrl${separator}qrcode=1&user_code=${data.userCode}"
            }
            else -> verificationUrl
        }
        val now = System.currentTimeMillis()
        val interval = (data.interval ?: 5L).coerceAtLeast(2L)
        val expiresIn = (data.expiresIn ?: 300L).coerceAtLeast(interval)
        LocalQrCode(
            display = QrCodeCreateResponse(
                qrId = "local-$deviceId",
                verificationUrl = scanUrl,
                qrcodeBase64 = makeQrSvg(scanUrl),
            ),
            deviceCode = deviceCode,
            deviceId = deviceId,
            intervalSeconds = interval,
            expiresAtEpochMs = now + expiresIn * 1_000L,
        )
    }

    suspend fun poll(qr: LocalQrCode): LocalQrPollResult = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() >= qr.expiresAtEpochMs) {
            throw IOException("二维码已过期，请重新生成")
        }
        val response = execute(
            Request.Builder()
                .url(TOKEN_URL)
                .headers(tapHeaders())
                .post(
                    FormBody.Builder()
                        .add("grant_type", "device_token")
                        .add("client_id", LEANCLOUD_APP_ID)
                        .add("secret_type", "hmac-sha-1")
                        .add("code", qr.deviceCode)
                        .add("version", "1.0")
                        .add("platform", "unity")
                        .add("info", "{\"device_id\":\"${qr.deviceId}\"}")
                        .build(),
                )
                .build(),
        )
        val envelope = runCatching { json.decodeFromString<TokenEnvelope>(response) }
            .getOrElse { throw IOException("TapTap Token 响应格式无效") }
        if (!envelope.success) {
            val error = envelope.error ?: envelope.data?.let {
                runCatching { json.decodeFromJsonElement<TapTapError>(it) }.getOrNull()
            }
            val code = error?.code.orEmpty().lowercase()
            return@withContext when {
                code.contains("authorization_waiting") -> LocalQrPollResult.Scanned
                code.contains("authorization_pending") || code.contains("slow_down") -> LocalQrPollResult.Pending
                else -> throw IOException(tapTapError(error, "TapTap Token 获取失败"))
            }
        }
        val token = envelope.data?.let {
            runCatching { json.decodeFromJsonElement<TapTapToken>(it) }.getOrNull()
        } ?: throw IOException("TapTap 未返回 Token")
        LocalQrPollResult.Confirmed(fetchLeanCloudSession(token))
    }

    private fun fetchLeanCloudSession(token: TapTapToken): String {
        val userUrl = "$USER_INFO_URL?client_id=$LEANCLOUD_APP_ID".toHttpUrl()
        val accountBody = execute(
            Request.Builder()
                .url(userUrl)
                .headers(tapHeaders())
                .header("Authorization", buildMacAuthorization(token, userUrl))
                .get()
                .build(),
        )
        val account = runCatching { json.decodeFromString<AccountEnvelope>(accountBody) }
            .getOrElse { throw IOException("TapTap 账号信息响应格式无效") }
        if (!account.success) throw IOException("TapTap 账号信息获取失败")
        val accountData = account.data ?: throw IOException("TapTap 账号信息获取失败")
        val authData = buildJsonObject {
            put("authData", buildJsonObject {
                put("taptap", buildJsonObject {
                    put("kid", token.kid)
                    put("access_token", token.accessToken ?: token.kid)
                    put("token_type", "mac")
                    put("mac_key", token.macKey)
                    put("mac_algorithm", "hmac-sha-1")
                    put("openid", accountData.openid)
                    put("unionid", accountData.unionid)
                })
            })
        }.toString()
        val userResponse = execute(
            Request.Builder()
                .url("$LEANCLOUD_BASE_URL/users")
                .header("User-Agent", "LeanCloud-CSharp-SDK/1.0.3")
                .header("Content-Type", "application/json")
                .header("X-LC-Id", LEANCLOUD_APP_ID)
                .header("X-LC-Key", LEANCLOUD_APP_KEY)
                .post(authData.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        val user = runCatching { json.decodeFromString<LeanCloudUser>(userResponse) }
            .getOrElse { throw IOException("LeanCloud 登录响应格式无效") }
        return user.sessionToken?.takeIf(String::isNotBlank)
            ?: throw IOException("LeanCloud 未返回 SessionToken")
    }

    private fun buildMacAuthorization(token: TapTapToken, requestUrl: okhttp3.HttpUrl): String {
        val timestamp = System.currentTimeMillis() / 1_000L
        val nonce = SecureRandom().nextLong().toString()
        val pathAndQuery = requestUrl.encodedPath + requestUrl.encodedQuery?.let { "?$it" }.orEmpty()
        val input = "$timestamp\n$nonce\nGET\n$pathAndQuery\n${requestUrl.host}\n443\n\n"
        val mac = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(token.macKey.toByteArray(Charsets.UTF_8), "HmacSHA1"))
            update(input.toByteArray(Charsets.UTF_8))
        }
        val signature = Base64.encodeToString(mac.doFinal(), Base64.NO_WRAP)
        return "MAC id=\"${token.kid}\",ts=\"$timestamp\",nonce=\"$nonce\",mac=\"$signature\""
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful && body.isBlank()) {
                throw IOException("上游服务返回 HTTP ${response.code}")
            }
            return body
        }
    }

    private fun tapHeaders() = okhttp3.Headers.Builder()
        .add("Content-Type", "application/x-www-form-urlencoded")
        .add("User-Agent", "TapTapAndroidSDK/3.16.5")
        .build()

    private fun makeQrSvg(value: String): String {
        val matrix = MultiFormatWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            256,
            256,
            mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            ),
        )
        val svg = StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ${matrix.width} ${matrix.height}\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/>")
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) svg.append("<rect x=\"$x\" y=\"$y\" width=\"1\" height=\"1\" fill=\"black\"/>")
            }
        }
        return "data:image/svg+xml;base64,${Base64.encodeToString((svg.append("</svg>").toString()).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
    }

    private fun tapTapError(error: TapTapError?, fallback: String): String =
        error?.description?.takeIf(String::isNotBlank)
            ?: error?.code?.takeIf(String::isNotBlank)
            ?: fallback

    @Serializable
    private data class DeviceCodeEnvelope(
        val success: Boolean = false,
        val data: DeviceCodeData? = null,
        val error: TapTapError? = null,
    )

    @Serializable
    private data class DeviceCodeData(
        @SerialName("device_code") val deviceCode: String? = null,
        @SerialName("verification_url") val verificationUrl: String? = null,
        @SerialName("user_code") val userCode: String? = null,
        val interval: Long? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("qrcode_url") val qrcodeUrl: String? = null,
    )

    @Serializable
    private data class TokenEnvelope(
        val success: Boolean = false,
        val data: JsonElement? = null,
        val error: TapTapError? = null,
    )

    @Serializable
    private data class TapTapToken(
        val kid: String,
        @SerialName("mac_key") val macKey: String,
        @SerialName("access_token") val accessToken: String? = null,
    )

    @Serializable
    private data class AccountEnvelope(
        val success: Boolean = false,
        val data: AccountData? = null,
    )

    @Serializable
    private data class AccountData(
        val openid: String,
        val unionid: String,
    )

    @Serializable
    private data class LeanCloudUser(
        @SerialName("sessionToken") val sessionToken: String? = null,
    )

    @Serializable
    private data class TapTapError(
        @SerialName("error") val code: String? = null,
        @SerialName("error_description") val description: String? = null,
    )

    private companion object {
        const val DEVICE_CODE_URL = "https://accounts.tapapis.cn/oauth2/v1/device/code"
        const val TOKEN_URL = "https://accounts.tapapis.cn/oauth2/v1/token"
        const val USER_INFO_URL = "https://open.tapapis.cn/account/basic-info/v1"
        const val LEANCLOUD_BASE_URL = "https://rak3ffdi.cloud.tds1.tapapis.cn/1.1"
        const val LEANCLOUD_APP_ID = "rAK3FfdieFob2Nn8Am"
        const val LEANCLOUD_APP_KEY = "Qr9AEqtuoSVS3zeD6iVbM4ZC0AtkJcQ89tywVyi0"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
