package xyz.plcliangpicup.phigrosscore.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.plcliangpicup.phigrosscore.BuildConfig
import xyz.plcliangpicup.phigrosscore.data.AppAnnouncement
import xyz.plcliangpicup.phigrosscore.data.AppRepository
import xyz.plcliangpicup.phigrosscore.data.AppUpdateManifest
import xyz.plcliangpicup.phigrosscore.data.B30ImageStyle
import xyz.plcliangpicup.phigrosscore.data.B30Snapshot
import xyz.plcliangpicup.phigrosscore.data.ConstantTableEntry
import xyz.plcliangpicup.phigrosscore.data.CustomChartDraft
import xyz.plcliangpicup.phigrosscore.data.LoginProgress
import xyz.plcliangpicup.phigrosscore.data.LeaderboardSnapshot
import xyz.plcliangpicup.phigrosscore.data.QrCodeCreateResponse
import xyz.plcliangpicup.phigrosscore.data.RankingImageKind
import xyz.plcliangpicup.phigrosscore.data.RksCalculatorDraft
import xyz.plcliangpicup.phigrosscore.data.RksGuessStatus
import xyz.plcliangpicup.phigrosscore.data.SongScoreResult
import xyz.plcliangpicup.phigrosscore.data.SongScoreImageStyle
import xyz.plcliangpicup.phigrosscore.data.SuggestionPost
import xyz.plcliangpicup.phigrosscore.data.ChartAchievementResponse
import xyz.plcliangpicup.phigrosscore.data.CustomRankingImageScore
import xyz.plcliangpicup.phigrosscore.data.SongInfo
import xyz.plcliangpicup.phigrosscore.data.calculateChartRks
import xyz.plcliangpicup.phigrosscore.data.customRankingScores
import java.io.File

enum class AppPage { HOME, B30, SONG, CONSTANT_TABLE, LEADERBOARD, IMAGE, MORE, SETTINGS }

data class AppUiState(
    val isLoggedIn: Boolean = false,
    val isDarkTheme: Boolean = false,
    val autoRefreshOnLaunch: Boolean = true,
    val autoCheckAppUpdates: Boolean = true,
    val showNavigationHandle: Boolean = true,
    val useSwipeNavigation: Boolean = false,
    val b30ImageStyle: B30ImageStyle = B30ImageStyle.CLASSIC,
    val songScoreImageStyle: SongScoreImageStyle = SongScoreImageStyle.DEFAULT,
    val navigationHandlePosition: Float = 0.5f,
    val hasStoredSessionToken: Boolean = false,
    val revealedSessionToken: String? = null,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val page: AppPage = AppPage.HOME,
    val snapshot: B30Snapshot? = null,
    val unknownTrackIds: List<String> = emptyList(),
    val unknownTrackNoticeIds: List<String> = emptyList(),
    val rksDelta: Double? = null,
    val rksDeltaEvent: Long = 0L,
    val imageFile: File? = null,
    val p30ImageFile: File? = null,
    val customB30ImageFile: File? = null,
    val customP30ImageFile: File? = null,
    val songQuery: String = "",
    val songResults: List<SongScoreResult> = emptyList(),
    val hasSearchedSongs: Boolean = false,
    val songImageSongId: String? = null,
    val songImageFile: File? = null,
    val isGeneratingSongImage: Boolean = false,
    val songImageGenerationElapsedSeconds: Int = 0,
    val constantTableEntries: List<ConstantTableEntry> = emptyList(),
    val leaderboard: LeaderboardSnapshot? = null,
    val isLeaderboardLoading: Boolean = false,
    val suggestionPost: SuggestionPost? = null,
    val ownSuggestionPosts: List<SuggestionPost> = emptyList(),
    val commentedSuggestionPosts: List<SuggestionPost> = emptyList(),
    val isSuggestionLoading: Boolean = false,
    val isSuggestionSubmitting: Boolean = false,
    val suggestionNotificationsEnabled: Boolean = false,
    val suggestionOpenRequestId: Long = 0L,
    val achievementSongResults: List<SongInfo> = emptyList(),
    val achievementRates: ChartAchievementResponse? = null,
    val isAchievementLoading: Boolean = false,
    val checkin: xyz.plcliangpicup.phigrosscore.data.CheckinStatus? = null,
    val checkinLoading: Boolean = false,
    val checkinError: String? = null,
    val checkinRanks: List<xyz.plcliangpicup.phigrosscore.data.CheckinRank> = emptyList(),
    val rksGuessGame: RksGuessStatus? = null,
    val rksGuessLeaderboard: xyz.plcliangpicup.phigrosscore.data.RksGuessWinLeaderboard? = null,
    val rksGuessLeaderboardLoading: Boolean = false,
    val rksGuessLeaderboardError: String? = null,
    val isRksGuessLoading: Boolean = false,
    val rksCalculatorDraft: RksCalculatorDraft = RksCalculatorDraft(),
    val isGeneratingB30Image: Boolean = false,
    val b30ImageGenerationElapsedSeconds: Int = 0,
    val isGeneratingP30Image: Boolean = false,
    val p30ImageGenerationElapsedSeconds: Int = 0,
    val isGeneratingCustomRankingImage: Boolean = false,
    val showImagePagerGuide: Boolean = true,
    val showSuggestionSwipeGuide: Boolean = true,
    val showNavigationGuide: Boolean = true,
    val showExperienceSurveyPrompt: Boolean = false,
    val loginProgress: LoginProgress = LoginProgress.Idle,
    val availableAppUpdate: AppUpdateManifest? = null,
    val announcement: AppAnnouncement? = null,
    val announcementHistory: List<AppAnnouncement> = emptyList(),
    val isAnnouncementHistoryLoading: Boolean = false,
    val hasLoadedAnnouncementHistory: Boolean = false,
    val announcementHistoryError: String? = null,
    val isCheckingAppUpdate: Boolean = false,
    val isDownloadingAppUpdate: Boolean = false,
    val appUpdateDownloadedBytes: Long = 0L,
    val appUpdateTotalBytes: Long = 0L,
    val downloadedAppUpdate: File? = null,
    val message: String? = null,
)

class AppViewModel(internal val repository: AppRepository) : ViewModel() {
    private companion object {
        const val SONG_CATALOG_SYNC_INTERVAL_MS = 15_000L
    }

    private val _state = MutableStateFlow(
        AppUiState(
            isLoggedIn = repository.hasSession,
            isDarkTheme = repository.isDarkTheme,
            autoRefreshOnLaunch = repository.autoRefreshOnLaunch,
            autoCheckAppUpdates = repository.autoCheckAppUpdates,
            showNavigationHandle = repository.showNavigationHandle,
            useSwipeNavigation = repository.useSwipeNavigation,
            b30ImageStyle = repository.b30ImageStyle,
            songScoreImageStyle = repository.songScoreImageStyle,
            navigationHandlePosition = repository.navigationHandlePosition,
            hasStoredSessionToken = repository.hasStoredSessionToken,
            unknownTrackIds = repository.unknownTrackIds.toList(),
            showNavigationGuide = repository.shouldShowNavigationGuide,
            showExperienceSurveyPrompt = repository.shouldShowExperienceSurveyPrompt,
            showImagePagerGuide = repository.shouldShowImagePagerGuide,
            showSuggestionSwipeGuide = repository.shouldShowSuggestionSwipeGuide,
            suggestionNotificationsEnabled = repository.suggestionNotificationsEnabled,
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var qrJob: Job? = null
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var announcementHistoryJob: Job? = null
    private var firstLoginImageJob: Job? = null
    private var imageTimerJob: Job? = null
    private var p30ImageTimerJob: Job? = null
    private var songImageJob: Job? = null
    private var songImageTimerJob: Job? = null
    private var achievementSearchJob: Job? = null
    private var rksGuessRefreshJob: Job? = null
    private var rksGuessLeaderboardJob: Job? = null
    private var songCatalogSyncJob: Job? = null
    private val songCatalogSyncMutex = Mutex()
    private var latestAchievementSearchQuery = ""
    private var generateImagePairAfterNextRefresh = false

    init {
        if (repository.autoCheckAppUpdates) checkAppUpdate(silent = true)
        viewModelScope.launch {
            runCatching { repository.fetchPendingAnnouncement() }
                .onSuccess { announcement ->
                    if (announcement != null) {
                        _state.update { it.copy(announcement = announcement) }
                    }
                }
        }
        viewModelScope.launch {
            val cached = repository.cachedSnapshot()
            if (cached != null) {
                repository.activateUnknownTrackIds(repository.unknownTrackIds + cached.unknownSongIds)
            }
            val hiddenTracks = repository.unknownTrackIds.isNotEmpty()
            _state.update {
                it.copy(
                    snapshot = cached,
                    unknownTrackIds = repository.unknownTrackIds.toList(),
                    customB30ImageFile = if (hiddenTracks) null else repository.cachedCustomB30Image.takeIf(File::exists),
                    customP30ImageFile = if (hiddenTracks) null else repository.cachedCustomP30Image.takeIf(File::exists),
                    imageFile = if (hiddenTracks) null else repository.cachedImage.takeIf(File::exists),
                    p30ImageFile = if (hiddenTracks) null else repository.cachedP30Image.takeIf(File::exists),
                    rksCalculatorDraft = repository.rksCalculatorDraft,
                )
            }
            if (repository.loadCachedSongCatalog()) {
                _state.update { current ->
                    current.copy(
                        snapshot = current.snapshot?.let(repository::sanitizeSnapshot),
                        constantTableEntries = repository.constantTableEntries(),
                    )
                }
            }
            syncSongCatalog()
            _state.update {
                it.copy(
                    unknownTrackIds = repository.unknownTrackIds.toList(),
                    constantTableEntries = repository.constantTableEntries(),
                )
            }
            if (repository.hasSession && repository.shouldGenerateInitialImagePair && !hiddenTracks) {
                generateInitialImagePair()
            }
            if (repository.hasSession && repository.autoRefreshOnLaunch) {
                refreshB30(showLoading = cached == null)
            }
        }
    }

    fun selectPage(page: AppPage) {
        _state.update { it.copy(page = page) }
        if (page == AppPage.LEADERBOARD && _state.value.leaderboard == null) refreshLeaderboard()
    }

    fun setRksCalculatorDraft(draft: RksCalculatorDraft) {
        val normalized = repository.sanitizeRksCalculatorDraft(draft)
        repository.setRksCalculatorDraft(normalized)
        _state.update { it.copy(rksCalculatorDraft = normalized) }
    }

    fun dismissNavigationGuide() {
        repository.markNavigationGuideShown()
        _state.update { it.copy(showNavigationGuide = false) }
    }

    fun dismissExperienceSurveyPrompt() {
        repository.markExperienceSurveyPromptShown()
        _state.update { it.copy(showExperienceSurveyPrompt = false) }
    }

    fun dismissImagePagerGuide() {
        repository.markImagePagerGuideShown()
        _state.update { it.copy(showImagePagerGuide = false) }
    }

    fun dismissSuggestionSwipeGuide() {
        repository.markSuggestionSwipeGuideShown()
        _state.update { it.copy(showSuggestionSwipeGuide = false) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun dismissAnnouncement() = _state.update { it.copy(announcement = null) }

    fun refreshAnnouncementHistory() {
        announcementHistoryJob?.cancel()
        _state.update {
            it.copy(
                isAnnouncementHistoryLoading = true,
                announcementHistoryError = null,
            )
        }
        announcementHistoryJob = viewModelScope.launch {
            try {
                val announcements = repository.fetchAnnouncementHistory()
                _state.update {
                    it.copy(
                        announcementHistory = announcements,
                        isAnnouncementHistoryLoading = false,
                        hasLoadedAnnouncementHistory = true,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isAnnouncementHistoryLoading = false,
                        hasLoadedAnnouncementHistory = true,
                        announcementHistoryError = readableError(error),
                    )
                }
            }
        }
    }

    /** 启动前台曲库轮询；版本未变化时服务器只返回 304。 */
    fun startSongCatalogSync() {
        if (songCatalogSyncJob?.isActive == true) return
        songCatalogSyncJob = viewModelScope.launch {
            syncSongCatalog()
            while (isActive) {
                delay(SONG_CATALOG_SYNC_INTERVAL_MS)
                syncSongCatalog()
            }
        }
    }

    fun stopSongCatalogSync() {
        songCatalogSyncJob?.cancel()
        songCatalogSyncJob = null
    }

    private suspend fun syncSongCatalog() {
        songCatalogSyncMutex.withLock {
            runCatching { repository.refreshSongCatalog() }
                .onSuccess { changed ->
                    if (changed) {
                        _state.update { current ->
                            current.copy(
                                snapshot = current.snapshot?.let(repository::sanitizeSnapshot),
                                constantTableEntries = repository.constantTableEntries(),
                            )
                        }
                    }
                }
                .onFailure {
                    // 曲库同步是后台保活任务；网络短暂不可用时继续使用最近
                    // 的完整缓存，下一轮自动重试，不打断用户当前操作。
                }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        if (_state.value.isDarkTheme == enabled) return
        repository.setDarkTheme(enabled)
        // Generated images keep their original appearance; rendering captures the next settings.
        _state.update { it.copy(isDarkTheme = enabled) }
    }
    fun setAutoRefreshOnLaunch(enabled: Boolean) {
        repository.setAutoRefreshOnLaunch(enabled)
        _state.update { it.copy(autoRefreshOnLaunch = enabled) }
    }

    fun setAutoCheckAppUpdates(enabled: Boolean) {
        repository.setAutoCheckAppUpdates(enabled)
        _state.update { it.copy(autoCheckAppUpdates = enabled) }
    }

    fun setShowNavigationHandle(enabled: Boolean) {
        repository.setShowNavigationHandle(enabled)
        _state.update { it.copy(showNavigationHandle = enabled) }
    }

    fun setB30ImageStyle(style: B30ImageStyle) {
        if (_state.value.b30ImageStyle == style) return
        repository.setB30ImageStyle(style)
        _state.update { it.copy(b30ImageStyle = style) }
    }
    fun setSongScoreImageStyle(style: SongScoreImageStyle) {
        if (_state.value.songScoreImageStyle == style) return
        if (_state.value.isGeneratingSongImage) {
            _state.update { it.copy(message = "单曲成绩图正在生成，请稍后再切换样式") }
            return
        }
        repository.setSongScoreImageStyle(style)
        _state.update {
            it.copy(
                songScoreImageStyle = style,
                songImageSongId = null,
                songImageFile = null,
                message = "已切换单曲成绩图样式，进入曲目后将按需生成",
            )
        }
    }

    fun setNavigationHandlePosition(position: Float) {
        val normalized = position.coerceIn(0f, 1f)
        repository.setNavigationHandlePosition(normalized)
        _state.update { it.copy(navigationHandlePosition = normalized) }
    }

    fun checkAppUpdate(silent: Boolean = false) {
        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            _state.update { it.copy(isCheckingAppUpdate = true) }
            runCatching { repository.checkAppUpdate(BuildConfig.VERSION_CODE) }
                .onSuccess { update ->
                    _state.update {
                        it.copy(
                            availableAppUpdate = update,
                            isCheckingAppUpdate = false,
                            isDownloadingAppUpdate = false,
                            appUpdateDownloadedBytes = 0L,
                            appUpdateTotalBytes = update?.sizeBytes ?: 0L,
                            downloadedAppUpdate = null,
                            message = if (update == null && !silent) "当前已是最新版本" else it.message,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isCheckingAppUpdate = false,
                            message = if (silent) it.message else readableError(error),
                        )
                    }
                }
        }
    }

    fun dismissAppUpdate() {
        _state.update {
            if (it.availableAppUpdate?.mandatory == true || it.isDownloadingAppUpdate) it
            else it.copy(availableAppUpdate = null, downloadedAppUpdate = null)
        }
    }

    fun downloadAppUpdate() {
        val update = _state.value.availableAppUpdate ?: return
        if (_state.value.isDownloadingAppUpdate) return
        updateDownloadJob?.cancel()
        updateDownloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isDownloadingAppUpdate = true,
                    appUpdateDownloadedBytes = 0L,
                    appUpdateTotalBytes = update.sizeBytes ?: 0L,
                    downloadedAppUpdate = null,
                    message = null,
                )
            }
            runCatching {
                repository.downloadAppUpdate(update) { downloaded, total ->
                    _state.update {
                        it.copy(
                            appUpdateDownloadedBytes = downloaded,
                            appUpdateTotalBytes = if (total > 0) total else it.appUpdateTotalBytes,
                        )
                    }
                }
            }.onSuccess { file ->
                _state.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        downloadedAppUpdate = file,
                        appUpdateDownloadedBytes = file.length(),
                        appUpdateTotalBytes = file.length(),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        downloadedAppUpdate = null,
                        message = readableError(error),
                    )
                }
            }
        }
    }

    fun revealSessionToken() {
        val token = repository.storedSessionToken()
        _state.update {
            if (token == null) {
                it.copy(
                    hasStoredSessionToken = false,
                    message = "旧版会话未保留 SessionToken，请退出并重新登录一次后获取",
                )
            } else {
                it.copy(hasStoredSessionToken = true, revealedSessionToken = token)
            }
        }
    }

    fun hideSessionToken() = _state.update { it.copy(revealedSessionToken = null) }

    fun loginWithToken(token: String) {
        qrJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(loginProgress = LoginProgress.Exchanging, message = null) }
            runCatching { repository.loginWithSessionToken(token) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            isLoggedIn = true,
                            hasStoredSessionToken = true,
                            loginProgress = LoginProgress.Idle,
                        )
                    }
                    generateImagePairAfterNextRefresh = repository.shouldGenerateInitialImagePair
                    refreshB30(showLoading = true)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loginProgress = LoginProgress.Failed(readableError(error)))
                    }
                }
        }
    }

    fun startQrLogin() {
        qrJob?.cancel()
        qrJob = viewModelScope.launch {
            _state.update { it.copy(loginProgress = LoginProgress.CreatingQr, message = null) }
            try {
                try {
                    val localQr = repository.createLocalQr()
                    performQrLogin(localQr.display) { onStatus ->
                        repository.awaitLocalQrConfirmation(localQr, onStatus)
                    }
                } catch (localError: Throwable) {
                    if (localError is CancellationException) throw localError
                    _state.update {
                        it.copy(
                            loginProgress = LoginProgress.CreatingQr,
                            message = "本地二维码不可用，正在切换服务器二维码",
                        )
                    }
                    val serverQr = repository.createQr()
                    performQrLogin(serverQr) { onStatus ->
                        repository.awaitQrConfirmation(serverQr, onStatus)
                    }
                }
                _state.update {
                    it.copy(
                        isLoggedIn = true,
                        hasStoredSessionToken = true,
                        loginProgress = LoginProgress.Idle,
                    )
                }
                generateImagePairAfterNextRefresh = repository.shouldGenerateInitialImagePair
                refreshB30(showLoading = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update {
                    it.copy(loginProgress = LoginProgress.Failed(readableError(error)))
                }
            }
        }
    }

    private suspend fun performQrLogin(
        qr: QrCodeCreateResponse,
        awaitConfirmation: suspend (suspend (String) -> Unit) -> Unit,
    ) {
        val svg = repository.decodeQrSvg(qr.qrcodeBase64)
        _state.update {
            it.copy(
                loginProgress = LoginProgress.WaitingForScan(
                    qrId = qr.qrId,
                    qrSvg = svg,
                    verificationUrl = qr.verificationUrl,
                    status = "等待 TapTap 扫码",
                ),
            )
        }
        awaitConfirmation { status ->
            _state.update { old ->
                val progress = old.loginProgress as? LoginProgress.WaitingForScan
                old.copy(loginProgress = progress?.copy(status = status) ?: old.loginProgress)
            }
        }
    }

    fun cancelQrLogin() {
        qrJob?.cancel()
        _state.update { it.copy(loginProgress = LoginProgress.Idle) }
    }

    fun refreshB30(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _state.update { it.copy(isLoading = true, message = null) }
            runCatching { repository.fetchB30() }
                .onSuccess { snapshot ->
                    val newlyUnknownSongIds = repository.newlyUnknownSongIds(snapshot.unknownSongIds)
                    val hasUnknownTracks = repository.unknownTrackIds.isNotEmpty()
                    if (hasUnknownTracks) songImageJob?.cancel()
                    _state.update { current ->
                        val previousRks = current.snapshot?.totalRks
                        val delta = previousRks?.let { snapshot.totalRks - it }
                            ?.takeIf { kotlin.math.abs(it) >= 0.00005 }
                            ?.takeIf { !hasUnknownTracks }
                        current.copy(
                            snapshot = snapshot,
                            unknownTrackIds = repository.unknownTrackIds.toList(),
                            unknownTrackNoticeIds = if (newlyUnknownSongIds.isNotEmpty()) {
                                newlyUnknownSongIds
                            } else {
                                current.unknownTrackNoticeIds
                            },
                            rksDelta = if (hasUnknownTracks) null else delta ?: current.rksDelta,
                            rksDeltaEvent = if (delta != null) current.rksDeltaEvent + 1 else current.rksDeltaEvent,
                            imageFile = if (hasUnknownTracks) null else repository.cachedImage.takeIf(File::exists),
                            p30ImageFile = if (hasUnknownTracks) null else repository.cachedP30Image.takeIf(File::exists),
                            customB30ImageFile = if (hasUnknownTracks) null else repository.cachedCustomB30Image.takeIf(File::exists),
                            customP30ImageFile = if (hasUnknownTracks) null else repository.cachedCustomP30Image.takeIf(File::exists),
                            songResults = current.songResults.filter { repository.isSongVisible(it.songId) },
                            achievementSongResults = current.achievementSongResults.filter { repository.isSongVisible(it.id) },
                            achievementRates = current.achievementRates?.takeIf { repository.isSongVisible(it.songId) },
                            checkin = current.checkin?.let { status ->
                                status.copy(today = status.today?.let { record ->
                                    record.copy(chart = record.chart?.takeIf { repository.isSongVisible(it.songId) })
                                })
                            },
                            rksGuessGame = current.rksGuessGame?.takeIf(repository::isRksGuessStatusVisible),
                            rksCalculatorDraft = repository.rksCalculatorDraft,
                            constantTableEntries = repository.constantTableEntries(),
                            songImageSongId = current.songImageSongId?.takeIf(repository::isSongVisible),
                            songImageFile = current.songImageSongId?.takeIf(repository::isSongVisible)
                                ?.let { current.songImageFile },
                            isGeneratingSongImage = current.isGeneratingSongImage &&
                                current.songImageSongId?.let(repository::isSongVisible) == true,
                            isLoading = false,
                            isOffline = false,
                        )
                    }
                    if (!hasUnknownTracks && repository.shouldGenerateInitialImagePair) generateInitialImagePair()
                    if (generateImagePairAfterNextRefresh && !hasUnknownTracks) generateInitialImagePair()
                }
                .onFailure { error ->
                    val cached = repository.cachedSnapshot()
                    val expired = error.message?.contains("重新登录") == true
                    _state.update {
                        it.copy(
                            isLoggedIn = if (expired) false else it.isLoggedIn,
                            isLoading = false,
                            isOffline = cached != null,
                            snapshot = cached ?: it.snapshot,
                            message = readableError(error),
                        )
                    }
                }
        }
    }

    fun dismissUnknownTrackNotice() {
        _state.update { it.copy(unknownTrackNoticeIds = emptyList()) }
    }

    fun setUseSwipeNavigation(enabled: Boolean) {
        repository.setUseSwipeNavigation(enabled)
        _state.update { it.copy(useSwipeNavigation = enabled) }
    }

    private var checkinJob: Job? = null
    fun loadCheckin(month: String, submit: Boolean) {
        if (checkinJob?.isActive == true) return
        checkinJob = viewModelScope.launch {
            _state.update { it.copy(checkinLoading = true, checkinError = null) }
            try {
                val result = repository.checkin(month, submit)
                _state.update { it.copy(checkin = result) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                _state.update { it.copy(checkinError = readableError(e)) }
            } finally { _state.update { it.copy(checkinLoading = false) } }
        }
    }
    fun loadCheckinRanks() {
        viewModelScope.launch {
            runCatching { repository.checkinLeaderboard() }
                .onSuccess { result -> _state.update { it.copy(checkinRanks = result.items) } }
                .onFailure { e -> _state.update { it.copy(checkinError = readableError(e)) } }
        }
    }

    fun startRksGuess(mode: String) {
        if (_state.value.isRksGuessLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isRksGuessLoading = true, message = null) }
            runCatching { repository.startRksGuess(mode) }
                .onSuccess { game ->
                    _state.update {
                        it.copy(
                            rksGuessGame = game,
                            isRksGuessLoading = false,
                            isOffline = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isRksGuessLoading = false, message = readableError(error))
                    }
                }
        }
    }

    fun refreshRksGuess() {
        val gameId = _state.value.rksGuessGame?.gameId
        if (gameId == null) { loadRksGuessLeaderboard(); return }
        if (_state.value.isRksGuessLoading || rksGuessRefreshJob?.isActive == true) return
        rksGuessRefreshJob = viewModelScope.launch {
            runCatching { repository.fetchRksGuess(gameId) }
                .onSuccess { game ->
                    _state.update { current ->
                        if (current.rksGuessGame?.gameId != gameId) current
                        else current.copy(rksGuessGame = game, isOffline = false)
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        current.copy(message = readableError(error))
                    }
                }
        }
    }

    fun submitRksGuessAnswer(answer: Double) {
        val gameId = _state.value.rksGuessGame?.gameId ?: return
        if (_state.value.isRksGuessLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isRksGuessLoading = true, message = null) }
            runCatching { repository.submitRksGuessAnswer(gameId, answer) }
                .onSuccess { game ->
                    _state.update { current ->
                        if (current.rksGuessGame?.gameId != gameId) current
                        else current.copy(rksGuessGame = game, isRksGuessLoading = false, isOffline = false)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRksGuessLoading = false, message = readableError(error)) }
                }
        }
    }

    fun continueRksGuessRound() {
        val gameId = _state.value.rksGuessGame?.gameId ?: return
        if (_state.value.isRksGuessLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isRksGuessLoading = true, message = null) }
            runCatching { repository.continueRksGuessRound(gameId) }
                .onSuccess { game ->
                    _state.update { current ->
                        if (current.rksGuessGame?.gameId != gameId) current
                        else current.copy(rksGuessGame = game, isRksGuessLoading = false, isOffline = false)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRksGuessLoading = false, message = readableError(error)) }
                }
        }
    }

    fun leaveRksGuessGame() {
        val gameId = _state.value.rksGuessGame?.gameId ?: return
        rksGuessRefreshJob?.cancel()
        _state.update { it.copy(rksGuessGame = null, isRksGuessLoading = false) }
        viewModelScope.launch {
            runCatching { repository.leaveRksGuess(gameId) }
            loadRksGuessLeaderboard()
        }
    }

    private fun loadRksGuessLeaderboard() {
        rksGuessLeaderboardJob?.cancel()
        rksGuessLeaderboardJob = viewModelScope.launch {
            _state.update { it.copy(rksGuessLeaderboardLoading = true, rksGuessLeaderboardError = null) }
            try {
                val board = repository.fetchRksGuessLeaderboard()
                _state.update { it.copy(rksGuessLeaderboard = board, rksGuessLeaderboardLoading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                _state.update { it.copy(rksGuessLeaderboardLoading = false, rksGuessLeaderboardError = readableError(e)) }
            }
        }
    }

    fun refreshLeaderboard() {
        if (_state.value.isLeaderboardLoading) return
        viewModelScope.launch {
            val previousUnknownIds = repository.unknownTrackIds
            _state.update { it.copy(isLeaderboardLoading = true, message = null) }
            runCatching { repository.fetchLeaderboard(_state.value.snapshot) }
                .onSuccess { leaderboard ->
                    val unknownStateChanged = previousUnknownIds != repository.unknownTrackIds
                    _state.update {
                        it.copy(
                            leaderboard = leaderboard,
                            isLeaderboardLoading = false,
                            isOffline = false,
                        )
                    }
                    if (unknownStateChanged) refreshB30(showLoading = false)
                }
                .onFailure { error ->
                    val expired = error.message?.contains("重新登录") == true
                    _state.update {
                        it.copy(
                            isLoggedIn = if (expired) false else it.isLoggedIn,
                            isLeaderboardLoading = false,
                            message = readableError(error),
                        )
                    }
                }
        }
    }

    fun loadRandomSuggestion(excludeCurrent: Boolean = false) {
        if (_state.value.isSuggestionLoading) return
        viewModelScope.launch {
            val excludedIds = if (excludeCurrent) listOfNotNull(_state.value.suggestionPost?.id) else emptyList()
            _state.update { it.copy(isSuggestionLoading = true, message = null) }
            runCatching { repository.fetchRandomSuggestion(excludedIds) }
                .onSuccess { post ->
                    _state.update {
                        it.copy(
                            suggestionPost = post,
                            isSuggestionLoading = false,
                            isOffline = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSuggestionLoading = false,
                            message = if (error.message?.contains("未找到") == true) {
                                if (excludeCurrent) "暂时没有新的帖子" else "还没有人发布成绩图"
                            } else {
                                readableError(error)
                            },
                        )
                    }
                }
        }
    }

    fun searchAchievementSongs(query: String) {
        val normalized = query.trim()
        latestAchievementSearchQuery = normalized
        achievementSearchJob?.cancel()
        _state.update { it.copy(achievementSongResults = emptyList()) }
        if (normalized.isEmpty()) return
        achievementSearchJob = viewModelScope.launch {
            delay(180)
            try {
                val results = repository.searchAchievementSongs(normalized)
                if (latestAchievementSearchQuery == normalized) {
                    _state.update { it.copy(achievementSongResults = results) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 本地曲名/曲师/ID 筛选仍然可用；别名服务暂不可用时不打断输入。
            }
        }
    }

    fun loadChartAchievementRates(songId: String, difficulty: String) {
        if (_state.value.isAchievementLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isAchievementLoading = true, achievementRates = null, message = null) }
            runCatching { repository.fetchChartAchievementRates(songId, difficulty) }
                .onSuccess { response ->
                    _state.update { it.copy(achievementRates = response, isAchievementLoading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isAchievementLoading = false, message = readableError(error)) }
                }
        }
    }

    fun loadOwnSuggestionPosts() {
        if (_state.value.isSuggestionLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isSuggestionLoading = true, message = null) }
            runCatching {
                repository.fetchOwnSuggestionPosts() to repository.fetchCommentedSuggestionPosts()
            }
                .onSuccess { (posts, commentedPosts) ->
                    _state.update {
                        it.copy(
                            ownSuggestionPosts = posts,
                            commentedSuggestionPosts = commentedPosts,
                            isSuggestionLoading = false,
                            isOffline = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isSuggestionLoading = false, message = readableError(error))
                    }
                }
        }
    }

    fun openSuggestionPost(postId: String) {
        if (postId.isBlank()) return
        val requestId = SystemClock.elapsedRealtimeNanos()
        _state.update {
            it.copy(
                page = AppPage.MORE,
                suggestionOpenRequestId = requestId,
                isSuggestionLoading = true,
                message = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.fetchSuggestionPost(postId) }
                .onSuccess { post ->
                    _state.update {
                        it.copy(suggestionPost = post, isSuggestionLoading = false, isOffline = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isSuggestionLoading = false, message = readableError(error))
                    }
                }
        }
    }

    fun submitSuggestionPost(
        description: String,
        imageBytes: ByteArray,
        imageMimeType: String,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (_state.value.isSuggestionSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSuggestionSubmitting = true, message = null) }
            runCatching {
                repository.createSuggestionPost(description, imageBytes, imageMimeType)
            }.onSuccess { post ->
                _state.update {
                    it.copy(
                        suggestionPost = post,
                        ownSuggestionPosts = listOf(post) + it.ownSuggestionPosts.filterNot { own -> own.id == post.id },
                        isSuggestionSubmitting = false,
                        message = "已发送，等待建议",
                    )
                }
                onComplete(true)
            }.onFailure { error ->
                _state.update {
                    it.copy(isSuggestionSubmitting = false, message = readableError(error))
                }
                onComplete(false)
            }
        }
    }

    fun submitSuggestionComment(
        text: String,
        imageBytes: ByteArray? = null,
        imageMimeType: String? = null,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val post = _state.value.suggestionPost ?: return
        if (_state.value.isSuggestionSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSuggestionSubmitting = true, message = null) }
            runCatching {
                repository.createSuggestionComment(post.id, text, imageBytes, imageMimeType)
            }.onSuccess { comment ->
                _state.update { current ->
                    current.copy(
                        suggestionPost = current.suggestionPost?.copy(
                            comments = current.suggestionPost.comments + comment,
                        ),
                        isSuggestionSubmitting = false,
                    )
                }
                onComplete(true)
            }.onFailure { error ->
                _state.update {
                    it.copy(isSuggestionSubmitting = false, message = readableError(error))
                }
                onComplete(false)
            }
        }
    }

    fun deleteSuggestionPost(postId: String, onComplete: (Boolean) -> Unit = {}) {
        if (_state.value.isSuggestionSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSuggestionSubmitting = true, message = null) }
            runCatching { repository.deleteSuggestionPost(postId) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            suggestionPost = current.suggestionPost.takeUnless { it?.id == postId },
                            ownSuggestionPosts = current.ownSuggestionPosts.filterNot { it.id == postId },
                            commentedSuggestionPosts = current.commentedSuggestionPosts.filterNot { it.id == postId },
                            isSuggestionSubmitting = false,
                            message = "帖子已删除",
                        )
                    }
                    onComplete(true)
                }
                .onFailure { error ->
                    _state.update { it.copy(isSuggestionSubmitting = false, message = readableError(error)) }
                    onComplete(false)
                }
        }
    }

    fun deleteSuggestionComment(commentId: String, onComplete: (Boolean) -> Unit = {}) {
        if (_state.value.isSuggestionSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSuggestionSubmitting = true, message = null) }
            runCatching { repository.deleteSuggestionComment(commentId) }
                .onSuccess {
                    _state.update { current ->
                        val updatedPost = current.suggestionPost?.copy(
                            comments = current.suggestionPost.comments.filterNot { it.id == commentId },
                        )
                        current.copy(
                            suggestionPost = updatedPost,
                            ownSuggestionPosts = current.ownSuggestionPosts.map { post ->
                                if (post.id == updatedPost?.id) updatedPost else post
                            },
                            commentedSuggestionPosts = current.commentedSuggestionPosts
                                .map { post -> if (post.id == updatedPost?.id) updatedPost else post }
                                .filter { post -> post.comments.any { it.canDelete } },
                            isSuggestionSubmitting = false,
                            message = "评论已删除",
                        )
                    }
                    onComplete(true)
                }
                .onFailure { error ->
                    _state.update { it.copy(isSuggestionSubmitting = false, message = readableError(error)) }
                    onComplete(false)
                }
        }
    }

    fun setSuggestionNotificationsEnabled(enabled: Boolean) {
        repository.setSuggestionNotificationsEnabled(enabled)
        _state.update { it.copy(suggestionNotificationsEnabled = enabled) }
    }

    fun generateImage() {
        if (_state.value.isGeneratingB30Image) return
        viewModelScope.launch {
            renderRankingImage(RankingImageKind.B30, reportError = true)
        }
    }

    fun generateP30Image() {
        if (_state.value.isGeneratingP30Image) return
        viewModelScope.launch {
            renderRankingImage(RankingImageKind.P30, reportError = true)
        }
    }

    fun generateCustomRankingImage() {
        if (_state.value.isGeneratingCustomRankingImage) return
        val state = _state.value
        val draft = state.rksCalculatorDraft
        val ranking = draft.customRanking.takeIf { it == "b30" || it == "p30" } ?: "b30"
        val charts = if (ranking == "b30") draft.customB30Charts else draft.customP30Charts
        val scores = runCatching { customRankingScores(xyz.plcliangpicup.phigrosscore.data.resolvedCustomRankingCharts(charts, ranking), ranking) }.getOrElse { error ->
            _state.update { it.copy(message = error.message ?: "自定义成绩无效") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingCustomRankingImage = true, message = null) }
            runCatching {
                repository.renderCustomRankingImage(
                    ranking = ranking,
                    scores = scores,
                    style = B30ImageStyle.PHI_PLUGIN,
                    isDarkTheme = state.isDarkTheme,
                )
            }.onSuccess { file ->
                _state.update {
                    it.copy(
                        customB30ImageFile = if (ranking == "b30") file else it.customB30ImageFile,
                        customP30ImageFile = if (ranking == "p30") file else it.customP30ImageFile,
                        isOffline = false,
                        message = "自定义 ${ranking.uppercase()} 图片已生成",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        customB30ImageFile = repository.cachedCustomB30Image.takeIf(File::exists),
                        customP30ImageFile = repository.cachedCustomP30Image.takeIf(File::exists),
                        message = readableError(error),
                    )
                }
            }
            _state.update { it.copy(isGeneratingCustomRankingImage = false) }
        }
    }

    private fun generateInitialImagePair() {
        if (!repository.shouldGenerateInitialImagePair || firstLoginImageJob?.isActive == true) return
        firstLoginImageJob = viewModelScope.launch {
            val results = listOf(
                async { renderRankingImage(RankingImageKind.B30, reportError = false) },
                async { renderRankingImage(RankingImageKind.P30, reportError = false) },
            ).awaitAll()
            if (results.all { it }) {
                repository.markInitialImagePairGenerated()
                generateImagePairAfterNextRefresh = false
            }
        }
    }

    private suspend fun renderRankingImage(kind: RankingImageKind, reportError: Boolean): Boolean {
        beginImageGeneration(kind)
        return try {
            val state = _state.value
            val file = when (kind) {
                RankingImageKind.B30 -> repository.renderB30(state.b30ImageStyle, state.isDarkTheme)
                RankingImageKind.P30 -> repository.renderP30(state.b30ImageStyle, state.isDarkTheme)
            }
            _state.update {
                if (kind == RankingImageKind.B30) it.copy(imageFile = file, isOffline = false)
                else it.copy(p30ImageFile = file, isOffline = false)
            }
            true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _state.update {
                if (kind == RankingImageKind.B30) {
                    it.copy(
                        imageFile = repository.cachedImage.takeIf(File::exists),
                        message = if (reportError) readableError(error) else it.message,
                    )
                } else {
                    it.copy(
                        p30ImageFile = repository.cachedP30Image.takeIf(File::exists),
                        message = if (reportError) readableError(error) else it.message,
                    )
                }
            }
            false
        } finally {
            finishImageGeneration(kind)
        }
    }

    private fun beginImageGeneration(kind: RankingImageKind) {
        val timer = if (kind == RankingImageKind.B30) imageTimerJob else p30ImageTimerJob
        timer?.cancel()
        _state.update {
            if (kind == RankingImageKind.B30) {
                it.copy(isGeneratingB30Image = true, b30ImageGenerationElapsedSeconds = 0, message = null)
            } else {
                it.copy(isGeneratingP30Image = true, p30ImageGenerationElapsedSeconds = 0, message = null)
            }
        }
        val startedAt = SystemClock.elapsedRealtime()
        val timerJob = viewModelScope.launch {
            while (isActive) {
                val elapsedSeconds = ((SystemClock.elapsedRealtime() - startedAt) / 1_000L).toInt()
                _state.update {
                    if (kind == RankingImageKind.B30) it.copy(b30ImageGenerationElapsedSeconds = elapsedSeconds)
                    else it.copy(p30ImageGenerationElapsedSeconds = elapsedSeconds)
                }
                delay(250)
            }
        }
        if (kind == RankingImageKind.B30) imageTimerJob = timerJob else p30ImageTimerJob = timerJob
    }

    private fun finishImageGeneration(kind: RankingImageKind) {
        if (kind == RankingImageKind.B30) {
            imageTimerJob?.cancel()
            imageTimerJob = null
            _state.update { it.copy(isGeneratingB30Image = false) }
        } else {
            p30ImageTimerJob?.cancel()
            p30ImageTimerJob = null
            _state.update { it.copy(isGeneratingP30Image = false) }
        }
    }

    fun searchSong(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            _state.update { it.copy(message = "请输入曲名、曲师或曲目 ID") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    songQuery = normalized,
                    hasSearchedSongs = true,
                    message = null,
                )
            }
            runCatching { repository.searchSongScores(normalized) }
                .onSuccess { results ->
                    val newlyUnknown = repository.newlyUnknownSongIds(repository.unknownTrackIds.toList())
                    synchronizeUnknownTrackUi(newlyUnknown)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isOffline = false,
                            songResults = results.filter { result -> repository.isSongVisible(result.songId) },
                            message = if (results.isEmpty()) "没有找到相关曲目" else null,
                        )
                    }
                }
                .onFailure { error ->
                    val expired = error.message?.contains("重新登录") == true
                    _state.update {
                        it.copy(
                            isLoggedIn = if (expired) false else it.isLoggedIn,
                            isLoading = false,
                            message = readableError(error),
                        )
                    }
                }
        }
    }

    private fun synchronizeUnknownTrackUi(newlyUnknownIds: List<String>) {
        val hidden = repository.unknownTrackIds.isNotEmpty()
        if (hidden) songImageJob?.cancel()
        _state.update { current ->
            current.copy(
                unknownTrackIds = repository.unknownTrackIds.toList(),
                unknownTrackNoticeIds = newlyUnknownIds.ifEmpty { current.unknownTrackNoticeIds },
                snapshot = current.snapshot?.let(repository::sanitizeSnapshot),
                imageFile = if (hidden) null else repository.cachedImage.takeIf(File::exists),
                p30ImageFile = if (hidden) null else repository.cachedP30Image.takeIf(File::exists),
                customB30ImageFile = if (hidden) null else repository.cachedCustomB30Image.takeIf(File::exists),
                customP30ImageFile = if (hidden) null else repository.cachedCustomP30Image.takeIf(File::exists),
                rksDelta = if (hidden) null else current.rksDelta,
                rksCalculatorDraft = repository.rksCalculatorDraft,
                constantTableEntries = repository.constantTableEntries(),
                songResults = current.songResults.filter { repository.isSongVisible(it.songId) },
                achievementSongResults = current.achievementSongResults.filter { repository.isSongVisible(it.id) },
                achievementRates = current.achievementRates?.takeIf { repository.isSongVisible(it.songId) },
                checkin = current.checkin?.let { status ->
                    status.copy(today = status.today?.let { record ->
                        record.copy(chart = record.chart?.takeIf { repository.isSongVisible(it.songId) })
                    })
                },
                rksGuessGame = current.rksGuessGame?.takeIf(repository::isRksGuessStatusVisible),
                songImageSongId = current.songImageSongId?.takeIf(repository::isSongVisible),
                songImageFile = current.songImageSongId?.takeIf(repository::isSongVisible)
                    ?.let { current.songImageFile },
                isGeneratingSongImage = current.isGeneratingSongImage &&
                    current.songImageSongId?.let(repository::isSongVisible) == true,
            )
        }
    }

    fun constantSongDetail(songId: String): SongScoreResult? =
        repository.songDetail(songId, _state.value.snapshot)

    fun ensureSongImage(song: SongScoreResult) {
        val style = _state.value.songScoreImageStyle
        val cached = repository.cachedSongImage(song.songId, style).takeIf(File::exists)
        val current = _state.value
        if (current.songImageSongId == song.songId && (current.isGeneratingSongImage || current.songImageFile?.exists() == true)) {
            return
        }
        _state.update {
            it.copy(
                songImageSongId = song.songId,
                songImageFile = cached,
                isGeneratingSongImage = false,
                songImageGenerationElapsedSeconds = 0,
            )
        }
        if (cached == null) generateSongImage(song)
    }

    fun generateSongImage(song: SongScoreResult) {
        if (songImageJob?.isActive == true && _state.value.songImageSongId == song.songId) return
        songImageJob?.cancel()
        songImageJob = viewModelScope.launch {
            val style = _state.value.songScoreImageStyle
            _state.update {
                it.copy(
                    songImageSongId = song.songId,
                    isGeneratingSongImage = true,
                    songImageGenerationElapsedSeconds = 0,
                    message = null,
                )
            }
            val startedAt = SystemClock.elapsedRealtime()
            songImageTimerJob?.cancel()
            songImageTimerJob = viewModelScope.launch {
                while (isActive) {
                    val elapsed = ((SystemClock.elapsedRealtime() - startedAt) / 1_000L).toInt()
                    _state.update { it.copy(songImageGenerationElapsedSeconds = elapsed) }
                    delay(250)
                }
            }
            try {
                val file = repository.renderSongScoreImage(song, style)
                _state.update {
                    it.copy(
                        songImageSongId = song.songId,
                        songImageFile = file,
                        isGeneratingSongImage = false,
                        isOffline = false,
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update {
                    it.copy(
                        songImageFile = repository.cachedSongImage(song.songId, style).takeIf(File::exists),
                        message = readableError(error),
                    )
                }
            } finally {
                songImageTimerJob?.cancel()
                songImageTimerJob = null
                _state.update { it.copy(isGeneratingSongImage = false) }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            _state.update {
                it.copy(
                    snapshot = null,
                    imageFile = null,
                    p30ImageFile = null,
                    customB30ImageFile = null,
                    customP30ImageFile = null,
                    isGeneratingB30Image = false,
                    isGeneratingP30Image = false,
                    songImageSongId = null,
                    songImageFile = null,
                    isGeneratingSongImage = false,
                    songResults = emptyList(),
                    hasSearchedSongs = false,
                    constantTableEntries = repository.constantTableEntries(),
                    leaderboard = null,
                    message = "本地缓存已清除",
                )
            }
        }
    }

    fun clearCustomRanking() {
        val current = _state.value.rksCalculatorDraft
        val cleared = current.copy(
            b30Values = List(current.b30Values.size) { "" },
            p30Values = List(current.p30Values.size) { "" },
            customB30Charts = List(current.customB30Charts.size) { CustomChartDraft() },
            customP30Charts = List(current.customP30Charts.size) { CustomChartDraft() },
        ).normalized()
        repository.setRksCalculatorDraft(cleared)
        viewModelScope.launch {
            runCatching { repository.clearCustomRankingImage() }
        }
        _state.update {
            it.copy(
                rksCalculatorDraft = cleared,
                customB30ImageFile = null,
                customP30ImageFile = null,
                message = "自定义 BP30 内容已清空",
            )
        }
    }

    fun logout() {
        rksGuessLeaderboardJob?.cancel()
        checkinJob?.cancel()
        qrJob?.cancel()
        announcementHistoryJob?.cancel()
        firstLoginImageJob?.cancel()
        imageTimerJob?.cancel()
        p30ImageTimerJob?.cancel()
        songImageJob?.cancel()
        songImageTimerJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.logout()
            _state.value = AppUiState(
                isDarkTheme = repository.isDarkTheme,
                autoRefreshOnLaunch = repository.autoRefreshOnLaunch,
                autoCheckAppUpdates = repository.autoCheckAppUpdates,
                showNavigationHandle = repository.showNavigationHandle,
                useSwipeNavigation = repository.useSwipeNavigation,
                b30ImageStyle = repository.b30ImageStyle,
                customB30ImageFile = null,
                customP30ImageFile = null,
                navigationHandlePosition = repository.navigationHandlePosition,
                showNavigationGuide = repository.shouldShowNavigationGuide,
                showExperienceSurveyPrompt = repository.shouldShowExperienceSurveyPrompt,
                showImagePagerGuide = repository.shouldShowImagePagerGuide,
                showSuggestionSwipeGuide = repository.shouldShowSuggestionSwipeGuide,
                constantTableEntries = repository.constantTableEntries(),
                message = "已安全退出登录",
            )
        }
    }

    private fun readableError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: "操作失败，请稍后重试"
}

class AppViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(repository) as T
    }
}
