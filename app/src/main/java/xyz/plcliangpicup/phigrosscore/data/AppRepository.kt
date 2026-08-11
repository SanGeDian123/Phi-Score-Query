package xyz.plcliangpicup.phigrosscore.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import coil.imageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.io.IOException
import kotlin.math.pow

class AppRepository(
    context: Context,
    baseUrl: String,
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    private val api = PhiApiClient(baseUrl, json)
    private val localQrClient = TapTapLocalQrClient(json)
    private val sessionStore = SecureSessionStore(context)
    private val cacheStore = CacheStore(context, json)
    private val songCatalog = SongCatalog(context)
    private val songScoreImageRenderer = SongScoreImageRenderer(appContext)
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()

    val hasSession: Boolean get() = sessionStore.hasSession()
    val hasStoredSessionToken: Boolean get() = sessionStore.hasStoredSessionToken()
    val isDarkTheme: Boolean get() = preferences.getBoolean("dark_theme", false)
    val autoRefreshOnLaunch: Boolean get() = preferences.getBoolean("auto_refresh_on_launch", true)
    val autoCheckAppUpdates: Boolean get() = preferences.getBoolean("auto_check_app_updates", true)
    val showNavigationHandle: Boolean get() = preferences.getBoolean("show_navigation_handle", true)
    val b30ImageStyle: B30ImageStyle
        get() = B30ImageStyle.fromPreference(preferences.getString("b30_image_style", null))
    val songScoreImageStyle: SongScoreImageStyle
        get() = SongScoreImageStyle.fromPreference(preferences.getString("song_score_image_style", null))
    val navigationHandlePosition: Float get() = preferences.getFloat("navigation_handle_position", 0.5f)
        .coerceIn(0f, 1f)
    val shouldGenerateInitialImagePair: Boolean
        get() = !preferences.getBoolean("initial_image_pair_pre0976_p30v2_generated", false)
    val shouldShowNavigationGuide: Boolean get() = !preferences.getBoolean("navigation_drawer_guide_shown", false)
    val shouldShowExperienceSurveyPrompt: Boolean
        get() = !preferences.getBoolean("experience_survey_prompt_pre0975_shown", false)
    val cachedImage: File get() = cacheStore.imageFile
    val cachedP30Image: File get() = cacheStore.p30ImageFile
    val shouldShowImagePagerGuide: Boolean
        get() = !preferences.getBoolean("image_pager_guide_pre0976_shown", false)
    val rksCalculatorDraft: RksCalculatorDraft
        get() = preferences.getString("rks_calculator_draft_pre0978", null)
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString(RksCalculatorDraft.serializer(), encoded).normalized()
                }.getOrNull()
            }
            ?: RksCalculatorDraft()

    suspend fun fetchPendingAnnouncement(): AppAnnouncement? {
        val announcement = api.fetchAppAnnouncement()
        val lastSeenId = preferences.getString("last_seen_announcement_id", null)
        if (!announcement.isDisplayableAfter(lastSeenId)) return null
        preferences.edit().putString("last_seen_announcement_id", announcement.id).apply()
        return announcement
    }

    fun constantTableEntries(): List<ConstantTableEntry> = songCatalog.constantTableEntries()

    suspend fun loadCachedSongCatalog(): Boolean {
        val cached = cacheStore.readSongCatalog() ?: return false
        if (cached.version.isBlank() || cached.items.isEmpty()) return false
        songCatalog.applyRemoteCatalog(cached)
        return true
    }

    suspend fun refreshSongCatalog(): Boolean {
        val remote = api.fetchSongCatalog(songCatalog.version) ?: return false
        require(remote.version.matches(Regex("[0-9a-fA-F]{64}"))) { "服务器曲库版本无效" }
        require(remote.items.isNotEmpty()) { "服务器曲库为空" }
        require(remote.items.map(RemoteSongInfo::id).distinct().size == remote.items.size) {
            "服务器曲库包含重复曲目"
        }
        cacheStore.saveSongCatalog(remote)
        songCatalog.applyRemoteCatalog(remote)
        return true
    }

    fun songDetail(songId: String, snapshot: B30Snapshot?): SongScoreResult? {
        val song = songCatalog[songId] ?: return null
        val records = snapshot?.scoreRecords.orEmpty()
            .asSequence()
            .filter { it.songId == songId }
            .sortedBy { difficultyOrder(it.difficulty) }
            .map { record ->
                SongDifficultyScore(
                    difficulty = record.difficulty,
                    score = record.score,
                    accuracy = record.accuracy,
                    isFullCombo = record.isFullCombo,
                    chartConstant = record.chartConstant
                        ?: song.chartConstants[record.difficulty.uppercase()],
                    rankingScore = record.rks,
                    pushAcc = record.pushAcc,
                    pushAccHint = record.pushAccHint,
                )
            }
            .toList()
        return SongScoreResult(
            songId = song.id,
            songName = song.name,
            composer = song.composer,
            illustrator = song.illustrator,
            chapter = song.chapter,
            charts = song.charts,
            records = records,
        )
    }

    fun setDarkTheme(enabled: Boolean) {
        preferences.edit().putBoolean("dark_theme", enabled).apply()
    }

    fun setAutoRefreshOnLaunch(enabled: Boolean) {
        preferences.edit().putBoolean("auto_refresh_on_launch", enabled).apply()
    }

    fun setAutoCheckAppUpdates(enabled: Boolean) {
        preferences.edit().putBoolean("auto_check_app_updates", enabled).apply()
    }

    fun setShowNavigationHandle(enabled: Boolean) {
        preferences.edit().putBoolean("show_navigation_handle", enabled).apply()
    }

    fun setB30ImageStyle(style: B30ImageStyle) {
        preferences.edit().putString("b30_image_style", style.preferenceValue).apply()
    }

    fun setSongScoreImageStyle(style: SongScoreImageStyle) {
        preferences.edit().putString("song_score_image_style", style.preferenceValue).apply()
    }

    fun setNavigationHandlePosition(position: Float) {
        preferences.edit().putFloat("navigation_handle_position", position.coerceIn(0f, 1f)).apply()
    }

    fun setRksCalculatorDraft(draft: RksCalculatorDraft) {
        val encoded = json.encodeToString(RksCalculatorDraft.serializer(), draft.normalized())
        preferences.edit().putString("rks_calculator_draft_pre0978", encoded).apply()
    }

    fun markNavigationGuideShown() {
        preferences.edit().putBoolean("navigation_drawer_guide_shown", true).apply()
    }

    fun markExperienceSurveyPromptShown() {
        preferences.edit().putBoolean("experience_survey_prompt_pre0975_shown", true).apply()
    }

    fun markInitialImagePairGenerated() {
        preferences.edit().putBoolean("initial_image_pair_pre0976_p30v2_generated", true).apply()
    }

    fun markImagePagerGuideShown() {
        preferences.edit().putBoolean("image_pager_guide_pre0976_shown", true).apply()
    }

    fun storedSessionToken(): String? = sessionStore.readSessionToken()

    suspend fun cachedSnapshot(): B30Snapshot? = cacheStore.readSnapshot()?.withCatalogFallbacks()

    suspend fun checkAppUpdate(currentVersionCode: Int): AppUpdateManifest? {
        val update = api.fetchAppUpdate()
        require(update.versionCode > 0 && update.versionName.isNotBlank()) { "更新清单版本信息无效" }
        require(update.apkUrl.isNotBlank()) { "更新清单缺少安装包地址" }
        require(update.sha256.matches(Regex("[0-9A-Fa-f]{64}"))) { "更新清单校验值无效" }
        return update.takeIf { it.isNewerThan(currentVersionCode) }
    }

    suspend fun downloadAppUpdate(
        update: AppUpdateManifest,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File {
        val safeVersion = update.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(appContext.filesDir, "updates/Phi-Score-Query-$safeVersion.apk")
        val file = api.downloadAppUpdate(update, destination, onProgress)
        try {
            verifyDownloadedApk(file, update)
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    suspend fun loginWithSessionToken(rawToken: String) {
        val token = rawToken.trim()
        require(token.isNotEmpty()) { "SessionToken 不能为空" }
        val response = api.exchangeSession(token)
        saveSession(response, sessionToken = token)
    }

    suspend fun createQr(): QrCodeCreateResponse = api.createQrCode()

    suspend fun createLocalQr(): LocalQrCode = localQrClient.create()

    suspend fun awaitLocalQrConfirmation(
        qr: LocalQrCode,
        onStatus: suspend (String) -> Unit,
    ) {
        while (true) {
            when (val result = localQrClient.poll(qr)) {
                LocalQrPollResult.Pending -> onStatus("等待 TapTap 扫码")
                LocalQrPollResult.Scanned -> onStatus("已扫码，请在 TapTap 中确认")
                is LocalQrPollResult.Confirmed -> {
                    onStatus("正在建立安全会话")
                    loginWithSessionToken(result.sessionToken)
                    return
                }
            }
            delay(qr.intervalSeconds.coerceIn(2L, 10L) * 1_000L)
        }
    }

    suspend fun awaitQrConfirmation(
        qr: QrCodeCreateResponse,
        onStatus: suspend (String) -> Unit,
    ) {
        repeat(120) {
            val result = api.qrStatus(qr.qrId)
            when (result.status) {
                "Confirmed" -> {
                    val rawToken = result.sessionToken
                        ?: throw IllegalStateException("二维码已确认，但服务器未返回 SessionToken")
                    onStatus("正在建立安全会话")
                    loginWithSessionToken(rawToken)
                    return
                }
                "Expired" -> throw IllegalStateException("二维码已过期，请重新生成")
                "Error" -> throw IllegalStateException(result.message ?: "二维码登录失败")
                "Scanned" -> onStatus("已扫码，请在 TapTap 中确认")
                else -> onStatus("等待 TapTap 扫码")
            }
            delay((result.retryAfter ?: 3L).coerceIn(2L, 10L) * 1_000L)
        }
        throw IllegalStateException("二维码登录超时，请重新生成")
    }

    fun decodeQrSvg(dataUrl: String): ByteArray {
        val encoded = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
        require(encoded.isNotBlank()) { "服务器返回的二维码格式无效" }
        return Base64.decode(encoded, Base64.DEFAULT)
    }

    suspend fun fetchB30(): B30Snapshot {
        val previous = cacheStore.readSnapshot()?.withCatalogFallbacks()
        return authenticatedCall { token ->
            val response = api.fetchB30(token)
            val scoreRecords = response.toScoreSnapshot()
            val canCompare = previous?.scoreRecords?.isNotEmpty() == true
            val updatedScores = if (canCompare) {
                detectUpdatedScores(previous.scoreRecords, scoreRecords)
            } else {
                emptyList()
            }
            val snapshot = response.toSnapshot(
                scoreRecords = scoreRecords,
                updatedScores = updatedScores,
                hasUpdateComparison = canCompare,
            )
            cacheStore.saveSnapshot(snapshot)
            snapshot
        }
    }

    suspend fun searchSongScores(query: String): List<SongScoreResult> {
        val normalized = query.trim()
        require(normalized.isNotEmpty()) { "请输入曲名、曲师或曲目 ID" }
        return authenticatedCall { token ->
            val remoteMatches = runCatching { api.searchSongs(token, normalized).items }.getOrDefault(emptyList())
            api.fetchB30(token).toSongResults(normalized, remoteMatches)
        }
    }

    suspend fun fetchLeaderboard(snapshot: B30Snapshot?): LeaderboardSnapshot = authenticatedCall { token ->
        var playerProfile = snapshot?.playerProfile
        val snapshotAgeMs = snapshot?.let { System.currentTimeMillis() - it.cachedAtEpochMs } ?: Long.MAX_VALUE
        if (snapshotAgeMs > 15_000L) {
            val refreshedSave = api.fetchB30(token)
            playerProfile = refreshedSave.toPlayerProfile()
            // 排行榜写入在后端异步完成，给同一次刷新留下一个很短的落盘窗口。
            delay(350)
        }
        val response = api.fetchLeaderboard(token)
        val me = api.fetchLeaderboardMe(token)
        LeaderboardSnapshot(
            entries = response.items,
            me = me,
            playerProfile = playerProfile,
        )
    }

    suspend fun renderB30(
        style: B30ImageStyle,
        isDarkTheme: Boolean,
        width: Int = 1440,
    ): File = authenticatedCall { token ->
        cacheStore.saveImage(
            api.renderB30(token, width.coerceIn(900, 2400), style, isDarkTheme),
            RankingImageKind.B30,
        )
    }

    suspend fun renderP30(
        style: B30ImageStyle,
        isDarkTheme: Boolean,
        width: Int = 1440,
    ): File = authenticatedCall { token ->
        cacheStore.saveImage(
            api.renderP30(token, width.coerceIn(900, 2400), style, isDarkTheme),
            RankingImageKind.P30,
        )
    }

    fun cachedSongImage(songId: String, style: SongScoreImageStyle): File =
        songScoreImageRenderer.cachedFile(songId, style)

    suspend fun renderSongScoreImage(song: SongScoreResult, style: SongScoreImageStyle): File =
        songScoreImageRenderer.render(song, style)

    suspend fun deleteCachedSongImages() {
        songScoreImageRenderer.clear()
    }

    suspend fun deleteCachedB30Image() {
        cacheStore.deleteImage()
    }

    suspend fun logout() {
        sessionStore.read()?.let { stored -> runCatching { api.logout(stored.accessToken) } }
        sessionStore.clear()
        cacheStore.clear()
        songScoreImageRenderer.clear()
        songCatalog.resetToBundled()
    }

    suspend fun createSuggestionPost(
        description: String,
        imageBytes: ByteArray,
        imageMimeType: String,
    ): SuggestionPost = authenticatedCall { token ->
        api.createSuggestionPost(token, description, imageBytes, imageMimeType)
    }

    suspend fun fetchRandomSuggestion(excludeId: String? = null): SuggestionPost =
        authenticatedCall { token -> api.fetchRandomSuggestion(token, excludeId) }

    suspend fun createSuggestionComment(
        postId: String,
        text: String,
        imageBytes: ByteArray? = null,
        imageMimeType: String? = null,
    ): SuggestionComment = authenticatedCall { token ->
        api.createSuggestionComment(token, postId, text, imageBytes, imageMimeType)
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearCache() {
        cacheStore.clear()
        songScoreImageRenderer.clear()
        songCatalog.resetToBundled()
        appContext.imageLoader.memoryCache?.clear()
        appContext.imageLoader.diskCache?.clear()
    }

    @Suppress("DEPRECATION")
    private fun verifyDownloadedApk(file: File, update: AppUpdateManifest) {
        val packageManager = appContext.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw SecurityException("下载文件不是有效的 Android 安装包")
        if (archive.packageName != appContext.packageName) {
            throw SecurityException("安装包包名与当前应用不一致")
        }
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }
        if (archiveVersionCode != update.versionCode.toLong()) {
            throw SecurityException("安装包版本号与更新清单不一致")
        }
        val installed = packageManager.getPackageInfo(appContext.packageName, flags)
        val installedSigners = signerCertificates(installed, includeHistory = true)
        val archiveSigners = signerCertificates(archive, includeHistory = false)
        if (archiveSigners.isEmpty() || archiveSigners.none(installedSigners::contains)) {
            throw SecurityException("安装包签名与当前应用不一致")
        }
    }

    @Suppress("DEPRECATION")
    private fun signerCertificates(packageInfo: PackageInfo, includeHistory: Boolean): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (includeHistory && signingInfo.hasPastSigningCertificates()) {
                signingInfo.signingCertificateHistory
            } else {
                signingInfo.apkContentsSigners
            }
        } else {
            packageInfo.signatures
        }
        return signatures.orEmpty()
            .map { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }
            .toSet()
    }

    private suspend fun <T> authenticatedCall(block: suspend (String) -> T): T {
        var session = validSession(allowNetworkFailure = true)
        try {
            return block(session.accessToken)
        } catch (error: ApiException) {
            if (error.statusCode != 401) throw error
        }
        session = refreshSession(force = true)
        return block(session.accessToken)
    }

    private suspend fun validSession(allowNetworkFailure: Boolean): SecureSessionStore.StoredSession {
        val current = sessionStore.read() ?: throw SessionExpiredException()
        if (current.expiresAtEpochMs > System.currentTimeMillis() + 60_000L) return current
        return try {
            refreshSession(force = false)
        } catch (error: IOException) {
            if (allowNetworkFailure && current.expiresAtEpochMs > System.currentTimeMillis()) current
            else throw error
        }
    }

    private suspend fun refreshSession(force: Boolean): SecureSessionStore.StoredSession =
        refreshMutex.withLock {
            val current = sessionStore.read() ?: throw SessionExpiredException()
            if (!force && current.expiresAtEpochMs > System.currentTimeMillis() + 60_000L) {
                return@withLock current
            }
            val refreshed = try {
                api.refreshSession(current.accessToken)
            } catch (error: ApiException) {
                if (error.statusCode == 401) {
                    sessionStore.clear()
                    throw SessionExpiredException()
                }
                throw error
            }
            saveSession(refreshed)
            sessionStore.read() ?: throw SessionExpiredException()
        }

    private fun saveSession(response: SessionExchangeResponse, sessionToken: String? = null) {
        sessionStore.save(
            accessToken = response.accessToken,
            expiresAtEpochMs = System.currentTimeMillis() + response.expiresIn * 1_000L,
            sessionToken = sessionToken,
        )
    }

    private fun SaveAndRksResponse.toSnapshot(
        scoreRecords: List<ScoreSnapshotEntry>,
        updatedScores: List<ScoreSnapshotEntry>,
        hasUpdateComparison: Boolean,
    ): B30Snapshot {
        val items = rks.b30Charts.mapIndexed { index, ranked ->
            val record = save.gameRecord[ranked.songId]
                ?.firstOrNull { it.difficulty.equals(ranked.difficulty, ignoreCase = true) }
            val song = songCatalog[ranked.songId]
            val chartConstant = record?.chartConstant
                ?: song?.chartConstants?.get(ranked.difficulty.uppercase())
            val isBest = index < 27
            B30Item(
                position = if (isBest) index + 1 else index - 26,
                section = if (isBest) "BEST" else "AP",
                songId = ranked.songId,
                songName = song?.name ?: ranked.songId,
                composer = song?.composer.orEmpty(),
                difficulty = ranked.difficulty,
                chartConstant = chartConstant,
                score = record?.score ?: 0,
                accuracy = record?.accuracy ?: 0.0,
                rks = ranked.rks,
                isFullCombo = record?.isFullCombo ?: false,
                pushAcc = record?.pushAcc,
                pushAccHint = record?.pushAccHint.pushAccHintType(),
            )
        }
        return B30Snapshot(
            totalRks = rks.totalRks,
            items = items,
            gradeCounts = gradeCounts,
            saveUpdatedAt = save.updatedAt,
            cachedAtEpochMs = System.currentTimeMillis(),
            scoreRecords = scoreRecords,
            updatedScores = updatedScores,
            hasUpdateComparison = hasUpdateComparison,
            playerProfile = toPlayerProfile(),
        )
    }

    private fun SaveAndRksResponse.toPlayerProfile() = PlayerProfile(
        nickname = playerNickname?.takeIf(String::isNotBlank) ?: "Phigros Player",
        avatar = save.user?.avatar.validAvatarName()
            ?: save.summaryParsed?.avatar.validAvatarName().orEmpty(),
        challengeModeRank = save.summaryParsed?.challengeModeRank ?: 0,
    )

    private fun B30Snapshot.withCatalogFallbacks(): B30Snapshot {
        fun ScoreSnapshotEntry.withFallback(): ScoreSnapshotEntry {
            if (chartConstant != null) return this
            val fallback = songCatalog[songId]?.chartConstants?.get(difficulty.uppercase()) ?: return this
            return copy(
                chartConstant = fallback,
                rks = calculateChartRankingScore(accuracy, fallback),
            )
        }

        return copy(
            items = items.map { item ->
                if (item.chartConstant != null) item
                else item.copy(
                    chartConstant = songCatalog[item.songId]
                        ?.chartConstants
                        ?.get(item.difficulty.uppercase()),
                )
            },
            scoreRecords = scoreRecords.map { it.withFallback() },
            updatedScores = updatedScores.map { it.withFallback() },
        )
    }

    private fun SaveAndRksResponse.toScoreSnapshot(): List<ScoreSnapshotEntry> =
        save.gameRecord.flatMap { (songId, records) ->
            val song = songCatalog[songId]
            records.map { record ->
                val chartConstant = record.chartConstant
                    ?: song?.chartConstants?.get(record.difficulty.uppercase())
                ScoreSnapshotEntry(
                    songId = songId,
                    songName = song?.name ?: songId,
                    difficulty = record.difficulty,
                    score = record.score,
                    accuracy = record.accuracy,
                    rks = calculateChartRankingScore(record.accuracy, chartConstant),
                    isFullCombo = record.isFullCombo,
                    chartConstant = chartConstant,
                    pushAcc = record.pushAcc,
                    pushAccHint = record.pushAccHint.pushAccHintType(),
                )
            }
        }.sortedWith(
            compareBy<ScoreSnapshotEntry>({ it.songName.lowercase() }, { difficultyOrder(it.difficulty) }),
        )

    private fun SaveAndRksResponse.toSongResults(
        query: String,
        remoteMatches: List<RemoteSongInfo>,
    ): List<SongScoreResult> {
        val normalized = query.lowercase()
        val catalogMatches = songCatalog.search(query)
        val savedIdMatches = save.gameRecord.keys.asSequence()
            .filter { it.lowercase().contains(normalized) }
            .sorted()
            .toList()
        val remoteById = remoteMatches.associateBy(RemoteSongInfo::id)
        val songIds = (remoteMatches.map(RemoteSongInfo::id) + catalogMatches.map(SongInfo::id) + savedIdMatches).distinct()
        return songIds.asSequence()
            .map { songId ->
                val records = save.gameRecord[songId].orEmpty()
                val remote = remoteById[songId]
                val song = songCatalog[songId] ?: remote?.let(songCatalog::resolveRemote)
                SongScoreResult(
                    songId = songId,
                    songName = song?.name ?: remote?.name ?: songId,
                    composer = song?.composer ?: remote?.composer.orEmpty(),
                    illustrator = song?.illustrator ?: remote?.illustrator.orEmpty(),
                    chapter = song?.chapter.orEmpty(),
                    charts = song?.charts.orEmpty(),
                    records = records.sortedBy { difficultyOrder(it.difficulty) }.map { record ->
                        val chartConstant = record.chartConstant
                            ?: song?.chartConstants?.get(record.difficulty.uppercase())
                        SongDifficultyScore(
                            difficulty = record.difficulty,
                            score = record.score,
                            accuracy = record.accuracy,
                            isFullCombo = record.isFullCombo,
                            chartConstant = chartConstant,
                            rankingScore = calculateChartRankingScore(record.accuracy, chartConstant),
                            pushAcc = record.pushAcc,
                            pushAccHint = record.pushAccHint.pushAccHintType(),
                        )
                    },
                )
            }
            .take(20)
            .toList()
    }
}

internal fun detectUpdatedScores(
    previous: List<ScoreSnapshotEntry>,
    current: List<ScoreSnapshotEntry>,
): List<ScoreSnapshotEntry> {
    if (previous.isEmpty()) return emptyList()
    val oldByChart = previous.associateBy { it.songId to it.difficulty.uppercase() }
    return current.mapNotNull { latest ->
        val old = oldByChart[latest.songId to latest.difficulty.uppercase()]
        val scoreChanged = old == null || old.score != latest.score
        val accuracyChanged = old == null || old.accuracy != latest.accuracy
        val fullComboChanged = old == null || old.isFullCombo != latest.isFullCombo
        if (!scoreChanged && !accuracyChanged && !fullComboChanged) return@mapNotNull null
        latest.copy(
            previousScore = old?.score,
            previousAccuracy = old?.accuracy,
            scoreChanged = scoreChanged,
            accuracyChanged = accuracyChanged,
            fullComboChanged = fullComboChanged,
        )
    }.sortedWith(
        compareByDescending<ScoreSnapshotEntry> { it.rks }
            .thenBy { it.songName.lowercase() }
            .thenBy { difficultyOrder(it.difficulty) },
    )
}

internal fun selectBestCharts(
    records: List<ScoreSnapshotEntry>,
    limit: Int,
): List<ScoreSnapshotEntry> = records
    .sortedWith(
        compareByDescending<ScoreSnapshotEntry> { it.rks }
            .thenByDescending { it.score }
            .thenBy { it.songName.lowercase() }
            .thenBy { difficultyOrder(it.difficulty) },
    )
    .take(limit.coerceAtLeast(0))

internal fun selectPerfectCharts(
    records: List<ScoreSnapshotEntry>,
    limit: Int = 30,
): List<ScoreSnapshotEntry> = selectBestCharts(
    records = records.filter { it.score == 1_000_000 },
    limit = limit,
)

internal fun calculateP30Rks(records: List<ScoreSnapshotEntry>): Double {
    val b27 = selectPerfectCharts(records, limit = 27)
    val p3 = b27.take(3)
    return (p3.sumOf(ScoreSnapshotEntry::rks) + b27.sumOf(ScoreSnapshotEntry::rks)) / 30.0
}

private fun kotlinx.serialization.json.JsonElement?.pushAccHintType(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> contentOrNull
    is JsonObject -> (this["type"] as? JsonPrimitive)?.contentOrNull
    else -> null
}

private fun String?.validAvatarName(): String? = this
    ?.trim()
    ?.takeIf { value ->
        value.isNotEmpty() && value != "..." && value.none(Char::isISOControl)
    }

private fun difficultyOrder(difficulty: String): Int = when (difficulty.uppercase()) {
    "EZ" -> 0
    "HD" -> 1
    "IN" -> 2
    "AT" -> 3
    else -> 4
}

internal fun calculateChartRankingScore(accuracy: Double, chartConstant: Double?): Double {
    if (accuracy < 70.0 || chartConstant == null) return 0.0
    return (((accuracy - 55.0) / 45.0).pow(2) * chartConstant)
        .takeIf { it.isFinite() && it > 0.0 }
        ?: 0.0
}

class SessionExpiredException : Exception("登录已过期，请重新登录")
