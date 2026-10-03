package xyz.plcliangpicup.phigrosscore.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import xyz.plcliangpicup.phigrosscore.data.*

/** Debug-only renderer. Uses production composables with fixtures and no repository callbacks. */
class UiPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val target = intent.getStringExtra("ui_preview_page") ?: "HOME"
        if (target == "PRACTICE") {
            val stored = PracticeCharts.storedCharts(this)
            val fileName = intent.getStringExtra("practice_file") ?: stored.firstOrNull()?.fileName
            val source = stored.firstOrNull { it.fileName == fileName } ?: return finish()
            val file = java.io.File(filesDir, "practice-charts/${source.fileName}")
            startActivity(android.content.Intent(this, PracticePlayerActivity::class.java)
                .putExtra(PracticePlayerActivity.EXTRA_CHART_PATH, file.absolutePath)
                .putExtra(PracticePlayerActivity.EXTRA_CHART_SOURCE, PracticeCharts.encodeSource(source))
                .putExtra(PracticePlayerActivity.EXTRA_SEGMENT_MODE, intent.getBooleanExtra("practice_segment", true)))
            finish()
            return
        }

        val page = runCatching { AppPage.valueOf(target) }.getOrDefault(AppPage.HOME)
        val catalog = SongCatalog(applicationContext)
        setContent {
            var state by remember {
                mutableStateOf(
                    AppUiState(constantTableEntries = catalog.constantTableEntries()).previewFor(page)
                        .copy(isDarkTheme = intent.getBooleanExtra("ui_preview_dark", false))
                )
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !state.isDarkTheme
                    isAppearanceLightNavigationBars = !state.isDarkTheme
                }
            }
            PhigrosScoreTheme(darkTheme = state.isDarkTheme) {
                if (target == "LOGIN") {
                    LoginScreen(LoginProgress.Idle, {}, {}, {})
                } else {
                    MainShell(
                        state = state,
                        snackbar = remember { SnackbarHostState() },
                        onPage = { state = state.copy(page = it) },
                        onRefresh = {},
                        onRefreshLeaderboard = {},
                        onLoadRandomSuggestion = { _ -> },
                        onLoadOwnSuggestionPosts = {},
                        onOpenSuggestionPost = { _ -> },
                        onRksCalculatorDraftChange = { state = state.copy(rksCalculatorDraft = it) },
                        onSubmitSuggestionPost = { _, _, _, _ -> },
                        onSubmitSuggestionComment = { _, _, _, _ -> },
                        onDeleteSuggestionPost = { _, _ -> },
                        onDeleteSuggestionComment = { _, _ -> },
                        onSuggestionNotificationsChange = { _ -> },
                        onSearchAchievementSongs = { query -> state = state.copy(achievementSongResults = catalog.search(query)) },
                        onLoadAchievementRates = { _, _ -> },
                        onGenerateCustomRankingImage = {},
                        onClearCustomRanking = {},
                        onStartRksGuess = { _ -> },
                        onRefreshRksGuess = {},
                        onSubmitRksGuessAnswer = { _ -> },
                        onContinueRksGuessRound = {},
                        onLeaveRksGuessGame = {},
                        onSearchSong = { _ -> },
                        onOpenConstantSong = { id -> state.songResults.find { it.songId == id } },
                        onEnsureSongImage = { _ -> },
                        onGenerateSongImage = { _ -> },
                        onGenerateImage = {},
                        onGenerateP30Image = {},
                        onDismissImagePagerGuide = {},
                        onDismissSuggestionSwipeGuide = {},
                        onClearCache = {},
                        onThemeChange = { state = state.copy(isDarkTheme = it) },
                        onAutoRefreshChange = { state = state.copy(autoRefreshOnLaunch = it) },
                        onAutoUpdateChange = { state = state.copy(autoCheckAppUpdates = it) },
                        onNavigationHandleVisibilityChange = { _ -> },
                        onSwipeNavigationChange = { state = state.copy(useSwipeNavigation = it) },
                        onNavigationHandlePositionChange = { _ -> },
                        onB30ImageStyleChange = { state = state.copy(b30ImageStyle = it) },
                        onSongScoreImageStyleChange = { state = state.copy(songScoreImageStyle = it) },
                        onOpenSurvey = {},
                        onCheckUpdate = {},
                        onCheckin = { _, _ -> },
                        onCheckinRanks = {},
                        onRefreshAnnouncements = {},
                        onRevealSessionToken = {},
                        onHideSessionToken = {},
                        onLogout = {},
                        onDismissNavigationGuide = {},
                    )
                }
            }
        }
    }
}

private fun AppUiState.previewFor(page: AppPage): AppUiState {
    val songs = listOf(
        Triple("DistortedFate.Sakuzyo", "Distorted Fate", "Sakuzyo"),
        Triple("DESTRUCTION321.Normal1zervsBrokenNerdz", "DESTRUCTION 3,2,1", "Normal1zer vs. Broken Nerdz"),
        Triple("Rrharil.TeamGrimoire", "Rrhar'il", "Team Grimoire"),
        Triple("Chronostasis.黒皇帝", "Chronostasis", "黒皇帝"),
        Triple("FracturedAngel.DJRaisei", "Fractured Angel", "DJ Raisei"),
        Triple("SultanRage.MonstDeath", "Sultan Rage", "MonstDeath"),
    )
    val records = songs.mapIndexed { index, song ->
        ScoreSnapshotEntry(
            songId = song.first,
            songName = song.second,
            difficulty = if (index == 0) "AT" else "IN",
            score = 1_000_000 - index * 2_137,
            accuracy = 100.0 - index * .1734,
            rks = 16.42 - index * .19,
            isFullCombo = index < 4,
            chartConstant = 16.4 - index * .2,
        )
    }
    val items = records.mapIndexed { index, record ->
        B30Item(
            position = index + 1,
            section = if (index < 3) "AP" else "BEST",
            songId = record.songId,
            songName = record.songName,
            composer = songs[index].third,
            difficulty = record.difficulty,
            chartConstant = record.chartConstant,
            score = record.score,
            accuracy = record.accuracy,
            rks = record.rks,
            isFullCombo = record.isFullCombo,
        )
    }
    val snapshot = B30Snapshot(
        totalRks = 15.8476,
        items = items,
        gradeCounts = mapOf(
            "EZ" to GradeCounts(clear = 24, fullCombo = 82, phi = 196),
            "HD" to GradeCounts(clear = 31, fullCombo = 96, phi = 168),
            "IN" to GradeCounts(clear = 45, fullCombo = 103, phi = 121),
            "AT" to GradeCounts(clear = 7, fullCombo = 18, phi = 14),
        ),
        saveUpdatedAt = "2026-09-06T08:30:00Z",
        cachedAtEpochMs = System.currentTimeMillis(),
        scoreRecords = records + constantTableEntries
            .filter { it.chart.difficulty == "IN" && (it.chart.chartConstant ?: 0.0) in 14.5..16.2 }
            .sortedByDescending { it.chart.chartConstant }.take(30)
            .map { entry -> ScoreSnapshotEntry(
                songId = entry.song.id, songName = entry.song.name, difficulty = "IN",
                score = 1_000_000, accuracy = 100.0, rks = entry.chart.chartConstant ?: 15.0,
                isFullCombo = true, chartConstant = entry.chart.chartConstant,
            ) },
        playerProfile = PlayerProfile(nickname = "Preview Player", challengeModeRank = 508),
    )
    val songResults = listOf(
        SongScoreResult(
            songId = songs[0].first,
            songName = songs[0].second,
            composer = songs[0].third,
            illustrator = "阿戈魔AGM",
            chapter = "Finale",
            charts = listOf(SongChartInfo("IN", 15.7, 1_283, "Arenn Saki"), SongChartInfo("AT", 16.4, 1_646, "Arenn Saki")),
            records = listOf(
                SongDifficultyScore("IN", 1_000_000, 100.0, true, 15.7, 15.7, null, null),
                SongDifficultyScore("AT", 1_000_000, 100.0, true, 16.4, 16.4, null, null),
            ),
        ),
        SongScoreResult(
            songId = songs[1].first,
            songName = songs[1].second,
            composer = songs[1].third,
            illustrator = "Ratte",
            chapter = "Single",
            charts = listOf(SongChartInfo("IN", 15.9, 1_438, "Lanota")),
            records = listOf(SongDifficultyScore("IN", 998_734, 99.8123, true, 15.9, 15.72, 99.91, null)),
        ),
    )
    val constants = songs.mapIndexed { index, song ->
        val chart = SongChartInfo(if (index == 0) "AT" else "IN", 16.4 - index * .2, 1_200 + index * 73, "Chart Designer")
        ConstantTableEntry(
            song = SongInfo(song.first, song.second, song.third, "Illustrator", mapOf(chart.difficulty to (chart.chartConstant ?: 0.0)), "Single", listOf(chart)),
            chart = chart,
        )
    }
    val leaderboard = LeaderboardSnapshot(
        entries = listOf(
            LeaderboardEntry(1, nickname = "Azure", user = "preview-1", challengeModeRank = 516, score = 16.2841),
            LeaderboardEntry(2, nickname = "Lucent", user = "preview-2", challengeModeRank = 511, score = 16.1027),
            LeaderboardEntry(3, nickname = "Preview Player", user = "preview-me", challengeModeRank = 508, score = 15.8476),
            LeaderboardEntry(4, nickname = "Halcyon", user = "preview-4", challengeModeRank = 505, score = 15.7219),
            LeaderboardEntry(5, nickname = "Orbit", user = "preview-5", challengeModeRank = 503, score = 15.6392),
        ),
        me = LeaderboardMe(rank = 3, score = 15.8476, total = 1_284, percentile = 99.77),
        playerProfile = snapshot.playerProfile,
    )
    return copy(
        isLoggedIn = true,
        isDarkTheme = false,
        page = page,
        snapshot = snapshot,
        songQuery = "",
        songResults = songResults,
        hasSearchedSongs = true,
        constantTableEntries = if (constantTableEntries.isEmpty()) constants else constantTableEntries,
        leaderboard = leaderboard,
        hasStoredSessionToken = true,
        showNavigationHandle = false,
        showNavigationGuide = false,
        showExperienceSurveyPrompt = false,
        showImagePagerGuide = false,
        showSuggestionSwipeGuide = false,
        isLoading = false,
        isLeaderboardLoading = false,
        announcement = null,
        availableAppUpdate = null,
        message = null,
    )
}
