package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.plcliangpicup.phigrosscore.data.*
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RksTrendUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val output = File("C:/Users/Administrator/Documents/Phigros App/design-previews/rks-trend")

    private fun history(): RksHistoryResponse {
        val values = listOf(15.4216,15.4463,15.5048,15.5421,15.5684,15.5612,15.6238,15.6781,15.7029,15.7840,15.8612,15.8476)
        val days = listOf(28,26,23,21,19,16,14,11,8,5,2,0)
        val now = Instant.now().minusSeconds(300)
        val points = values.mapIndexed { i, rks -> RksHistoryItem(rks,
            if (i == 0) 0.0 else rks - values[i - 1], now.minusSeconds(days[i] * 86_400L).toString()) }
        return RksHistoryResponse(points.asReversed(), points.size, values.last(), values.max(), false)
    }

    private fun state(page: AppPage = AppPage.RKS_TREND, dark: Boolean = false) = AppUiState(
        isLoggedIn = true, page = page, isDarkTheme = dark, showNavigationGuide = false,
        showImagePagerGuide = false, showSuggestionSwipeGuide = false, showRksTrendGuide = true,
        rksHistory = history(),
        snapshot = B30Snapshot(totalRks = 15.8476, items = emptyList(), cachedAtEpochMs = System.currentTimeMillis(),
            saveUpdatedAt = Instant.now().toString(), playerProfile = PlayerProfile("PSQ", challengeModeRank = 508),
            scoreRecords = List(30) { i -> ScoreSnapshotEntry("preview-$i", "Preview", "IN", 1_000_000, 100.0,
                15.6 - i * .02, true, 15.6 - i * .02) },
            gradeCounts = listOf("EZ","HD","IN","AT").associateWith { GradeCounts(34,62,105) }),
    )

    private fun render(initial: AppUiState = state()) {
        compose.setContent { PhigrosScoreTheme(initial.isDarkTheme) { RksPreviewShell(initial) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_300)
    }

    private fun save(name: String, width: Int = 412, height: Int = 915) {
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { compose.activity.window.decorView.draw(Canvas(bitmap)) }
        output.mkdirs()
        File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun overviewGuideAndLargeNumberOpenTrendAndGuideStaysDismissed() {
        render(state(AppPage.HOME).copy(showNavigationGuide = true))
        compose.onNodeWithTag("rks-trend-guide", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("点击查看RKS趋势图", useUnmergedTree = true).assertIsDisplayed()
        assertNull(org.robolectric.shadows.ShadowDialog.getLatestDialog())
        save("01-概览页-首次引导.png")
        compose.onNodeWithTag("overview-rks-entry").performClick()
        compose.onNodeWithText("RKS趋势图").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回概览").performClick()
        compose.onNodeWithTag("rks-trend-guide", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("RKS趋势图  ›", useUnmergedTree = true).assertDoesNotExist()
        save("10-概览页-完成引导.png")
        compose.onNodeWithTag("overview-rks-entry").performClick()
        compose.onNodeWithTag("rks-trend-chart").assertIsDisplayed()
        compose.onNodeWithText("记录每一次变化").assertDoesNotExist()
        compose.onNodeWithText("按成绩同步时间记录").assertDoesNotExist()
        compose.onNodeWithText("轻触或左右滑动").assertDoesNotExist()
    }

    @Test fun lightTrendSelectionRangeTapAndDrag() {
        render()
        compose.onNodeWithTag("rks-trend-chart").assertIsDisplayed()
        save("02-RKS趋势图-日间.png")
        compose.onNodeWithContentDescription("上一条 RKS 记录").performClick()
        compose.onNode(hasText("15.8612") and hasAnyAncestor(hasTestTag("rks-selected-record")), useUnmergedTree = true).assertExists()
        save("03-RKS趋势图-节点详情.png")
        compose.onNodeWithTag("rks-range-WEEK").performClick()
        compose.onNodeWithContentDescription("RKS 趋势图，3 条记录，可使用下方按钮选择记录").assertExists()
        compose.onNodeWithTag("rks-trend-chart").performTouchInput { click(Offset(width * .2f, height * .5f)) }
        compose.onNodeWithTag("rks-trend-chart").performTouchInput { swipe(Offset(width * .22f, height * .5f), Offset(width * .9f, height * .5f)) }
        compose.onNodeWithContentDescription("下一条 RKS 记录").assertIsNotEnabled()
        compose.onNodeWithTag("rks-range-ALL").performClick()
        compose.onNodeWithTag("rks-trend-page").performScrollToNode(hasText("变化明细"))
        compose.onNodeWithText("变化明细").assertIsDisplayed()
        save("04-RKS趋势图-变化明细.png")
    }

    @Test fun darkTrend() { render(state(dark = true)); save("05-RKS趋势图-夜间.png") }

    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi") fun tabletTrend() {
        render(); compose.onNodeWithTag("rks-trend-chart").assertIsDisplayed(); save("06-RKS趋势图-平板.png",800,1280)
    }

    @Test @Config(qualifiers = "w915dp-h412dp-land-mdpi") fun landscapeTrend() {
        render(); compose.onNodeWithTag("rks-trend-chart").performScrollTo().assertIsDisplayed()
        save("07-RKS趋势图-横屏.png",915,412)
    }

    @Test @Config(qualifiers = "w320dp-h740dp-port-mdpi") fun compactScreen() {
        render(); compose.onNodeWithTag("rks-trend-chart").performScrollTo().assertIsDisplayed(); save("08-RKS趋势图-小屏.png",320,740)
    }

    @Test fun emptyHistoryHasNoInventedChart() {
        render(state().copy(rksHistory = RksHistoryResponse()))
        compose.onNodeWithText("成长轨迹，从这里开始").assertIsDisplayed()
        compose.onNodeWithTag("rks-trend-chart").assertDoesNotExist()
        save("09-RKS趋势图-空记录.png")
    }

    @Test fun singlePointAndPartialHistoryAndFailurePreserveRecords() {
        val partial = history().copy(items = history().items.take(1), hasMore = true, nextCursor = "cursor", total = 201)
        render(state().copy(rksHistory = partial, rksHistoryError = "网络连接暂时不可用"))
        compose.onNodeWithText("区间记录未加载完整，已保留读取的数据").assertIsDisplayed()
        compose.onNodeWithTag("rks-trend-chart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("已有第一条记录，下一次同步后继续绘制趋势。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("加载更早记录").assertDoesNotExist()
    }

    @Test fun backButtonStaysFixedAndWorksAfterScrollingToTheBottom() {
        render()
        val top = compose.onNodeWithTag("rks-trend-back").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("rks-trend-page").performScrollToNode(hasText("时间为成绩同步时的记录时间；未同步期间的实际达成时间无法追溯。"))
        compose.onNodeWithTag("rks-trend-back").assertIsDisplayed()
        assertEquals(top, compose.onNodeWithTag("rks-trend-back").fetchSemanticsNode().boundsInRoot.top, .1f)
        save("11-RKS趋势图-底部固定返回.png")
        compose.onNodeWithTag("rks-trend-back").performClick()
        compose.onNodeWithTag("overview-rks-entry").assertIsDisplayed()
    }
}

@Composable
private fun RksPreviewShell(initial: AppUiState) {
    var state by remember { mutableStateOf(initial) }
    MainShell(
        state = state, snackbar = remember { SnackbarHostState() },
        onPage = { state = state.copy(page = it, showRksTrendGuide = if (it == AppPage.RKS_TREND) false else state.showRksTrendGuide) },
        onRksTrendRangeChange = { state = state.copy(rksTrendRange = it) },
        onRefresh = {}, onRefreshLeaderboard = {}, onLoadRandomSuggestion = {}, onLoadOwnSuggestionPosts = {}, onOpenSuggestionPost = {},
        onRksCalculatorDraftChange = {}, onSubmitSuggestionPost = { _,_,_,_ -> }, onSubmitSuggestionComment = { _,_,_,_ -> },
        onDeleteSuggestionPost = { _,_ -> }, onDeleteSuggestionComment = { _,_ -> }, onSuggestionNotificationsChange = {},
        onSearchAchievementSongs = {}, onLoadAchievementRates = { _,_ -> }, onGenerateCustomRankingImage = {},
        onImportCustomRanking = {}, onClearCustomRanking = {}, onCheckin = { _,_ -> }, onCheckinRanks = {},
        onStartRksGuess = {}, onRefreshRksGuess = {}, onSubmitRksGuessAnswer = {}, onContinueRksGuessRound = {}, onLeaveRksGuessGame = {},
        onSearchSong = {}, onOpenConstantSong = { null }, onEnsureSongImage = {}, onGenerateSongImage = {},
        onGenerateImage = {}, onGenerateP30Image = {}, onGenerateBestNImage = {}, onDismissImagePagerGuide = {},
        onDismissSuggestionSwipeGuide = {}, onClearCache = {}, onThemeChange = {}, onAutoRefreshChange = {}, onAutoUpdateChange = {},
        onNavigationHandleVisibilityChange = {}, onNavigationHandlePositionChange = {}, onSwipeNavigationChange = {},
        onB30ImageStyleChange = {}, onSongScoreImageStyleChange = {}, onOpenSurvey = {}, onCheckUpdate = {},
        onRefreshAnnouncements = {}, onRevealSessionToken = {}, onHideSessionToken = {}, onLogout = {},
        onDismissNavigationGuide = { state = state.copy(showNavigationGuide = false) },
    )
}
