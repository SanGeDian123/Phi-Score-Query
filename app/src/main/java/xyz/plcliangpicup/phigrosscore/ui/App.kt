@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package xyz.plcliangpicup.phigrosscore.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.imageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.BuildConfig
import xyz.plcliangpicup.phigrosscore.R
import xyz.plcliangpicup.phigrosscore.data.AppAnnouncement
import xyz.plcliangpicup.phigrosscore.data.B30Item
import xyz.plcliangpicup.phigrosscore.data.B30ImageStyle
import xyz.plcliangpicup.phigrosscore.data.B30Snapshot
import xyz.plcliangpicup.phigrosscore.data.AppUpdateManifest
import xyz.plcliangpicup.phigrosscore.data.ConstantTableEntry
import xyz.plcliangpicup.phigrosscore.data.constantTableLevels
import xyz.plcliangpicup.phigrosscore.data.FeedbackNotificationManager
import xyz.plcliangpicup.phigrosscore.data.GradeCounts
import xyz.plcliangpicup.phigrosscore.data.LoginProgress
import xyz.plcliangpicup.phigrosscore.data.LeaderboardEntry
import xyz.plcliangpicup.phigrosscore.data.LeaderboardMe
import xyz.plcliangpicup.phigrosscore.data.LeaderboardSnapshot
import xyz.plcliangpicup.phigrosscore.data.PLAYER_LEADERBOARD_LIMIT
import xyz.plcliangpicup.phigrosscore.data.PlayerProfile
import androidx.lifecycle.repeatOnLifecycle
import xyz.plcliangpicup.phigrosscore.data.PracticeCharts
import xyz.plcliangpicup.phigrosscore.data.RksCalculatorDraft
import xyz.plcliangpicup.phigrosscore.data.ScoreSnapshotEntry
import xyz.plcliangpicup.phigrosscore.data.SongDifficultyScore
import xyz.plcliangpicup.phigrosscore.data.SongChartInfo
import xyz.plcliangpicup.phigrosscore.data.SongInfo
import xyz.plcliangpicup.phigrosscore.data.SongScoreResult
import xyz.plcliangpicup.phigrosscore.data.SongScoreImageStyle
import xyz.plcliangpicup.phigrosscore.data.SuggestionNotificationManager
import xyz.plcliangpicup.phigrosscore.data.calculateP30Rks
import xyz.plcliangpicup.phigrosscore.data.selectBestCharts
import xyz.plcliangpicup.phigrosscore.data.selectPerfectCharts
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class NavItem(val page: AppPage, val title: String, val icon: ImageVector)

private data class ChangelogEntry(
    val version: String,
    val label: String,
    val changes: List<String>,
)

private sealed interface ConstantTableRow {
    val key: String

    data class LevelHeader(val level: Int) : ConstantTableRow {
        override val key: String = "level-$level"
    }

    data class ConstantHeader(val constant: Double) : ConstantTableRow {
        override val key: String = "constant-${"%.1f".format(Locale.US, constant)}"
    }

    data class Chart(val entry: ConstantTableEntry) : ConstantTableRow {
        override val key: String = "chart-${entry.song.id}-${entry.chart.difficulty}"
    }
}

private val navItems = listOf(
    NavItem(AppPage.HOME, "概览", Icons.Default.Home),
    NavItem(AppPage.B30, "B30", Icons.Default.BarChart),
    NavItem(AppPage.SONG, "单曲", Icons.Default.Search),
    NavItem(AppPage.CONSTANT_TABLE, "定数表", Icons.Default.FormatListNumbered),
    NavItem(AppPage.LEADERBOARD, "排行榜", Icons.Default.Leaderboard),
    NavItem(AppPage.IMAGE, "图片", Icons.Default.Image),
    NavItem(AppPage.MORE, "更多", Icons.Default.MoreHoriz),
    NavItem(AppPage.SETTINGS, "设置", Icons.Default.Settings),
)

private val primaryNavItems = navItems.filter { it.page in setOf(AppPage.HOME, AppPage.B30, AppPage.SONG, AppPage.MORE) }

private const val PROJECT_REPOSITORY_URL = "https://github.com/SanGeDian123/Phi-Score-Query"
private const val BACKEND_REPOSITORY_URL = "https://github.com/Sczr0/Next-Phi-Backend"
private const val EXPERIENCE_SURVEY_URL = "https://wj.qq.com/s2/27522729/6kti/"

private val changelogEntries = listOf(
    ChangelogEntry(
        "Pre-0.9.7.12",
        "",
        listOf(
            "还原了部分谱面中的“噪域”；",
            "修复了“谱面播放与练习”功能的部分已知问题；",
            "将课题模式等级“黄”更改为“金”；",
            "玩家排行榜展示数量提升至前1,500名。",
            "自定义BP30功能现支持导入已有BP30。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.11-Fix",
        "定数表与新曲信息修复",
        listOf(
            "定数表新增 18 级入口，修复 18.0 及以上谱面未显示的问题，等级入口随曲库自动扩展。",
            "补充本次七首新曲的章节信息，统一归入 Chapter 9。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.11",
        "",
        listOf(
            "新增测试功能“谱面播放与练习”，可在支持该功能的单曲页面进入；",
            "新增功能“保存曲绘”，可在单曲页面点击最上方曲绘进入；",
            "优化了使用体验。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.10-Fix2",
        "",
        listOf(
            "补全了新曲目的完整信息；",
            "优化使用体验。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.10-Fix",
        "曲目名称显示修复",
        listOf(
            "修复存档更新详情中曲目名显示为曲目 ID 的问题。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.10",
        "自定义 BP30",
        listOf(
            "新增自定义 BP30 功能。",
            "优化了使用体验。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.9",
        "轻量界面与功能更新",
        listOf(
            "重构导航、概览、成绩、更多与设置页面，统一字体层级、留白和分组样式，白日主题保持蓝色。",
            "统一放慢页面、展开、主题与按压动效，采用更柔和的回弹；首页 P30 RKS 与课题等级统一字体。",
            "更多页新增“小游戏”分类与“RKS 猜猜乐”，支持单人模式和公开匹配。",
            "猜曲绘和开字母入口暂未开放，点击后会显示提示。",
            "修复“给建议”中所有图片放大后无法拖动查看的问题。",
            "更多页新增“谱面评级达成率”，可按定数选择谱面或使用曲目别名搜索，查看评级分布与我的成绩百分位。",
            "RKS 计算器新增“分数/ACC 计算”，支持按判定与 Max Combo 计算分数和 ACC。",
            "优化了使用体验。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.8-Fix",
        "启动修复",
        listOf(
            "修复 Pre-0.9.7.8 Release 安装后打开即崩溃的问题。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.8",
        "RKS 工具与建议区",
        listOf(
            "更多页新增 RKS 计算器，提升估算会读取账号真实 B27 与 AP3 并按替换线重新计算。",
            "更多页主体色跟随 APP 主题，浅色模式使用蓝色，深色模式使用青色。",
            "RKS 计算器输入保存在 APP 内，退出页面或重启后仍会恢复。",
            "新增求建议/给建议，可发布成绩图、随机浏览并评论或附上自己的成绩图。",
            "建议区修复空图片轮廓与发布时间时区，并支持作者删除帖子或评论。",
            "求建议可开启新评论通知，通知可直达帖子；帖子与评论成绩图支持点击放大。",
            "建议区只能选择 APP 内生成的 B30/P30 成绩图，不再调用系统相册。",
            "Phi-Plugin B30/P30 的 P1-P3 卡片光晕改为金色。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.7",
        "筛选与成绩图体验优化",
        listOf(
            "修复部分设备左侧导航栏无法上下滑动、较下方入口无法点击的问题。",
            "B30 与 P30 新增 Phi-Plugin 成绩图样式，样式选择改为下拉菜单。",
            "定数表新增 EZ、HD、IN、AT 难度多选筛选，并与定数等级筛选联动。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.6",
        "P30 成绩图与体验优化",
        listOf(
            "从定数表进入单曲详情后，返回时会保留原来的位置。",
            "优化 B30、P30 和单曲成绩图的生成速度。",
            "图片页新增 P30 成绩图，可横向滑动切换 B30 和 P30。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.5",
        "网络重试与体验问卷",
        listOf(
            "B30 图片、单曲图片、排行榜及其他联网场景新增无响应超时检测与自动重试，避免请求长时间无结果。",
            "APP 首次打开会提示体验问卷，设置页新增可随时打开的腾讯问卷入口。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.4",
        "单曲成绩图与动画优化",
        listOf(
            "新增并默认启用全新单曲成绩图样式，原样式更名为 Legacy 且可在设置页切换，升级后的旧图片缓存会自动失效。",
            "左侧导航新增“更多”页面，并以合适的 16:9 比例展示页面装修中的占位内容。",
            "B30、Best N 与 P30 展开后的谱面成绩统一使用定数表同款自上而下依次展开动画。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.3",
        "单曲成绩图与体验优化",
        listOf(
            "单曲详情新增单曲成绩图，现统一使用 APP 字体并将整体布局、分栏对齐、文字自适应及背景层次严格按设计图优化。",
            "单曲页返回会回到上一层，二维码登录优先在本地完成，B30 与单曲生图减少重复请求并保留已有图片。",
            "关于页新增作者板块并将开源信息放在其下方。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.2",
        "排行榜体验优化",
        listOf(
            "排行榜最多显示前 1000 名公开玩家，并固定排名序号为单行显示。",
            "排行榜滚动后可通过顶部向上箭头回到开头，点击当前玩家信息栏可跳转到本人排名。",
            "更新日志和新版本弹窗中的本版本功能均统一用一句话概述。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.1",
        "公开测试",
        listOf(
            "项目进入公开测试阶段，公开 Android 客户端、服务器部署工具和实际使用的后端修改源码。",
            "设置页新增“关于”页面，可分别访问 Phi-Score-Query 与 Next-Phi-Backend 的 GitHub 仓库。",
            "补充 Apache-2.0、AGPL v3、第三方声明与后端对应源码入口。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.0-Fix2",
        "曲目信息补全",
        listOf(
            "补全《星拂云锦 feat. koi》的章节信息：Single。",
            "补全 EZ、HD、IN 谱师：华星秋月、帷畔托星、灵琶弄月。",
            "补全 EZ、HD、IN 谱面物量：227、600、1235。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.0-Fix",
        "曲库发布修复",
        listOf(
            "修复 Pre-0.9.7.0 服务器发布包遗漏运行时曲库文件，导致 APP 同步后仍只有旧曲目的问题。",
            "内置曲库和服务器曲库更新至 312 首，新增《星拂云锦 feat. koi》及其完整定数信息。",
            "服务器部署会同步更新 info.csv、difficulty.csv、nicklist.yaml 和新曲曲绘，并在发布前验证公网曲库数量。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.7.0",
        "服务器曲库同步",
        listOf(
            "曲库改为由服务器统一提供，后端更新曲库后 APP 会在启动时自动同步。",
            "服务器曲库会保存在本地，网络不可用时自动使用上次同步结果或 APK 内置曲库。",
            "定数表、单曲搜索和曲目详情统一使用同步后的曲库，新曲无需先产生游玩记录即可显示。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.9-Fix",
        "曲库同步前修复",
        listOf(
            "修复 B30 成绩图底部版本水印固定停留在 Pre-0.9.6.8 的问题。",
            "生图时会把当前 APP 版本安全传递给服务器，并按版本隔离图片缓存，水印会随 APP 更新自动变化。",
            "主页 Ranking Score 与 P30 Ranking Score 统一字号，并放大 Challenge Mode 中间的白色等级数字。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.9",
        "定数表",
        listOf(
            "侧边导航新增定数表，默认按 17 级至 1 级展示全部谱面，并可直接筛选指定整数等级。",
            "同一等级内按精确定数从高到低排列，显示完整曲名、谱面难度和一位小数定数。",
            "定数表曲绘沿用单曲页面的横向渐变样式，点击任意谱面可直接进入对应曲目详情。",
            "定数表首次显示和切换等级时增加自上而下依次展开的流畅动画。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.8",
        "主题与成绩图",
        listOf(
            "统一主题色：白日模式使用深蓝色，黑夜模式使用绿色，更新存档按钮与全局强调色保持一致。",
            "成绩概览顶部改为列表式玩家信息栏，展示头像、玩家名、RKS、P30 RKS、课题模式背景板和存档更新时间。",
            "新增简约 B30 成绩图，可在设置中与经典样式切换；成绩图统一显示 P3+B27 完整信息。",
            "B30 成绩图水印改为 Phi Score Query 与当前 APP 版本。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.7",
        "成绩更新显示",
        listOf(
            "成绩更新信息改为按实际变化字段显示：仅 ACC 变化只显示 ACC，仅分数变化只显示分数，两者均变化时同时显示。",
            "已核对 Next-Phi-Backend 最新接口；当前后端未提供最近一次实际游玩详情，因此继续使用两次存档差分。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.6",
        "成绩图交互与生成优化",
        listOf(
            "修复 ACC 单独提升时把历史最高分误显示为本次游玩分数的问题。",
            "概览页 B30 成绩图支持点击跳转，图片页大图支持双指缩放和拖动查看。",
            "B30 图片生成时显示当前用时，重新生成前会先删除旧图片。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.5",
        "排行榜与生图提示",
        listOf(
            "排行榜不再显示头像资源名称，仅保留玩家昵称、头像、课题等级和 RKS。",
            "B30 图片页面的预计生图时间由约 1 分钟调整为约 30 秒。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.4",
        "排行榜头像与 B30 图片",
        listOf(
            "修复排行榜详细排名中的玩家头像，并过滤后端历史记录中的异常头像值。",
            "任意页面首次按下返回键先回到成绩概览并提示，2 秒内再次按下才退出 APP。",
            "修复更新日志中 Pre-0.9.6.2 被错误标记为“当前版本”的问题。",
            "首次登录成功后自动在后台生成一次 B30 成绩图，并显示在成绩概览底部。",
            "B30 图片保存改为直接写入系统相册，不再要求选择保存位置。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.3",
        "图片与导航优化",
        listOf(
            "统一曲绘与头像的内存、磁盘缓存和预加载策略，减少重复下载与图片解码等待。",
            "左侧导航箭头支持上下拖动，并可在设置中选择显示或隐藏。",
            "课题模式等级改用紧凑单字颜色标记，例如绿12、金21、红49、彩51。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.2",
        "曲目详情与排行榜",
        listOf(
            "单曲成绩支持进入曲目详情，展示完整曲绘、章节、谱师、定数与物量。",
            "新增玩家 RKS 排行榜，展示昵称、游戏头像与课题模式等级。",
            "下方导航改为全局侧边滑出导航，并加入首次使用引导。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.1-Fix",
        "排行与定数修复",
        listOf(
            "还原 B30 的 P3 与 Best 27 分类，并为 B30、Best N 补充推分目标。",
            "概览成绩更新卡片改用右侧曲绘与界面色渐隐样式。",
            "使用内置歌曲定数表回退补齐后端偶发缺失的定数信息。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6.1",
        "别名与排行扩展",
        listOf(
            "单曲成绩接入 Next-Phi-Backend 别名搜索，并在曲目信息右侧加入渐隐曲绘。",
            "B30 与 P30 补充第 28 至 30 名，并以 OVER FLOW 分割线区分。",
            "成绩排行支持一键回到当前板块顶部，所有曲目名称均完整换行显示。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6-Fix",
        "应用内更新",
        listOf(
            "新增应用内联网更新：启动时自动检查、展示更新内容并安全下载安装包。",
            "设置页新增自动检查开关与手动检查更新入口。",
            "安装前校验安装包的 SHA-256、包名、版本号与签名。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.6",
        "图标与成绩页",
        listOf(
            "更换应用图标，并针对 Android 启动器的不同图标形状完成适配。",
            "软件首次打开时默认使用白日风格。",
            "B30 页面更名为“成绩一览”，Best N 描述始终按填写的 N 展示。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.5-Fix",
        "P30 口径修复",
        listOf(
            "修正 P30 综合 RKS 为 P3 + B27 口径，最高 3 张 AP 谱面会重复计入一次。",
            "精简 P30 综合 RKS 卡片，仅保留标题与数值。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.5",
        "P30 综合 RKS",
        listOf(
            "P30 板块新增综合 RKS，按最高 30 张 All Perfect 谱面的 RKS 计算。",
            "综合 RKS 采用 30 个固定槽位，并在展开后的 P30 列表顶部显示。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.4",
        "体验优化",
        listOf(
            "优化概览、成绩统计、图片与设置页面的文本和操作布局。",
            "成绩统计及 B30、Best N、P30 默认收起，B30 改为先显示 AP 3。",
            "新增启动时自动更新开关，以及经过风险确认的 SessionToken 查看功能。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.3",
        "排行扩展",
        listOf(
            "B30 页面新增可自定义数量的 Best N 与 All Perfect P30。",
            "Best N、B30、P30 三个板块相互独立，均可单独收起或展开。",
            "修复黑夜风格下登录首页标题仍显示为黑色的问题。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.2",
        "风格与统计",
        listOf(
            "成绩统计支持 Clear、Full Combo 与 All Perfect，并可折叠查看。",
            "新增白日与黑夜风格，完善 B30 图片保存、分享和生成时间提示。",
            "更新成绩补充谱面定数，Ranking Score 变化时加入跳动动画。",
            "精简概览与设置页信息，并统一单曲推分提示。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.1",
        "存档更新",
        listOf(
            "新增存档更新对比，在概览中展示本次变化的曲绘、难度、得分、ACC 与 RKS。",
            "设置页新增完整更新日志，并补充查分服务说明。",
            "优化刷新结果、更新卡片和弹层的过渡动画。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.9.0",
        "功能预览",
        listOf(
            "新增单曲成绩搜索，可按曲名、曲师或曲目 ID 查询。",
            "新增 B30 图片生成、重新生成与系统分享。",
            "完善成绩缓存、离线状态提示与缓存管理。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.8.0",
        "界面重构",
        listOf(
            "加入 B30 完整列表、成绩统计和 Ranking Score 概览。",
            "手机使用底部导航，平板及横屏设备自动切换侧边导航。",
            "统一深色主题、难度配色和页面切换动画。",
        ),
    ),
    ChangelogEntry(
        "Pre-0.7.0",
        "安全登录",
        listOf(
            "加入 TapTap 扫码登录与 SessionToken 登录。",
            "短期会话使用 Android Keystore 加密保存，并支持自动续期。",
        ),
    ),
    ChangelogEntry(
        "0.1.0debug",
        "初始调试版本",
        listOf(
            "完成 Android 客户端原型与 Next-Phi-Backend 基础接入。",
            "实现存档读取、基础 RKS 计算和调试版成绩展示。",
        ),
    ),
)

@Composable
fun PhigrosScoreApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val appScope = rememberCoroutineScope()
    var lastHomeBackPressAt by rememberSaveable { mutableStateOf(0L) }
    var pendingUpdateInstall by remember { mutableStateOf<File?>(null) }
    var showExperienceSurvey by rememberSaveable { mutableStateOf(false) }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingUpdateInstall?.takeIf(File::exists)?.let { file ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
                launchApkInstaller(context, file)
                pendingUpdateInstall = null
            }
        }
    }
    val installUpdate: (File) -> Unit = { file ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            pendingUpdateInstall = file
            unknownSourcesLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } else {
            launchApkInstaller(context, file)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    val prefetchArtworkIds = remember(state.snapshot?.updatedScores, state.songResults) {
        buildList {
            state.snapshot?.updatedScores?.forEach { add(it.songId) }
            state.songResults.forEach { add(it.songId) }
        }.distinct().take(10)
    }
    LaunchedEffect(prefetchArtworkIds) {
        prefetchArtworkIds.forEach { songId ->
            context.imageLoader.enqueue(
                lowArtworkRequest(context, songId, illustrationUrl(songId)),
            )
        }
    }
    BackHandler(enabled = state.isLoggedIn) {
        if (state.page != AppPage.HOME) {
            lastHomeBackPressAt = SystemClock.elapsedRealtime()
            viewModel.selectPage(AppPage.HOME)
            appScope.launch {
                snackbar.showSnackbar(
                    message = "再按一次退出 APP",
                    duration = SnackbarDuration.Short,
                )
            }
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHomeBackPressAt <= 2_000L) {
                (context as? Activity)?.finish()
            } else {
                lastHomeBackPressAt = now
                appScope.launch {
                    snackbar.showSnackbar(
                        message = "再按一次退出 APP",
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        if (!state.isLoggedIn) {
            LoginScreen(
                progress = state.loginProgress,
                onTokenLogin = viewModel::loginWithToken,
                onQrLogin = viewModel::startQrLogin,
                onCancelQr = viewModel::cancelQrLogin,
            )
        } else {
            MainShell(
                state = state,
                snackbar = snackbar,
                onPage = viewModel::selectPage,
                onRefresh = { viewModel.refreshB30() },
                onRefreshLeaderboard = viewModel::refreshLeaderboard,
                onLoadRandomSuggestion = viewModel::loadRandomSuggestion,
                onLoadOwnSuggestionPosts = viewModel::loadOwnSuggestionPosts,
                onOpenSuggestionPost = viewModel::openSuggestionPost,
                onRksCalculatorDraftChange = viewModel::setRksCalculatorDraft,
                onSubmitSuggestionPost = viewModel::submitSuggestionPost,
                onSubmitSuggestionComment = viewModel::submitSuggestionComment,
                onDeleteSuggestionPost = viewModel::deleteSuggestionPost,
                onDeleteSuggestionComment = viewModel::deleteSuggestionComment,
                onSuggestionNotificationsChange = viewModel::setSuggestionNotificationsEnabled,
                 onSearchAchievementSongs = viewModel::searchAchievementSongs,
                  onLoadAchievementRates = viewModel::loadChartAchievementRates,
                  onGenerateCustomRankingImage = viewModel::generateCustomRankingImage,
                  onImportCustomRanking = viewModel::importCustomRanking,
                  onClearCustomRanking = viewModel::clearCustomRanking,
                  onCheckin = viewModel::loadCheckin,
                  onCheckinRanks = viewModel::loadCheckinRanks,
                  onStartRksGuess = viewModel::startRksGuess,
                  onRefreshRksGuess = viewModel::refreshRksGuess,
                  onSubmitRksGuessAnswer = viewModel::submitRksGuessAnswer,
                  onContinueRksGuessRound = viewModel::continueRksGuessRound,
                  onLeaveRksGuessGame = viewModel::leaveRksGuessGame,
                  onSearchSong = viewModel::searchSong,
                onOpenConstantSong = viewModel::constantSongDetail,
                onEnsureSongImage = viewModel::ensureSongImage,
                onGenerateSongImage = viewModel::generateSongImage,
                onGenerateImage = viewModel::generateImage,
                onGenerateP30Image = viewModel::generateP30Image,
                onDismissImagePagerGuide = viewModel::dismissImagePagerGuide,
                onDismissSuggestionSwipeGuide = viewModel::dismissSuggestionSwipeGuide,
                onClearCache = viewModel::clearCache,
                onThemeChange = viewModel::setDarkTheme,
                onAutoRefreshChange = viewModel::setAutoRefreshOnLaunch,
                onAutoUpdateChange = viewModel::setAutoCheckAppUpdates,
                onNavigationHandleVisibilityChange = viewModel::setShowNavigationHandle,
                onSwipeNavigationChange = viewModel::setUseSwipeNavigation,
                onNavigationHandlePositionChange = viewModel::setNavigationHandlePosition,
                onB30ImageStyleChange = viewModel::setB30ImageStyle,
                onSongScoreImageStyleChange = viewModel::setSongScoreImageStyle,
                onOpenSurvey = { showExperienceSurvey = true },
                onCheckUpdate = { viewModel.checkAppUpdate(silent = false) },
                onRefreshAnnouncements = viewModel::refreshAnnouncementHistory,
                onRevealSessionToken = viewModel::revealSessionToken,
                onHideSessionToken = viewModel::hideSessionToken,
                onLogout = viewModel::logout,
                onDismissNavigationGuide = viewModel::dismissNavigationGuide,
            )
        }
        if (!state.isLoggedIn) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        state.availableAppUpdate?.takeIf { state.announcement == null }?.let { update ->
            AppUpdateDialog(
                update = update,
                state = state,
                onDismiss = viewModel::dismissAppUpdate,
                onDownload = viewModel::downloadAppUpdate,
                onInstall = installUpdate,
            )
        }
        ExperienceSurveyPrompt(
            visible = state.isLoggedIn &&
                state.page == AppPage.HOME &&
                !state.showNavigationGuide &&
                state.announcement == null &&
                state.showExperienceSurveyPrompt,
            onDismiss = viewModel::dismissExperienceSurveyPrompt,
            onConfirm = {
                viewModel.dismissExperienceSurveyPrompt()
                showExperienceSurvey = true
            },
        )
        AnnouncementPrompt(
            announcement = state.announcement,
            onDismiss = viewModel::dismissAnnouncement,
        )
        if (state.unknownTrackNoticeIds.isNotEmpty() &&
            state.announcement == null &&
            state.availableAppUpdate == null &&
            !state.showNavigationGuide &&
            !state.showExperienceSurveyPrompt &&
            !showExperienceSurvey
        ) {
            UnknownTrackNoticeDialog(
                songIds = state.unknownTrackNoticeIds,
                onDismiss = viewModel::dismissUnknownTrackNotice,
            )
        }
        FeedbackHost(viewModel.repository, state.isLoggedIn)
    }
    if (showExperienceSurvey) {
        ExperienceSurveyDialog(onDismiss = { showExperienceSurvey = false })
    }
}

@Composable
private fun UnknownTrackNoticeDialog(
    songIds: List<String>,
    onDismiss: () -> Unit,
) {
    val message = buildString {
        append("检测到存档中有 ${songIds.size} 首尚未被后端曲库收录的曲目。这些可能是今晚更新的主线第九章新曲。\n\n")
        append("后端补齐曲目信息前，它们暂不参与 RKS 计算，也不会显示在成绩更新信息中；曲库更新后会自动恢复。")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现暂未收录的曲目") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}


@Composable
private fun AnnouncementPrompt(
    announcement: AppAnnouncement?,
    onDismiss: () -> Unit,
) {
    var renderedAnnouncement by remember { mutableStateOf<AppAnnouncement?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(announcement) {
        if (announcement != null) {
            renderedAnnouncement = announcement
            visible = true
        } else if (renderedAnnouncement != null) {
            visible = false
            delay(420)
            renderedAnnouncement = null
        }
    }

    val cardProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = appTween(durationMillis = if (visible) 280 else 190),
        label = "announcement-card-fade",
    )
    val rendered = renderedAnnouncement ?: return
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(appTween(240)),
        exit = fadeOut(appTween(200)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .66f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 22.dp, vertical = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurfaceRaised),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .graphicsLayer {
                        alpha = cardProgress
                        scaleX = .94f + cardProgress * .06f
                        scaleY = .94f + cardProgress * .06f
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        rendered.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                    rendered.publishedAt?.takeIf(String::isNotBlank)?.let { publishedAt ->
                        Text(
                            publishedAt,
                            color = AppTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    HorizontalDivider(
                        color = AppTextMuted.copy(alpha = .16f),
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                    SelectionContainer {
                        Text(
                            rendered.body,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    ) {
                        Text("我知道了")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExperienceSurveyPrompt(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val paperProgress = remember { Animatable(0f) }
    val cardProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = appTween(durationMillis = if (visible) 260 else 180),
        label = "survey-card-visibility",
    )
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(visible) {
        if (visible) {
            paperProgress.snapTo(0f)
            delay(130)
            paperProgress.animateTo(
                targetValue = 1f,
                animationSpec = appSpring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        } else {
            paperProgress.snapTo(0f)
        }
    }
    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(appTween(220)),
        exit = fadeOut(appTween(180)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .58f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val compactLayout = maxHeight < 620.dp || maxWidth > maxHeight
            val modalShape = RoundedCornerShape(if (compactLayout) 28.dp else 34.dp)
            Card(
                modifier = Modifier
                    .fillMaxWidth(.88f)
                    .widthIn(max = if (compactLayout) 500.dp else 430.dp)
                    .graphicsLayer {
                        val scale = .94f + (.06f * cardProgress)
                        scaleX = scale
                        scaleY = scale
                        alpha = cardProgress
                        transformOrigin = TransformOrigin.Center
                    }
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = {},
                    ),
                shape = modalShape,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFDFBFF),
                                    Color(0xFFF7F4FC),
                                ),
                            ),
                        )
                        .padding(
                            start = if (compactLayout) 22.dp else 26.dp,
                            end = if (compactLayout) 22.dp else 26.dp,
                            top = if (compactLayout) 14.dp else 22.dp,
                            bottom = if (compactLayout) 18.dp else 24.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SurveyFolderIllustration(
                        paperProgress = paperProgress.value,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compactLayout) 190.dp else 258.dp),
                    )
                    Spacer(Modifier.height(if (compactLayout) 8.dp else 14.dp))
                    Text(
                        text = "体验问卷调查",
                        color = Color(0xFF101A2D),
                        fontSize = if (compactLayout) 25.sp else 29.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(if (compactLayout) 8.dp else 12.dp))
                    SurveyPromptDescription(compactLayout = compactLayout)
                    Spacer(Modifier.height(if (compactLayout) 16.dp else 22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF0B5E9E)),
                        ) {
                            Text("暂不填写", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1.25f).height(50.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF075A9D),
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("填写问卷", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SurveyPromptDescription(compactLayout: Boolean) {
    val text = "点击填写 Phi Score Query 使用体验调查问卷"
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.bodyMedium.copy(
        color = Color(0xFF52647D),
        textAlign = TextAlign.Center,
    )
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidthPx = with(density) { maxWidth.roundToPx() } - 4
        var fittedFontSize = if (compactLayout) 12f else 13f
        while (fittedFontSize > 8f) {
            val measuredWidth = textMeasurer.measure(
                text = AnnotatedString(text),
                style = baseStyle.copy(fontSize = fittedFontSize.sp),
                maxLines = 1,
                softWrap = false,
            ).size.width
            if (measuredWidth <= availableWidthPx) break
            fittedFontSize -= .25f
        }
        Text(
            text = text,
            style = baseStyle.copy(fontSize = fittedFontSize.sp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun SurveyFolderIllustration(
    paperProgress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val unit = minOf(size.width / 320f, size.height / 250f)
        val origin = Offset(
            x = (size.width - 320f * unit) / 2f,
            y = (size.height - 250f * unit) / 2f,
        )
        val progress = paperProgress.coerceIn(0f, 1.12f)
        val sideProgress = ((progress - .06f) / .94f).coerceIn(0f, 1.08f)
        val centerProgress = (progress / .92f).coerceIn(0f, 1.1f)

        fun point(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)

        drawRoundRect(
            color = Color(0xFF0B4888).copy(alpha = .18f),
            topLeft = point(47f, 111f),
            size = Size(226f * unit, 126f * unit),
            cornerRadius = CornerRadius(20f * unit),
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF1598E7), Color(0xFF075AAB)),
                start = point(48f, 100f),
                end = point(272f, 220f),
            ),
            topLeft = point(47f, 101f),
            size = Size(226f * unit, 128f * unit),
            cornerRadius = CornerRadius(19f * unit),
        )

        drawSurveyPaper(
            left = origin.x + 70f * unit,
            top = origin.y + (38f + (1f - sideProgress) * 118f) * unit,
            width = 105f * unit,
            height = 139f * unit,
            rotation = -11f,
            alpha = sideProgress.coerceIn(0f, 1f),
            unit = unit,
        )
        drawSurveyPaper(
            left = origin.x + 151f * unit,
            top = origin.y + (39f + (1f - sideProgress) * 118f) * unit,
            width = 105f * unit,
            height = 139f * unit,
            rotation = 11f,
            alpha = sideProgress.coerceIn(0f, 1f),
            unit = unit,
        )
        drawSurveyPaper(
            left = origin.x + 108f * unit,
            top = origin.y + (21f + (1f - centerProgress) * 125f) * unit,
            width = 106f * unit,
            height = 146f * unit,
            rotation = 0f,
            alpha = centerProgress.coerceIn(0f, 1f),
            unit = unit,
        )

        drawRoundRect(
            color = Color(0xFF052E68).copy(alpha = .16f),
            topLeft = point(40f, 141f),
            size = Size(240f * unit, 104f * unit),
            cornerRadius = CornerRadius(20f * unit),
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF2B9BEA), Color(0xFF0753A5)),
                start = point(42f, 132f),
                end = point(280f, 235f),
            ),
            topLeft = point(40f, 132f),
            size = Size(240f * unit, 104f * unit),
            cornerRadius = CornerRadius(20f * unit),
        )
        drawLine(
            color = Color.White.copy(alpha = .24f),
            start = point(57f, 134f),
            end = point(263f, 134f),
            strokeWidth = 1.5f * unit,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSurveyPaper(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    rotation: Float,
    alpha: Float,
    unit: Float,
) {
    if (alpha <= 0f) return
    val pivot = Offset(left + width / 2f, top + height / 2f)
    rotate(degrees = rotation, pivot = pivot) {
        drawRoundRect(
            color = Color(0xFF3C6A9A).copy(alpha = .09f * alpha),
            topLeft = Offset(left + 3f * unit, top + 5f * unit),
            size = Size(width, height),
            cornerRadius = CornerRadius(10f * unit),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = alpha),
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(10f * unit),
        )
        val markX = left + 21f * unit
        val lineStart = left + 36f * unit
        val lineEnd = left + width - 15f * unit
        listOf(31f, 58f, 85f).forEachIndexed { index, row ->
            val y = top + row * unit
            drawCircle(
                color = Color(0xFFD8E9FF).copy(alpha = alpha),
                radius = 7f * unit,
                center = Offset(markX, y),
            )
            if (index < 2) {
                drawLine(
                    color = Color(0xFF4B9AF0).copy(alpha = alpha),
                    start = Offset(markX - 3.5f * unit, y),
                    end = Offset(markX - .5f * unit, y + 3f * unit),
                    strokeWidth = 2.2f * unit,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0xFF4B9AF0).copy(alpha = alpha),
                    start = Offset(markX - .5f * unit, y + 3f * unit),
                    end = Offset(markX + 4.5f * unit, y - 4f * unit),
                    strokeWidth = 2.2f * unit,
                    cap = StrokeCap.Round,
                )
            }
            drawLine(
                color = Color(0xFFCFE1F7).copy(alpha = alpha),
                start = Offset(lineStart, y - 2f * unit),
                end = Offset(lineEnd, y - 2f * unit),
                strokeWidth = 4f * unit,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFE3EDF9).copy(alpha = alpha),
                start = Offset(lineStart, y + 6f * unit),
                end = Offset(lineStart + (lineEnd - lineStart) * .68f, y + 6f * unit),
                strokeWidth = 3f * unit,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun AppUpdateDialog(
    update: AppUpdateManifest,
    state: AppUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (File) -> Unit,
) {
    val downloadedFile = state.downloadedAppUpdate
    val totalBytes = state.appUpdateTotalBytes.takeIf { it > 0 } ?: update.sizeBytes ?: 0L
    val progress = if (totalBytes > 0) {
        (state.appUpdateDownloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val canDismiss = !update.mandatory && !state.isDownloadingAppUpdate
    AlertDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        title = { Text("发现新版本 ${update.versionName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "当前版本 ${BuildConfig.VERSION_NAME} · ${formatFileSize(update.sizeBytes)}",
                    color = AppTextMuted,
                    fontSize = 12.sp,
                )
                update.publishedAt?.takeIf(String::isNotBlank)?.let {
                    Text("发布时间：$it", color = AppTextMuted, fontSize = 12.sp)
                }
                if (update.changelog.isNotEmpty()) {
                    Text("更新内容", fontWeight = FontWeight.Bold)
                    update.changelog.forEach { change -> Text("• $change") }
                }
                if (state.isDownloadingAppUpdate) {
                    if (totalBytes > 0) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "正在下载 ${formatFileSize(state.appUpdateDownloadedBytes)} / ${formatFileSize(totalBytes)}",
                            color = AppTextMuted,
                            fontSize = 12.sp,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("正在下载安装包", color = AppTextMuted, fontSize = 12.sp)
                    }
                }
                if (downloadedFile != null) {
                    Text("安装包已完成安全校验，可以开始安装。", color = AppAccent, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (downloadedFile != null) onInstall(downloadedFile) else onDownload() },
                enabled = !state.isDownloadingAppUpdate,
            ) {
                Text(
                    when {
                        state.isDownloadingAppUpdate -> "正在下载"
                        downloadedFile != null -> "安装更新"
                        else -> "下载并安装"
                    },
                )
            }
        },
        dismissButton = {
            if (canDismiss) TextButton(onClick = onDismiss) { Text("稍后提醒") }
        },
    )
}

@Composable
internal fun LoginScreen(
    progress: LoginProgress,
    onTokenLogin: (String) -> Unit,
    onQrLogin: () -> Unit,
    onCancelQr: () -> Unit,
) {
    var tokenDialog by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(AppBackground).padding(WindowInsets.safeDrawing.asPaddingValues()),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= 700.dp
        val contentModifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 20.dp)
        Column(contentModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(70.dp).clip(RoundedCornerShape(21.dp)).background(AppAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Φ",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Phi Score Query",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text("轻松查看你的 Phigros 成绩", color = AppTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.height(28.dp))

            when (val current = progress) {
                LoginProgress.Idle, is LoginProgress.Failed -> {
                    Button(
                        onClick = onQrLogin,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Default.QrCode2, null)
                        Spacer(Modifier.width(10.dp))
                        Text("使用 TapTap 扫码登录")
                    }
                    Spacer(Modifier.height(9.dp))
                    OutlinedButton(
                        onClick = { tokenDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.width(10.dp))
                        Text("使用 SessionToken")
                    }
                    if (current is LoginProgress.Failed) {
                        Text(
                            current.message,
                            color = AppDanger,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                    }
                }
                LoginProgress.CreatingQr, LoginProgress.Exchanging -> {
                    CircularProgressIndicator()
                    Text(
                        if (progress == LoginProgress.CreatingQr) "正在生成二维码" else "正在建立安全会话",
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
                is LoginProgress.WaitingForScan -> {
                    val imageLoader = rememberQrImageLoader()
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        AsyncImage(
                            model = current.qrSvg,
                            imageLoader = imageLoader,
                            contentDescription = "TapTap 登录二维码",
                            modifier = Modifier.size(if (wide) 300.dp else 250.dp).padding(12.dp),
                        )
                    }
                    Text(current.status, modifier = Modifier.padding(top = 16.dp), color = AppAccent)
                    Text("使用另一台设备的 TapTap 扫描", color = AppTextMuted, fontSize = 12.sp)
                    TextButton(onClick = onCancelQr) { Text("取消") }
                }
            }
        }
    }

    if (tokenDialog) {
        AlertDialog(
            onDismissRequest = { tokenDialog = false },
            title = { Text("SessionToken 登录") },
            text = {
                Column {
                    Text("Token 仅通过 HTTPS 发送，并使用 Android Keystore 加密保存在本机。", color = AppTextMuted)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("SessionToken") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (token.isNotBlank()) {
                                tokenDialog = false
                                onTokenLogin(token)
                                token = ""
                            }
                        }),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        tokenDialog = false
                        onTokenLogin(token)
                        token = ""
                    },
                    enabled = token.isNotBlank(),
                ) { Text("安全登录") }
            },
            dismissButton = { TextButton(onClick = { tokenDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun rememberQrImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }
}

@Composable
internal fun MainShell(
    state: AppUiState,
    snackbar: SnackbarHostState,
    onPage: (AppPage) -> Unit,
    onRefresh: () -> Unit,
    onRefreshLeaderboard: () -> Unit,
    onLoadRandomSuggestion: (Boolean) -> Unit,
    onLoadOwnSuggestionPosts: () -> Unit,
    onOpenSuggestionPost: (String) -> Unit,
    onRksCalculatorDraftChange: (RksCalculatorDraft) -> Unit,
    onSubmitSuggestionPost: (String, ByteArray, String, (Boolean) -> Unit) -> Unit,
    onSubmitSuggestionComment: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
    onDeleteSuggestionPost: (String, (Boolean) -> Unit) -> Unit,
    onDeleteSuggestionComment: (String, (Boolean) -> Unit) -> Unit,
    onSuggestionNotificationsChange: (Boolean) -> Unit,
    onSearchAchievementSongs: (String) -> Unit,
    onLoadAchievementRates: (String, String) -> Unit,
    onGenerateCustomRankingImage: () -> Unit,
    onImportCustomRanking: () -> Unit,
    onClearCustomRanking: () -> Unit,
    onCheckin: (String, Boolean) -> Unit,
    onCheckinRanks: () -> Unit,
    onStartRksGuess: (String) -> Unit,
    onRefreshRksGuess: () -> Unit,
    onSubmitRksGuessAnswer: (Double) -> Unit,
    onContinueRksGuessRound: () -> Unit,
    onLeaveRksGuessGame: () -> Unit,
    onSearchSong: (String) -> Unit,
    onOpenConstantSong: (String) -> SongScoreResult?,
    onEnsureSongImage: (SongScoreResult) -> Unit,
    onGenerateSongImage: (SongScoreResult) -> Unit,
    onGenerateImage: () -> Unit,
    onGenerateP30Image: () -> Unit,
    onDismissImagePagerGuide: () -> Unit,
    onDismissSuggestionSwipeGuide: () -> Unit,
    onClearCache: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onNavigationHandleVisibilityChange: (Boolean) -> Unit,
    onNavigationHandlePositionChange: (Float) -> Unit,
    onSwipeNavigationChange: (Boolean) -> Unit,
    onB30ImageStyleChange: (B30ImageStyle) -> Unit,
    onSongScoreImageStyleChange: (SongScoreImageStyle) -> Unit,
    onOpenSurvey: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRefreshAnnouncements: () -> Unit,
    onRevealSessionToken: () -> Unit,
    onHideSessionToken: () -> Unit,
    onLogout: () -> Unit,
    onDismissNavigationGuide: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAppDrawer by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val hasSuggestionUnread by SuggestionNotificationManager.observeInAppUnread(context).collectAsState()
    val feedbackUnreadCount by FeedbackNotificationManager.observeInAppUnread(context).collectAsState()
    val unreadPages = remember(hasSuggestionUnread, feedbackUnreadCount) {
        buildSet<AppPage> {
            if (hasSuggestionUnread) add(AppPage.MORE)
            if (feedbackUnreadCount > 0) add(AppPage.SETTINGS)
        }
    }
    val keyboardVisible = WindowInsets.isImeVisible
    val navigationBottomPadding by animateDpAsState(
        targetValue = if (keyboardVisible) 0.dp else if (state.useSwipeNavigation) 48.dp else 76.dp,
        animationSpec = appTween(180), label = "navigation-bottom-padding",
    )
    val openNavigation: () -> Unit = {
        if (state.useSwipeNavigation) showAppDrawer = true
        else scope.launch { drawerState.open() }
    }
    LaunchedEffect(state.useSwipeNavigation) {
        showAppDrawer = false
        drawerState.close()
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !state.useSwipeNavigation,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
            ) {
                Column(
                    Modifier.fillMaxHeight().width(292.dp).padding(WindowInsets.safeDrawing.asPaddingValues()),
                ) {
                    Row(
                        Modifier.padding(start = 18.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                .background(AppAccent.copy(alpha = .15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Φ", color = AppAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Phi Score Query", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(BuildConfig.VERSION_NAME, color = AppTextMuted, fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) {
                        navItems.forEach { item ->
                            NavigationDrawerItem(
                                selected = state.page == item.page,
                                onClick = {
                                    onPage(item.page)
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(item.icon, null) },
                                label = {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            item.title,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = if (state.page == item.page) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        if (item.page in unreadPages) {
                                            InAppUnreadDot()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = AppAccent.copy(alpha = .13f),
                                    selectedIconColor = AppAccent,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = AppTextMuted,
                                    unselectedTextColor = AppTextMuted,
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
    ) {
        Scaffold(
            containerColor = AppBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).background(AppBackground),
            ) {
                    PageContent(
                    state = state,
                    snackbar = snackbar,
                    onPage = onPage,
                    onRefresh = onRefresh,
                    onRefreshLeaderboard = onRefreshLeaderboard,
                    onLoadRandomSuggestion = onLoadRandomSuggestion,
                    onLoadOwnSuggestionPosts = onLoadOwnSuggestionPosts,
                    onOpenSuggestionPost = onOpenSuggestionPost,
                    onRksCalculatorDraftChange = onRksCalculatorDraftChange,
                    onSubmitSuggestionPost = onSubmitSuggestionPost,
                    onSubmitSuggestionComment = onSubmitSuggestionComment,
                    onDeleteSuggestionPost = onDeleteSuggestionPost,
                    onDeleteSuggestionComment = onDeleteSuggestionComment,
                     onSuggestionNotificationsChange = onSuggestionNotificationsChange,
                      onSearchAchievementSongs = onSearchAchievementSongs,
                      onLoadAchievementRates = onLoadAchievementRates,
                      onGenerateCustomRankingImage = onGenerateCustomRankingImage,
                      onImportCustomRanking = onImportCustomRanking,
                      onClearCustomRanking = onClearCustomRanking,
                      onCheckin = onCheckin,
                      onCheckinRanks = onCheckinRanks,
                      onStartRksGuess = onStartRksGuess,
                      onRefreshRksGuess = onRefreshRksGuess,
                      onSubmitRksGuessAnswer = onSubmitRksGuessAnswer,
                      onContinueRksGuessRound = onContinueRksGuessRound,
                      onLeaveRksGuessGame = onLeaveRksGuessGame,
                      onSearchSong = onSearchSong,
                    onOpenConstantSong = onOpenConstantSong,
                    onEnsureSongImage = onEnsureSongImage,
                    onGenerateSongImage = onGenerateSongImage,
                    onGenerateImage = onGenerateImage,
                    onGenerateP30Image = onGenerateP30Image,
                    onOpenDrawer = openNavigation,
                    onDismissImagePagerGuide = onDismissImagePagerGuide,
                    onDismissSuggestionSwipeGuide = onDismissSuggestionSwipeGuide,
                    onClearCache = onClearCache,
                    onThemeChange = onThemeChange,
                    onAutoRefreshChange = onAutoRefreshChange,
                    onAutoUpdateChange = onAutoUpdateChange,
                    onNavigationHandleVisibilityChange = onNavigationHandleVisibilityChange,
                    onSwipeNavigationChange = onSwipeNavigationChange,
                    onB30ImageStyleChange = onB30ImageStyleChange,
                    onSongScoreImageStyleChange = onSongScoreImageStyleChange,
                    onOpenSurvey = onOpenSurvey,
                    onCheckUpdate = onCheckUpdate,
                    onRefreshAnnouncements = onRefreshAnnouncements,
                    onRevealSessionToken = onRevealSessionToken,
                    onHideSessionToken = onHideSessionToken,
                    onLogout = onLogout,
                    modifier = Modifier.fillMaxSize().padding(bottom = navigationBottomPadding),
                )
                AnimatedContent(
                    targetState = if (keyboardVisible) null else state.useSwipeNavigation,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    contentAlignment = Alignment.BottomCenter,
                    transitionSpec = {
                        (fadeIn(appTween(160), initialAlpha = 0f) + slideInVertically(appTween(200)) { it }) togetherWith
                            (fadeOut(appTween(120)) + slideOutVertically(appTween(160)) { it })
                    },
                    label = "navigation-mode-transition",
                ) { useSwipeNavigation ->
                    if (useSwipeNavigation == true) {
                        SwipeNavigationHandle(
                            onOpen = openNavigation,
                            hasUnread = unreadPages.isNotEmpty(),
                        )
                    } else if (useSwipeNavigation == false) {
                        BottomNavigationDock(
                            currentPage = state.page,
                            onPage = onPage,
                            onOpenMenu = openNavigation,
                            unreadPages = unreadPages,
                        )
                    }
                }
            }
        }
    }
    if (state.useSwipeNavigation && showAppDrawer) {
        SwipeNavigationDrawer(
            items = navItems,
            currentPage = state.page,
            onDismiss = { showAppDrawer = false },
            onPage = onPage,
            unreadPages = unreadPages,
        )
    }
    if (state.showNavigationGuide) {
        AlertDialog(
            onDismissRequest = onDismissNavigationGuide,
            icon = { Icon(Icons.Default.Menu, null, tint = AppAccent) },
            title = { Text("导航方式已更新") },
            text = { Text(if (state.useSwipeNavigation) "从底部上滑或点击底部入口，展开全部功能。下滑即可收起。" else "常用页面位于底部，点击“菜单”可打开完整导航。也可以从屏幕左侧边缘右滑。") },
            confirmButton = {
                Button(onClick = onDismissNavigationGuide) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun PageContent(
    state: AppUiState,
    snackbar: SnackbarHostState,
    onPage: (AppPage) -> Unit,
    onRefresh: () -> Unit,
    onRefreshLeaderboard: () -> Unit,
    onLoadRandomSuggestion: (Boolean) -> Unit,
    onLoadOwnSuggestionPosts: () -> Unit,
    onOpenSuggestionPost: (String) -> Unit,
    onRksCalculatorDraftChange: (RksCalculatorDraft) -> Unit,
    onSubmitSuggestionPost: (String, ByteArray, String, (Boolean) -> Unit) -> Unit,
    onSubmitSuggestionComment: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
    onDeleteSuggestionPost: (String, (Boolean) -> Unit) -> Unit,
    onDeleteSuggestionComment: (String, (Boolean) -> Unit) -> Unit,
    onSuggestionNotificationsChange: (Boolean) -> Unit,
    onSearchAchievementSongs: (String) -> Unit,
    onLoadAchievementRates: (String, String) -> Unit,
    onGenerateCustomRankingImage: () -> Unit,
    onImportCustomRanking: () -> Unit,
    onClearCustomRanking: () -> Unit,
    onCheckin: (String, Boolean) -> Unit,
    onCheckinRanks: () -> Unit,
    onStartRksGuess: (String) -> Unit,
    onRefreshRksGuess: () -> Unit,
    onSubmitRksGuessAnswer: (Double) -> Unit,
    onContinueRksGuessRound: () -> Unit,
    onLeaveRksGuessGame: () -> Unit,
    onSearchSong: (String) -> Unit,
    onOpenConstantSong: (String) -> SongScoreResult?,
    onEnsureSongImage: (SongScoreResult) -> Unit,
    onGenerateSongImage: (SongScoreResult) -> Unit,
    onGenerateImage: () -> Unit,
    onGenerateP30Image: () -> Unit,
    onOpenDrawer: () -> Unit,
    onDismissImagePagerGuide: () -> Unit,
    onDismissSuggestionSwipeGuide: () -> Unit,
    onClearCache: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onNavigationHandleVisibilityChange: (Boolean) -> Unit,
    onSwipeNavigationChange: (Boolean) -> Unit,
    onB30ImageStyleChange: (B30ImageStyle) -> Unit,
    onSongScoreImageStyleChange: (SongScoreImageStyle) -> Unit,
    onOpenSurvey: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRefreshAnnouncements: () -> Unit,
    onRevealSessionToken: () -> Unit,
    onHideSessionToken: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.page,
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (fadeIn(appTween(if (state.useSwipeNavigation) 130 else 180)) +
                    slideInHorizontally(appTween(if (state.useSwipeNavigation) 160 else 240)) { direction * it / 20 }) togetherWith
                    (fadeOut(appTween(if (state.useSwipeNavigation) 90 else 120)) +
                        slideOutHorizontally(appTween(if (state.useSwipeNavigation) 130 else 180)) { -direction * it / 24 })
            },
            label = "page-transition",
        ) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = AppContentMaxWidth).fillMaxSize()) {
                    when (page) {
                AppPage.HOME -> HomePage(state, onRefresh, onOpenImage = { onPage(AppPage.IMAGE) })
                AppPage.B30 -> B30Page(state, onRefresh)
                AppPage.SONG -> SingleSongPage(
                    state = state,
                    onSearch = onSearchSong,
                    onEnsureSongImage = onEnsureSongImage,
                    onGenerateSongImage = onGenerateSongImage,
                )
                AppPage.CONSTANT_TABLE -> ConstantTablePage(
                    state = state,
                    onOpenSong = onOpenConstantSong,
                    onEnsureSongImage = onEnsureSongImage,
                    onGenerateSongImage = onGenerateSongImage,
                )
                AppPage.LEADERBOARD -> LeaderboardPage(state, onRefreshLeaderboard)
                AppPage.IMAGE -> ImagePage(
                    state = state,
                    onGenerateB30Image = onGenerateImage,
                    onGenerateP30Image = onGenerateP30Image,
                    onOpenDrawer = onOpenDrawer,
                    onDismissGuide = onDismissImagePagerGuide,
                )
                AppPage.MORE -> MorePage(
                    state = state,
                    onRefreshB30 = onRefresh,
                    onLoadRandomSuggestion = onLoadRandomSuggestion,
                    onLoadOwnSuggestionPosts = onLoadOwnSuggestionPosts,
                    onOpenSuggestionPost = onOpenSuggestionPost,
                    onRksCalculatorDraftChange = onRksCalculatorDraftChange,
                    onSubmitSuggestionPost = onSubmitSuggestionPost,
                    onSubmitSuggestionComment = onSubmitSuggestionComment,
                    onDeleteSuggestionPost = onDeleteSuggestionPost,
                    onDeleteSuggestionComment = onDeleteSuggestionComment,
                    onSuggestionNotificationsChange = onSuggestionNotificationsChange,
                    onDismissSuggestionSwipeGuide = onDismissSuggestionSwipeGuide,
                      onSearchAchievementSongs = onSearchAchievementSongs,
                      onLoadAchievementRates = onLoadAchievementRates,
                      onGenerateCustomRankingImage = onGenerateCustomRankingImage,
                      onImportCustomRanking = onImportCustomRanking,
                      onClearCustomRanking = onClearCustomRanking,
                      onCheckin = onCheckin,
                      onCheckinRanks = onCheckinRanks,
                      onStartRksGuess = onStartRksGuess,
                      onRefreshRksGuess = onRefreshRksGuess,
                      onSubmitRksGuessAnswer = onSubmitRksGuessAnswer,
                      onContinueRksGuessRound = onContinueRksGuessRound,
                      onLeaveRksGuessGame = onLeaveRksGuessGame,
                  )
                AppPage.SETTINGS -> SettingsPage(
                    state = state,
                    onClearCache = onClearCache,
                    onThemeChange = onThemeChange,
                    onAutoRefreshChange = onAutoRefreshChange,
                    onAutoUpdateChange = onAutoUpdateChange,
                    onNavigationHandleVisibilityChange = onNavigationHandleVisibilityChange,
                    onSwipeNavigationChange = onSwipeNavigationChange,
                    onB30ImageStyleChange = onB30ImageStyleChange,
                    onSongScoreImageStyleChange = onSongScoreImageStyleChange,
                    onOpenSurvey = onOpenSurvey,
                    onCheckUpdate = onCheckUpdate,
                    onRefreshAnnouncements = onRefreshAnnouncements,
                    onRevealSessionToken = onRevealSessionToken,
                    onHideSessionToken = onHideSessionToken,
                    onLogout = onLogout,
                )
                    }
                }
            }
        }
        if (state.isLoading) {
            LinearProgressIndicator(
                Modifier.widthIn(max = AppReadableMaxWidth).fillMaxWidth(.46f).height(2.dp)
                    .clip(RoundedCornerShape(999.dp)).align(Alignment.TopCenter),
                color = AppAccent,
                trackColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun BottomNavigationDock(
    currentPage: AppPage,
    onPage: (AppPage) -> Unit,
    onOpenMenu: () -> Unit,
    unreadPages: Set<AppPage>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 10.dp).widthIn(max = 520.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            primaryNavItems.forEach { item ->
                BottomNavigationItem(
                    title = item.title,
                    icon = item.icon,
                    selected = currentPage == item.page,
                    onClick = { onPage(item.page) },
                    hasUnread = item.page in unreadPages,
                    modifier = Modifier.weight(1f),
                )
            }
            BottomNavigationItem(
                title = "菜单",
                icon = Icons.Default.Menu,
                selected = primaryNavItems.none { it.page == currentPage },
                onClick = onOpenMenu,
                hasUnread = unreadPages.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomNavigationItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    hasUnread: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = appSpring(dampingRatio = .9f, stiffness = 650f),
        label = "bottom-nav-scale",
    )
    val itemBackground by animateColorAsState(
        if (selected) AppAccent.copy(alpha = .065f) else Color.Transparent,
        appTween(180), label = "bottom-nav-background",
    )
    val itemTint by animateColorAsState(
        if (selected) AppAccent else AppTextMuted,
        appTween(180), label = "bottom-nav-tint",
    )
    Column(
        modifier
            .height(50.dp)
            .appPressMotion(interactionSource, pressedScale = .96f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(13.dp))
            .background(itemBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(icon, null, tint = itemTint, modifier = Modifier.size(20.dp))
            if (hasUnread) {
                InAppUnreadDot(Modifier.offset(x = 3.dp, y = (-2).dp))
            }
        }
        Text(title, color = itemTint, fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun NavigationDrawerHandle(
    positionFraction: Float,
    onPositionChange: (Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var localFraction by rememberSaveable { mutableFloatStateOf(positionFraction.coerceIn(0f, 1f)) }
    LaunchedEffect(positionFraction) {
        localFraction = positionFraction.coerceIn(0f, 1f)
    }
    BoxWithConstraints(modifier.fillMaxHeight().width(24.dp)) {
        val handleHeight = 54.dp
        val handleHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { handleHeight.toPx() }
        val availablePx = (constraints.maxHeight - handleHeightPx).coerceAtLeast(1f)
        Box(
            Modifier
                .offset { IntOffset(0, (localFraction * availablePx).roundToInt()) }
                .width(17.dp)
                .height(handleHeight)
                .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                .background(AppAccent.copy(alpha = .68f))
                .draggable(
                    state = rememberDraggableState { delta ->
                        localFraction = (localFraction + delta / availablePx).coerceIn(0f, 1f)
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = { onPositionChange(localFraction) },
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ChevronRight, "打开或上下移动侧边导航入口", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LeaderboardPage(state: AppUiState, onRefresh: () -> Unit) {
    val leaderboard = state.leaderboard
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "排行榜",
            subtitle = leaderboard?.let { "共 ${it.me.total.coerceAtLeast(it.entries.size)} 位公开玩家" }
                ?: "公开玩家 RKS 排名",
        ) {
            IconButton(onClick = onRefresh, enabled = !state.isLeaderboardLoading) {
                RefreshIcon(state.isLeaderboardLoading)
            }
        }
        when {
            leaderboard == null && state.isLeaderboardLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            leaderboard == null -> EmptyState(
                "暂未读取排行榜",
                "刷新后会提交当前存档 RKS，并读取公开 Ranklist。",
                onRefresh,
                "读取排行榜",
            )
            else -> {
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                val showScrollToTop by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                    }
                }
                val ownEntryIndex = leaderboard.entries.indexOfFirst { it.rank == leaderboard.me.rank }
                val scrollToOwn: (() -> Unit)? = if (ownEntryIndex >= 0) {
                    {
                        coroutineScope.launch { listState.animateScrollToItem(2 + ownEntryIndex) }
                    }
                } else {
                    null
                }

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = AppPageHorizontalPadding, end = AppPageHorizontalPadding, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        item(key = "leaderboard-me") {
                            CurrentPlayerRankCard(leaderboard.playerProfile, leaderboard.me, scrollToOwn)
                        }
                        item(key = "leaderboard-title") {
                            Text(
                                "排名",
                                color = AppAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 7.dp, bottom = 1.dp),
                            )
                        }
                        if (leaderboard.entries.isEmpty()) {
                            item(key = "leaderboard-empty") {
                                Text(
                                    "还没有可公开显示的排行榜记录。玩家刷新存档后会自动加入。",
                                    color = AppTextMuted,
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            itemsIndexed(
                                leaderboard.entries,
                                key = { _, entry -> "rank-${entry.rank}-${entry.user}" },
                            ) { _, entry -> LeaderboardRow(entry) }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToTop,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 18.dp),
                        enter = fadeIn(appTween(180)) + slideInVertically(appTween(180)) { -it / 2 },
                        exit = fadeOut(appTween(140)),
                    ) {
                        androidx.compose.material3.SmallFloatingActionButton(
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                            containerColor = AppAccent,
                            contentColor = Color.White,
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, "回到排行榜顶部")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentPlayerRankCard(
    profile: xyz.plcliangpicup.phigrosscore.data.PlayerProfile?,
    me: xyz.plcliangpicup.phigrosscore.data.LeaderboardMe,
    onClick: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .10f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(profile?.avatar, profile?.nickname.orEmpty(), 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(profile?.nickname ?: "当前玩家", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                challengeModeLabel(profile?.challengeModeRank)?.let {
                    Text("课题模式 $it", color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    when {
                        me.rank > PLAYER_LEADERBOARD_LIMIT -> "当前排名不在前 1,500 名"
                        me.rank > 0 -> "超过 ${"%.2f".format(me.percentile)}% 的公开玩家"
                        else -> "刷新存档后生成排名"
                    },
                    color = AppTextMuted,
                    fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (me.rank > 0) "#${me.rank}" else "--", color = AppAccent, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("%.4f RKS".format(me.score), color = AppTextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    val displayName = entry.nickname?.takeIf(String::isNotBlank)
        ?: entry.alias?.takeIf(String::isNotBlank)
        ?: "Phigros Player"
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(52.dp).height(38.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (entry.rank <= 3) AppAccent.copy(alpha = .18f) else AppSurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "#${entry.rank}",
                    color = if (entry.rank <= 3) AppAccent else AppTextMuted,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
            Spacer(Modifier.width(10.dp))
            PlayerAvatar(entry.avatar, displayName, 42.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(displayName, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    challengeModeLabel(entry.challengeModeRank)?.let {
                        Text("课题 $it", color = AppAccent, fontSize = 10.sp)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%.4f".format(entry.score), color = AppAccent, fontWeight = FontWeight.Black)
                Text("RKS", color = AppTextMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun PlayerAvatar(avatar: String?, playerName: String, size: androidx.compose.ui.unit.Dp) {
    val normalizedAvatar = avatar.validAvatarName()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(AppSurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Person, playerName, tint = AppTextMuted, modifier = Modifier.size(size * .52f))
        normalizedAvatar?.let {
            AsyncImage(
                model = avatarImageRequest(LocalContext.current, it),
                contentDescription = "$playerName 的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun WidePlayerAvatar(
    avatar: String?,
    playerName: String,
    width: androidx.compose.ui.unit.Dp = 116.dp,
    height: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val normalizedAvatar = avatar.validAvatarName()
    Box(
        Modifier.width(width).height(height).clip(RoundedCornerShape(12.dp)).background(AppSurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Person, playerName, tint = AppTextMuted, modifier = Modifier.size(height * .5f))
        normalizedAvatar?.let {
            AsyncImage(
                model = avatarImageRequest(LocalContext.current, it),
                contentDescription = "$playerName 的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun challengeModeLabel(rank: Int?): String? {
    if (rank == null || rank <= 0) return null
    val text = rank.toString().padStart(3, '0')
    val color = when (text.first()) {
        '1' -> "绿"
        '2' -> "蓝"
        '3' -> "红"
        '4' -> "金"
        '5' -> "彩"
        else -> return null
    }
    return "$color${text.drop(1)}"
}

@Composable
private fun PageHeader(
    title: String,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = AppPageHorizontalPadding, end = AppPageHorizontalPadding, top = 20.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitleContent != null) Box(Modifier.padding(top = 5.dp)) { subtitleContent() }
            else subtitle?.let { Text(it, color = AppTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)) }
        }
        action?.invoke()
    }
}

@Composable
private fun HomePage(state: AppUiState, onRefresh: () -> Unit, onOpenImage: () -> Unit) {
    val snapshot = state.snapshot
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("概览", if (state.isOffline) "离线缓存" else "TapTap · 大陆版") {
            IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                RefreshIcon(state.isLoading)
            }
        }
        Column(Modifier.padding(horizontal = AppPageHorizontalPadding), verticalArrangement = Arrangement.spacedBy(AppSectionSpacing)) {
            if (snapshot == null) {
                EmptyState("暂无成绩", "刷新后显示最新存档", onRefresh)
            } else {
                PlayerOverviewPanel(snapshot, state.isOffline, state.rksDelta, state.rksDeltaEvent)
                Spacer(Modifier.height(6.dp))
                UpdatedScoresOverview(snapshot)
                GradeOverview(snapshot)
                HomeB30ImageSection(
                    image = state.imageFile,
                    generatingB30 = state.isGeneratingB30Image,
                    generatingP30 = state.isGeneratingP30Image,
                    b30ElapsedSeconds = state.b30ImageGenerationElapsedSeconds,
                    p30ElapsedSeconds = state.p30ImageGenerationElapsedSeconds,
                    onOpenImage = onOpenImage,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HomeB30ImageSection(
    image: File?,
    generatingB30: Boolean,
    generatingP30: Boolean,
    b30ElapsedSeconds: Int,
    p30ElapsedSeconds: Int,
    onOpenImage: () -> Unit,
) {
    val generating = generatingB30 || generatingP30
    if (!generating && image?.exists() != true) return
    val generatingLabel = when {
        generatingB30 && generatingP30 -> "B30 和 P30"
        generatingP30 -> "P30"
        else -> "B30"
    }
    val elapsedSeconds = maxOf(
        b30ElapsedSeconds.takeIf { generatingB30 } ?: 0,
        p30ElapsedSeconds.takeIf { generatingP30 } ?: 0,
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("B30 成绩图", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (generating) {
            Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(
                        "正在后台生成 $generatingLabel 成绩图… · 已用时 ${formatGenerationElapsed(elapsedSeconds)}",
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        if (image?.exists() == true) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image)
                    .memoryCacheKey("home-b30-${image.lastModified()}-${image.length()}")
                    .build(),
                contentDescription = "B30 成绩图",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppSurface)
                    .clickable(onClick = onOpenImage),
                contentScale = ContentScale.FillWidth,
            )
            Text(
                "点击成绩图进入图片页面",
                color = AppTextMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun UpdatedScoresOverview(snapshot: B30Snapshot) {
    AnimatedVisibility(
        visible = snapshot.hasUpdateComparison,
        enter = fadeIn(appTween(320)) + expandVertically(appTween(420)),
        exit = fadeOut(appTween(180)) + shrinkVertically(appTween(260)),
    ) {
        Column(
            Modifier.fillMaxWidth().animateContentSize(appTween(420)),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = AppAccent, modifier = Modifier.size(21.dp))
                AnimatedContent(
                    targetState = snapshot.updatedScores.size,
                    transitionSpec = {
                        (fadeIn(appTween(260)) + slideInVertically(appTween(300)) { it / 2 }) togetherWith
                            (fadeOut(appTween(160)) + slideOutVertically(appTween(180)) { -it / 2 })
                    },
                    label = "updated-score-count",
                ) { count ->
                    Text(
                        if (count == 0) "未发现新的成绩" else "更新了${count}份成绩",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (snapshot.updatedScores.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        "本次读取的存档与上次一致。",
                        color = AppTextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            } else {
                snapshot.updatedScores.forEachIndexed { index, score ->
                    UpdatedScoreRow(score, index)
                }
            }
        }
    }
}

@Composable
private fun UpdatedScoreRow(score: ScoreSnapshotEntry, index: Int) {
    var useFallbackArtwork by remember(score.songId) { mutableStateOf(false) }
    var visible by remember(
        score.songId,
        score.difficulty,
        score.score,
        score.accuracy,
        score.isFullCombo,
    ) { mutableStateOf(false) }
    LaunchedEffect(score.songId, score.difficulty, score.score, score.accuracy, score.isFullCombo) {
        delay(index.coerceAtMost(8) * 55L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(appTween(320)) + slideInHorizontally(appTween(420)) { it / 5 },
        exit = fadeOut(appTween(160)) + slideOutHorizontally(appTween(220)) { -it / 6 },
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(11.dp)) {
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = lowArtworkRequest(
                        LocalContext.current,
                        score.songId,
                        if (useFallbackArtwork) fallbackIllustrationUrl(score.songId)
                        else illustrationUrl(score.songId),
                    ),
                    contentDescription = "${score.songName} 曲绘",
                    contentScale = ContentScale.Crop,
                    onError = { if (!useFallbackArtwork) useFallbackArtwork = true },
                    modifier = Modifier.matchParentSize().background(AppSurfaceRaised),
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(
                            0f to AppSurface,
                            .48f to AppSurface,
                            .76f to AppSurface.copy(alpha = .82f),
                            1f to AppSurface.copy(alpha = .08f),
                        ),
                    ),
                )
                BoxWithConstraints(Modifier.fillMaxWidth(.78f).padding(14.dp)) {
                    val compact = maxWidth < 330.dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            score.songName,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 5.dp),
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(difficultyColor(score.difficulty).copy(alpha = .18f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    score.difficulty,
                                    color = difficultyColor(score.difficulty),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            Text(
                                scoreUpdateText(score),
                                fontSize = if (compact) 15.sp else 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Text(
                            "${if (score.isFullCombo) "FC · " else ""}定数 ${score.chartConstant?.let { String.format(Locale.US, "%.1f", it) } ?: "--"}",
                            color = AppTextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "%.4f".format(score.rks),
                                color = difficultyColor(score.difficulty),
                                fontWeight = FontWeight.Bold,
                                fontSize = if (compact) 13.sp else 15.sp,
                            )
                            Text("RKS", color = AppTextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

internal fun scoreUpdateText(score: ScoreSnapshotEntry): String = buildList {
    if (score.scoreChanged) {
        add("分数 ${String.format(Locale.US, "%,d", score.score)}")
    }
    if (score.accuracyChanged) {
        add("ACC ${String.format(Locale.US, "%.4f", score.accuracy)}%")
    }
    if (score.fullComboChanged && !score.scoreChanged && !score.accuracyChanged) {
        add(if (score.isFullCombo) "FC" else "FC 状态已更新")
    }
}.joinToString(" · ").ifBlank { "成绩已更新" }

@Composable
private fun PlayerOverviewPanel(snapshot: B30Snapshot, offline: Boolean, rksDelta: Double?, rksDeltaEvent: Long) {
    val profile = snapshot.playerProfile
    val playerName = profile?.nickname?.takeIf(String::isNotBlank) ?: "Phigros Player"
    val p30Rks = remember(snapshot.scoreRecords) { calculateP30Rks(snapshot.scoreRecords) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(profile?.avatar, playerName, 40.dp)
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(playerName, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (offline) "离线数据" else "已同步", color = AppTextMuted, fontSize = 11.sp)
            }
        }
        Column {
            Text("Ranking Score", color = AppTextMuted, fontSize = 12.sp)
            var showDelta by remember { mutableStateOf(false) }
            LaunchedEffect(rksDeltaEvent) {
                if (rksDeltaEvent > 0 && rksDelta != null) {
                    showDelta = true
                    delay(2_400)
                    showDelta = false
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedRksNumber(snapshot.totalRks,
                    color = AppAccent, fontSize = 46.sp, lineHeight = 56.sp, fontWeight = FontWeight.Medium)
                AnimatedVisibility(
                    visible = showDelta,
                    enter = fadeIn(appTween(220)) + slideInHorizontally(appTween(360)) { it / 2 } + scaleIn(appSpring(), initialScale = .72f),
                    exit = fadeOut(appTween(260)) + slideOutHorizontally(appTween(320)) { it / 3 } + scaleOut(appTween(280), targetScale = .86f),
                ) {
                    Text(
                        "RKS ${if ((rksDelta ?: 0.0) >= 0.0) "+" else ""}${String.format(Locale.US, "%.4f", rksDelta ?: 0.0)}",
                        color = if ((rksDelta ?: 0.0) >= 0.0) AppAccent else MaterialTheme.colorScheme.error,
                        fontFamily = AppNumericFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp, top = 10.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("P30 RKS", color = AppTextMuted, fontSize = 11.sp)
                AnimatedRksNumber(p30Rks,
                    fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("课题模式", color = AppTextMuted, fontSize = 11.sp)
                Text(challengeModeLabel(profile?.challengeModeRank) ?: "—",
                    fontFamily = AppNumericFont, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
        Text(formatSaveUpdatedAt(snapshot.saveUpdatedAt, snapshot.cachedAtEpochMs) + " 更新",
            color = AppTextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ChallengeModePlate(rank: Int?) {
    val plate = challengeModePlateResource(rank)
    val level = challengeModeLevel(rank)
    if (plate == null || level == null) {
        Text("--", color = AppTextMuted, fontWeight = FontWeight.Bold)
        return
    }
    Box(
        Modifier.width(118.dp).height(38.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(plate),
            contentDescription = "课题模式 ${challengeModeLabel(rank)}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Text(
            level,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.graphicsLayer {
                scaleX = 1.08f
                scaleY = 1.08f
            },
        )
    }
}

private fun challengeModePlateResource(rank: Int?): Int? = when (rank?.toString()?.padStart(3, '0')?.firstOrNull()) {
    '1' -> R.drawable.challenge_green
    '2' -> R.drawable.challenge_blue
    '3' -> R.drawable.challenge_red
    '4' -> R.drawable.challenge_gold
    '5' -> R.drawable.challenge_rainbow
    else -> null
}

internal fun challengeModeLevel(rank: Int?): String? {
    if (rank == null || rank <= 0) return null
    val text = rank.toString().padStart(3, '0')
    if (text.first() !in '1'..'5') return null
    return text.drop(1)
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = AppAccent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun GradeOverview(snapshot: B30Snapshot) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val total = gradeSummary(snapshot)
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("成绩统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!expanded) {
                        Text(
                            "C ${total.clear} · FC ${total.fullCombo} · AP ${total.allPerfect}",
                            color = AppTextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "折叠成绩统计" else "展开成绩统计",
                    tint = AppAccent,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(appTween(260)) + expandVertically(appTween(360)),
                exit = fadeOut(appTween(160)) + shrinkVertically(appTween(260)),
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradeMetric("Clear", total.clear, Modifier.weight(1f))
                        GradeMetric("FC", total.fullCombo, Modifier.weight(1f))
                        GradeMetric("AP", total.allPerfect, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = AppSurfaceRaised)
                    listOf("EZ", "HD", "IN", "AT").forEach { difficulty ->
                        val summary = gradeSummary(snapshot, difficulty)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                difficulty,
                                color = difficultyColor(difficulty),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.width(34.dp),
                            )
                            DifficultyGradeCount("C", summary.clear, Modifier.weight(1f))
                            DifficultyGradeCount("FC", summary.fullCombo, Modifier.weight(1f))
                            DifficultyGradeCount("AP", summary.allPerfect, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyGradeCount(label: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center) {
        Text("$label ", fontSize = 12.sp)
        Text(count.toString(), color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private data class GradeSummary(val clear: Int, val fullCombo: Int, val allPerfect: Int)

private fun gradeSummary(snapshot: B30Snapshot, difficulty: String? = null): GradeSummary {
    val records = snapshot.scoreRecords.filter {
        difficulty == null || it.difficulty.equals(difficulty, ignoreCase = true)
    }
    if (records.isNotEmpty()) {
        return GradeSummary(
            clear = records.count { it.score > 0 || it.accuracy > 0.0 },
            fullCombo = records.count { it.isFullCombo || it.score >= 1_000_000 },
            allPerfect = records.count { it.score >= 1_000_000 || it.accuracy >= 100.0 },
        )
    }
    val counts = if (difficulty == null) snapshot.gradeCounts.values else listOfNotNull(snapshot.gradeCounts[difficulty])
    return GradeSummary(
        clear = counts.sumOf { it.clear + it.fullCombo + it.phi },
        fullCombo = counts.sumOf { it.fullCombo + it.phi },
        allPerfect = counts.sumOf { it.phi },
    )
}

@Composable
private fun GradeMetric(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(vertical = 11.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count.toString(), color = AppAccent, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(label, color = AppTextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun B30Page(state: AppUiState, onRefresh: () -> Unit) {
    val snapshot = state.snapshot
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var bestNText by rememberSaveable { mutableStateOf("30") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        PageHeader("成绩", subtitleContent = {
            snapshot?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RKS ", color = AppTextMuted, fontSize = 12.sp)
                    AnimatedRksNumber(it.totalRks, color = AppTextMuted, fontSize = 12.sp)
                }
            }
        }) {
            IconButton(onClick = onRefresh, enabled = !state.isLoading) { RefreshIcon(state.isLoading) }
        }
        BoxWithConstraints(
            Modifier.padding(horizontal = AppPageHorizontalPadding).fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).background(AppSurfaceRaised).padding(4.dp),
        ) {
            val tabGap = 4.dp
            val tabWidth = (maxWidth - tabGap * 2) / 3
            val tabStepPx = with(LocalDensity.current) { (tabWidth + tabGap).toPx() }
            val indicatorPosition by animateFloatAsState(
                targetValue = selectedTab.toFloat(),
                animationSpec = appTween(160),
                label = "ranking-tab-position",
            )
            // One indicator avoids overlapping outgoing/incoming selection fills.
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier.offset { IntOffset((tabStepPx * indicatorPosition).roundToInt(), 0) }
                        .width(tabWidth).fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp)).background(AppSurface),
                )
            }
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(tabGap),
            ) {
                listOf("B30", "Best N", "P30").forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    val interactionSource = remember { MutableInteractionSource() }
                    val labelColor by animateColorAsState(
                        if (selected) AppAccent else AppTextMuted,
                        appTween(120), label = "ranking-tab-label",
                    )
                    Box(
                        Modifier.weight(1f).heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = androidx.compose.ui.semantics.Role.Tab,
                                onClick = { selectedTab = index },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(title, color = labelColor,
                            modifier = Modifier.appPressMotion(interactionSource, pressedScale = .96f),
                            fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
        if (snapshot == null) {
            EmptyState("暂无成绩", "刷新后显示最新存档", onRefresh)
        } else {
            val requestedN = bestNText.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val bestCharts = remember(snapshot.scoreRecords, requestedN) {
                selectBestCharts(snapshot.scoreRecords, requestedN)
            }
            val perfectCharts = remember(snapshot.scoreRecords) { selectPerfectCharts(snapshot.scoreRecords) }
            LaunchedEffect(selectedTab) { listState.scrollToItem(0) }
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = AppPageHorizontalPadding, end = AppPageHorizontalPadding,
                        top = 12.dp, bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (selectedTab) {
                        0 -> {
                            val ap = snapshot.items.filter { it.section == "AP" }
                            val best = snapshot.items.filter { it.section == "BEST" }
                            item { SectionLabel("P3") }
                            itemsIndexed(ap, key = { i, it -> "ap-$i-${it.songId}" }) { _, item -> B30Row(item) }
                            item { SectionLabel("Best 27") }
                            itemsIndexed(best, key = { i, it -> "best-$i-${it.songId}" }) { _, item -> B30Row(item) }
                        }
                        1 -> {
                            item {
                                OutlinedTextField(
                                    value = bestNText,
                                    onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) bestNText = it },
                                    label = { Text("谱面数量") },
                                    supportingText = { Text(if (requestedN == 0) "请输入大于 0 的整数" else "共 ${bestCharts.size} 张谱面") },
                                    isError = requestedN == 0,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                )
                            }
                            itemsIndexed(bestCharts, key = { i, it -> "n-$i-${it.songId}" }) { i, item ->
                                SnapshotRankingRow(i + 1, item, showPushTarget = true)
                            }
                        }
                        2 -> {
                            item { P30RksSummary(calculateP30Rks(snapshot.scoreRecords)) }
                            if (perfectCharts.isEmpty()) {
                                item { Text("暂无 All Perfect 谱面", color = AppTextMuted, modifier = Modifier.padding(16.dp)) }
                            }
                            itemsIndexed(perfectCharts.take(27), key = { i, it -> "p-$i-${it.songId}" }) { i, item ->
                                SnapshotRankingRow(i + 1, item)
                            }
                            if (perfectCharts.size > 27) item { OverflowDivider() }
                            itemsIndexed(perfectCharts.drop(27), key = { i, it -> "po-$i-${it.songId}" }) { i, item ->
                                SnapshotRankingRow(i + 28, item)
                            }
                        }
                    }
                }
                if (listState.firstVisibleItemIndex > 3) {
                    FilledTonalButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) { Icon(Icons.Default.KeyboardArrowUp, "回到顶部") }
                }
            }
        }
    }
}

@Composable
private fun P30RksSummary(rks: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().border(
            width = 1.dp,
            color = AppAccent.copy(alpha = .28f),
            shape = MaterialTheme.shapes.medium,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("P30 综合 RKS", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            AnimatedRksNumber(rks, color = AppAccent, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RankingSectionHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurfaceRaised),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = AppAccent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AppTextMuted, fontSize = 11.sp)
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (expanded) "收起 $title" else "展开 $title",
                tint = AppAccent,
            )
        }
    }
}

@Composable
private fun RankingSectionDivider() {
    HorizontalDivider(
        color = AppTextMuted.copy(alpha = .2f),
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun OverflowDivider() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = AppAccent.copy(alpha = .42f))
        Text(
            "溢出位",
            color = AppAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
        HorizontalDivider(Modifier.weight(1f), color = AppAccent.copy(alpha = .42f))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = AppAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun B30Row(item: B30Item) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(25.dp).height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${item.position}", fontWeight = FontWeight.Medium, color = AppTextMuted)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.songName, fontWeight = FontWeight.Bold)
                Text(
                    "${item.difficulty} ${item.chartConstant?.let { "%.1f".format(it) } ?: "--"}  ·  ${"%,d".format(item.score)}",
                    color = AppTextMuted,
                    fontSize = 12.sp,
                )
                Text("ACC ${"%.4f".format(item.accuracy)}%${if (item.score == 1_000_000) "  ·  AP" else if (item.isFullCombo) "  ·  FC" else ""}", fontSize = 12.sp)
                pushAccLabel(item.pushAcc, item.pushAccHint)?.let {
                    Text(it, color = difficultyColor(item.difficulty), fontSize = 10.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%.4f".format(item.rks), color = AppAccent, fontWeight = FontWeight.Bold)
                Text("RKS", color = AppTextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SnapshotRankingRow(position: Int, item: ScoreSnapshotEntry, showPushTarget: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(25.dp).height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(position.toString(), fontWeight = FontWeight.Medium, color = AppTextMuted)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.songName, fontWeight = FontWeight.Bold)
                Text(
                    "${item.difficulty} ${item.chartConstant?.let { "%.1f".format(it) } ?: "--"}  ·  ${"%,d".format(item.score)}",
                    color = AppTextMuted,
                    fontSize = 12.sp,
                )
                val status = when {
                    item.score == 1_000_000 -> "  ·  AP"
                    item.isFullCombo -> "  ·  FC"
                    else -> ""
                }
                Text("ACC ${"%.4f".format(item.accuracy)}%$status", fontSize = 12.sp)
                if (showPushTarget) {
                    pushAccLabel(item.pushAcc, item.pushAccHint)?.let {
                        Text(it, color = difficultyColor(item.difficulty), fontSize = 10.sp)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%.4f".format(item.rks), color = AppAccent, fontWeight = FontWeight.Bold)
                Text("RKS", color = AppTextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ConstantTablePage(
    state: AppUiState,
    onOpenSong: (String) -> SongScoreResult?,
    onEnsureSongImage: (SongScoreResult) -> Unit,
    onGenerateSongImage: (SongScoreResult) -> Unit,
) {
    var selectedLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedDifficultyMask by rememberSaveable { mutableStateOf(CONSTANT_DIFFICULTY_ALL_MASK) }
    var selectedSong by remember { mutableStateOf<SongScoreResult?>(null) }
    val listState = rememberLazyListState()
    var lastDisplayedLevel by rememberSaveable { mutableStateOf(selectedLevel) }
    var lastDisplayedDifficultyMask by rememberSaveable { mutableStateOf(selectedDifficultyMask) }
    val levels = remember(state.constantTableEntries) {
        constantTableLevels(state.constantTableEntries)
    }
    val rows = remember(state.constantTableEntries, selectedLevel, selectedDifficultyMask) {
        buildConstantTableRows(state.constantTableEntries, selectedLevel, selectedDifficultyMask)
    }
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    selectedSong?.let { song ->
        BackHandler { selectedSong = null }
        SongDetailPage(
            song = song,
            onBack = { selectedSong = null },
            songImageFile = state.songImageFile.takeIf { state.songImageSongId == song.songId },
            isGeneratingSongImage = state.isGeneratingSongImage && state.songImageSongId == song.songId,
            songImageGenerationElapsedSeconds = state.songImageGenerationElapsedSeconds,
            onEnsureSongImage = { onEnsureSongImage(song) },
            onGenerateSongImage = { onGenerateSongImage(song) },
            backDescription = "返回定数表",
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("定数表")
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppPageHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "level-all") {
                ConstantLevelChip(
                    label = "全部",
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                )
            }
            items(levels, key = { "level-$it" }) { level ->
                ConstantLevelChip(
                    label = level.toString(),
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = level },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppPageHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "difficulty-all") {
                ConstantLevelChip(
                    label = "全部",
                    selected = selectedDifficultyMask == CONSTANT_DIFFICULTY_ALL_MASK,
                    onClick = { selectedDifficultyMask = CONSTANT_DIFFICULTY_ALL_MASK },
                )
            }
            items(CONSTANT_DIFFICULTIES, key = { "difficulty-$it" }) { difficulty ->
                val bit = constantDifficultyBit(difficulty)
                ConstantLevelChip(
                    label = difficulty,
                    selected = selectedDifficultyMask and bit != 0,
                    onClick = { selectedDifficultyMask = selectedDifficultyMask xor bit },
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        if (rows.isEmpty()) {
            EmptyState("暂无定数资料", "当前定数与难度筛选条件下没有谱面。", {})
        } else {
            LaunchedEffect(selectedLevel, selectedDifficultyMask) {
                if (
                    lastDisplayedLevel != selectedLevel ||
                    lastDisplayedDifficultyMask != selectedDifficultyMask
                ) {
                    listState.scrollToItem(0)
                    lastDisplayedLevel = selectedLevel
                    lastDisplayedDifficultyMask = selectedDifficultyMask
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.key },
                    ) { index, row ->
                        AnimatedConstantTableRow(
                            animationKey = ((selectedLevel ?: 0) shl 4) or selectedDifficultyMask,
                            rowIndex = index,
                            rowKey = row.key,
                        ) {
                            when (row) {
                                is ConstantTableRow.LevelHeader -> ConstantLevelHeader(row.level)
                                is ConstantTableRow.ConstantHeader -> ConstantValueHeader(row.constant)
                                is ConstantTableRow.Chart -> ConstantChartCard(
                                    entry = row.entry,
                                    onClick = {
                                        selectedSong = onOpenSong(row.entry.song.id)
                                    },
                                )
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToTop,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 18.dp),
                    enter = fadeIn(appTween(180)) + slideInVertically(appTween(180)) { -it / 2 },
                    exit = fadeOut(appTween(140)),
                ) {
                    androidx.compose.material3.SmallFloatingActionButton(
                        onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                        containerColor = AppAccent,
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "鍥炲埌瀹氭暟琛ㄩ《閮?")
                    }
                }
            }
        }
    }
}

private fun buildConstantTableRows(
    entries: List<ConstantTableEntry>,
    selectedLevel: Int?,
    selectedDifficultyMask: Int,
): List<ConstantTableRow> = buildList {
    val filtered = entries.filter { entry ->
        val matchesLevel = selectedLevel == null || entry.chart.chartConstant?.toInt() == selectedLevel
        val matchesDifficulty = selectedDifficultyMask and constantDifficultyBit(entry.chart.difficulty) != 0
        matchesLevel && matchesDifficulty
    }
    filtered.groupBy { it.chart.chartConstant?.toInt() ?: 0 }
        .toSortedMap(compareByDescending { it })
        .forEach { (level, levelEntries) ->
            add(ConstantTableRow.LevelHeader(level))
            levelEntries.groupBy { requireNotNull(it.chart.chartConstant) }
                .toSortedMap(compareByDescending { it })
                .forEach { (constant, constantEntries) ->
                    add(ConstantTableRow.ConstantHeader(constant))
                    constantEntries.forEach { add(ConstantTableRow.Chart(it)) }
                }
        }
}

@Composable
private fun ConstantLevelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = AppSurface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = AppAccent,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun AnimatedConstantTableRow(
    animationKey: Int,
    rowIndex: Int,
    rowKey: String,
    content: @Composable () -> Unit,
) {
    var visible by remember(animationKey, rowKey) { mutableStateOf(false) }
    LaunchedEffect(animationKey, rowKey) {
        delay(if (rowIndex <= 12) rowIndex * 38L else 0L)
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = appTween(300),
        label = "constant-table-row-$rowKey",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            scaleY = .82f + (.18f * progress)
            translationY = (1f - progress) * 18f
            transformOrigin = TransformOrigin(0.5f, 0f)
        },
    ) {
        content()
    }
}

@Composable
private fun ConstantLevelHeader(level: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Text(
            level.toString(),
            color = AppAccent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ConstantValueHeader(constant: Double) {
    Text(
        "%.1f".format(Locale.US, constant),
        color = AppAccent,
        fontSize = 17.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, bottom = 1.dp),
    )
}

@Composable
private fun ConstantChartCard(
    entry: ConstantTableEntry,
    onClick: () -> Unit,
) {
    var useFallbackArtwork by remember(entry.song.id) { mutableStateOf(false) }
    val color = difficultyColor(entry.chart.difficulty)
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize(),
    ) {
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 96.dp)) {
            AsyncImage(
                model = lowArtworkRequest(
                    LocalContext.current,
                    entry.song.id,
                    if (useFallbackArtwork) fallbackIllustrationUrl(entry.song.id)
                    else illustrationUrl(entry.song.id),
                ),
                contentDescription = "${entry.song.name} 曲绘",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = { if (!useFallbackArtwork) useFallbackArtwork = true },
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to AppSurface,
                        .5f to AppSurface,
                        .78f to AppSurface.copy(alpha = .82f),
                        1f to AppSurface.copy(alpha = .08f),
                    ),
                ),
            )
            Column(
                Modifier.fillMaxWidth(.76f).padding(horizontal = 15.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    entry.song.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = .17f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            entry.chart.difficulty,
                            color = color,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        "%.1f".format(Locale.US, requireNotNull(entry.chart.chartConstant)),
                        color = AppTextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 9.dp),
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "查看曲目详情",
                        tint = AppTextMuted,
                        modifier = Modifier.padding(start = 4.dp).size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleSongPage(
    state: AppUiState,
    onSearch: (String) -> Unit,
    onEnsureSongImage: (SongScoreResult) -> Unit,
    onGenerateSongImage: (SongScoreResult) -> Unit,
) {
    var query by remember(state.songQuery) { mutableStateOf(state.songQuery) }
    var selectedSong by remember { mutableStateOf<SongScoreResult?>(null) }
    selectedSong?.let { song ->
        BackHandler { selectedSong = null }
        SongDetailPage(
            song = song,
            onBack = { selectedSong = null },
            songImageFile = state.songImageFile.takeIf { state.songImageSongId == song.songId },
            isGeneratingSongImage = state.isGeneratingSongImage && state.songImageSongId == song.songId,
            songImageGenerationElapsedSeconds = state.songImageGenerationElapsedSeconds,
            onEnsureSongImage = { onEnsureSongImage(song) },
            onGenerateSongImage = { onGenerateSongImage(song) },
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "单曲",
            if (state.hasSearchedSongs) "${state.songResults.size} 个结果" else "曲名、别名、曲师或 ID",
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppPageHorizontalPadding),
            label = { Text("搜索曲目") },
            placeholder = { Text("搜索曲目") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                IconButton(
                    onClick = { onSearch(query) },
                    enabled = query.isNotBlank() && !state.isLoading,
                ) {
                    if (state.isLoading) RefreshIcon(true) else Icon(Icons.Default.Search, "查询")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (query.isNotBlank() && !state.isLoading) onSearch(query)
            }),
        )
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = when {
                !state.hasSearchedSongs -> 0
                state.songResults.isEmpty() -> 1
                else -> 2
            },
            transitionSpec = { fadeIn(appTween(220)) togetherWith fadeOut(appTween(140)) },
            label = "song-search-result",
            modifier = Modifier.fillMaxSize(),
        ) { contentState ->
            when (contentState) {
                0 -> SongSearchIntro()
                1 -> EmptyState(
                    "没有找到成绩",
                    "仅显示当前存档中存在成绩的曲目，可尝试曲名、别名、曲师或曲目 ID。",
                    { onSearch(query) },
                    "重新查询",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(state.songResults, key = { _, song -> song.songId }) { index, song ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(appTween(220, delayMillis = (index.coerceAtMost(6) * 45))) +
                                slideInVertically(appTween(220, delayMillis = (index.coerceAtMost(6) * 45))) { it / 8 },
                        ) {
                            SongScoreCard(song, onClick = { selectedSong = song })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongSearchIntro() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Search, null, tint = AppTextMuted.copy(alpha = .66f), modifier = Modifier.size(32.dp))
        Text("输入关键词开始搜索", color = AppTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun SongScoreCard(song: SongScoreResult, onClick: () -> Unit) {
    var useFallbackArtwork by remember(song.songId) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = lowArtworkRequest(
                        LocalContext.current,
                        song.songId,
                        if (useFallbackArtwork) fallbackIllustrationUrl(song.songId)
                        else illustrationUrl(song.songId),
                    ),
                    contentDescription = "${song.songName} 曲绘",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    onError = { if (!useFallbackArtwork) useFallbackArtwork = true },
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(
                            0f to AppSurface,
                            .48f to AppSurface,
                            .76f to AppSurface.copy(alpha = .82f),
                            1f to AppSurface.copy(alpha = .08f),
                        ),
                    ),
                )
                Column(Modifier.fillMaxWidth(.76f).padding(14.dp)) {
                    Text(song.songName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(song.composer, song.illustrator).filter(String::isNotBlank).joinToString(" · "),
                        color = AppTextMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                song.records.forEachIndexed { index, record ->
                    SongDifficultyRow(record)
                    if (index != song.records.lastIndex) {
                        HorizontalDivider(Modifier.padding(vertical = 9.dp), color = AppSurfaceRaised)
                    }
                }
            }
        }
    }
}

@Composable
private fun SongDetailPage(
    song: SongScoreResult,
    onBack: () -> Unit,
    songImageFile: File?,
    isGeneratingSongImage: Boolean,
    songImageGenerationElapsedSeconds: Int,
    onEnsureSongImage: () -> Unit,
    onGenerateSongImage: () -> Unit,
    backDescription: String = "返回单曲成绩",
) {
    var useFallbackArtwork by remember(song.songId) { mutableStateOf(false) }
    var showArtwork by rememberSaveable(song.songId) { mutableStateOf(false) }
    val practiceContext = LocalContext.current
    val practiceEntries by PracticeCharts.entries.collectAsState()
    val practiceLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(song.songId, practiceLifecycle) {
        practiceLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                PracticeCharts.refresh(practiceContext)
                kotlinx.coroutines.delay(15_000)
            }
        }
    }
    LaunchedEffect(song.songId) { onEnsureSongImage() }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = if (useFallbackArtwork) {
                lowArtworkRequest(LocalContext.current, song.songId, fallbackIllustrationUrl(song.songId))
            } else {
                fullArtworkRequest(LocalContext.current, song.songId, fullIllustrationUrl(song.songId))
            },
            contentDescription = "${song.songName} 完整曲绘",
            contentScale = ContentScale.Crop,
            onError = {
                if (!useFallbackArtwork) useFallbackArtwork = true
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to AppBackground.copy(alpha = .22f),
                    .36f to AppBackground.copy(alpha = .52f),
                    .68f to AppBackground.copy(alpha = .9f),
                    1f to AppBackground,
                ),
            ),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .34f)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, backDescription, tint = Color.White)
                }
            }
            Box(
                Modifier.fillMaxWidth().height(176.dp)
                    .clickable(onClickLabel = "查看完整曲绘") { showArtwork = true },
            )
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(AppBackground.copy(alpha = .96f))
                    .padding(horizontal = AppPageHorizontalPadding, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(song.songName, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                SongMetadataCard(song)
                practiceEntries.filter { it.songId == song.songId }.forEach { source ->
                    androidx.compose.runtime.key(source.fileName) {
                        PracticeEntryButton(source, Modifier.fillMaxWidth())
                    }
                }
                SongScoreImageCard(
                    image = songImageFile,
                    isGenerating = isGeneratingSongImage,
                    elapsedSeconds = songImageGenerationElapsedSeconds,
                    onGenerate = onGenerateSongImage,
                )
                Text("谱面信息", color = AppAccent, fontSize = 15.sp, fontWeight = FontWeight.Black)
                song.charts.forEach { chart ->
                    val record = song.records.firstOrNull {
                        it.difficulty.equals(chart.difficulty, ignoreCase = true)
                    }
                    SongChartDetailCard(chart, record)
                }
                if (song.charts.isEmpty()) {
                    Text("暂无可用谱面资料。", color = AppTextMuted)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    if (showArtwork) {
        SongArtworkPage(song = song, onDismiss = { showArtwork = false })
    }
}

@Composable
private fun SongScoreImageCard(
    image: File?,
    isGenerating: Boolean,
    elapsedSeconds: Int,
    onGenerate: () -> Unit,
) {
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }
    var showZoomedImage by remember(image?.absolutePath, image?.lastModified()) { mutableStateOf(false) }
    val saveScope = rememberCoroutineScope()
    val saveCurrentImage: () -> Unit = {
        image?.takeIf(File::exists)?.let { source ->
            saveScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { saveB30ImageToGallery(context, source, "Phi-Song-${System.currentTimeMillis()}.png") }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "已保存到相册" else "保存失败，请稍后重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) saveCurrentImage()
        else Toast.makeText(context, "需要存储权限才能保存到相册", Toast.LENGTH_SHORT).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("单曲成绩图", color = AppAccent, fontSize = 15.sp, fontWeight = FontWeight.Black)
        when {
            image?.exists() == true -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(image)
                        .memoryCacheKey("song-score-${image.lastModified()}-${image.length()}")
                        .build(),
                    contentDescription = "单曲成绩图",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(AppSurface)
                        .clickable { showZoomedImage = true },
                )
                if (isGenerating) {
                    Text(
                        "正在更新单曲成绩图 · 已用时 ${formatGenerationElapsed(elapsedSeconds)}",
                        color = AppTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = onGenerate, enabled = !isGenerating, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isGenerating) "正在更新" else "更新图片")
                    }
                    OutlinedButton(onClick = { showActions = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(6.dp))
                        Text("保存或分享")
                    }
                }
            }
            isGenerating -> Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(13.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        "正在生成单曲成绩图 · 已用时 ${formatGenerationElapsed(elapsedSeconds)}",
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            else -> EmptyState(
                "尚未生成单曲成绩图",
                "首次进入曲目详情时会自动生成，也可以手动生成。",
                onGenerate,
                "生成单曲成绩图",
            )
        }
    }
    if (showActions && image?.exists() == true) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text("保存或分享单曲成绩图") },
            confirmButton = {
                Button(
                    onClick = {
                        showActions = false
                        if (
                            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            galleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            saveCurrentImage()
                        }
                    },
                ) { Text("保存到相册") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showActions = false
                        shareImage(context, image, "分享单曲成绩图")
                    },
                ) { Text("系统分享") }
            },
        )
    }
    if (showZoomedImage && image?.exists() == true) {
        ZoomableB30ImageDialog(
            image = image,
            onDismiss = { showZoomedImage = false },
            contentDescription = "放大的单曲成绩图",
        )
    }
}

@Composable
private fun SongMetadataCard(song: SongScoreResult) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface.copy(alpha = .9f)), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SongMetadataLine("章节", song.chapter.ifBlank { "未分类" })
            SongMetadataLine("曲师", song.composer.ifBlank { "未知" })
            SongMetadataLine("曲绘", song.illustrator.ifBlank { "未知" })
        }
    }
}

@Composable
private fun SongMetadataLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = AppTextMuted, fontSize = 12.sp, modifier = Modifier.width(48.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SongChartDetailCard(
    chart: xyz.plcliangpicup.phigrosscore.data.SongChartInfo,
    record: SongDifficultyScore?,
) {
    val color = difficultyColor(chart.difficulty)
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurface.copy(alpha = .92f)),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = .26f), RoundedCornerShape(13.dp)),
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(chart.difficulty, color = color, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("定数 ${chart.chartConstant?.let { "%.1f".format(it) } ?: "--"}", fontWeight = FontWeight.Bold)
                    Text(
                        "物量 ${chart.noteCount?.let { "%,d".format(it) } ?: "--"}",
                        color = AppTextMuted,
                        fontSize = 12.sp,
                    )
                }
                record?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.4f".format(it.rankingScore), color = color, fontWeight = FontWeight.Black)
                        Text("Ranking Score", color = AppTextMuted, fontSize = 9.sp)
                    }
                }
            }
            chart.charter.takeIf(String::isNotBlank)?.let {
                Text("谱师：$it", color = AppTextMuted, fontSize = 12.sp)
            }
            record?.let {
                HorizontalDivider(color = AppTextMuted.copy(alpha = .14f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("成绩 ${"%,d".format(it.score)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("ACC ${"%.4f".format(it.accuracy)}%", color = AppTextMuted, fontSize = 12.sp)
                }
                pushAccLabel(it.pushAcc, it.pushAccHint)?.let { label ->
                    Text(label, color = color, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SongDifficultyRow(record: SongDifficultyScore) {
    val color = difficultyColor(record.difficulty)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = .16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(record.difficulty, color = color, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("%,d".format(record.score), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "ACC ${"%.4f".format(record.accuracy)}%${if (record.isFullCombo) " · FC" else ""}",
                color = AppTextMuted,
                fontSize = 11.sp,
            )
            pushAccLabel(record.pushAcc, record.pushAccHint)?.let { Text(it, color = color, fontSize = 10.sp) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.4f".format(record.rankingScore), color = color, fontWeight = FontWeight.Bold)
            Text("RKS", color = AppTextMuted, fontSize = 9.sp)
            Text("定数 ${record.chartConstant?.let { "%.1f".format(it) } ?: "--"}", color = AppTextMuted, fontSize = 10.sp)
        }
    }
}

private fun pushAccLabel(pushAcc: Double?, pushAccHint: String?): String? = when (pushAccHint) {
    "already_phi" -> "当前无法推分"
    "unreachable" -> "当前无法推分"
    "phi_only" -> "需要 Phi 才能推分"
    "target_acc" -> pushAcc?.let { "推分目标 ${"%.3f".format(it)}%" }
    else -> pushAcc?.let { "推分目标 ${"%.3f".format(it)}%" }
}

@Composable
private fun ImagePage(
    state: AppUiState,
    onGenerateB30Image: () -> Unit,
    onGenerateP30Image: () -> Unit,
    onOpenDrawer: () -> Unit,
    onDismissGuide: () -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    var actionPage by remember { mutableStateOf<Int?>(null) }
    var zoomPage by remember { mutableStateOf<Int?>(null) }
    val actionImage = when (actionPage) {
        0 -> state.imageFile
        1 -> state.p30ImageFile
        else -> null
    }
    val actionLabel = if (actionPage == 1) "P30" else "B30"
    val saveScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val drawerSwipeThreshold = with(density) { 56.dp.toPx() }
    val drawerEdgeWidth = with(density) { 40.dp.toPx() }
    val saveCurrentImage: () -> Unit = {
        actionImage?.takeIf(File::exists)?.let { source ->
            saveScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        saveB30ImageToGallery(
                            context,
                            source,
                            "Phi-$actionLabel-${System.currentTimeMillis()}.png",
                        )
                    }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "已保存到相册" else "保存失败，请稍后重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) saveCurrentImage()
        else Toast.makeText(context, "需要存储权限才能保存到相册", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(state.showImagePagerGuide) {
        if (state.showImagePagerGuide) {
            delay(500)
            if (pagerState.currentPage == 0) {
                pagerState.animateScrollToPage(1, animationSpec = appTween(650))
                delay(650)
                pagerState.animateScrollToPage(0, animationSpec = appTween(600))
            }
            delay(250)
            onDismissGuide()
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("成绩图片")
        Box(
            Modifier
                .weight(1f)
                .pointerInput(pagerState, onOpenDrawer, drawerSwipeThreshold, drawerEdgeWidth) {
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
                            if (
                                (pagerState.currentPage == 0 || start.x <= drawerEdgeWidth) &&
                                delta.x >= drawerSwipeThreshold &&
                                delta.x > abs(delta.y) * 1.25f
                            ) {
                                change.consume()
                                onOpenDrawer()
                                break
                            }
                        }
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val isP30 = page == 1
                RankingImagePanel(
                    label = if (isP30) "P30" else "B30",
                    image = if (isP30) state.p30ImageFile else state.imageFile,
                    isGenerating = if (isP30) state.isGeneratingP30Image else state.isGeneratingB30Image,
                    elapsedSeconds = if (isP30) {
                        state.p30ImageGenerationElapsedSeconds
                    } else {
                        state.b30ImageGenerationElapsedSeconds
                    },
                    onGenerate = if (isP30) onGenerateP30Image else onGenerateB30Image,
                    onZoom = { zoomPage = page },
                    onActions = { actionPage = page },
                )
            }
            if (state.showImagePagerGuide) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .94f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Text(
                        "滑动切换 B30 / P30",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(2) { page ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == page) 9.dp else 7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (pagerState.currentPage == page) AppAccent else AppTextMuted.copy(alpha = .38f)),
                )
            }
        }
    }
    if (actionPage != null && actionImage?.exists() == true) {
        AlertDialog(
            onDismissRequest = { actionPage = null },
            title = { Text("保存或分享 $actionLabel 图片") },
            text = { Text("可以直接保存到系统相册，也可以通过系统分享给其他应用。") },
            confirmButton = {
                Button(
                    onClick = {
                        actionPage = null
                        if (
                            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            galleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            saveCurrentImage()
                        }
                    },
                ) { Text("保存到相册") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        actionPage = null
                        shareImage(context, actionImage, "分享 $actionLabel 成绩图")
                    },
                ) { Text("系统分享") }
            },
        )
    }
    val zoomImage = if (zoomPage == 1) state.p30ImageFile else state.imageFile
    if (zoomPage != null && zoomImage?.exists() == true) {
        val zoomLabel = if (zoomPage == 1) "P30" else "B30"
        ZoomableB30ImageDialog(
            image = zoomImage,
            onDismiss = { zoomPage = null },
            contentDescription = "放大的 $zoomLabel 成绩图",
        )
    }
}

@Composable
private fun RankingImagePanel(
    label: String,
    image: File?,
    isGenerating: Boolean,
    elapsedSeconds: Int,
    onGenerate: () -> Unit,
    onZoom: () -> Unit,
    onActions: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        if (image?.exists() == true) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image)
                    .memoryCacheKey("ranking-$label-${image.lastModified()}-${image.length()}")
                    .build(),
                contentDescription = "$label 图片",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppSurface)
                    .clickable(onClick = onZoom),
                contentScale = ContentScale.FillWidth,
            )
            Text(
                "点击放大，支持双指缩放",
                color = AppTextMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onGenerate, enabled = !isGenerating) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (isGenerating) "正在生成" else "重新生成")
                }
                OutlinedButton(onClick = onActions) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(7.dp))
                    Text("保存或分享")
                }
            }
        } else if (isGenerating) {
            Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        "正在生成 $label… · ${formatGenerationElapsed(elapsedSeconds)}",
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        } else {
            EmptyState("尚未生成 $label", "", onGenerate, "生成 $label 图片")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun ZoomableB30ImageDialog(
    image: File,
    onDismiss: () -> Unit,
    contentDescription: String = "放大的 B30 成绩图",
) {
    var scale by remember(image.absolutePath, image.lastModified()) { mutableFloatStateOf(1f) }
    var offsetX by remember(image.absolutePath, image.lastModified()) { mutableFloatStateOf(0f) }
    var offsetY by remember(image.absolutePath, image.lastModified()) { mutableFloatStateOf(0f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .96f))) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image)
                    .memoryCacheKey("image-zoom-${image.absolutePath}-${image.lastModified()}-${image.length()}")
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(image.absolutePath, image.lastModified()) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 6f)
                            scale = nextScale
                            if (nextScale <= 1.01f) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )
            Text(
                "双指缩放 · 拖动查看",
                color = Color.White.copy(alpha = .76f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
            ) {
                Text("关闭", color = Color.White)
            }
        }
    }
}

@Composable
private fun MorePage(
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
    onImportCustomRanking: () -> Unit,
    onClearCustomRanking: () -> Unit,
    onCheckin: (String, Boolean) -> Unit,
    onCheckinRanks: () -> Unit,
    onStartRksGuess: (String) -> Unit,
    onRefreshRksGuess: () -> Unit,
    onSubmitRksGuessAnswer: (Double) -> Unit,
    onContinueRksGuessRound: () -> Unit,
    onLeaveRksGuessGame: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MoreFeaturesPage(
            state = state,
            onRefreshB30 = onRefreshB30,
            onLoadRandomSuggestion = onLoadRandomSuggestion,
            onLoadOwnSuggestionPosts = onLoadOwnSuggestionPosts,
            onOpenSuggestionPost = onOpenSuggestionPost,
            onRksCalculatorDraftChange = onRksCalculatorDraftChange,
            onSubmitSuggestionPost = onSubmitSuggestionPost,
            onSubmitSuggestionComment = onSubmitSuggestionComment,
            onDeleteSuggestionPost = onDeleteSuggestionPost,
            onDeleteSuggestionComment = onDeleteSuggestionComment,
            onSuggestionNotificationsChange = onSuggestionNotificationsChange,
            onDismissSuggestionSwipeGuide = onDismissSuggestionSwipeGuide,
            onSearchAchievementSongs = onSearchAchievementSongs,
            onLoadAchievementRates = onLoadAchievementRates,
            onGenerateCustomRankingImage = onGenerateCustomRankingImage,
            onImportCustomRanking = onImportCustomRanking,
            onClearCustomRanking = onClearCustomRanking,
            onCheckin = onCheckin,
                      onCheckinRanks = onCheckinRanks,
                      onStartRksGuess = onStartRksGuess,
            onRefreshRksGuess = onRefreshRksGuess,
            onSubmitRksGuessAnswer = onSubmitRksGuessAnswer,
            onContinueRksGuessRound = onContinueRksGuessRound,
            onLeaveRksGuessGame = onLeaveRksGuessGame,
            modifier = Modifier.weight(1f),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ExperienceSurveyDialog(onDismiss: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val current = webView
        if (current?.canGoBack() == true) current.goBack() else onDismiss()
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(AppBackground)
                .padding(WindowInsets.safeDrawing.asPaddingValues()),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Phi Score Query 使用体验调查问卷",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭问卷")
                }
            }
            HorizontalDivider(color = AppTextMuted.copy(alpha = .16f))
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(EXPERIENCE_SURVEY_URL)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun SettingsPage(
    state: AppUiState,
    onClearCache: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onNavigationHandleVisibilityChange: (Boolean) -> Unit,
    onSwipeNavigationChange: (Boolean) -> Unit,
    onB30ImageStyleChange: (B30ImageStyle) -> Unit,
    onSongScoreImageStyleChange: (SongScoreImageStyle) -> Unit,
    onOpenSurvey: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRefreshAnnouncements: () -> Unit,
    onRevealSessionToken: () -> Unit,
    onHideSessionToken: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmRevealSessionToken by rememberSaveable { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var showAnnouncements by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPracticeResources by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val noiseSettings = remember(context) { PracticeNoiseSettings(context) }
    var noiseCompatibilityMode by remember { mutableStateOf(noiseSettings.compatibilityMode) }
    val feedbackUnread by FeedbackNotificationManager.observeInAppUnread(context).collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("设置", BuildConfig.VERSION_NAME)
        Column(
            Modifier.padding(horizontal = AppPageHorizontalPadding).widthIn(max = AppReadableMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsSectionLabel("外观")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppSurface)) {
            ThemeSetting(
                isDarkTheme = state.isDarkTheme,
                onThemeChange = onThemeChange,
            )
            B30ImageStyleSetting(state.b30ImageStyle, onB30ImageStyleChange)
            SongScoreImageStyleSetting(state.songScoreImageStyle, onSongScoreImageStyleChange)
            }
            SettingsSectionLabel("应用")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppSurface)) {
            SwipeNavigationSetting(state.useSwipeNavigation, onSwipeNavigationChange)
            AutoRefreshSetting(state.autoRefreshOnLaunch, onAutoRefreshChange)
            AppUpdateSetting(
                enabled = state.autoCheckAppUpdates,
                isChecking = state.isCheckingAppUpdate,
                onEnabledChange = onAutoUpdateChange,
                onCheckUpdate = onCheckUpdate,
            )
            SettingCard(
                Icons.Default.FolderOpen,
                "谱面资源管理",
                "查看与删除已下载的谱面",
                onClick = { showPracticeResources = true },
            )
            }
            SettingsSectionLabel("谱面播放")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppSurface)) {
                NoiseCompatibilitySetting(noiseCompatibilityMode) { enabled ->
                    noiseSettings.setCompatibilityMode(enabled)
                    noiseCompatibilityMode = enabled
                }
            }
            SettingsSectionLabel("反馈")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppSurface)) {
                SettingCard(Icons.Default.RateReview, "提交反馈", "问题与建议", onClick = { FeedbackNavigation.destination.value = "create" })
                SettingCard(
                    icon = Icons.Default.History,
                    title = "我的反馈",
                    detail = if (feedbackUnread > 0) "$feedbackUnread 条反馈有新进展" else "查看处理状态与管理员回复",
                    onClick = { FeedbackNavigation.destination.value = "mine" },
                    hasUnread = feedbackUnread > 0,
                )
            }
            SettingsSectionLabel("账户与信息")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppSurface)) {
            SettingCard(
                Icons.Default.Lock,
                "获取 SessionToken",
                if (state.hasStoredSessionToken) "本机加密保存" else "需重新登录后获取",
                onClick = { confirmRevealSessionToken = true },
            )
            SettingCard(
                Icons.Default.History,
                "更新日志",
                null,
                onClick = { showChangelog = true },
            )
            SettingCard(
                Icons.Default.Campaign,
                "公告",
                when {
                    state.announcementHistory.isNotEmpty() -> "共 ${state.announcementHistory.size} 条公告 · 最新发布在前"
                    state.hasLoadedAnnouncementHistory && state.announcementHistoryError == null -> "暂无公告"
                    else -> "查看全部历史公告"
                },
                onClick = {
                    showAnnouncements = true
                    onRefreshAnnouncements()
                },
            )
            SettingCard(
                Icons.Default.Info,
                "关于",
                "开源许可与项目源码",
                onClick = { showAbout = true },
            )
            SettingCard(
                Icons.Default.RateReview,
                "体验问卷",
                null,
                onClick = onOpenSurvey,
            )
            }
            OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Cached, null)
                Spacer(Modifier.width(8.dp))
                Text("清除缓存")
            }
            Button(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppDanger, contentColor = Color.White),
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                Spacer(Modifier.width(8.dp))
                Text("退出登录")
            }
            Text(
                "本项目为非官方玩家项目，与南京鸽游网络有限公司及《Phigros》官方不存在授权、合作或运营关系。",
                color = AppTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 20.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
    if (showChangelog) {
        ChangelogSheet(onDismiss = { showChangelog = false })
    }
    if (showAnnouncements) {
        AnnouncementHistorySheet(
            announcements = state.announcementHistory,
            isLoading = state.isAnnouncementHistoryLoading,
            hasLoaded = state.hasLoadedAnnouncementHistory,
            error = state.announcementHistoryError,
            onRefresh = onRefreshAnnouncements,
            onDismiss = { showAnnouncements = false },
        )
    }
    if (showAbout) {
        AboutSheet(onDismiss = { showAbout = false })
    }
    if (showPracticeResources) {
        PracticeResourceManagementSheet(onDismiss = { showPracticeResources = false })
    }
    if (confirmRevealSessionToken) {
        AlertDialog(
            onDismissRequest = { confirmRevealSessionToken = false },
            title = { Text("确认显示 SessionToken？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "SessionToken 相当于账号登录凭证，任何获得者都可能读取你的存档。",
                        color = AppDanger,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("不可向任何人泄露、不可截图分享，也不要在录屏或公共场合中显示。")
                    Text("请确认周围无人且屏幕未被录制后再继续。", color = AppTextMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRevealSessionToken = false
                        onRevealSessionToken()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppDanger),
                ) { Text("我已了解风险，继续显示") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevealSessionToken = false }) { Text("取消") }
            },
        )
    }
    state.revealedSessionToken?.let { token ->
        AlertDialog(
            onDismissRequest = onHideSessionToken,
            title = { Text("SessionToken") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "严禁向外泄露。关闭弹窗前请确认没有截屏、录屏或他人窥视。",
                        color = AppDanger,
                        fontWeight = FontWeight.Bold,
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppSurfaceRaised),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                token,
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onHideSessionToken) { Text("关闭") }
            },
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("确认退出登录？") },
            text = { Text("本机保存的安全会话、B30 数据和图片缓存都会被删除。") },
            confirmButton = {
                Button(
                    onClick = { confirmLogout = false; onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppDanger),
                ) { Text("退出并清除") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        color = AppTextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 3.dp),
    )
}

@Composable
private fun ThemeSetting(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = MaterialTheme.shapes.medium) {
        CompactSettingRow("页面风格") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoice(
                    label = "白日",
                    icon = Icons.Default.WbSunny,
                    selected = !isDarkTheme,
                    onClick = { if (isDarkTheme) onThemeChange(false) },
                    modifier = Modifier.weight(1f),
                )
                ThemeChoice(
                    label = "黑夜",
                    icon = Icons.Default.DarkMode,
                    selected = isDarkTheme,
                    onClick = { if (!isDarkTheme) onThemeChange(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
@Composable
private fun B30ImageStyleSetting(
    style: B30ImageStyle,
    onStyleChange: (B30ImageStyle) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        CompactSettingRow("B30 / P30 样式") {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(b30ImageStyleLabel(style), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 13.sp)
                Icon(Icons.Default.ExpandMore, "展开样式列表", modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(appTween(160)) + expandVertically(appTween(220)),
            exit = fadeOut(appTween(120)) + shrinkVertically(appTween(180)),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp)).background(AppBackground)) {
                B30ImageStyle.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            expanded = false
                            onStyleChange(option)
                        }.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(b30ImageStyleLabel(option), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (option == style) Icon(Icons.Default.CheckCircle, "已选择", tint = AppAccent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactSettingRow(label: String, controls: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        // Stack controls for narrow screens and accessibility text sizes.
        if (maxWidth < 330.dp || LocalDensity.current.fontScale > 1.15f) {
            Column {
                Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                controls()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Box(Modifier.width(194.dp)) { controls() }
            }
        }
    }
}

private val CONSTANT_DIFFICULTIES = listOf("EZ", "HD", "IN", "AT")
private const val CONSTANT_DIFFICULTY_ALL_MASK = 0b1111

private fun constantDifficultyBit(difficulty: String): Int = when (difficulty.uppercase(Locale.US)) {
    "EZ" -> 0b0001
    "HD" -> 0b0010
    "IN" -> 0b0100
    "AT" -> 0b1000
    else -> 0
}

private fun b30ImageStyleLabel(style: B30ImageStyle): String = when (style) {
    B30ImageStyle.CLASSIC -> "经典"
    B30ImageStyle.MINIMAL -> "简约"
    B30ImageStyle.PHI_PLUGIN -> "Phi-Plugin"
}

@Composable
private fun SongScoreImageStyleSetting(
    style: SongScoreImageStyle,
    onStyleChange: (SongScoreImageStyle) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = MaterialTheme.shapes.medium) {
        CompactSettingRow("单曲图片") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoice(
                    label = "默认",
                    icon = Icons.Default.Image,
                    selected = style == SongScoreImageStyle.DEFAULT,
                    onClick = { onStyleChange(SongScoreImageStyle.DEFAULT) },
                    modifier = Modifier.weight(1f),
                )
                ThemeChoice(
                    label = "Legacy",
                    icon = Icons.Default.History,
                    selected = style == SongScoreImageStyle.LEGACY,
                    onClick = { onStyleChange(SongScoreImageStyle.LEGACY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AutoRefreshSetting(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("启动时更新存档", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun NoiseCompatibilitySetting(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("噪域兼容模式", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text("使用旧版噪域样式", color = AppTextMuted, fontSize = 12.sp)
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun AppUpdateSetting(
    enabled: Boolean,
    isChecking: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动检查更新", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            TextButton(
                onClick = onCheckUpdate,
                enabled = !isChecking,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Default.SystemUpdate, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isChecking) "正在检查" else "检查更新")
            }
        }
    }
}

@Composable
private fun NavigationHandleSetting(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("显示侧边导航箭头", fontWeight = FontWeight.Bold)
                Text(
                    if (enabled) "可拖动或隐藏" else "从左侧边缘右滑打开",
                    color = AppTextMuted,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
internal fun ThemeChoice(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AppAccent.copy(alpha = .075f) else Color.Transparent,
            contentColor = if (selected) AppAccent else MaterialTheme.colorScheme.onSurface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) AppAccent.copy(alpha = .4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f),
        ),
    ) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    detail: String?,
    onClick: (() -> Unit)? = null,
    hasUnread: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.appPressMotion(interactionSource).clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(cardModifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(AppAccent.copy(alpha = .11f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = AppAccent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                detail?.let { Text(it, color = AppTextMuted, fontSize = 11.sp, lineHeight = 15.sp) }
            }
            if (hasUnread) {
                InAppUnreadDot(Modifier.padding(end = 8.dp))
            }
            if (onClick != null) {
                Icon(Icons.Default.ChevronRight, null, tint = AppTextMuted.copy(alpha = .72f), modifier = Modifier.padding(start = 8.dp).size(19.dp))
            }
        }
    }
}

@Composable
private fun ChangelogSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.88f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text("更新日志", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(
                "从最初的调试版本到 ${BuildConfig.VERSION_NAME}",
                color = AppTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = 18.dp),
            )
            changelogEntries.forEachIndexed { index, entry ->
                ChangelogCard(entry, index)
                if (index != changelogEntries.lastIndex) Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AnnouncementHistorySheet(
    announcements: List<AppAnnouncement>,
    isLoading: Boolean,
    hasLoaded: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(.88f),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "公告",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "所有公告按发布时间倒序收纳，最新公告显示在最前",
                            color = AppTextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新公告", tint = AppAccent)
                    }
                }

                when {
                    announcements.isEmpty() && (!hasLoaded || isLoading) -> {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppAccent)
                        }
                    }

                    announcements.isEmpty() && error != null -> {
                        AnnouncementHistoryError(
                            error = error,
                            onRefresh = onRefresh,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    announcements.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.Campaign, null, tint = AppTextMuted, modifier = Modifier.size(34.dp))
                            Text(
                                "暂无公告",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                "新公告发布后会收纳在这里",
                                color = AppTextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    else -> {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    top = 18.dp,
                                    end = 20.dp,
                                    bottom = 32.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (error != null) {
                                    item(key = "announcement-load-error") {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = AppDanger.copy(alpha = .08f),
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(Icons.Default.Warning, null, tint = AppDanger)
                                                Text(
                                                    "刷新失败，当前显示上次加载的公告",
                                                    color = AppTextMuted,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                                                )
                                                TextButton(onClick = onRefresh) { Text("重试") }
                                            }
                                        }
                                    }
                                }
                                itemsIndexed(
                                    items = announcements,
                                    key = { _, item -> item.id },
                                ) { index, announcement ->
                                    AnnouncementHistoryCard(
                                        announcement = announcement,
                                        isLatest = index == 0,
                                        animationIndex = index,
                                    )
                                }
                            }
                            if (isLoading) {
                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementHistoryError(
    error: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Warning, null, tint = AppDanger, modifier = Modifier.size(36.dp))
        Text(
            "公告加载失败",
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            modifier = Modifier.padding(top = 13.dp),
        )
        Text(
            error,
            color = AppTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        FilledTonalButton(onClick = onRefresh, modifier = Modifier.padding(top = 16.dp)) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(7.dp))
            Text("重新加载")
        }
    }
}

@Composable
private fun AnnouncementHistoryCard(
    announcement: AppAnnouncement,
    isLatest: Boolean,
    animationIndex: Int,
) {
    var visible by remember(announcement.id) { mutableStateOf(false) }
    LaunchedEffect(announcement.id) {
        delay(animationIndex.coerceAtMost(6) * 38L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(appTween(260)) + slideInVertically(appTween(320)) { it / 7 },
        exit = fadeOut(appTween(140)),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isLatest) AppSurfaceRaised else AppBackground,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isLatest) {
                        Modifier.border(1.dp, AppAccent.copy(alpha = .38f), RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(Modifier.fillMaxWidth().padding(17.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            announcement.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 23.sp,
                        )
                        announcement.publishedAt?.takeIf(String::isNotBlank)?.let { publishedAt ->
                            Text(
                                publishedAt,
                                color = AppTextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                    if (isLatest) {
                        Text(
                            "最新",
                            color = AppAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(AppAccent.copy(alpha = .12f))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                HorizontalDivider(
                    color = AppTextMuted.copy(alpha = .14f),
                    modifier = Modifier.padding(vertical = 14.dp),
                )
                SelectionContainer {
                    Text(
                        announcement.body,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.78f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("关于", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Card(
                colors = CardDefaults.cardColors(containerColor = AppBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.author_avatar),
                        contentDescription = "作者头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(82.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, AppAccent.copy(alpha = .65f), RoundedCornerShape(22.dp)),
                    )
                    Column(
                        Modifier.padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            "Phi Score Query - ${BuildConfig.VERSION_NAME}",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text("• Developed by ...", color = AppTextMuted, fontSize = 13.sp)
                        Text("• Special thanks to 塔弦：致」", color = AppTextMuted, fontSize = 13.sp)
                    }
                }
            }
            Text("开源信息", color = AppAccent, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(
                "非官方 Phigros 成绩查询工具，与 Pigeon Games 或 TapTap 无隶属关系。客户端代码采用 Apache-2.0 许可证；本项目使用并修改的后端代码沿用 GNU AGPL v3。",
                color = AppTextMuted,
                fontSize = 13.sp,
            )
            SettingCard(
                Icons.AutoMirrored.Filled.OpenInNew,
                "Phi-Score-Query",
                "客户端源码、服务器工具与实际部署的后端对应源码",
                onClick = { openExternalUrl(context, PROJECT_REPOSITORY_URL) },
            )
            SettingCard(
                Icons.AutoMirrored.Filled.OpenInNew,
                "Next-Phi-Backend",
                "Sczr0 维护的上游后端项目",
                onClick = { openExternalUrl(context, BACKEND_REPOSITORY_URL) },
            )
            Text(
                "继续访问 GitHub 即可查看完整许可证、修改说明和构建方式。",
                color = AppTextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ChangelogCard(entry: ChangelogEntry, index: Int) {
    var visible by remember(entry.version) { mutableStateOf(false) }
    LaunchedEffect(entry.version) {
        delay(index.coerceAtMost(6) * 45L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(appTween(300)) + slideInVertically(appTween(380)) { it / 5 },
        exit = fadeOut(appTween(140)),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (index == 0) AppSurfaceRaised else AppBackground,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (index == 0) {
                        Modifier.border(1.dp, AppAccent.copy(alpha = .35f), RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.version, color = AppAccent, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    if (entry.label.isNotEmpty()) Text(
                        entry.label,
                        color = AppTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 9.dp),
                    )
                }
                entry.changes.forEach { change ->
                    Row(Modifier.padding(top = 9.dp)) {
                        Text("•", color = AppAccent, fontWeight = FontWeight.Bold)
                        Text(change, color = AppTextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    action: () -> Unit,
    actionText: String = "刷新",
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(AppAccent.copy(alpha = .10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.BarChart, null, tint = AppAccent, modifier = Modifier.size(25.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
        Text(detail, color = AppTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp, bottom = 16.dp))
        FilledTonalButton(onClick = action) { Text(actionText) }
    }
}

@Composable
private fun RefreshIcon(loading: Boolean) {
    val transition = rememberInfiniteTransition(label = "refresh-rotation")
    val animatedRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(appTween(900, easing = LinearEasing)),
        label = "refresh-angle",
    )
    Icon(
        Icons.Default.Refresh,
        "刷新",
        modifier = Modifier.graphicsLayer { rotationZ = if (loading) animatedRotation else 0f },
    )
}

@Composable
private fun difficultyColor(difficulty: String): Color = when (difficulty) {
    "EZ" -> Color(0xFF78E08F)
    "HD" -> Color(0xFF8FD3FF)
    "IN" -> Color(0xFFFF5C68)
    "AT" -> Color(0xFFA7ADB8)
    else -> AppAccent
}

private fun formatTime(epochMs: Long): String = runCatching {
    DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
}.getOrDefault("未知")

private fun formatSaveUpdatedAt(saveUpdatedAt: String?, cachedAtEpochMs: Long): String {
    val instant = saveUpdatedAt
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
                ?: runCatching { java.time.OffsetDateTime.parse(value).toInstant() }.getOrNull()
        }
        ?: Instant.ofEpochMilli(cachedAtEpochMs)
    return runCatching {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault(formatTime(cachedAtEpochMs))
}

private fun formatGenerationElapsed(seconds: Int): String = when {
    seconds < 60 -> "${seconds}秒"
    else -> "${seconds / 60}分${(seconds % 60).toString().padStart(2, '0')}秒"
}

private fun illustrationUrl(songId: String): String =
    "${BuildConfig.API_BASE_URL.trimEnd('/')}/_ill/illLow/${Uri.encode(songId)}.png"

internal fun fullIllustrationUrl(songId: String): String =
    "${BuildConfig.API_BASE_URL.trimEnd('/')}/_ill/ill/${Uri.encode(songId)}.png"

private fun fallbackIllustrationUrl(songId: String): String =
    "https://raw.githubusercontent.com/Catrong/phi-plugin-ill/main/illLow/${Uri.encode(songId)}.png"

internal fun lowArtworkRequest(context: Context, songId: String, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(if (url.startsWith(BuildConfig.API_BASE_URL)) "$url?preview=1" else url)
        .size(720, 380)
        .scale(coil.size.Scale.FILL)
        .precision(coil.size.Precision.INEXACT)
        .memoryCacheKey("illustration-low-v2-$songId")
        .diskCacheKey("illustration-low-v2-$songId")
        .build()

private fun fullArtworkRequest(context: Context, songId: String, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data("$url?preview=1")
        .memoryCacheKey("illustration-preview-v2-$songId")
        .diskCacheKey("illustration-preview-v2-$songId")
        .placeholderMemoryCacheKey("illustration-low-v2-$songId")
        .build()

internal fun avatarImageRequest(context: Context, avatar: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(avatarUrl(avatar))
        .size(320)
        .scale(coil.size.Scale.FIT)
        .precision(coil.size.Precision.INEXACT)
        .memoryCacheKey("avatar-${avatarAssetKey(avatar)}")
        .diskCacheKey("avatar-${avatarAssetKey(avatar)}")
        .build()

internal fun avatarAssetKey(avatar: String): String = MessageDigest.getInstance("SHA-256")
    .digest(avatar.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun avatarUrl(avatar: String): String =
    "${BuildConfig.API_BASE_URL.trimEnd('/')}/avatar/${avatarAssetKey(avatar)}.png"

internal fun String?.validAvatarName(): String? = this
    ?.trim()
    ?.takeIf { value ->
        value.isNotEmpty() && value != "..." && value.none(Char::isISOControl)
    }

internal fun shareImage(
    context: Context,
    file: File,
    chooserTitle: String = "分享 B30 成绩图",
) {
    val uri: Uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

@Suppress("DEPRECATION")
internal fun saveB30ImageToGallery(
    context: Context,
    source: File,
    fileName: String = "Phi-B30-${System.currentTimeMillis()}.png",
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册图片")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("无法写入相册图片")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    } else {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        if (!pictures.exists() && !pictures.mkdirs()) error("无法访问系统相册")
        val destination = File(pictures, fileName)
        source.copyTo(destination, overwrite = false)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            arrayOf("image/png"),
            null,
        )
    }
}

private fun launchApkInstaller(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "无法打开链接，请检查浏览器设置", Toast.LENGTH_SHORT).show() }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "大小未知"
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) "%.1f MB".format(megabytes) else "%.0f KB".format(bytes / 1024.0)
}
