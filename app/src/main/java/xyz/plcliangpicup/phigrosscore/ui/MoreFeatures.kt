package xyz.plcliangpicup.phigrosscore.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.BuildConfig
import xyz.plcliangpicup.phigrosscore.R
import xyz.plcliangpicup.phigrosscore.data.AccountRksProjection
import xyz.plcliangpicup.phigrosscore.data.B30ImageStyle
import xyz.plcliangpicup.phigrosscore.data.B30Snapshot
import xyz.plcliangpicup.phigrosscore.data.ChartRksSolution
import xyz.plcliangpicup.phigrosscore.data.ConstantTableEntry
import xyz.plcliangpicup.phigrosscore.data.CustomChartDraft
import xyz.plcliangpicup.phigrosscore.data.PlayScoreAndAccuracy
import xyz.plcliangpicup.phigrosscore.data.RksCalculatorDraft
import xyz.plcliangpicup.phigrosscore.data.RksGuessClue
import xyz.plcliangpicup.phigrosscore.data.RksGuessStatus
import xyz.plcliangpicup.phigrosscore.data.SuggestionAuthor
import xyz.plcliangpicup.phigrosscore.data.SuggestionComment
import xyz.plcliangpicup.phigrosscore.data.SuggestionNotificationManager
import xyz.plcliangpicup.phigrosscore.data.SuggestionPost
import xyz.plcliangpicup.phigrosscore.data.SongInfo
import xyz.plcliangpicup.phigrosscore.data.calculateChartRks
import xyz.plcliangpicup.phigrosscore.data.calculateCustomCompositeRks
import xyz.plcliangpicup.phigrosscore.data.CUSTOM_RANKING_SLOT_COUNT
import xyz.plcliangpicup.phigrosscore.data.customRankingChartRks
import xyz.plcliangpicup.phigrosscore.data.customRankingComposite
import xyz.plcliangpicup.phigrosscore.data.customRankingOrderValid
import xyz.plcliangpicup.phigrosscore.data.customRankingPerfectSlot
import xyz.plcliangpicup.phigrosscore.data.customRankingScores
import xyz.plcliangpicup.phigrosscore.data.sortCustomRankingCharts
import xyz.plcliangpicup.phigrosscore.data.calculatePlayScoreAndAccuracy
import xyz.plcliangpicup.phigrosscore.data.projectAccountRksIncrease
import xyz.plcliangpicup.phigrosscore.data.solveChartRks
import java.io.File
import java.util.Locale
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class MoreFeature { CHECKIN, RKS, ACHIEVEMENT, CUSTOM, SUGGESTION, RKS_GUESS }
private enum class CalculatorMode(val label: String, val preferenceValue: String) {
    THREE_VALUE("知二推一", "three_value"),
    GROWTH("提升估算", "growth"),
    SCORE_ACC("分数/ACC 计算", "score_acc");

    companion object {
        fun fromPreference(value: String): CalculatorMode =
            entries.firstOrNull { it.preferenceValue == value } ?: THREE_VALUE
    }
}
private enum class CurrentMetric(val label: String, val preferenceValue: String) {
    ACC("当前 ACC", "acc"),
    RKS("当前单曲 RKS", "rks");

    companion object {
        fun fromPreference(value: String): CurrentMetric =
            entries.firstOrNull { it.preferenceValue == value } ?: ACC
    }
}
private enum class CustomRanking(val label: String, val preferenceValue: String) {
    B30("B30", "b30"),
    P30("P30", "p30");

    companion object {
        fun fromPreference(value: String): CustomRanking =
            entries.firstOrNull { it.preferenceValue == value } ?: B30
    }
}
private enum class SuggestionTab(val label: String) { ASK("求建议"), GIVE("给建议") }

private data class GeneratedScoreImage(val label: String, val file: File)

@Composable
internal fun MoreFeaturesPage(
    state: AppUiState,
    onRefreshB30: () -> Unit,
    onLoadRandomSuggestion: (Boolean) -> Unit,
    onLoadOwnSuggestionPosts: () -> Unit,
    onOpenSuggestionPost: (String) -> Unit,
    onRksCalculatorDraftChange: (RksCalculatorDraft) -> Unit,
    onSubmitSuggestionPost: (String, ByteArray, String, (Boolean) -> Unit) -> Unit,
    onSubmitSuggestionComment: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
    onDeleteSuggestionPost: (String, (Boolean) -> Unit) -> Unit,
    onDeleteSuggestionComment: (String, (Boolean) -> Unit) -> Unit,
    onSuggestionNotificationsChange: (Boolean) -> Unit,
    onDismissSuggestionSwipeGuide: () -> Unit,
    onSearchAchievementSongs: (String) -> Unit,
    onLoadAchievementRates: (String, String) -> Unit,
    onGenerateCustomRankingImage: () -> Unit,
    onClearCustomRanking: () -> Unit,
    onCheckin: (String, Boolean) -> Unit,
    onCheckinRanks: () -> Unit,
    onStartRksGuess: (String) -> Unit,
    onRefreshRksGuess: () -> Unit,
    onSubmitRksGuessAnswer: (Double) -> Unit,
    onContinueRksGuessRound: () -> Unit,
    onLeaveRksGuessGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<MoreFeature?>(null) }
    val context = LocalContext.current
    val hasSuggestionUnread by SuggestionNotificationManager.observeInAppUnread(context).collectAsState()
    LaunchedEffect(selected) {
        if (selected == MoreFeature.SUGGESTION) {
            SuggestionNotificationManager.clearInAppUnread(context)
        }
    }
    LaunchedEffect(state.hasStoredSessionToken) {
        if (state.hasStoredSessionToken) onCheckin(java.time.YearMonth.now(java.time.ZoneId.of("Asia/Shanghai")).toString(), false)
    }
    LaunchedEffect(state.checkin?.serverDate) {
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
        delay(java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1000L))
        onCheckin(java.time.YearMonth.now(java.time.ZoneId.of("Asia/Shanghai")).toString(), false)
    }
    var achievementSelectedEntry by remember { mutableStateOf<ConstantTableEntry?>(null) }
    var unavailableMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.suggestionOpenRequestId) {
        if (state.suggestionOpenRequestId != 0L) selected = MoreFeature.SUGGESTION
    }
    LaunchedEffect(state.unknownTrackIds) {
        val selectedId = achievementSelectedEntry?.song?.id
        if (selectedId != null && state.unknownTrackIds.any { it.equals(selectedId, ignoreCase = true) }) {
            achievementSelectedEntry = null
        }
    }
    BackHandler(enabled = selected != null) {
        if (selected == MoreFeature.ACHIEVEMENT && achievementSelectedEntry != null) {
            achievementSelectedEntry = null
        } else {
            if (selected == MoreFeature.RKS_GUESS && state.rksGuessGame != null) {
                onLeaveRksGuessGame()
            }
            achievementSelectedEntry = null
            selected = null
        }
    }
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(appTween(220)) + slideInHorizontally(appTween(280)) { it / 7 }) togetherWith
                    (fadeOut(appTween(150)) + slideOutHorizontally(appTween(220)) { -it / 9 })
            } else {
                (fadeIn(appTween(220)) + slideInHorizontally(appTween(280)) { -it / 7 }) togetherWith
                    (fadeOut(appTween(150)) + slideOutHorizontally(appTween(220)) { it / 9 })
            }
        },
        label = "more-feature-transition",
        modifier = modifier.fillMaxSize(),
    ) { feature ->
        when (feature) {
            null -> MoreDashboard(
                state = state,
                hasSuggestionUnread = hasSuggestionUnread,
                onSelect = {
                    achievementSelectedEntry = null
                    selected = it
                },
                onUnavailable = { unavailableMessage = it },
            )
            MoreFeature.CHECKIN -> FeatureScaffold("每日签到", onBack = { selected = null }) {
                CheckinScreen(state, onCheckin, onCheckinRanks)
            }
            MoreFeature.RKS -> FeatureScaffold("RKS 计算器", onBack = {
                achievementSelectedEntry = null
                selected = null
            }) {
                RksCalculatorScreen(
                    draft = state.rksCalculatorDraft,
                    snapshot = state.snapshot,
                    isRefreshing = state.isLoading,
                    onRefreshB30 = onRefreshB30,
                    onDraftChange = onRksCalculatorDraftChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MoreFeature.SUGGESTION -> FeatureScaffold("求建议 / 给建议", onBack = {
                achievementSelectedEntry = null
                selected = null
            }) {
                SuggestionHubScreen(
                    state = state,
                    onLoadRandomSuggestion = onLoadRandomSuggestion,
                    onLoadOwnSuggestionPosts = onLoadOwnSuggestionPosts,
                    onOpenSuggestionPost = onOpenSuggestionPost,
                    onSubmitSuggestionPost = onSubmitSuggestionPost,
                    onSubmitSuggestionComment = onSubmitSuggestionComment,
                    onDeleteSuggestionPost = onDeleteSuggestionPost,
                    onDeleteSuggestionComment = onDeleteSuggestionComment,
                    onSuggestionNotificationsChange = onSuggestionNotificationsChange,
                    onDismissSuggestionSwipeGuide = onDismissSuggestionSwipeGuide,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MoreFeature.ACHIEVEMENT -> FeatureScaffold("谱面评级达成率", onBack = {
                if (achievementSelectedEntry != null) {
                    achievementSelectedEntry = null
                } else {
                    selected = null
                }
            }) {
                ChartAchievementScreen(
                    state = state,
                    onSearchSongs = onSearchAchievementSongs,
                    onLoadRates = onLoadAchievementRates,
                    selectedEntry = achievementSelectedEntry,
                    onSelectedEntryChange = { achievementSelectedEntry = it },
                )
            }
            MoreFeature.CUSTOM -> FeatureScaffold("自定义 BP30", onBack = {
                achievementSelectedEntry = null
                selected = null
            }) {
                CustomRankingScreen(
                    draft = state.rksCalculatorDraft,
                    entries = state.constantTableEntries,
                    remoteSongs = state.achievementSongResults,
                    imageFile = if (CustomRanking.fromPreference(state.rksCalculatorDraft.customRanking) == CustomRanking.B30) {
                        state.customB30ImageFile
                    } else {
                        state.customP30ImageFile
                    },
                    isGenerating = state.isGeneratingCustomRankingImage,
                    onDraftChange = onRksCalculatorDraftChange,
                    onGenerateImage = onGenerateCustomRankingImage,
                    onSearchSongs = onSearchAchievementSongs,
                    onClearAll = onClearCustomRanking,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MoreFeature.RKS_GUESS -> FeatureScaffold(
                when (state.rksGuessGame?.mode) {
                    "single" -> "RKS猜猜乐-[单人模式]"
                    "public" -> "RKS猜猜乐-[公开匹配]"
                    else -> "RKS猜猜乐"
                },
                onBack = {
                if (state.rksGuessGame != null) onLeaveRksGuessGame()
                achievementSelectedEntry = null
                selected = null
            }) {
                RksGuessGameScreen(
                    state = state,
                    onStart = onStartRksGuess,
                    onRefresh = onRefreshRksGuess,
                    onSubmitAnswer = onSubmitRksGuessAnswer,
                    onContinueRound = onContinueRksGuessRound,
                    onLeave = onLeaveRksGuessGame,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    unavailableMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { unavailableMessage = null },
            title = { Text("暂未开放") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { unavailableMessage = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun MoreDashboard(
    state: AppUiState,
    hasSuggestionUnread: Boolean,
    onSelect: (MoreFeature) -> Unit,
    onUnavailable: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = AppPageHorizontalPadding, end = AppPageHorizontalPadding,
            top = 4.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Text("更多", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp)) }
        item {
            CheckinEntry(state) { onSelect(MoreFeature.CHECKIN) }
        }
        item {
            FeatureGroup("成绩工具") {
                MoreFeatureTile("RKS 计算器", "推算 · 提升估算 · 分数 / ACC",
                    { Icon(Icons.Default.Calculate, null) }, { onSelect(MoreFeature.RKS) })
                FeatureRowDivider()
                MoreFeatureTile("谱面评级达成率", "查看评级分布与个人百分位",
                    { Icon(Icons.Default.Insights, null) }, { onSelect(MoreFeature.ACHIEVEMENT) })
                FeatureRowDivider()
                MoreFeatureTile("自定义 BP30", "编辑谱面 · 生成成绩图",
                    { Icon(Icons.Default.Functions, null) }, { onSelect(MoreFeature.CUSTOM) })
            }
        }
        item {
            FeatureGroup("交流") {
                MoreFeatureTile("求建议 / 给建议", "分享成绩，交流推分思路",
                    { Icon(Icons.Default.Forum, null) }, { onSelect(MoreFeature.SUGGESTION) },
                    hasUnread = hasSuggestionUnread)
            }
        }
        item {
            FeatureGroup("小游戏") {
                MoreFeatureTile("RKS 猜猜乐", "单人 · 公开匹配",
                    { RksGuessIcon() }, { onSelect(MoreFeature.RKS_GUESS) })
                FeatureRowDivider()
                MoreFeatureTile("猜曲绘", "暂未开放",
                    { Icon(Icons.Default.Image, null) }, { onUnavailable("猜曲绘 暂未开放") })
                FeatureRowDivider()
                MoreFeatureTile("开字母", "暂未开放",
                    { Icon(Icons.Default.Casino, null) }, { onUnavailable("开字母 暂未开放") })
            }
        }
    }
}

@Composable
private fun FeatureGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AppTextMuted, fontSize = 12.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppSurface)) {
            content()
        }
    }
}

@Composable
private fun FeatureRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
        thickness = .5.dp,
    )
}

@Composable
private fun RksGuessIcon() {
    Image(
        painter = painterResource(R.drawable.rks_guess_icon),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        colorFilter = ColorFilter.tint(AppAccent),
    )
}

@Composable
private fun MoreFeatureTile(
    title: String,
    caption: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    hasUnread: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val glow = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(Unit) {
        delay(180)
        glow.animateTo(1f, appTween(520))
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .appPressMotion(interactionSource)
            .graphicsLayer {
                translationY = (1f - glow.value) * 7f
                alpha = .76f + glow.value * .24f
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppAccent.copy(alpha = .07f)),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides AppAccent) { icon() }
            }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
            }
            if (hasUnread) {
                InAppUnreadDot(Modifier.padding(end = 8.dp))
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .58f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun FeatureScaffold(title: String, onBack: () -> Unit, actions: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = AppPageHorizontalPadding, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            actions?.invoke()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .38f))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun RksCalculatorScreen(
    draft: RksCalculatorDraft,
    snapshot: B30Snapshot?,
    isRefreshing: Boolean,
    onRefreshB30: () -> Unit,
    onDraftChange: (RksCalculatorDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = CalculatorMode.fromPreference(draft.mode)
    Column(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalculatorMode.entries.chunked(3).forEach { rowModes ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowModes.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { onDraftChange(draft.copy(mode = item.preferenceValue)) },
                            label = {
                                Text(
                                    item.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp,
                                )
                            },
                            leadingIcon = if (mode == item) {
                                { Icon(Icons.Default.Functions, null, Modifier.size(15.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - rowModes.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(appTween(220)) + slideInVertically(appTween(260)) { it / 12 }) togetherWith
                    (fadeOut(appTween(130)) + slideOutVertically(appTween(180)) { -it / 14 })
            },
            label = "calculator-mode",
            modifier = Modifier.weight(1f),
        ) {
            when (it) {
                CalculatorMode.THREE_VALUE -> ThreeValueCalculator(draft, onDraftChange)
                CalculatorMode.GROWTH -> GrowthCalculator(
                    draft = draft,
                    snapshot = snapshot,
                    isRefreshing = isRefreshing,
                    onRefreshB30 = onRefreshB30,
                    onDraftChange = onDraftChange,
                )
                CalculatorMode.SCORE_ACC -> ScoreAccCalculator(draft, onDraftChange)
            }
        }
    }
}

@Composable
private fun ScoreAccCalculator(
    draft: RksCalculatorDraft,
    onDraftChange: (RksCalculatorDraft) -> Unit,
) {
    var result by remember { mutableStateOf<PlayScoreAndAccuracy?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "输入本次游玩的判定与最大连击数",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        IntegerField(draft.playNoteCount, { onDraftChange(draft.copy(playNoteCount = it)) }, "谱面物量", "1214")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IntegerField(
                draft.playPerfectCount,
                { onDraftChange(draft.copy(playPerfectCount = it)) },
                "Perfect",
                "1180",
                modifier = Modifier.weight(1f),
            )
            IntegerField(
                draft.playGoodCount,
                { onDraftChange(draft.copy(playGoodCount = it)) },
                "Good",
                "20",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IntegerField(
                draft.playBadCount,
                { onDraftChange(draft.copy(playBadCount = it)) },
                "Bad",
                "8",
                modifier = Modifier.weight(1f),
            )
            IntegerField(
                draft.playMissCount,
                { onDraftChange(draft.copy(playMissCount = it)) },
                "Miss",
                "6",
                modifier = Modifier.weight(1f),
            )
        }
        IntegerField(draft.playMaxCombo, { onDraftChange(draft.copy(playMaxCombo = it)) }, "Max Combo", "968")
        Button(
            onClick = {
                runCatching {
                    calculatePlayScoreAndAccuracy(
                        noteCount = draft.playNoteCount.toIntOrNull()
                            ?: throw IllegalArgumentException("请填写谱面物量"),
                        perfectCount = draft.playPerfectCount.toIntOrNull()
                            ?: throw IllegalArgumentException("请填写 Perfect 数量"),
                        goodCount = draft.playGoodCount.toIntOrNull()
                            ?: throw IllegalArgumentException("请填写 Good 数量"),
                        badCount = draft.playBadCount.toIntOrNull()
                            ?: throw IllegalArgumentException("请填写 Bad 数量"),
                        missCount = draft.playMissCount.toIntOrNull()
                            ?: throw IllegalArgumentException("请填写 Miss 数量"),
                        maxCombo = draft.playMaxCombo.toIntOrNull()
                            ?: throw IllegalArgumentException("请填写 Max Combo"),
                    )
                }.onSuccess {
                    result = it
                    error = null
                }.onFailure {
                    result = null
                    error = it.message
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("计算分数与 ACC", fontWeight = FontWeight.Bold) }
        AnimatedVisibility(visible = result != null || error != null, enter = fadeIn() + slideInVertically { it / 3 }) {
            result?.let {
                ScoreAccResultCard(it)
            } ?: ErrorText(error.orEmpty())
        }
    }
}

@Composable
private fun ScoreAccResultCard(result: PlayScoreAndAccuracy) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .13f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(appSpring()),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "本次游玩结果",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("SCORE", color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(
                    String.format(Locale.US, "%,d", result.score),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            HorizontalDivider(color = AppAccent.copy(alpha = .24f))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ACCURACY", color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${formatNumber(result.accuracy, 2)}%",
                        color = AppAccent,
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
                Text(
                    "${String.format(Locale.US, "%,d", result.noteCount)} Notes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ThreeValueCalculator(
    draft: RksCalculatorDraft,
    onDraftChange: (RksCalculatorDraft) -> Unit,
) {
    val constant = draft.threeConstant
    val accuracy = draft.threeAccuracy
    val rks = draft.threeRks
    var result by remember { mutableStateOf<ChartRksSolution?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("留空一项", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        DecimalField(constant, { onDraftChange(draft.copy(threeConstant = it)) }, "谱面定数", "15.9")
        DecimalField(accuracy, { onDraftChange(draft.copy(threeAccuracy = it)) }, "ACC", "99.25")
        DecimalField(rks, { onDraftChange(draft.copy(threeRks = it)) }, "单曲 RKS", "15.37")
        Button(
            onClick = {
                runCatching {
                    solveChartRks(constant.toDoubleOrNull(), accuracy.toDoubleOrNull(), rks.toDoubleOrNull())
                }.onSuccess {
                    result = it
                    error = null
                    onDraftChange(
                        draft.copy(
                            threeConstant = constant.ifBlank { formatNumber(it.chartConstant, 4) },
                            threeAccuracy = accuracy.ifBlank { formatNumber(it.accuracy, 4) },
                            threeRks = rks.ifBlank { formatNumber(it.chartRks, 4) },
                        ),
                    )
                }.onFailure {
                    result = null
                    error = it.message
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("计算", fontWeight = FontWeight.Bold) }
        AnimatedVisibility(visible = result != null || error != null, enter = fadeIn() + slideInVertically { it / 3 }) {
            result?.let {
                ResultPanel(
                    title = "计算结果",
                    lines = listOf(
                        "定数" to formatNumber(it.chartConstant, 4),
                        "ACC" to "${formatNumber(it.accuracy, 4)}%",
                        "单曲 RKS" to formatNumber(it.chartRks, 6),
                    ),
                )
            } ?: ErrorText(error.orEmpty())
        }
    }
}

@Composable
private fun GrowthCalculator(
    draft: RksCalculatorDraft,
    snapshot: B30Snapshot?,
    isRefreshing: Boolean,
    onRefreshB30: () -> Unit,
    onDraftChange: (RksCalculatorDraft) -> Unit,
) {
    val constant = draft.growthConstant
    val metric = CurrentMetric.fromPreference(draft.growthMetric)
    val currentValue = draft.growthCurrentValue
    val targetAccuracy = draft.growthTargetAccuracy
    var result by remember { mutableStateOf<AccountRksProjection?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snapshot?.cachedAtEpochMs) {
        result = null
        error = null
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DecimalField(constant, { onDraftChange(draft.copy(growthConstant = it)) }, "谱面定数", "15.9")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurrentMetric.entries.forEach {
                FilterChip(
                    selected = metric == it,
                    onClick = { onDraftChange(draft.copy(growthMetric = it.preferenceValue)) },
                    label = { Text(it.label) },
                )
            }
        }
        DecimalField(
            currentValue,
            { onDraftChange(draft.copy(growthCurrentValue = it)) },
            metric.label,
            if (metric == CurrentMetric.ACC) "98.50" else "14.87",
        )
        DecimalField(
            targetAccuracy,
            { onDraftChange(draft.copy(growthTargetAccuracy = it)) },
            "目标 ACC",
            "99.50",
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .09f)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("真实 B30", fontWeight = FontWeight.Black)
                        Text(
                            snapshot?.let { "当前综合 ${formatNumber(it.totalRks, 6)}" } ?: "尚未读取成绩",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    OutlinedButton(onClick = onRefreshB30, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("刷新")
                        }
                    }
                }
                snapshot?.items
                    ?.filter { it.section.equals("BEST", ignoreCase = true) }
                    ?.lastOrNull()
                    ?.let { Text("B27 末位 ${formatNumber(it.rks, 6)}", color = AppAccent, fontSize = 12.sp) }
            }
        }
        Button(
            onClick = {
                runCatching {
                    val value = requireNotNull(currentValue.toDoubleOrNull()) { "请填写当前成绩" }
                    val actualB30 = requireNotNull(snapshot) { "暂无真实 B30，请先刷新成绩" }
                    projectAccountRksIncrease(
                        chartConstant = requireNotNull(constant.toDoubleOrNull()) { "请填写谱面定数" },
                        currentAccuracy = value.takeIf { metric == CurrentMetric.ACC },
                        currentChartRks = value.takeIf { metric == CurrentMetric.RKS },
                        targetAccuracy = requireNotNull(targetAccuracy.toDoubleOrNull()) { "请填写目标 ACC" },
                        currentAccountRks = actualB30.totalRks,
                        b30Items = actualB30.items,
                    )
                }.onSuccess {
                    result = it
                    error = null
                }.onFailure {
                    result = null
                    error = it.message
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("估算提升", fontWeight = FontWeight.Bold) }
        AnimatedVisibility(visible = result != null || error != null, enter = fadeIn() + slideInVertically { it / 3 }) {
            result?.let {
                ResultPanel(
                    title = "+${formatNumber(it.accountIncrease, 6)} RKS",
                    lines = listOf(
                        "真实综合" to formatNumber(it.currentAccountRks, 6),
                        "当前单曲" to formatNumber(it.currentChartRks, 6),
                        "目标单曲" to formatNumber(it.targetChartRks, 6),
                        "B27 末位" to formatNumber(it.best27Floor, 6),
                        "B27 提升" to "+${formatNumber(it.best27Increase, 6)}",
                        "AP3 提升" to "+${formatNumber(it.ap3Increase, 6)}",
                        "预计综合" to formatNumber(it.projectedAccountRks, 6),
                    ),
                )
            } ?: ErrorText(error.orEmpty())
        }
        Text(
            "按真实 B27 与 AP3 重新排序；目标未进入有效槽位时提升为 0。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun CustomRankingScreen(
    draft: RksCalculatorDraft,
    entries: List<ConstantTableEntry>,
    remoteSongs: List<SongInfo>,
    imageFile: File?,
    isGenerating: Boolean,
    onDraftChange: (RksCalculatorDraft) -> Unit,
    onGenerateImage: () -> Unit,
    onSearchSongs: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranking = CustomRanking.fromPreference(draft.customRanking)
    val inputMode = CustomInputMode.fromPreference(draft.customInputMode)
    val charts = if (ranking == CustomRanking.B30) draft.customB30Charts else draft.customP30Charts
    val values = if (ranking == CustomRanking.B30) draft.b30Values else draft.p30Values
    val manual = inputMode == CustomInputMode.MANUAL
    val rksBySlot = if (manual) values.map { it.toDoubleOrNull() }
        else charts.mapIndexed { index, chart -> customRankingChartRks(chart, ranking.preferenceValue, index) }
    val rows = xyz.plcliangpicup.phigrosscore.data.customRankingRows(
        rksBySlot, ranking.preferenceValue, if (manual) null else charts.map(::customChartKey),
    )
    val resolvedCharts = xyz.plcliangpicup.phigrosscore.data.resolvedCustomRankingCharts(charts, ranking.preferenceValue)
    val displayedRks = rows.map { rksBySlot[it.sourceIndex] }
    val filled = if (manual) displayedRks.count { it != null } else resolvedCharts.count { customChartKey(it) != null }
    val composite = customRankingComposite(displayedRks)
    val orderValid = customRankingOrderValid(rksBySlot, ranking.preferenceValue)
    val validation = remember(resolvedCharts, ranking) { runCatching { customRankingScores(resolvedCharts, ranking.preferenceValue) } }
    val progress by animateFloatAsState(filled / 36f, appTween(300), label = "custom-progress")
    var pickerIndex by remember { mutableStateOf<Int?>(null) }
    var pickerQuery by remember { mutableStateOf("") }
    var showImagePreview by remember(imageFile?.absolutePath, imageFile?.lastModified()) { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val rankingListState = rememberLazyListState()
    var focusedRowKey by remember(ranking, inputMode) { mutableStateOf<String?>(null) }
    val focusedRowIndex = rows.indexOfFirst { it.stableKey == focusedRowKey }
    val rankingViewportHeight by remember {
        derivedStateOf { rankingListState.layoutInfo.viewportSize.height }
    }
    LaunchedEffect(focusedRowKey, focusedRowIndex, rankingViewportHeight) {
        if (focusedRowIndex < 0 || rankingViewportHeight <= 0) return@LaunchedEffect
        // Wait for the reordered lazy items to be measured before targeting their new index.
        // Tracking is outside the item composition so it also works for moves off screen.
        withFrameNanos { }
        rankingListState.animateScrollToItem(
            index = focusedRowIndex + 1, // The controls occupy lazy item zero.
            scrollOffset = -(rankingViewportHeight / 3),
        )
    }
    fun updateCharts(next: List<CustomChartDraft>) = onDraftChange(
        if (ranking == CustomRanking.B30) draft.copy(customB30Charts = next) else draft.copy(customP30Charts = next),
    )
    fun updateValues(next: List<String>) = onDraftChange(
        if (ranking == CustomRanking.B30) draft.copy(b30Values = next) else draft.copy(p30Values = next),
    )
    LaunchedEffect(pickerIndex) { pickerQuery = "" }

    // MainShell already applies keyboard/system insets; do not subtract the IME twice.
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomRanking.entries.forEach { item ->
                FilterChip(selected = ranking == item,
                    onClick = { onDraftChange(draft.copy(customRanking = item.preferenceValue)) },
                    label = { Text(item.label) })
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("RKS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                if (composite != null) {
                    AnimatedRksNumber(composite, 22.sp, color = AppAccent, fractionDigits = 4, fontWeight = FontWeight.Bold, animateDecrease = true)
                } else {
                    Text("--", fontSize = 22.sp, fontFamily = AppNumericFont, color = AppAccent)
                }
            }
        }
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        state = rankingListState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomInputMode.entries.forEach { mode ->
                        FilterChip(selected = inputMode == mode,
                            onClick = { onDraftChange(draft.copy(customInputMode = mode.preferenceValue)) },
                            label = { Text(if (mode == CustomInputMode.MANUAL) "输入 RKS" else "选择谱面") },
                            modifier = Modifier.weight(1f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$filled / 36", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppNumericFont, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteOutline, "清空全部", modifier = Modifier.size(20.dp)) }
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape))
            }
        }
        items(rows, key = { "${ranking.preferenceValue}-${inputMode.preferenceValue}-${it.stableKey}" }) { row ->
            val index = rows.indexOf(row)
            val sourceIndex = row.sourceIndex
            Column(Modifier.onFocusChanged { focus ->
                if (focus.hasFocus) focusedRowKey = row.stableKey
                else if (focusedRowKey == row.stableKey) focusedRowKey = null
            }.focusGroup().animateItem(
                fadeInSpec = appTween(180), placementSpec = appTween(320), fadeOutSpec = appTween(150),
            )) {
                if (manual) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(customSlotLabel(index, ranking), color = AppAccent, fontFamily = AppNumericFont, modifier = Modifier.width(68.dp))
                        if (row.mirrored) {
                            Row(Modifier.weight(1f).padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                AnimatedRksNumber(values[sourceIndex].toDoubleOrNull() ?: 0.0, 18.sp, color = AppAccent, animateDecrease = true)
                                Spacer(Modifier.weight(1f))
                                Text("P${rows.take(3).indexOfFirst { it.sourceIndex == sourceIndex } + 1}", color = AppTextMuted, fontSize = 11.sp)
                            }
                        } else DecimalField(value = values[sourceIndex], onValueChange = { next ->
                            updateValues(values.toMutableList().apply { this[sourceIndex] = next })
                        }, label = "RKS", placeholder = "0.0000", modifier = Modifier.weight(1f))
                    }
                } else {
                    CustomChartSlotCard(index, ranking, resolvedCharts[index], onPick = { pickerIndex = sourceIndex },
                        onClear = { updateCharts(charts.toMutableList().apply { this[sourceIndex] = CustomChartDraft() }) },
                        onScoreChange = { next -> updateCharts(charts.toMutableList().apply { this[sourceIndex] = this[sourceIndex].copy(score = next) }) },
                        onAccuracyChange = { next -> updateCharts(charts.toMutableList().apply { this[sourceIndex] = this[sourceIndex].copy(accuracy = next) }) },
                        linkedFrom = if (row.mirrored) rows.take(3).indexOfFirst { it.sourceIndex == sourceIndex } + 1 else null)
                }
            }
        }
        item {
            AnimatedVisibility(!manual, enter = fadeIn(appTween(220)), exit = fadeOut(appTween(150))) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onGenerateImage, enabled = validation.isSuccess && !isGenerating,
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        if (isGenerating) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isGenerating) "正在生成…" else "生成 ${ranking.label} 图片")
                    }
                    if (validation.isFailure && filled == 36) {
                        Text(validation.exceptionOrNull()?.message.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (!manual && imageFile != null) {
            item { CustomRankingImageCard(ranking, imageFile, onOpen = { showImagePreview = true }) }
        }
    }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, title = { Text("清空自定义成绩？") },
        text = { Text("B30、P30 的填写内容和图片将一并清空。") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClearAll() }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
    pickerIndex?.let { index ->
        CustomChartPickerDialog(entries, remoteSongs.flatMap(::constantEntriesForSong),
            xyz.plcliangpicup.phigrosscore.data.customRankingPickerUsedKeys(charts, index),
            pickerQuery, { pickerQuery = it }, onSearchSongs, { pickerIndex = null }, onSelect = { entry ->
                val old = charts[index]
                val same = customChartKey(old) == "${entry.song.id}-${entry.chart.difficulty.uppercase()}"
                val perfect = customRankingPerfectSlot(ranking.preferenceValue, index)
                val chart = CustomChartDraft(source = "catalog", songId = entry.song.id, songName = entry.song.name,
                    difficulty = entry.chart.difficulty.uppercase(),
                    chartConstant = entry.chart.chartConstant?.let { formatNumber(it, 4) }.orEmpty(),
                    score = if (perfect) "1000000" else old.score.takeIf { same }.orEmpty(),
                    accuracy = if (perfect) "100" else old.accuracy.takeIf { same }.orEmpty())
                updateCharts(charts.toMutableList().apply { this[index] = chart })
                pickerIndex = null
            })
    }
    if (showImagePreview && imageFile?.exists() == true) {
        CustomRankingImagePreview(imageFile, ranking, onDismiss = { showImagePreview = false })
    }
}

private fun customSlotLabel(index: Int, ranking: CustomRanking): String =
    if (ranking == CustomRanking.P30 || index < 3) "P${index + 1}" else "Best ${index - 2}"

private fun constantEntriesForSong(song: SongInfo): List<ConstantTableEntry> =
    song.charts.mapNotNull { chart ->
        chart.chartConstant?.takeIf { it > 0.0 }?.let { ConstantTableEntry(song, chart) }
    }

@Composable
private fun CustomRankingImageCard(
    ranking: CustomRanking,
    imageFile: File?,
    onOpen: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .68f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(appTween(220)),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 620.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .clickable(enabled = imageFile?.exists() == true, onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                if (imageFile?.exists() == true) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageFile)
                            .memoryCacheKey("custom-${ranking.preferenceValue}-${imageFile.lastModified()}-${imageFile.length()}")
                            .build(),
                        contentDescription = "自定义 ${ranking.label} 图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 620.dp).padding(4.dp),
                    )
                } else {
                    Text(
                        "图片生成后显示在这里",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(20.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (imageFile?.exists() == true) {
                Text(
                    "点击查看 · 保存与分享",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CustomRankingImagePreview(
    image: File,
    ranking: CustomRanking,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var scale by remember(image.absolutePath, image.lastModified()) { mutableFloatStateOf(1f) }
    var offset by remember(image.absolutePath, image.lastModified()) { mutableStateOf(Offset.Zero) }
    var pendingSave by remember { mutableStateOf<File?>(null) }
    val savePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val file = pendingSave
        pendingSave = null
        if (granted && file != null) {
            runCatching { saveB30ImageToGallery(context, file, "Phi-Custom-${ranking.preferenceValue}-${System.currentTimeMillis()}.png") }
                .onSuccess { Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "保存失败，请稍后重试", Toast.LENGTH_SHORT).show() }
        }
    }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = nextScale
        offset = if (nextScale <= 1f) Offset.Zero else offset + panChange
    }

    fun saveImage() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = image
            savePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        runCatching { saveB30ImageToGallery(context, image, "Phi-Custom-${ranking.preferenceValue}-${System.currentTimeMillis()}.png") }
            .onSuccess { Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "保存失败，请稍后重试", Toast.LENGTH_SHORT).show() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image)
                    .memoryCacheKey("custom-preview-${image.absolutePath}-${image.lastModified()}-${image.length()}")
                    .build(),
                contentDescription = "放大的自定义 ${ranking.label} 图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .transformable(transformableState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Text(
                "双指缩放 · 放大后拖动查看",
                color = Color.White.copy(alpha = .76f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
            ) { Icon(Icons.Default.Close, "关闭", tint = Color.White) }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = ::saveImage) { Text("保存到相册") }
                Button(onClick = {
                    runCatching { shareImage(context, image, "分享自定义 ${ranking.label} 图片") }
                        .onFailure { Toast.makeText(context, "分享失败，请稍后重试", Toast.LENGTH_SHORT).show() }
                }) { Text("分享") }
            }
        }
    }
}

private fun customChartKey(chart: CustomChartDraft): String? =
    chart.songId.trim().takeIf { it.isNotEmpty() }
        ?.let { songId -> chart.difficulty.trim().uppercase().takeIf { it.isNotEmpty() }?.let { "$songId-$it" } }

@Composable
private fun CustomChartSlotCard(
    index: Int,
    ranking: CustomRanking,
    chart: CustomChartDraft,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onScoreChange: (String) -> Unit,
    onAccuracyChange: (String) -> Unit,
    linkedFrom: Int? = null,
) {
    val label = customSlotLabel(index, ranking)
    val calculatedRks = customRankingChartRks(chart, ranking.preferenceValue, index)
    val perfect = linkedFrom != null || customRankingPerfectSlot(ranking.preferenceValue, index)
    val selected = chart.source == "catalog" && chart.songName.isNotBlank()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(appTween(180)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(enabled = linkedFrom == null, onClick = onPick).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = AppAccent, fontFamily = AppNumericFont, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, modifier = Modifier.width(64.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (selected) chart.songName else "选择谱面", fontWeight = FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        if (selected) {
                            Text("${chart.difficulty} ${chart.chartConstant}", color = AppAccent, fontFamily = AppNumericFont, fontSize = 11.sp)
                        }
                    }
                    if (!selected) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                if (linkedFrom != null) Text("P$linkedFrom", color = AppTextMuted, fontSize = 11.sp)
                if (selected && linkedFrom == null) IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, "清空 $label", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            if (selected && !perfect) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntegerField(chart.score, onScoreChange, label = "分数（可选）", placeholder = "1000000",
                        maxValue = 1_000_000, modifier = Modifier.weight(1f))
                    DecimalField(chart.accuracy, onAccuracyChange, label = "ACC", placeholder = "99.50",
                        maxValue = 100.0, modifier = Modifier.weight(1f))
                }
                if (calculatedRks != null) Text("RKS ${formatNumber(calculatedRks, 4)}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, fontFamily = AppNumericFont, modifier = Modifier.padding(vertical = 4.dp))
                else Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun CustomChartPickerDialog(
    entries: List<ConstantTableEntry>,
    remoteEntries: List<ConstantTableEntry>,
    usedKeys: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (ConstantTableEntry) -> Unit,
) {
    LaunchedEffect(query) {
        if (query.trim().isNotEmpty()) {
            delay(220)
            onSearch(query)
        }
    }
    val filtered = remember(entries, remoteEntries, usedKeys, query) {
        val needle = query.trim()
        val remoteKeys = remoteEntries.mapTo(mutableSetOf()) { entry ->
            "${entry.song.id}-${entry.chart.difficulty.uppercase()}"
        }
        (entries + if (needle.isBlank()) emptyList() else remoteEntries)
            .distinctBy { "${it.song.id}-${it.chart.difficulty.uppercase()}" }
            .asSequence()
            .filter { entry ->
                val key = "${entry.song.id}-${entry.chart.difficulty.uppercase()}"
                key !in usedKeys && (
                    needle.isBlank() || key in remoteKeys || entry.song.name.contains(needle, true) ||
                        entry.song.composer.contains(needle, true) || entry.song.id.contains(needle, true)
                    )
            }
            .take(120)
            .toList()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(.94f).fillMaxHeight(.84f),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("选择谱面", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("搜索曲目") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("没有可选的谱面", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(
                            items = filtered,
                            key = { "${it.song.id}-${it.chart.difficulty}" },
                        ) { entry ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(entry) }
                                    .padding(horizontal = 12.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(entry.song.name, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(entry.chart.difficulty, color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(entry.chart.chartConstant?.let { formatNumber(it, 1) } ?: "--",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = AppNumericFont)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    maxValue: Double? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.isEmpty() ||
                (next.matches(Regex("\\d{0,3}(\\.\\d{0,8})?")) &&
                    (maxValue == null || next.toDoubleOrNull()?.let { it <= maxValue } == true))
            ) onValueChange(next)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultPanel(title: String, lines: List<Pair<String, String>>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .12f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(appSpring()),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = AppAccent, fontSize = 25.sp, fontWeight = FontWeight.Black)
            lines.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
}

@Composable
private fun SuggestionHubScreen(
    state: AppUiState,
    onLoadRandomSuggestion: (Boolean) -> Unit,
    onLoadOwnSuggestionPosts: () -> Unit,
    onOpenSuggestionPost: (String) -> Unit,
    onSubmitSuggestionPost: (String, ByteArray, String, (Boolean) -> Unit) -> Unit,
    onSubmitSuggestionComment: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
    onDeleteSuggestionPost: (String, (Boolean) -> Unit) -> Unit,
    onDeleteSuggestionComment: (String, (Boolean) -> Unit) -> Unit,
    onSuggestionNotificationsChange: (Boolean) -> Unit,
    onDismissSuggestionSwipeGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(SuggestionTab.ASK) }
    LaunchedEffect(state.suggestionOpenRequestId) {
        if (state.suggestionOpenRequestId != 0L) tab = SuggestionTab.GIVE
    }
    val generatedImages = remember(state.imageFile, state.p30ImageFile) {
        listOfNotNull(
            state.imageFile?.takeIf(File::exists)?.let { GeneratedScoreImage("B30", it) },
            state.p30ImageFile?.takeIf(File::exists)?.let { GeneratedScoreImage("P30", it) },
        )
    }
    LaunchedEffect(tab, state.suggestionPost) {
        if (tab == SuggestionTab.GIVE && state.suggestionPost == null && !state.isSuggestionLoading) {
            onLoadRandomSuggestion(false)
        }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuggestionTab.entries.forEach {
                FilterChip(
                    selected = tab == it,
                    onClick = { tab = it },
                    label = { Text(it.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(appTween(220)) + slideInHorizontally(appTween(260)) { it / 8 }) togetherWith
                    (fadeOut(appTween(140)) + slideOutHorizontally(appTween(180)) { -it / 10 })
            },
            label = "suggestion-tab",
            modifier = Modifier.weight(1f),
        ) {
            when (it) {
                SuggestionTab.ASK -> AskSuggestionPane(
                    submitting = state.isSuggestionSubmitting,
                    loading = state.isSuggestionLoading,
                    generatedImages = generatedImages,
                    ownPosts = state.ownSuggestionPosts,
                    commentedPosts = state.commentedSuggestionPosts,
                    notificationsEnabled = state.suggestionNotificationsEnabled,
                    onLoadOwnPosts = onLoadOwnSuggestionPosts,
                    onOpenPost = {
                        tab = SuggestionTab.GIVE
                        onOpenSuggestionPost(it)
                    },
                    onDeletePost = onDeleteSuggestionPost,
                    onNotificationsChange = onSuggestionNotificationsChange,
                    onSubmit = { description, bytes, mime, done ->
                        onSubmitSuggestionPost(description, bytes, mime) { success ->
                            done(success)
                            if (success) tab = SuggestionTab.GIVE
                        }
                    },
                )
                SuggestionTab.GIVE -> GiveSuggestionPane(
                    state = state,
                    generatedImages = generatedImages,
                    onNext = { onLoadRandomSuggestion(true) },
                    showSwipeGuide = state.showSuggestionSwipeGuide,
                    onDismissSwipeGuide = onDismissSuggestionSwipeGuide,
                    onSubmitComment = onSubmitSuggestionComment,
                    onDeletePost = onDeleteSuggestionPost,
                    onDeleteComment = onDeleteSuggestionComment,
                )
            }
        }
    }
}

@Composable
private fun AskSuggestionPane(
    submitting: Boolean,
    loading: Boolean,
    generatedImages: List<GeneratedScoreImage>,
    ownPosts: List<xyz.plcliangpicup.phigrosscore.data.SuggestionPost>,
    commentedPosts: List<xyz.plcliangpicup.phigrosscore.data.SuggestionPost>,
    notificationsEnabled: Boolean,
    onLoadOwnPosts: () -> Unit,
    onOpenPost: (String) -> Unit,
    onDeletePost: (String, (Boolean) -> Unit) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSubmit: (String, ByteArray, String, (Boolean) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("求建议！") }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val selected = generatedImages.firstOrNull { it.label == selectedLabel }
        ?: generatedImages.firstOrNull()
    LaunchedEffect(generatedImages.map { it.label }) {
        if (selectedLabel !in generatedImages.map { it.label }) {
            selectedLabel = generatedImages.firstOrNull()?.label
        }
    }
    LaunchedEffect(Unit) { onLoadOwnPosts() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScoreImagePickerCard(
            images = generatedImages,
            selected = selected,
            onSelect = {
                selectedLabel = it.label
                localError = null
            },
        )
        SuggestionNotificationToggle(
            enabled = notificationsEnabled,
            onEnabledChange = onNotificationsChange,
        )
        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= 120) description = it },
            label = { Text("文字描述") },
            placeholder = { Text("求建议！") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        localError?.let { ErrorText(it) }
        Button(
            enabled = selected != null && !submitting,
            onClick = {
                val image = selected ?: return@Button
                scope.launch {
                    runCatching { readGeneratedScoreImage(image) }
                        .onSuccess { bytes ->
                            onSubmit(description, bytes, "image/png") { success ->
                                if (success) {
                                    selectedLabel = null
                                    description = "求建议！"
                                }
                            }
                        }
                        .onFailure { localError = it.message }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (submitting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text("发送成绩图", fontWeight = FontWeight.Bold)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (ownPosts.isNotEmpty()) {
            Text("我的求建议帖子", fontWeight = FontWeight.Black, fontSize = 15.sp)
            ownPosts.forEach { post ->
                OwnSuggestionPostCard(
                    post = post,
                    submitting = submitting,
                    onOpen = { onOpenPost(post.id) },
                    onDelete = { done -> onDeletePost(post.id, done) },
                )
            }
        }
        if (commentedPosts.isNotEmpty()) {
            Text("我的建议评论", fontWeight = FontWeight.Black, fontSize = 15.sp)
            commentedPosts.forEach { post ->
                val ownCommentCount = post.comments.count { it.canDelete }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !submitting) { onOpenPost(post.id) },
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .56f),
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(post.description, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(
                            "$ownCommentCount 条我的评论 · 点击管理",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartAchievementScreen(
    state: AppUiState,
    onSearchSongs: (String) -> Unit,
    onLoadRates: (String, String) -> Unit,
    selectedEntry: ConstantTableEntry?,
    onSelectedEntryChange: (ConstantTableEntry?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val selectionListState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val entries = remember(state.constantTableEntries, state.achievementSongResults, query) {
        val needle = query.trim()
        val aliasMatchIds = state.achievementSongResults.asSequence().map(SongInfo::id).toSet()
        state.constantTableEntries.filter { entry ->
            needle.isBlank() || entry.song.name.contains(needle, true) ||
                entry.song.composer.contains(needle, true) || entry.song.id.contains(needle, true) ||
                entry.song.id in aliasMatchIds
        }
    }
    LaunchedEffect(query) {
        if (query.isNotBlank()) selectionListState.animateScrollToItem(0)
    }
    AnimatedContent(
        targetState = selectedEntry,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(appTween(180)) + slideInHorizontally(appTween(240)) { it / 8 }) togetherWith
                    (fadeOut(appTween(120)) + slideOutHorizontally(appTween(180)) { -it / 10 })
            } else {
                (fadeIn(appTween(180)) + slideInHorizontally(appTween(240)) { -it / 8 }) togetherWith
                    (fadeOut(appTween(120)) + slideOutHorizontally(appTween(180)) { it / 10 })
            }
        },
        label = "achievement-selection-detail-transition",
        modifier = Modifier.fillMaxSize(),
    ) { entry ->
        if (entry == null) {
            LazyColumn(
                state = selectionListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onSelectedEntryChange(null)
                            onSearchSongs(it)
                        },
                        label = { Text("搜索曲目") },
                        placeholder = { Text("可输入曲名、别名、曲师或曲目 ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (entries.isEmpty()) {
                    item {
                        Text("没有匹配谱面", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val grouped = entries.groupBy { it.chart.chartConstant ?: 0.0 }
                        .toSortedMap(compareByDescending { it })
                    grouped.forEach { (constant, charts) ->
                        item(key = "constant-$constant") {
                            Text(
                                String.format(Locale.US, "%.1f", constant),
                                color = AppAccent,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(start = 5.dp, top = 5.dp),
                            )
                        }
                        items(
                            items = charts,
                            key = { "${it.song.id}-${it.chart.difficulty}" },
                        ) { chartEntry ->
                            AchievementChartCard(chartEntry) {
                                onSelectedEntryChange(chartEntry)
                            }
                        }
                    }
                }
            }
        } else {
            val result = state.achievementRates?.takeIf {
                it.songId == entry.song.id && it.difficulty == entry.chart.difficulty
            }
            LazyColumn(
                state = detailListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { AchievementSongHeader(entry) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onSelectedEntryChange(null) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (query.isBlank()) "返回定数表" else "返回搜索结果")
                        }
                        Button(
                            onClick = { onLoadRates(entry.song.id, entry.chart.difficulty) },
                            enabled = !state.isAchievementLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (result == null) "查看达成率" else "刷新数据")
                        }
                    }
                }
                item {
                    AnimatedVisibility(
                        visible = state.isAchievementLoading,
                        enter = fadeIn(appTween(160)) + slideInVertically(appTween(180)) { it / 3 },
                        exit = fadeOut(appTween(120)),
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                item {
                    AnimatedVisibility(
                        visible = result != null && !state.isAchievementLoading,
                        enter = fadeIn(appTween(240)) + slideInVertically(appTween(280)) { it / 8 },
                        exit = fadeOut(appTween(140)),
                    ) {
                        result?.let { response ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                response.mine?.let { mine -> MyAchievementCard(mine, response.total) }
                                Text("统计样本 ${response.total} 份", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
                                    ),
                                    shape = RoundedCornerShape(17.dp),
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(11.dp),
                                    ) {
                                        Text("评级分布", fontWeight = FontWeight.Black, fontSize = 19.sp)
                                        val order = listOf("AP", "FC", "V", "S", "A", "B", "C", "F")
                                        order.mapNotNull { grade -> response.rates.firstOrNull { it.grade == grade } }
                                            .forEach { rateItem ->
                                                val progress = rateItem.rate.toFloat().coerceIn(0f, 1f)
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Text(rateItem.grade, fontWeight = FontWeight.Black, color = AppAccent)
                                                    Text(
                                                        "${String.format(Locale.US, "%.2f", rateItem.rate * 100)}% · ${rateItem.count} 份",
                                                        fontSize = 12.sp,
                                                    )
                                                }
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                    }
                                }
                                if (response.total == 0) {
                                    Text(
                                        "暂无该谱面的统计样本，玩家刷新存档后会逐步补充。",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
private enum class CustomInputMode(val label: String, val preferenceValue: String) {
    MANUAL("手动输入", "manual"),
    CATALOG("一键填充", "catalog");

    companion object {
        fun fromPreference(value: String): CustomInputMode =
            entries.firstOrNull { it.preferenceValue == value } ?: MANUAL
    }
}

@Composable
private fun AchievementChartCard(
    entry: xyz.plcliangpicup.phigrosscore.data.ConstantTableEntry,
    onClick: () -> Unit,
) {
    var useFallback by remember(entry.song.id) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 94.dp)) {
            AsyncImage(
                model = lowArtworkRequest(
                    LocalContext.current, entry.song.id,
                    if (useFallback) achievementFallbackIllustrationUrl(entry.song.id)
                    else achievementIllustrationUrl(entry.song.id),
                ),
                contentDescription = "${entry.song.name} 曲绘",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = { if (!useFallback) useFallback = true },
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.surfaceVariant,
                        .58f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .94f),
                        1f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .12f),
                    ),
                ),
            )
            Column(Modifier.fillMaxWidth(.78f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(entry.song.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.song.composer, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Text(
                    "${entry.chart.difficulty}  ${String.format(Locale.US, "%.1f", entry.chart.chartConstant)}" +
                        entry.chart.noteCount?.let { "  ·  $it Notes" }.orEmpty(),
                    color = AppAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun AchievementSongHeader(entry: xyz.plcliangpicup.phigrosscore.data.ConstantTableEntry) {
    AchievementChartCard(entry, onClick = {})
}

@Composable
private fun MyAchievementCard(
    mine: xyz.plcliangpicup.phigrosscore.data.MyChartAchievement,
    total: Int,
) {
    val exceededPercent = mine.exceededRate.coerceIn(0.0, 1.0) * 100.0
    val topPercent = mine.topRate.coerceIn(0.0, 1.0) * 100.0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .68f)),
        shape = RoundedCornerShape(17.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("我的成绩", fontWeight = FontWeight.Black, fontSize = 19.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format(Locale.US, "%,d", mine.score), color = AppAccent, fontSize = 29.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(achievementGradeDrawable(mine.grade)),
                    contentDescription = mine.grade,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(62.dp),
                )
            }
            Text("超过全部存档", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("${formatNumber(exceededPercent, 1)}%", color = AppAccent, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("当前位于  前 ${formatNumber(topPercent, 1)}%", fontWeight = FontWeight.Bold)
            Text("高于 ${String.format(Locale.US, "%,d", mine.exceededCount)} 份存档（共 ${String.format(Locale.US, "%,d", total)} 份）", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            LinearProgressIndicator(
                progress = { mine.exceededRate.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = AppAccent,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("较低", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Text("较高", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

private fun achievementGradeDrawable(grade: String): Int = when (grade.uppercase()) {
    "AP" -> R.drawable.grade_ap
    "FC" -> R.drawable.grade_fc
    "V" -> R.drawable.grade_v
    "S" -> R.drawable.grade_s
    "A" -> R.drawable.grade_a
    "B" -> R.drawable.grade_b
    "C" -> R.drawable.grade_c
    else -> R.drawable.grade_f
}

private fun achievementIllustrationUrl(songId: String): String =
    "${BuildConfig.API_BASE_URL.trimEnd('/')}/_ill/illLow/${Uri.encode(songId)}.png"

private fun achievementFallbackIllustrationUrl(songId: String): String =
    "https://raw.githubusercontent.com/Catrong/phi-plugin-ill/main/illLow/${Uri.encode(songId)}.png"

@Composable
private fun ScoreImagePickerCard(
    images: List<GeneratedScoreImage>,
    selected: GeneratedScoreImage?,
    onSelect: (GeneratedScoreImage) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { image ->
                    FilterChip(
                        selected = selected?.label == image.label,
                        onClick = { onSelect(image) },
                        label = { Text(image.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)),
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, null, tint = AppAccent, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(9.dp))
                    Text("请先在成绩图页面生成 B30 / P30", fontWeight = FontWeight.Bold)
                }
            } else {
                AsyncImage(
                    model = selected.file,
                    contentDescription = "待发送成绩图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp).padding(8.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun GiveSuggestionPane(
    state: AppUiState,
    generatedImages: List<GeneratedScoreImage>,
    onNext: () -> Unit,
    showSwipeGuide: Boolean,
    onDismissSwipeGuide: () -> Unit,
    onSubmitComment: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
    onDeletePost: (String, (Boolean) -> Unit) -> Unit,
    onDeleteComment: (String, (Boolean) -> Unit) -> Unit,
) {
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var confirmDeletePost by remember { mutableStateOf(false) }
    val post = state.suggestionPost
    if (state.isSuggestionLoading && post == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppAccent)
        }
        return
    }
    if (post == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = onNext) { Text("抽一张成绩图") }
        }
        return
    }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val navigationEdge = with(density) { 40.dp.toPx() }
    val swipeModifier = Modifier.pointerInput(state.isSuggestionLoading, post.id) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val start = down.position
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.position - start
                if (abs(delta.x) >= swipeThreshold && abs(delta.x) > abs(delta.y) * 1.2f) {
                    val swipeRight = delta.x > 0f
                    // Keep the system drawer gesture available from the left edge.
                    if (swipeRight && start.x <= navigationEdge) break
                    change.consume()
                    if (!swipeRight && !state.isSuggestionLoading) {
                        onNext()
                    }
                    break
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().then(swipeModifier)) {
        AnimatedContent(
            targetState = post,
            transitionSpec = {
                (fadeIn(appTween(260)) + slideInVertically(appTween(320)) { it / 10 }) togetherWith
                    (fadeOut(appTween(150)) + slideOutVertically(appTween(220)) { -it / 12 })
            },
            label = "random-suggestion-post",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SuggestionAuthorRow(current.author, Modifier.weight(1f))
                        if (current.canDelete) {
                            IconButton(onClick = { confirmDeletePost = true }) {
                                Icon(Icons.Default.DeleteOutline, "删除帖子")
                            }
                        }
                    }
                }
                if (state.isSuggestionLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    SuggestionRemoteImage(
                        imageUrl = current.imageUrl,
                        contentDescription = "${current.author.nickname} 的成绩图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 260.dp, max = 760.dp)
                            .clickable { previewUrl = current.imageUrl },
                        cornerRadius = 16,
                        decodeSizePx = 1440,
                    )
                }
                item {
                    Text(current.description, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(formatCommunityTime(current.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                if (current.comments.isNotEmpty()) {
                    item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)) }
                    items(current.comments, key = { it.id }) {
                        SuggestionCommentCard(
                            comment = it,
                            submitting = state.isSuggestionSubmitting,
                            onImageClick = { url -> previewUrl = url },
                            onDelete = { done -> onDeleteComment(it.id, done) },
                        )
                    }
                }
                item {
                    CommentComposer(
                        submitting = state.isSuggestionSubmitting,
                        generatedImages = generatedImages,
                        onSubmit = onSubmitComment,
                    )
                }
            }
        }
        if (showSwipeGuide) {
            SuggestionSwipeGuide(
                onDismiss = onDismissSwipeGuide,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
    previewUrl?.let { FullscreenSuggestionImage(it, onDismiss = { previewUrl = null }) }
    if (confirmDeletePost) {
        DeleteConfirmationDialog(
            title = "删除求建议帖子？",
            text = "帖子及其全部评论将不再展示。",
            onDismiss = { confirmDeletePost = false },
            onConfirm = {
                onDeletePost(post.id) { if (it) confirmDeletePost = false }
            },
        )
    }
}

@Composable
private fun SuggestionSwipeGuide(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .95f)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("滑动刷新成绩图", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text("向左滑动：随机刷新一张新的成绩图", color = Color.White, fontSize = 12.sp)
            Text(
                "从屏幕左侧向右滑动仍会唤出导航；帖子不会按固定队列排列。",
                color = Color.White.copy(alpha = .86f),
                fontSize = 11.sp,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("知道了", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SuggestionAuthorRow(author: SuggestionAuthor, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            author.avatar.validAvatarName()?.let { avatar ->
                AsyncImage(
                    model = avatarImageRequest(LocalContext.current, avatar),
                    contentDescription = author.nickname,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(author.nickname, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                challengeModeLabel(author.challengeModeRank)?.let {
                    Text("课题 $it", color = AppAccent, fontSize = 10.sp)
                }
                Text("${formatNumber(author.rks, 4)} RKS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SuggestionRemoteImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int,
    decodeSizePx: Int,
) {
    val context = LocalContext.current
    var loading by remember(imageUrl) { mutableStateOf(true) }
    var originalFallback by remember(imageUrl) { mutableStateOf(false) }
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val displayedUrl = remember(imageUrl, originalFallback) {
        if (originalFallback || !imageUrl.contains("/suggestion-media/")) imageUrl
        else android.net.Uri.parse(imageUrl).buildUpon().appendQueryParameter("preview", "1").build().toString()
    }
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = .12f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .68f), shape),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(displayedUrl)
                .size(decodeSizePx, 8192)
                .memoryCacheKey("suggestion-$decodeSizePx-$displayedUrl")
                .diskCacheKey("suggestion-$displayedUrl")
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onLoading = { loading = true },
            onSuccess = { loading = false; imageFailed = false },
            onError = {
                if (!originalFallback) { originalFallback = true; loading = true }
                else { loading = false; imageFailed = true }
            },
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(loading, enter = fadeIn(appTween(160)), exit = fadeOut(appTween(180))) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .86f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                Text("图片加载中", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (imageFailed) {
            TextButton(onClick = { originalFallback = false; imageFailed = false; loading = true }) {
                Text("加载失败，点击重试")
            }
        }
    }
}

@Composable
private fun SuggestionCommentCard(
    comment: SuggestionComment,
    submitting: Boolean,
    onImageClick: (String) -> Unit,
    onDelete: ((Boolean) -> Unit) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f)),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SuggestionAuthorRow(comment.author, Modifier.weight(1f))
                if (comment.canDelete) {
                    IconButton(onClick = { confirmDelete = true }, enabled = !submitting) {
                        Icon(Icons.Default.DeleteOutline, "删除评论")
                    }
                }
            }
            if (comment.text.isNotBlank()) Text(comment.text, fontSize = 13.sp)
            comment.imageUrl?.takeIf(String::isNotBlank)?.let {
                SuggestionRemoteImage(
                    imageUrl = it,
                    contentDescription = "评论成绩图",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 520.dp)
                        .clickable { onImageClick(it) },
                    cornerRadius = 12,
                    decodeSizePx = 1080,
                )
            }
            Text(formatCommunityTime(comment.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
    if (confirmDelete) {
        DeleteConfirmationDialog(
            title = "删除这条评论？",
            text = "删除后无法恢复。",
            onDismiss = { confirmDelete = false },
            onConfirm = { onDelete { if (it) confirmDelete = false } },
        )
    }
}

@Composable
private fun CommentComposer(
    submitting: Boolean,
    generatedImages: List<GeneratedScoreImage>,
    onSubmit: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val selected = generatedImages.firstOrNull { it.label == selectedLabel }
    LaunchedEffect(generatedImages.map { it.label }) {
        if (selectedLabel !in generatedImages.map { it.label }) {
            selectedLabel = null
        }
    }
    Card(
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 240) text = it },
                placeholder = { Text("写下建议") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            selected?.let {
                AsyncImage(
                    model = it.file,
                    contentDescription = "待发布成绩图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            localError?.let { ErrorText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (generatedImages.isEmpty()) {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Spacer(Modifier.width(5.dp))
                        Text("暂无成绩图")
                    }
                } else {
                    generatedImages.forEach { image ->
                        FilterChip(
                            selected = selected?.label == image.label,
                            onClick = {
                                selectedLabel = image.label.takeUnless { it == selectedLabel }
                                localError = null
                            },
                            label = { Text(image.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Button(
                    enabled = !submitting && (text.isNotBlank() || selected != null),
                    onClick = {
                        scope.launch {
                            runCatching {
                                selected?.let { readGeneratedScoreImage(it) }
                            }.onSuccess { bytes ->
                                onSubmit(text, bytes, bytes?.let { "image/png" }) { success ->
                                    if (success) {
                                        text = ""
                                        selectedLabel = null
                                    }
                                }
                            }.onFailure { localError = it.message }
                        }
                    },
                ) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, "发布建议")
                }
            }
        }
    }
}

@Composable
private fun SuggestionNotificationToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        onEnabledChange(granted)
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("评论通知", fontWeight = FontWeight.Bold)
                Text("有新建议时提醒我", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    permissionDenied = false
                    if (!checked) {
                        onEnabledChange(false)
                    } else if (
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        onEnabledChange(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
    }
    if (permissionDenied) ErrorText("需要通知权限才能提醒新评论")
}

@Composable
private fun OwnSuggestionPostCard(
    post: SuggestionPost,
    submitting: Boolean,
    onOpen: () -> Unit,
    onDelete: ((Boolean) -> Unit) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !submitting, onClick = onOpen),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .56f),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(post.description, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(
                    "${formatCommunityTime(post.createdAt)} · ${post.comments.size} 条评论",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            IconButton(
                onClick = { confirmDelete = true },
                enabled = !submitting,
            ) { Icon(Icons.Default.DeleteOutline, "删除帖子") }
        }
    }
    if (confirmDelete) {
        DeleteConfirmationDialog(
            title = "删除求建议帖子？",
            text = "帖子及其全部评论将不再展示。",
            onDismiss = { confirmDelete = false },
            onConfirm = { onDelete { if (it) confirmDelete = false } },
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun IntegerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    maxValue: Int? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { changed ->
            if (changed.isEmpty() ||
                (changed.matches(Regex("\\d{0,7}")) &&
                    (maxValue == null || changed.toIntOrNull()?.let { it <= maxValue } == true))
            ) onValueChange(changed)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun FullscreenSuggestionImage(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale <= 1f) Offset.Zero else offset + panChange
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .build(),
                contentDescription = "放大查看成绩图",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .transformable(transformableState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Text(
                "双指缩放 · 放大后拖动查看",
                color = Color.White.copy(alpha = .76f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
            ) { Icon(Icons.Default.Close, "关闭", tint = Color.White) }
        }
    }
}

@Composable
private fun RksGuessGameScreen(
    state: AppUiState,
    onStart: (String) -> Unit,
    onRefresh: () -> Unit,
    onSubmitAnswer: (Double) -> Unit,
    onContinueRound: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = state.rksGuessGame
    var selectedMode by remember { mutableStateOf("single") }
    var answerText by rememberSaveable(game?.gameId, game?.round) { mutableStateOf("") }
    var nowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val contentScrollState = rememberScrollState()
    LaunchedEffect(game?.gameId) { if (game == null) onRefresh() }

    LaunchedEffect(game?.gameId, game?.status, game?.deadlineAtEpochMs) {
        while (game?.status == "playing" || game?.status == "round_result" && game?.mode == "public") {
            nowEpochMs = System.currentTimeMillis()
            delay(250)
        }
    }
    LaunchedEffect(game?.gameId, game?.status) {
        if (game?.status == "waiting" || game?.status == "playing" || game?.status == "round_result") {
            while (true) {
                delay(if (game.status == "waiting") 2_000L else 1_200L)
                onRefresh()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        if (game == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(contentScrollState)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "根据两条 B30 成绩线索，估算目标玩家的综合 RKS。",
                    color = AppTextMuted,
                    fontSize = 13.sp,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("single" to "单人模式", "public" to "公开匹配").forEach { (value, label) ->
                        FilterChip(
                            selected = selectedMode == value,
                            onClick = { selectedMode = value },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                AnimatedContent(
                    targetState = selectedMode,
                    transitionSpec = {
                        (fadeIn(appTween(240)) + slideInHorizontally(appTween(320)) { it / 10 }) togetherWith
                            (fadeOut(appTween(150)) + slideOutHorizontally(appTween(240)) { -it / 12 })
                    },
                    label = "rksGuessModeRules",
                ) { mode ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .10f)),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (mode == "single") {
                                    "RKS猜猜乐 – 玩法说明[单人模式]"
                                } else {
                                    "RKS猜猜乐 – 玩法说明[公开匹配]"
                                },
                                color = AppAccent,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                if (mode == "single") {
                                    "游戏开始时，每回合随机给出2条目标玩家的B30成绩，玩家需根据B30数据提交目标玩家的RKS估计值。一轮游戏共5回合，提交的答案与目标玩家的RKS准确值的差值小于0.10即可获胜。\n\n" +
                                        "玩家本人的成绩数据不会进入随机池，每回合提供的B30数据不会重复。"
                                } else {
                                    "2名玩家准备后开始游戏，每回合随机给出2条对方玩家的B30成绩，玩家需根据B30数据提交对方玩家的RKS估计值。一轮游戏共5回合，每回合结束时，提交的答案与对方玩家的RKS准确值差值较小的一方获得1分。命中对方玩家的RKS准确值，或游戏结束时分数较高的一方获胜。\n\n" +
                                        "每回合提供的B30数据不会重复。当第5回合结算满足平局条件时，进入第6回合。"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Button(
                    onClick = { onStart(selectedMode) },
                    enabled = !state.isRksGuessLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isRksGuessLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (selectedMode == "single") "开始单人游戏" else "开始公开匹配")
                }
                RksGuessWinBoard(state, onRefresh)
            }
        } else {
            val remainingMs = (game.deadlineAtEpochMs ?: nowEpochMs) - nowEpochMs
            val remainingSeconds = ((remainingMs + 999L) / 1_000L).coerceIn(0L, 60L)
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(appTween(220)) +
                        slideInVertically(appTween(360)) { -it / 2 } +
                        scaleIn(appTween(360), initialScale = .9f),
                    exit = fadeOut(appTween(140)) +
                        slideOutVertically(appTween(220)) { -it / 3 } +
                        scaleOut(appTween(220), targetScale = .96f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppBackground)
                        .padding(start = 14.dp, top = 12.dp, end = 14.dp),
                ) {
                    RksGuessGameHeader(game, remainingSeconds)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(contentScrollState)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (game.status == "playing") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RksGuessPlayerStatus(
                                label = "${game.myNickname}(你)",
                                value = game.myAnswer?.let { formatNumber(it, 2) } ?: "暂未提交答案",
                                score = game.myScore,
                                modifier = Modifier.weight(1f),
                            )
                            if (game.mode == "public") {
                                RksGuessPlayerStatus(
                                    label = "对方玩家",
                                    value = if (game.opponentSubmitted) "已提交答案" else "暂未提交答案",
                                    score = game.opponentScore,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = game.status to game.round,
                        transitionSpec = {
                            (fadeIn(appTween(240)) + slideInHorizontally(appTween(320)) { it / 7 }) togetherWith
                                (fadeOut(appTween(170)) + slideOutHorizontally(appTween(260)) { -it / 7 })
                        },
                        label = "rksGuessRoundPage",
                    ) { (pageStatus, _) ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (pageStatus) {
                "waiting" -> {
                    RksGuessWaitingCard()
                    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                        Text("取消匹配")
                    }
                }
                "playing" -> {
                    val countdownProgress by animateFloatAsState(
                        targetValue = (remainingMs / 60_000f).coerceIn(0f, 1f),
                        animationSpec = appTween(240),
                        label = "rksGuessCountdown",
                    )
                    RksGuessCountdownBar(countdownProgress, remainingSeconds)
                    RksGuessClueHistory(game)
                    val submitted = game.myAnswer != null
                    RksGuessAnswerCard(
                        game = game,
                        answerText = answerText,
                        onAnswerTextChange = { answerText = it },
                        onSubmitAnswer = onSubmitAnswer,
                        submitted = submitted,
                        remainingSeconds = remainingSeconds,
                        loading = state.isRksGuessLoading,
                    )
                    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                        Text("退出游戏")
                    }
                }
                "round_result" -> {
                    game.lastRound?.let { RksGuessRoundSummary(game, it) }
                    if (game.mode == "public") {
                        val confirmationProgress by animateFloatAsState(
                            targetValue = (remainingMs / 60_000f).coerceIn(0f, 1f),
                            animationSpec = appTween(240),
                            label = "rksGuessConfirmationCountdown",
                        )
                        RksGuessCountdownBar(confirmationProgress, remainingSeconds)
                        Text(
                            "${remainingSeconds}s 内确认",
                            color = if (remainingSeconds <= 10) MaterialTheme.colorScheme.error else AppTextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(
                        onClick = onContinueRound,
                        enabled = !state.isRksGuessLoading && !game.myRoundReady,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (state.isRksGuessLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            when {
                                game.myRoundReady -> "等待对方玩家进入下一回合"
                                game.mode == "single" -> "进入第 ${game.round + 1} 回合"
                                else -> "确认并进入第 ${game.round + 1} 回合"
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (game.mode == "public" && game.opponentRoundReady && !game.myRoundReady) {
                        Text(
                            "对方玩家已准备好进入下一回合",
                            color = AppAccent,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                        Text("退出游戏")
                    }
                }
                "finished" -> {
                    RksGuessFinishedHero(game)
                    if (game.mode == "single") {
                        game.targetRks?.let { Text("本局目标 RKS：${formatNumber(it, 2)}", color = AppAccent) }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            game.myRks?.let { Text("你的 RKS：${formatNumber(it, 2)}", color = AppAccent, modifier = Modifier.weight(1f)) }
                            game.opponentRks?.let { Text("对方玩家 RKS：${formatNumber(it, 2)}", color = AppAccent, modifier = Modifier.weight(1f)) }
                        }
                    }
                    game.lastRound?.let { RksGuessRoundSummary(game, it) }
                    Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("返回小游戏") }
                }
                else -> {
                    Text("游戏状态：${game.status}", color = AppTextMuted)
                    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("返回") }
                }
            }
            }
            }
            }
        }
    }
    }
}

@Composable
private fun RksGuessWaitingCard() {
    val transition = rememberInfiniteTransition(label = "rksGuessWaiting")
    val pulse by transition.animateFloat(
        initialValue = .94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(appTween(900), RepeatMode.Reverse),
        label = "rksGuessWaitingPulse",
    )
    val glow by transition.animateFloat(
        initialValue = .08f,
        targetValue = .2f,
        animationSpec = infiniteRepeatable(appTween(1_100), RepeatMode.Reverse),
        label = "rksGuessWaitingGlow",
    )
    val dots = List(3) { index ->
        transition.animateFloat(
            initialValue = .28f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                appTween(durationMillis = 640, delayMillis = index * 130),
                RepeatMode.Reverse,
            ),
            label = "rksGuessWaitingDot$index",
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .09f + glow)),
        border = BorderStroke(1.dp, AppAccent.copy(alpha = .25f + glow)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(54.dp).graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.fillMaxSize(), color = AppAccent, strokeWidth = 2.dp)
            }
            Text("正在寻找另一位玩家", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                dots.forEach { dot ->
                    Box(
                        Modifier
                            .size(5.dp)
                            .graphicsLayer { alpha = dot.value }
                            .background(AppAccent, CircleShape),
                    )
                }
            }
            Text("匹配成功后会自动开始第 1 回合。", color = AppTextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RksGuessCountdownBar(progress: Float, remainingSeconds: Long) {
    val transition = rememberInfiniteTransition(label = "rksGuessCountdownUrgency")
    val urgentPulse by transition.animateFloat(
        initialValue = .86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(appTween(460), RepeatMode.Reverse),
        label = "rksGuessCountdownUrgencyPulse",
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            if (remainingSeconds in 1..10) {
                alpha = urgentPulse
                scaleY = 1f + (1f - urgentPulse) * .65f
            }
        },
        color = if (remainingSeconds <= 10) MaterialTheme.colorScheme.error else AppAccent,
    )
}

@Composable
private fun RksGuessFinishedHero(game: RksGuessStatus) {
    val title = when (game.winner) {
        game.role -> "你赢了"
        "A", "B" -> "对方玩家获胜"
        "none" -> when {
            game.finishReason?.contains("判负") == true -> "你输了"
            game.mode == "single" -> "挑战失败"
            else -> "本局未分出胜负"
        }
        else -> "本局平局"
    }
    val won = game.winner == game.role
    val resultColor = when {
        won -> AppAccent
        game.winner == "A" || game.winner == "B" || title == "你输了" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    val reveal = remember(game.gameId, game.winner, game.finishReason) { Animatable(0f) }
    LaunchedEffect(game.gameId, game.winner, game.finishReason) {
        reveal.animateTo(1f, appSpring(dampingRatio = .58f, stiffness = 280f))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * 22f
                scaleX = .88f + reveal.value * .12f
                scaleY = .88f + reveal.value * .12f
            },
        colors = CardDefaults.cardColors(containerColor = resultColor.copy(alpha = .09f)),
        border = BorderStroke(1.dp, resultColor.copy(alpha = .5f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                if (won) Icons.Default.EmojiEvents else Icons.Default.Casino,
                null,
                tint = resultColor,
                modifier = Modifier.size(34.dp),
            )
            Text(title, color = resultColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(game.finishReason ?: "游戏结束", color = AppTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            AnimatedContent(
                targetState = game.myScore to game.opponentScore,
                transitionSpec = {
                    (fadeIn(appTween(240)) + scaleIn(appTween(300), initialScale = .8f)) togetherWith
                        (fadeOut(appTween(140)) + scaleOut(appTween(190), targetScale = 1.12f))
                },
                label = "rksGuessFinalScore",
            ) { (myScore, opponentScore) ->
                Text("最终比分：$myScore : $opponentScore", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RksGuessGameHeader(
    game: RksGuessStatus,
    remainingSeconds: Long,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(appTween(220)),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface.copy(alpha = .92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, AppAccent.copy(alpha = .58f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val headerTitle = "第 ${game.round.coerceAtLeast(1).toString().padStart(2, '0')} / " +
                    "${game.totalRounds.coerceAtLeast(1).toString().padStart(2, '0')} 回合"
                AnimatedContent(
                    targetState = headerTitle,
                    transitionSpec = {
                        (fadeIn(appTween(220)) + slideInVertically(appTween(280)) { it / 2 }) togetherWith
                            (fadeOut(appTween(140)) + slideOutVertically(appTween(220)) { -it / 2 })
                    },
                    modifier = Modifier.weight(1f),
                    label = "rksGuessHeaderTitle",
                ) { title ->
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val timerLabel = when {
                    game.status == "waiting" -> "暂未开始"
                    game.status == "round_result" && game.mode == "public" -> "确认 ${remainingSeconds}s"
                    game.status == "round_result" -> "已结算"
                    game.status == "finished" -> "已结束"
                    remainingSeconds > 0 -> "剩余 ${remainingSeconds}s"
                    else -> "结算中"
                }
                AnimatedContent(
                    targetState = timerLabel,
                    transitionSpec = {
                        (fadeIn(appTween(150)) + slideInVertically(appTween(180)) { -it / 2 }) togetherWith
                            (fadeOut(appTween(120)) + slideOutVertically(appTween(160)) { it / 2 })
                    },
                        label = "rksGuessTimer",
                ) { label ->
                    Text(
                        label,
                        color = if (remainingSeconds in 1..10) MaterialTheme.colorScheme.error else AppAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            if (game.status != "waiting") {
                RksGuessRoundProgress(game)
            }
        }
    }
}

@Composable
private fun RksGuessRoundProgress(game: RksGuessStatus) {
    val roundCount = game.totalRounds.coerceIn(1, 6)
    val settledRound = when (game.status) {
        "round_result", "finished" -> game.round
        else -> game.round - 1
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(roundCount) { index ->
            val round = index + 1
            val isCurrent = game.status != "finished" && round == game.round
            val isSettled = round <= settledRound
            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1.35f else 1f,
                animationSpec = appSpring(dampingRatio = .62f, stiffness = 430f),
                label = "rksGuessRoundDotScale",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(
                        color = when {
                            isCurrent -> AppAccent
                            isSettled -> AppAccent.copy(alpha = .58f)
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f)
                        },
                        shape = CircleShape,
                    ),
            )
            if (index < roundCount - 1) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            if (round < settledRound || round == settledRound) AppAccent.copy(alpha = .36f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .56f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun RksGuessPlayerStatus(label: String, value: String?, score: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = AppTextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AnimatedContent(
            targetState = value ?: "暂未提交答案",
            transitionSpec = {
                (fadeIn(appTween(210)) + slideInVertically(appTween(260)) { it / 2 }) togetherWith
                    (fadeOut(appTween(130)) + slideOutVertically(appTween(210)) { -it / 2 })
            },
            label = "rksGuessPlayerAnswer",
        ) { answer ->
            Text(answer, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
        AnimatedContent(
            targetState = score,
            transitionSpec = {
                (fadeIn(appTween(220)) + slideInVertically(appTween(280)) { it }) togetherWith
                    (fadeOut(appTween(130)) + slideOutVertically(appTween(210)) { -it })
            },
            label = "rksGuessScore",
        ) { displayedScore ->
            Text("得分 $displayedScore", color = AppAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RksGuessClueHistory(game: RksGuessStatus) {
    val entries = game.clueHistory.sortedBy { it.round }.flatMap { group ->
        group.clues.mapIndexed { index, clue -> Triple(group.round, index, clue) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("线索记录", color = AppAccent, fontWeight = FontWeight.Black)
        if (entries.isEmpty()) {
            Text("正在准备本回合线索…", color = AppTextMuted, fontSize = 12.sp)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(360.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                itemsIndexed(entries, key = { flatIndex, item -> "${item.first}-${item.second}-${item.third.slot}-$flatIndex" }) { _, item ->
                    RksGuessClueCard(item.first, item.second + 1, item.third)
                }
            }
        }
    }
}

@Composable
private fun RksGuessClueCard(round: Int, clueIndex: Int, clue: RksGuessClue) {
    val reveal = remember(round, clueIndex, clue.slot) { Animatable(0f) }
    val imageScale = remember(round, clueIndex, clue.slot) { Animatable(1f) }
    LaunchedEffect(round, clueIndex, clue.slot) {
        delay((clueIndex - 1).coerceAtLeast(0) * 110L)
        reveal.animateTo(1f, appTween(360))
    }
    LaunchedEffect(round, clueIndex, clue.slot) {
        delay((clueIndex - 1).coerceAtLeast(0) * 110L)
        imageScale.animateTo(1.035f, appTween(1_100))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * 24f
                scaleX = .985f + reveal.value * .015f
                scaleY = .985f + reveal.value * .015f
            }
            .animateContentSize(appTween(220)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = lowArtworkRequest(LocalContext.current, clue.songId, achievementIllustrationUrl(clue.songId)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = imageScale.value
                    scaleY = imageScale.value
                },
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to AppSurface,
                        .58f to AppSurface.copy(alpha = .96f),
                        1f to AppSurface.copy(alpha = .28f),
                    ),
                ),
            )
            Column(
                Modifier.fillMaxSize().padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("第 ${round} 回合 · 线索 #${clueIndex.toString().padStart(2, '0')}", color = AppTextMuted, fontSize = 10.sp)
                Text(
                    "${clue.slot} - ${clue.songName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(.78f),
                )
                Text("[${clue.difficulty}]", color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(String.format(Locale.US, "%07d", clue.score), fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("${formatNumber(clue.accuracy, 2)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(
                    "定数 ${clue.chartConstant?.let { formatNumber(it, 2) } ?: "--"}  →  单曲 RKS ${formatNumber(clue.rks, 2)}",
                    color = AppAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun RksGuessAnswerCard(
    game: RksGuessStatus,
    answerText: String,
    onAnswerTextChange: (String) -> Unit,
    onSubmitAnswer: (Double) -> Unit,
    submitted: Boolean,
    remainingSeconds: Long,
    loading: Boolean,
) {
    val submitScale = remember(game.gameId, game.round) { Animatable(1f) }
    LaunchedEffect(submitted) {
        if (submitted) {
            submitScale.snapTo(.975f)
            submitScale.animateTo(1f, appSpring(dampingRatio = .5f, stiffness = 520f))
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = submitScale.value
                scaleY = submitScale.value
            }
            .animateContentSize(appTween(220)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (submitted) AppAccent.copy(alpha = .075f) else AppSurface.copy(alpha = .94f),
        ),
        border = BorderStroke(1.dp, AppAccent.copy(alpha = if (submitted) .78f else .45f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("答案提交", color = AppAccent, fontWeight = FontWeight.Black)
            Text(
                if (game.mode == "single") "匿名目标玩家的 RKS 估计值（精确到小数点后 2 位）"
                else "对方玩家的 RKS 估计值（精确到小数点后 2 位）",
                color = AppTextMuted,
                fontSize = 11.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { value ->
                        val normalized = value.replace(',', '.')
                        val validShape = normalized.isEmpty() || normalized.matches(Regex("\\d{0,2}([.]\\d{0,2})?"))
                        val validRange = normalized.toDoubleOrNull()?.let { it <= 30.0 } ?: true
                        if (validShape && validRange) onAnswerTextChange(normalized)
                    },
                    singleLine = true,
                    enabled = !submitted && !loading && remainingSeconds > 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { answerText.toDoubleOrNull()?.let(onSubmitAnswer) },
                    enabled = !submitted && !loading && remainingSeconds > 0 && answerText.toDoubleOrNull() != null,
                    modifier = Modifier.width(106.dp).height(56.dp),
                ) {
                    AnimatedContent(
                        targetState = when {
                            loading -> "loading"
                            submitted -> "submitted"
                            else -> "ready"
                        },
                        transitionSpec = {
                            (fadeIn(appTween(180)) + scaleIn(appTween(220), initialScale = .86f)) togetherWith
                                (fadeOut(appTween(120)) + scaleOut(appTween(160), targetScale = .9f))
                        },
                        label = "rksGuessSubmitButton",
                    ) { buttonState ->
                        when (buttonState) {
                            "loading" -> CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                            "submitted" -> Icon(Icons.Default.CheckCircle, "已提交", Modifier.size(19.dp))
                            else -> Text("提交答案", fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = submitted || game.mode == "public" && game.opponentSubmitted,
                enter = fadeIn(appTween(200)) + slideInVertically(appTween(240)) { it / 3 },
                exit = fadeOut(appTween(140)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (submitted) {
                        Icon(Icons.Default.CheckCircle, null, tint = AppAccent, modifier = Modifier.size(15.dp))
                    }
                    Text(
                        if (submitted) {
                            "你的答案：${formatNumber(game.myAnswer ?: 0.0, 2)} · 等待对方或回合结算"
                        } else {
                            "对方玩家已提交答案；具体数值将在回合结算后公开。"
                        },
                        color = if (submitted) AppTextMuted else AppAccent,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RksGuessRoundSummary(game: RksGuessStatus, result: xyz.plcliangpicup.phigrosscore.data.RksGuessRoundResult) {
    val reveal = remember(game.gameId, result.round, result.outcome) { Animatable(0f) }
    LaunchedEffect(game.gameId, result.round, result.outcome) {
        reveal.animateTo(1f, appSpring(dampingRatio = .72f, stiffness = 330f))
    }
    val resultAccent = when (result.outcome) {
        "win" -> AppAccent
        "lose" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * 18f
                scaleX = .96f + reveal.value * .04f
                scaleY = .96f + reveal.value * .04f
            }
            .animateContentSize(appTween(260)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = resultAccent.copy(alpha = .075f)),
        border = BorderStroke(1.dp, resultAccent.copy(alpha = .46f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(
                    if (result.outcome == "win") Icons.Default.CheckCircle else Icons.Default.Casino,
                    null,
                    tint = resultAccent,
                    modifier = Modifier.size(18.dp),
                )
                Text("第 ${result.round} 回合结果", color = resultAccent, fontWeight = FontWeight.Black)
            }
            if (game.mode == "single") {
                Text(
                    if (result.outcome == "win") "本回合命中：估算结果在胜利范围内" else "本回合未命中",
                    fontSize = 12.sp,
                )
            } else {
                Text(
                    when (result.outcome) {
                        "win" -> "本回合你获胜"
                        "lose" -> "本回合对方玩家获胜"
                        else -> "本回合双方各得 1 分"
                    },
                    fontSize = 12.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("答案公布", color = resultAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${game.myNickname}(你)：${result.myAnswer?.let { formatNumber(it, 2) } ?: "未提交"}",
                    color = AppTextMuted,
                    fontSize = 11.sp,
                )
                if (game.mode == "public") {
                    Text(
                        "对方玩家：${result.opponentAnswer?.let { formatNumber(it, 2) } ?: "未提交"}",
                        color = AppTextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private suspend fun readGeneratedScoreImage(image: GeneratedScoreImage): ByteArray = withContext(Dispatchers.IO) {
    require(image.file.exists() && image.file.isFile) { "APP 内生成的成绩图已失效，请重新生成" }
    val bytes = image.file.readBytes()
    require(bytes.isNotEmpty()) { "APP 内生成的成绩图为空" }
    require(bytes.size <= 8 * 1024 * 1024) { "成绩图不能超过 8 MB" }
    require(bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }) {
        "APP 内生成的成绩图格式无效"
    }
    bytes
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

private fun formatNumber(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

internal fun formatCommunityTime(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        val instant = runCatching { OffsetDateTime.parse(value).toInstant() }
            .getOrElse { Instant.parse(value) }
        COMMUNITY_TIME_FORMAT.format(instant.atZone(zoneId))
    }.getOrElse {
        value.replace('T', ' ').substringBefore('.').removeSuffix("Z").take(16)
    }

private val COMMUNITY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
