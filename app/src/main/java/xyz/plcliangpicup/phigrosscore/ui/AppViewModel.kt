package xyz.plcliangpicup.phigrosscore.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.plcliangpicup.phigrosscore.BuildConfig
import xyz.plcliangpicup.phigrosscore.data.AppRepository
import xyz.plcliangpicup.phigrosscore.data.AppUpdateManifest
import xyz.plcliangpicup.phigrosscore.data.B30ImageStyle
import xyz.plcliangpicup.phigrosscore.data.B30Snapshot
import xyz.plcliangpicup.phigrosscore.data.ConstantTableEntry
import xyz.plcliangpicup.phigrosscore.data.LoginProgress
import xyz.plcliangpicup.phigrosscore.data.LeaderboardSnapshot
import xyz.plcliangpicup.phigrosscore.data.QrCodeCreateResponse
import xyz.plcliangpicup.phigrosscore.data.SongScoreResult
import xyz.plcliangpicup.phigrosscore.data.SongScoreImageStyle
import java.io.File

enum class AppPage { HOME, B30, SONG, CONSTANT_TABLE, LEADERBOARD, IMAGE, MORE, SETTINGS }

data class AppUiState(
    val isLoggedIn: Boolean = false,
    val isDarkTheme: Boolean = false,
    val autoRefreshOnLaunch: Boolean = true,
    val autoCheckAppUpdates: Boolean = true,
    val showNavigationHandle: Boolean = true,
    val b30ImageStyle: B30ImageStyle = B30ImageStyle.CLASSIC,
    val songScoreImageStyle: SongScoreImageStyle = SongScoreImageStyle.DEFAULT,
    val navigationHandlePosition: Float = 0.5f,
    val hasStoredSessionToken: Boolean = false,
    val revealedSessionToken: String? = null,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val page: AppPage = AppPage.HOME,
    val snapshot: B30Snapshot? = null,
    val imageFile: File? = null,
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
    val isGeneratingB30Image: Boolean = false,
    val b30ImageGenerationElapsedSeconds: Int = 0,
    val showNavigationGuide: Boolean = true,
    val loginProgress: LoginProgress = LoginProgress.Idle,
    val availableAppUpdate: AppUpdateManifest? = null,
    val isCheckingAppUpdate: Boolean = false,
    val isDownloadingAppUpdate: Boolean = false,
    val appUpdateDownloadedBytes: Long = 0L,
    val appUpdateTotalBytes: Long = 0L,
    val downloadedAppUpdate: File? = null,
    val message: String? = null,
)

class AppViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        AppUiState(
            isLoggedIn = repository.hasSession,
            isDarkTheme = repository.isDarkTheme,
            autoRefreshOnLaunch = repository.autoRefreshOnLaunch,
            autoCheckAppUpdates = repository.autoCheckAppUpdates,
            showNavigationHandle = repository.showNavigationHandle,
            b30ImageStyle = repository.b30ImageStyle,
            songScoreImageStyle = repository.songScoreImageStyle,
            navigationHandlePosition = repository.navigationHandlePosition,
            hasStoredSessionToken = repository.hasStoredSessionToken,
            showNavigationGuide = repository.shouldShowNavigationGuide,
            constantTableEntries = repository.constantTableEntries(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var qrJob: Job? = null
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var firstLoginImageJob: Job? = null
    private var imageTimerJob: Job? = null
    private var songImageJob: Job? = null
    private var songImageTimerJob: Job? = null
    private var generateImageAfterNextRefresh = false

    init {
        if (repository.autoCheckAppUpdates) checkAppUpdate(silent = true)
        viewModelScope.launch {
            if (repository.loadCachedSongCatalog()) {
                _state.update {
                    it.copy(constantTableEntries = repository.constantTableEntries())
                }
            }
            viewModelScope.launch {
                runCatching { repository.refreshSongCatalog() }
                    .onSuccess { changed ->
                        if (changed) {
                            _state.update {
                                it.copy(constantTableEntries = repository.constantTableEntries())
                            }
                        }
                    }
            }
            val cached = repository.cachedSnapshot()
            _state.update {
                it.copy(
                    snapshot = cached,
                    imageFile = repository.cachedImage.takeIf(File::exists),
                )
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

    fun dismissNavigationGuide() {
        repository.markNavigationGuideShown()
        _state.update { it.copy(showNavigationGuide = false) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun setDarkTheme(enabled: Boolean) {
        if (_state.value.isDarkTheme == enabled) return
        if (_state.value.isGeneratingB30Image) {
            _state.update { it.copy(message = "成绩图正在生成，请稍后再切换主题") }
            return
        }
        repository.setDarkTheme(enabled)
        _state.update { it.copy(isDarkTheme = enabled, imageFile = null) }
        viewModelScope.launch {
            runCatching { repository.deleteCachedB30Image() }
            _state.update {
                it.copy(message = "已切换界面主题，请重新生成对应的 B30 成绩图")
            }
        }
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
        if (_state.value.isGeneratingB30Image) {
            _state.update { it.copy(message = "成绩图正在生成，请稍后再切换样式") }
            return
        }
        repository.setB30ImageStyle(style)
        _state.update { it.copy(b30ImageStyle = style, imageFile = null) }
        viewModelScope.launch {
            runCatching { repository.deleteCachedB30Image() }
            _state.update {
                it.copy(message = "已切换 B30 成绩图样式，请重新生成图片")
            }
        }
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
                    generateImageAfterNextRefresh = repository.shouldGenerateFirstLoginB30Image
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
                generateImageAfterNextRefresh = repository.shouldGenerateFirstLoginB30Image
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
                    _state.update {
                        it.copy(snapshot = snapshot, isLoading = false, isOffline = false)
                    }
                    if (generateImageAfterNextRefresh) generateFirstLoginImage()
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

    fun refreshLeaderboard() {
        if (_state.value.isLeaderboardLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLeaderboardLoading = true, message = null) }
            runCatching { repository.fetchLeaderboard(_state.value.snapshot) }
                .onSuccess { leaderboard ->
                    _state.update {
                        it.copy(
                            leaderboard = leaderboard,
                            isLeaderboardLoading = false,
                            isOffline = false,
                        )
                    }
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

    fun generateImage() {
        if (_state.value.isGeneratingB30Image) return
        viewModelScope.launch {
            // Keep the last successful image visible while the next one is rendered.
            // This avoids a blank page and also preserves the old image on failure.
            beginImageGeneration(clearDisplayedImage = false)
            try {
                val state = _state.value
                val file = repository.renderB30(state.b30ImageStyle, state.isDarkTheme)
                _state.update { it.copy(imageFile = file, isOffline = false) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update {
                    it.copy(
                        imageFile = repository.cachedImage.takeIf(File::exists),
                        message = readableError(error),
                    )
                }
            } finally {
                finishImageGeneration()
            }
        }
    }

    private fun generateFirstLoginImage() {
        if (!generateImageAfterNextRefresh || firstLoginImageJob?.isActive == true) return
        firstLoginImageJob = viewModelScope.launch {
            beginImageGeneration(clearDisplayedImage = false)
            try {
                val state = _state.value
                val file = repository.renderB30(state.b30ImageStyle, state.isDarkTheme)
                repository.markFirstLoginB30ImageGenerated()
                generateImageAfterNextRefresh = false
                _state.update { it.copy(imageFile = file, isOffline = false) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update { it.copy(imageFile = repository.cachedImage.takeIf(File::exists)) }
            } finally {
                finishImageGeneration()
            }
        }
    }

    private fun beginImageGeneration(clearDisplayedImage: Boolean) {
        imageTimerJob?.cancel()
        _state.update {
            it.copy(
                isGeneratingB30Image = true,
                b30ImageGenerationElapsedSeconds = 0,
                imageFile = if (clearDisplayedImage) null else it.imageFile,
                message = null,
            )
        }
        val startedAt = SystemClock.elapsedRealtime()
        imageTimerJob = viewModelScope.launch {
            while (isActive) {
                val elapsedSeconds = ((SystemClock.elapsedRealtime() - startedAt) / 1_000L).toInt()
                _state.update { it.copy(b30ImageGenerationElapsedSeconds = elapsedSeconds) }
                delay(250)
            }
        }
    }

    private fun finishImageGeneration() {
        imageTimerJob?.cancel()
        imageTimerJob = null
        _state.update { it.copy(isGeneratingB30Image = false) }
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
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isOffline = false,
                            songResults = results,
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

    fun logout() {
        qrJob?.cancel()
        firstLoginImageJob?.cancel()
        imageTimerJob?.cancel()
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
                b30ImageStyle = repository.b30ImageStyle,
                navigationHandlePosition = repository.navigationHandlePosition,
                showNavigationGuide = repository.shouldShowNavigationGuide,
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
