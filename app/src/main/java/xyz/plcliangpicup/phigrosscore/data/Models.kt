package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AppUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val publishedAt: String? = null,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long? = null,
    val mandatory: Boolean = false,
    val changelog: List<String> = emptyList(),
)

internal fun AppUpdateManifest.isNewerThan(currentVersionCode: Int): Boolean =
    versionCode > currentVersionCode

enum class B30ImageStyle(val preferenceValue: String) {
    CLASSIC("classic"),
    MINIMAL("minimal");

    companion object {
        fun fromPreference(value: String?): B30ImageStyle =
            entries.firstOrNull { it.preferenceValue == value } ?: CLASSIC
    }
}

enum class RankingImageKind(val requestValue: String) {
    B30("b30"),
    P30("p30"),
}

enum class SongScoreImageStyle(val preferenceValue: String) {
    DEFAULT("default"),
    LEGACY("legacy");

    companion object {
        fun fromPreference(value: String?): SongScoreImageStyle =
            entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}

@Serializable
data class SessionExchangeResponse(
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String,
)

@Serializable
data class QrCodeCreateResponse(
    val qrId: String,
    val verificationUrl: String,
    val qrcodeBase64: String,
)

@Serializable
data class QrCodeStatusResponse(
    val status: String,
    val sessionToken: String? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val retryAfter: Long? = null,
)

@Serializable
data class SaveAndRksResponse(
    val save: ParsedSave,
    val rks: PlayerRks,
    val gradeCounts: Map<String, GradeCounts> = emptyMap(),
    val playerNickname: String? = null,
)

@Serializable
data class SongSearchPage(
    val items: List<RemoteSongInfo> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class RemoteSongInfo(
    val id: String,
    val name: String,
    val composer: String = "",
    val illustrator: String = "",
    val chartConstants: RemoteChartConstants = RemoteChartConstants(),
)

@Serializable
data class RemoteChartConstants(
    val ez: Double? = null,
    val hd: Double? = null,
    @SerialName("in") val inLevel: Double? = null,
    val at: Double? = null,
)

@Serializable
data class RemoteSongCatalog(
    val version: String,
    val items: List<RemoteSongInfo> = emptyList(),
)

@Serializable
data class ParsedSave(
    @SerialName("game_record") val gameRecord: Map<String, List<DifficultyRecord>>,
    val user: PlayerSaveUser? = null,
    val summaryParsed: PlayerSummary? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
)

@Serializable
data class PlayerSaveUser(
    val avatar: String = "",
    val background: String = "",
    @SerialName("self_intro") val selfIntro: String = "",
)

@Serializable
data class PlayerSummary(
    @SerialName("challenge_mode_rank") val challengeModeRank: Int = 0,
    @SerialName("ranking_score") val rankingScore: Double = 0.0,
    val avatar: String = "",
)

@Serializable
data class DifficultyRecord(
    val difficulty: String,
    val score: Int,
    val accuracy: Double,
    @SerialName("is_full_combo") val isFullCombo: Boolean,
    @SerialName("chart_constant") val chartConstant: Double? = null,
    @SerialName("push_acc") val pushAcc: Double? = null,
    @SerialName("push_acc_hint") val pushAccHint: JsonElement? = null,
)

@Serializable
data class PlayerRks(
    val totalRks: Double,
    val b30Charts: List<RankedChart>,
)

@Serializable
data class RankedChart(
    val songId: String,
    val difficulty: String,
    val rks: Double,
)

@Serializable
data class GradeCounts(
    @SerialName("C") val clear: Int = 0,
    @SerialName("FC") val fullCombo: Int = 0,
    @SerialName("P") val phi: Int = 0,
)

@Serializable
data class B30Snapshot(
    val totalRks: Double,
    val items: List<B30Item>,
    val gradeCounts: Map<String, GradeCounts>,
    val saveUpdatedAt: String? = null,
    val cachedAtEpochMs: Long,
    val scoreRecords: List<ScoreSnapshotEntry> = emptyList(),
    val updatedScores: List<ScoreSnapshotEntry> = emptyList(),
    val hasUpdateComparison: Boolean = false,
    val playerProfile: PlayerProfile? = null,
)

@Serializable
data class PlayerProfile(
    val nickname: String = "Phigros Player",
    val avatar: String = "",
    val challengeModeRank: Int = 0,
)

@Serializable
data class ScoreSnapshotEntry(
    val songId: String,
    val songName: String,
    val difficulty: String,
    val score: Int,
    val accuracy: Double,
    val rks: Double,
    val isFullCombo: Boolean,
    val chartConstant: Double? = null,
    val pushAcc: Double? = null,
    val pushAccHint: String? = null,
    val previousScore: Int? = null,
    val previousAccuracy: Double? = null,
    val scoreChanged: Boolean = false,
    val accuracyChanged: Boolean = false,
    val fullComboChanged: Boolean = false,
)

@Serializable
data class B30Item(
    val position: Int,
    val section: String,
    val songId: String,
    val songName: String,
    val composer: String = "",
    val difficulty: String,
    val chartConstant: Double? = null,
    val score: Int = 0,
    val accuracy: Double = 0.0,
    val rks: Double,
    val isFullCombo: Boolean = false,
    val pushAcc: Double? = null,
    val pushAccHint: String? = null,
)

data class SongInfo(
    val id: String,
    val name: String,
    val composer: String,
    val illustrator: String,
    val chartConstants: Map<String, Double> = emptyMap(),
    val chapter: String = "",
    val charts: List<SongChartInfo> = emptyList(),
)

data class SongChartInfo(
    val difficulty: String,
    val chartConstant: Double? = null,
    val noteCount: Int? = null,
    val charter: String = "",
)

data class ConstantTableEntry(
    val song: SongInfo,
    val chart: SongChartInfo,
)

data class SongScoreResult(
    val songId: String,
    val songName: String,
    val composer: String,
    val illustrator: String,
    val chapter: String = "",
    val charts: List<SongChartInfo> = emptyList(),
    val records: List<SongDifficultyScore>,
)

@Serializable
data class LeaderboardResponse(
    val items: List<LeaderboardEntry> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val alias: String? = null,
    val nickname: String? = null,
    val user: String = "",
    val avatar: String? = null,
    val challengeModeRank: Int? = null,
    val score: Double,
    val updatedAt: String = "",
)

@Serializable
data class LeaderboardMe(
    val rank: Int = 0,
    val score: Double = 0.0,
    val total: Int = 0,
    val percentile: Double = 0.0,
)

data class LeaderboardSnapshot(
    val entries: List<LeaderboardEntry>,
    val me: LeaderboardMe,
    val playerProfile: PlayerProfile?,
)

data class SongDifficultyScore(
    val difficulty: String,
    val score: Int,
    val accuracy: Double,
    val isFullCombo: Boolean,
    val chartConstant: Double?,
    val rankingScore: Double,
    val pushAcc: Double?,
    val pushAccHint: String?,
)

@Serializable
data class ProblemDetails(
    val title: String? = null,
    val detail: String? = null,
    val message: String? = null,
    val code: String? = null,
    val status: Int? = null,
)

class ApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)

sealed interface LoginProgress {
    data object Idle : LoginProgress
    data object Exchanging : LoginProgress
    data object CreatingQr : LoginProgress
    data class WaitingForScan(
        val qrId: String,
        val qrSvg: ByteArray,
        val verificationUrl: String,
        val status: String,
    ) : LoginProgress
    data class Failed(val message: String) : LoginProgress
}
