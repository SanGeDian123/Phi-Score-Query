package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.plcliangpicup.phigrosscore.data.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import coil.ImageLoader
import coil.Coil
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimeRewindUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val output = File("C:/Users/Administrator/Documents/Codex/2026-10-24/phi-plugin-2025-2-14-phigros/outputs/TimeRewind")

    private fun fixture(page: AppPage, rewind: Boolean): AppUiState {
        val context = compose.activity.applicationContext
        Coil.setImageLoader(ImageLoader.Builder(context).components { add(coil.intercept.Interceptor { chain ->
            val request = chain.request
            val name = android.net.Uri.parse(request.data.toString()).lastPathSegment.orEmpty()
            val asset = File("D:/CodexPhiTimeRewindRender/resources/ill/illLow", name)
            chain.proceed(if (asset.isFile) request.newBuilder().data(asset).build() else request)
        }) }.build())
        val catalog = SongCatalog(context)
        val records = catalog.constantTableEntries().filter { File("D:/CodexPhiTimeRewindRender/resources/ill/illLow", "${it.song.id}.png").isFile }.take(180).mapIndexed { i, entry ->
            val accuracy = if (i % 4 == 0) 100.0 else 99.7 - (i % 9) * .08
            ScoreSnapshotEntry(entry.song.id, entry.song.name, entry.chart.difficulty,
                if (accuracy == 100.0) 1_000_000 else 990_000 - (i % 9) * 730,
                accuracy, calculateChartRankingScore(accuracy, entry.chart.chartConstant), i % 3 == 0,
                entry.chart.chartConstant)
        }
        val fixtureFile = File("D:/CodexPhiTimeRewindRender/preview-snapshot.json")
        val previewRecords = if (fixtureFile.isFile) Json { ignoreUnknownKeys = true }.decodeFromString<List<ScoreSnapshotEntry>>(fixtureFile.readText()) else records
        val normal = B30Snapshot(totalRks = 16.8217, items = emptyList(),
            gradeCounts = listOf("EZ","HD","IN","AT").associateWith { GradeCounts(34, 62, 105) },
            cachedAtEpochMs = System.currentTimeMillis(), scoreRecords = previewRecords,
            playerProfile = PlayerProfile("PSQ", challengeModeRank = 446))
        catalog.timeRewindMode = rewind
        val snapshot = if (rewind) catalog.rewindConstants.snapshot(normal) { catalog[it] } else normal
        return AppUiState(isLoggedIn = true, page = page, snapshot = snapshot, timeRewindMode = rewind,
            constantTableEntries = catalog.constantTableEntries(),
            b30ImageStyle = B30ImageStyle.PHI_PLUGIN, showImagePagerGuide = false,
            showNavigationGuide = false, showSuggestionSwipeGuide = false,
            imageFile = File(output, "09-Phi-Plugin-B19.png").takeIf { rewind && it.isFile })
    }

    private fun render(page: AppPage, rewind: Boolean = true) {
        val state = fixture(page, rewind)
        compose.setContent { PhigrosScoreTheme(false) { PreviewShell(state) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
    }

    private fun save(name: String, width: Int = 412, height: Int = 915) {
        compose.waitForIdle()
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        compose.runOnIdle {
            compose.activity.window.decorView.draw(canvas)
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            if (dialog != null && dialog.isShowing) {
                canvas.drawColor(0x66000000)
                val view = dialog.window!!.decorView
                canvas.save()
                canvas.translate((width - view.width) / 2f, (height - view.height) / 2f)
                view.draw(canvas)
                canvas.restore()
            }
        }
        output.mkdirs()
        File(output, name).outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun settingsEntryPreview() {
        render(AppPage.SETTINGS, false)
        compose.onNodeWithText("本项目为非官方玩家项目，与南京鸽游网络有限公司及《Phigros》官方不存在授权、合作或运营关系。").performScrollTo()
        save("01-设置入口.png")
    }
    @Test fun confirmationPreview() {
        render(AppPage.SETTINGS, false)
        compose.onNodeWithText("时间回溯").performScrollTo()
        compose.onNodeWithText("时间回溯").performClick()
        compose.onNodeWithText("你将携带当前存档回到2025年2月14日，\n是否确定？").assertIsDisplayed()
        save("02-确认问题.png")
    }
    @Test @Config(qualifiers = "w915dp-h412dp-land-mdpi") fun landscapeConfirmationPreview() {
        render(AppPage.SETTINGS, false)
        compose.onNodeWithText("时间回溯").performScrollTo().performClick()
        compose.onNodeWithText("是").assertIsDisplayed()
        compose.onNodeWithText("否").assertIsDisplayed()
        save("13-确认-横屏.png", 915, 412)
    }
    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi") fun tabletConfirmationPreview() {
        render(AppPage.SETTINGS, false)
        compose.onNodeWithText("时间回溯").performScrollTo().performClick()
        compose.onNodeWithText("是").assertIsDisplayed()
        compose.onNodeWithText("否").assertIsDisplayed()
        save("14-确认-平板.png", 800, 1280)
    }
    @Test fun animationPreview() {
        val started = LocalDate.of(2026, 10, 24).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        compose.setContent { PhigrosScoreTheme(false) { TimeRewindFrame(started, 2270L) } }
        save("03-时间回溯动画.png")
    }
    @Test fun overviewPreview() {
        render(AppPage.HOME)
        compose.onNodeWithText("时间回溯").assertExists()
        compose.onNodeWithText("P30", substring = true).assertDoesNotExist()
        save("04-概览.png")
    }
    @Test fun b19Preview() {
        render(AppPage.B30)
        compose.onNodeWithText("P1").assertExists()
        compose.onAllNodesWithText("B19").assertCountEquals(3)
        compose.onNodeWithText("B30", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Best N").assertDoesNotExist()
        compose.onNodeWithText("P30", substring = true).assertDoesNotExist()
        compose.onNodeWithText("查看 B19 图片").assertExists()
        save("05-B19.png")
        save("05-B30.png") // Keep the URL from the previously delivered preview working.
    }
    @Test fun imagePreview() {
        render(AppPage.IMAGE)
        compose.onNodeWithText("Best N").assertDoesNotExist()
        compose.onNodeWithText("B30", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("B30 图片").assertDoesNotExist()
        compose.onAllNodesWithText("B19").assertCountEquals(3)
        compose.onNodeWithText("P30", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("image-tab-1").assertDoesNotExist()
        compose.onNodeWithTag("image-tab-2").assertDoesNotExist()
        compose.onNodeWithTag("ranking-image-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Best N").assertDoesNotExist()
        compose.onNodeWithText("P30", substring = true).assertDoesNotExist()
        save("06-图片.png")
        compose.onNodeWithText("保存或分享").performScrollTo().performClick()
        compose.onNodeWithText("保存或分享 B19 图片").assertExists()
        compose.onNodeWithText("B30", substring = true).assertDoesNotExist()
    }
    @Test fun normalModeKeepsBestNAndP30() {
        render(AppPage.B30, false)
        compose.onNodeWithText("Best N").assertExists()
        compose.onNodeWithText("P30").assertExists()
        compose.onNodeWithText("B19").assertDoesNotExist()
    }
    @Test fun constantsPreview() { render(AppPage.CONSTANT_TABLE); save("07-定数表.png") }
    @Test fun settingsExitPreview() {
        render(AppPage.SETTINGS)
        compose.onNodeWithText("本项目为非官方玩家项目，与南京鸽游网络有限公司及《Phigros》官方不存在授权、合作或运营关系。").performScrollTo()
        save("08-设置退出.png")
    }
    @Test fun animationDatesAndLimits() {
        val at = LocalDate.of(2026, 10, 24).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("261024", rewindDateDigits(at))
        assertEquals(setOf(AppPage.HOME,AppPage.B30,AppPage.IMAGE,AppPage.CONSTANT_TABLE,AppPage.SETTINGS), timeRewindPages)
        assertFalse(AppUiState().timeRewindMode)
        assertEquals("B19", bRankingLabel(true))
        assertEquals("B30", bRankingLabel(false))
    }
    @Test fun sayingNoReturnsToSettingsWithoutEnablingTheMode() {
        render(AppPage.SETTINGS, false)
        compose.onNodeWithText("时间回溯").performScrollTo().performClick()
        compose.onNodeWithText("清除缓存").assertDoesNotExist()
        compose.onNodeWithText("否").performClick()
        compose.onNodeWithText("清除缓存").assertExists()
        compose.onNodeWithText("退出时间回溯").assertDoesNotExist()
    }
    @Test fun exitAnimatesBeforeRestoringNormalPages() {
        render(AppPage.SETTINGS)
        compose.onNodeWithText("退出时间回溯").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("退出时间回溯").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(420)
        compose.onNodeWithTag("time-rewind-return").assertExists()
        compose.onNodeWithText("P30 RKS").assertDoesNotExist()
        save("12-返回现在.png")
        compose.mainClock.advanceTimeBy(TIME_REWIND_RETURN_DURATION_MS + 1_000)
        compose.onNodeWithTag("time-rewind-return").assertDoesNotExist()
        compose.onNodeWithText("已同步").assertExists()
        compose.onNodeWithText("P30 RKS").assertExists()
    }
    @Test fun returnDateAxisKeepsPastAndPresentDistinct() {
        val at = LocalDate.of(2026, 10, 25).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        compose.setContent { PhigrosScoreTheme(false) { TimeRewindFrame(at, TIME_REWIND_DURATION_MS, returning = true) } }
        compose.onAllNodesWithText("2026.10.25").assertCountEquals(2)
        compose.onAllNodesWithText("2025.02.14").assertCountEquals(1)
    }
    @Test fun returnTransitionCompletesOnlyOnce() {
        var completed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { PhigrosScoreTheme(false) { TimeRewindReturnTransition(1792800000000L) { completed++ } } }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertEquals(0, completed) }
        compose.mainClock.advanceTimeBy(TIME_REWIND_RETURN_DURATION_MS + 1_000)
        compose.runOnIdle { assertEquals(1, completed) }
    }
    @Test fun transitionCompletesOnceAfterTheDateHasSettled() {
        var completed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { PhigrosScoreTheme(false) { TimeRewindTransition(1792800000000L) { completed++ } } }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(0, completed) }
        compose.mainClock.advanceTimeBy(TIME_REWIND_DURATION_MS + 100)
        compose.runOnIdle { assertEquals(1, completed) }
    }
}

@Composable
private fun PreviewShell(initial: AppUiState) {
    var state by remember { mutableStateOf(initial) }
    val catalog = SongCatalog(androidx.compose.ui.platform.LocalContext.current)
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
                        onImportCustomRanking = {},
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
                        onGenerateBestNImage = {},
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
                        onStartTimeRewind = { state = state.copy(timeRewindConfirmation = true) },
                        onCancelTimeRewind = { state = state.copy(timeRewindConfirmation = false) },
                        onConfirmTimeRewind = { state = state.copy(timeRewindConfirmation = false, timeRewindStartedAt = System.currentTimeMillis()) },
                        onFinishTimeRewind = { state = state.copy(timeRewindStartedAt = null, timeRewindMode = true, page = AppPage.HOME) },
                        onExitTimeRewind = { state = state.copy(timeRewindMode = false, page = AppPage.HOME, imageFile = null) },
                    )
}
