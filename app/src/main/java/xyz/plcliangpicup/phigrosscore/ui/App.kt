@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.plcliangpicup.phigrosscore.ui

import android.Manifest
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import xyz.plcliangpicup.phigrosscore.data.B30Item
import xyz.plcliangpicup.phigrosscore.data.B30ImageStyle
import xyz.plcliangpicup.phigrosscore.data.B30Snapshot
import xyz.plcliangpicup.phigrosscore.data.AppUpdateManifest
import xyz.plcliangpicup.phigrosscore.data.ConstantTableEntry
import xyz.plcliangpicup.phigrosscore.data.LoginProgress
import xyz.plcliangpicup.phigrosscore.data.LeaderboardEntry
import xyz.plcliangpicup.phigrosscore.data.ScoreSnapshotEntry
import xyz.plcliangpicup.phigrosscore.data.SongDifficultyScore
import xyz.plcliangpicup.phigrosscore.data.SongScoreResult
import xyz.plcliangpicup.phigrosscore.data.calculateP30Rks
import xyz.plcliangpicup.phigrosscore.data.selectBestCharts
import xyz.plcliangpicup.phigrosscore.data.selectPerfectCharts
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private data class NavItem(val page: AppPage, val title: String, val icon: ImageVector)

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
    NavItem(AppPage.SETTINGS, "设置", Icons.Default.Settings),
)

private const val PROJECT_REPOSITORY_URL = "https://github.com/SanGeDian123/Phi-Score-Query"
private const val BACKEND_REPOSITORY_URL = "https://github.com/Sczr0/Next-Phi-Backend"

private val changelogEntries = listOf(
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
            "课题模式等级改用紧凑单字颜色标记，例如绿12、黄21、红49、彩51。",
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
                onSearchSong = viewModel::searchSong,
                onOpenConstantSong = viewModel::constantSongDetail,
                onEnsureSongImage = viewModel::ensureSongImage,
                onGenerateSongImage = viewModel::generateSongImage,
                onGenerateImage = viewModel::generateImage,
                onClearCache = viewModel::clearCache,
                onThemeChange = viewModel::setDarkTheme,
                onAutoRefreshChange = viewModel::setAutoRefreshOnLaunch,
                onAutoUpdateChange = viewModel::setAutoCheckAppUpdates,
                onNavigationHandleVisibilityChange = viewModel::setShowNavigationHandle,
                onNavigationHandlePositionChange = viewModel::setNavigationHandlePosition,
                onB30ImageStyleChange = viewModel::setB30ImageStyle,
                onCheckUpdate = { viewModel.checkAppUpdate(silent = false) },
                onRevealSessionToken = viewModel::revealSessionToken,
                onHideSessionToken = viewModel::hideSessionToken,
                onLogout = viewModel::logout,
                onDismissNavigationGuide = viewModel::dismissNavigationGuide,
            )
        }
        if (!state.isLoggedIn) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        state.availableAppUpdate?.let { update ->
            AppUpdateDialog(
                update = update,
                state = state,
                onDismiss = viewModel::dismissAppUpdate,
                onDownload = viewModel::downloadAppUpdate,
                onInstall = installUpdate,
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
private fun LoginScreen(
    progress: LoginProgress,
    onTokenLogin: (String) -> Unit,
    onQrLogin: () -> Unit,
    onCancelQr: () -> Unit,
) {
    var tokenDialog by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= 700.dp
        val contentModifier = Modifier
            .fillMaxWidth(if (wide) 0.68f else 1f)
            .padding(horizontal = if (wide) 36.dp else 24.dp, vertical = 24.dp)
        Column(contentModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(24.dp)).background(AppAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Phi\nScore\nQuery",
                    color = Color(0xFF042019),
                    fontSize = 16.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "Phi Score Query",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text("非官方 Phigros 成绩查询工具", color = AppTextMuted, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(38.dp))

            when (val current = progress) {
                LoginProgress.Idle, is LoginProgress.Failed -> {
                    Button(
                        onClick = onQrLogin,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.QrCode2, null)
                        Spacer(Modifier.width(10.dp))
                        Text("使用 TapTap 扫码登录")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { tokenDialog = true },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(8.dp),
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
                    Text("请使用另一台设备上的 TapTap 扫描", color = AppTextMuted, fontSize = 13.sp)
                    TextButton(onClick = onCancelQr) { Text("取消") }
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = AppTextMuted, modifier = Modifier.size(14.dp))
                Text(
                    "SessionToken 将通过 Android Keystore 加密保存在本机",
                    color = AppTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
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
private fun MainShell(
    state: AppUiState,
    snackbar: SnackbarHostState,
    onPage: (AppPage) -> Unit,
    onRefresh: () -> Unit,
    onRefreshLeaderboard: () -> Unit,
    onSearchSong: (String) -> Unit,
    onOpenConstantSong: (String) -> SongScoreResult?,
    onEnsureSongImage: (SongScoreResult) -> Unit,
    onGenerateSongImage: (SongScoreResult) -> Unit,
    onGenerateImage: () -> Unit,
    onClearCache: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onNavigationHandleVisibilityChange: (Boolean) -> Unit,
    onNavigationHandlePositionChange: (Float) -> Unit,
    onB30ImageStyleChange: (B30ImageStyle) -> Unit,
    onCheckUpdate: () -> Unit,
    onRevealSessionToken: () -> Unit,
    onHideSessionToken: () -> Unit,
    onLogout: () -> Unit,
    onDismissNavigationGuide: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = AppSurface) {
                Column(
                    Modifier.fillMaxHeight().width(280.dp).padding(WindowInsets.safeDrawing.asPaddingValues()),
                ) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                        Text("Phi Score Query", fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text(BuildConfig.VERSION_NAME, color = AppTextMuted, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = AppTextMuted.copy(alpha = .16f))
                    navItems.forEach { item ->
                        NavigationDrawerItem(
                            selected = state.page == item.page,
                            onClick = {
                                onPage(item.page)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.title) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "从屏幕左侧向右滑动，可随时打开导航",
                        color = AppTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(22.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            containerColor = AppBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                PageContent(
                    state = state,
                    snackbar = snackbar,
                    onPage = onPage,
                    onRefresh = onRefresh,
                    onRefreshLeaderboard = onRefreshLeaderboard,
                    onSearchSong = onSearchSong,
                    onOpenConstantSong = onOpenConstantSong,
                    onEnsureSongImage = onEnsureSongImage,
                    onGenerateSongImage = onGenerateSongImage,
                    onGenerateImage = onGenerateImage,
                    onClearCache = onClearCache,
                    onThemeChange = onThemeChange,
                    onAutoRefreshChange = onAutoRefreshChange,
                    onAutoUpdateChange = onAutoUpdateChange,
                    onNavigationHandleVisibilityChange = onNavigationHandleVisibilityChange,
                    onB30ImageStyleChange = onB30ImageStyleChange,
                    onCheckUpdate = onCheckUpdate,
                    onRevealSessionToken = onRevealSessionToken,
                    onHideSessionToken = onHideSessionToken,
                    onLogout = onLogout,
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.showNavigationHandle) {
                    NavigationDrawerHandle(
                        positionFraction = state.navigationHandlePosition,
                        onPositionChange = onNavigationHandlePositionChange,
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
        }
    }
    if (state.showNavigationGuide) {
        AlertDialog(
            onDismissRequest = onDismissNavigationGuide,
            icon = { Icon(Icons.Default.Menu, null, tint = AppAccent) },
            title = { Text("导航方式已更新") },
            text = {
                Text("底部导航栏已改为侧边导航。在任意页面从屏幕左侧向右滑动，或点击左侧箭头即可打开。箭头可上下拖动，也可在设置中隐藏。")
            },
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
    onSearchSong: (String) -> Unit,
    onOpenConstantSong: (String) -> SongScoreResult?,
    onEnsureSongImage: (SongScoreResult) -> Unit,
    onGenerateSongImage: (SongScoreResult) -> Unit,
    onGenerateImage: () -> Unit,
    onClearCache: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onNavigationHandleVisibilityChange: (Boolean) -> Unit,
    onB30ImageStyleChange: (B30ImageStyle) -> Unit,
    onCheckUpdate: () -> Unit,
    onRevealSessionToken: () -> Unit,
    onHideSessionToken: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.page,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 20 }) togetherWith
                    (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 24 })
            },
            label = "page-transition",
        ) { page ->
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
                AppPage.IMAGE -> ImagePage(state, onGenerateImage)
                AppPage.SETTINGS -> SettingsPage(
                    state = state,
                    onClearCache = onClearCache,
                    onThemeChange = onThemeChange,
                    onAutoRefreshChange = onAutoRefreshChange,
                    onAutoUpdateChange = onAutoUpdateChange,
                    onNavigationHandleVisibilityChange = onNavigationHandleVisibilityChange,
                    onB30ImageStyleChange = onB30ImageStyleChange,
                    onCheckUpdate = onCheckUpdate,
                    onRevealSessionToken = onRevealSessionToken,
                    onHideSessionToken = onHideSessionToken,
                    onLogout = onLogout,
                )
            }
        }
        if (state.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
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
    BoxWithConstraints(modifier.fillMaxHeight().width(28.dp)) {
        val handleHeight = 76.dp
        val handleHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { handleHeight.toPx() }
        val availablePx = (constraints.maxHeight - handleHeightPx).coerceAtLeast(1f)
        Box(
            Modifier
                .offset { IntOffset(0, (localFraction * availablePx).roundToInt()) }
                .width(22.dp)
                .height(handleHeight)
                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(AppAccent.copy(alpha = .82f))
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(8.dp).height(2.dp).background(Color.White.copy(alpha = .65f)))
                Spacer(Modifier.height(5.dp))
                Icon(Icons.Default.ChevronRight, "打开或上下移动侧边导航入口", tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(8.dp).height(2.dp).background(Color.White.copy(alpha = .65f)))
            }
        }
    }
}

@Composable
private fun LeaderboardPage(state: AppUiState, onRefresh: () -> Unit) {
    val leaderboard = state.leaderboard
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "玩家排行榜",
            subtitle = leaderboard?.let { "共 ${it.me.total.coerceAtLeast(it.entries.size)} 位公开玩家" }
                ?: "按各玩家存档 RKS 排名",
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
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        item(key = "leaderboard-me") {
                            CurrentPlayerRankCard(leaderboard.playerProfile, leaderboard.me, scrollToOwn)
                        }
                        item(key = "leaderboard-title") {
                            Text(
                                "RANKLIST",
                                color = AppAccent,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
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
                        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 2 },
                        exit = fadeOut(tween(140)),
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
        colors = CardDefaults.cardColors(containerColor = AppSurfaceRaised),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(profile?.avatar, profile?.nickname.orEmpty(), 62.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(profile?.nickname ?: "当前玩家", fontSize = 18.sp, fontWeight = FontWeight.Black)
                challengeModeLabel(profile?.challengeModeRank)?.let {
                    Text("课题模式 $it", color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    when {
                        onClick != null -> "点击跳转到本人排名"
                        me.rank > 1000 -> "当前排名不在前 1000 名"
                        me.rank > 0 -> "超过 ${"%.2f".format(me.percentile)}% 的公开玩家"
                        else -> "刷新存档后生成排名"
                    },
                    color = AppTextMuted,
                    fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (me.rank > 0) "#${me.rank}" else "--", color = AppAccent, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(11.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(60.dp).height(43.dp).clip(RoundedCornerShape(9.dp))
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
            PlayerAvatar(entry.avatar, displayName, 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
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
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl(it))
                    .size(160)
                    .memoryCacheKey("avatar-${avatarAssetKey(it)}")
                    .diskCacheKey("avatar-${avatarAssetKey(it)}")
                    .build(),
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
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl(it))
                    .size(320, 180)
                    .memoryCacheKey("avatar-wide-${avatarAssetKey(it)}")
                    .diskCacheKey("avatar-${avatarAssetKey(it)}")
                    .build(),
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
        '4' -> "黄"
        '5' -> "彩"
        else -> return null
    }
    return "$color${text.drop(1)}"
}

@Composable
private fun PageHeader(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = AppTextMuted, fontSize = 13.sp) }
        }
        action?.invoke()
    }
}

@Composable
private fun HomePage(state: AppUiState, onRefresh: () -> Unit, onOpenImage: () -> Unit) {
    val snapshot = state.snapshot
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("成绩概览", if (state.isOffline) "当前显示离线缓存" else "大陆版 TapTap") {
            IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                RefreshIcon(state.isLoading)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (snapshot == null) {
                EmptyState("还没有成绩数据", "点击刷新，从服务器读取最新存档。", onRefresh)
            } else {
                PlayerOverviewPanel(snapshot, state.isOffline)
                QuickAction("更新存档", Icons.Default.Refresh, onRefresh, Modifier.fillMaxWidth())
                UpdatedScoresOverview(snapshot)
                GradeOverview(snapshot)
                HomeB30ImageSection(
                    image = state.imageFile,
                    generating = state.isGeneratingB30Image,
                    elapsedSeconds = state.b30ImageGenerationElapsedSeconds,
                    onOpenImage = onOpenImage,
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun HomeB30ImageSection(
    image: File?,
    generating: Boolean,
    elapsedSeconds: Int,
    onOpenImage: () -> Unit,
) {
    if (!generating && image?.exists() != true) return
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
                        "正在后台生成 B30 成绩图… · 已用时 ${formatGenerationElapsed(elapsedSeconds)}",
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
        enter = fadeIn(tween(320)) + expandVertically(tween(420)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(260)),
    ) {
        Column(
            Modifier.fillMaxWidth().animateContentSize(tween(420)),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = AppAccent, modifier = Modifier.size(21.dp))
                AnimatedContent(
                    targetState = snapshot.updatedScores.size,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 2 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it / 2 })
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
        enter = fadeIn(tween(320)) + slideInHorizontally(tween(420)) { it / 5 },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -it / 6 },
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
private fun PlayerOverviewPanel(snapshot: B30Snapshot, offline: Boolean) {
    val profile = snapshot.playerProfile
    val playerName = profile?.nickname?.takeIf(String::isNotBlank) ?: "Phigros Player"
    val p30Rks = remember(snapshot.scoreRecords) { calculateP30Rks(snapshot.scoreRecords) }
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurfaceRaised),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, AppAccent.copy(alpha = .46f), RoundedCornerShape(15.dp)),
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    listOf(
                        AppSurfaceRaised,
                        AppAccent.copy(alpha = .12f),
                        AppSurfaceRaised,
                    ),
                ),
            ),
        ) {
            Box(Modifier.fillMaxHeight().width(5.dp).background(AppAccent))
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 17.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WidePlayerAvatar(profile?.avatar, playerName)
                    Column(Modifier.weight(1f).padding(start = 15.dp)) {
                        Text("PLAYER NAME", color = AppTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            playerName,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            if (offline) "离线成绩" else "成绩已同步",
                            color = if (offline) Color(0xFFFFB84D) else AppAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Icon(
                        if (offline) Icons.Default.Warning else Icons.Default.CheckCircle,
                        null,
                        tint = if (offline) Color(0xFFFFB84D) else AppAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(top = 16.dp), color = AppTextMuted.copy(alpha = .18f))
                OverviewInfoRow("Ranking Score") {
                    Text(String.format(Locale.US, "%.4f", snapshot.totalRks), fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
                OverviewInfoRow("P30 Ranking Score") {
                    Text(String.format(Locale.US, "%.4f", p30Rks), fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
                OverviewInfoRow("Challenge Mode") {
                    ChallengeModePlate(profile?.challengeModeRank)
                }
                OverviewInfoRow("上次更新", showDivider = false) {
                    Text(
                        formatSaveUpdatedAt(snapshot.saveUpdatedAt, snapshot.cachedAtEpochMs),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewInfoRow(
    label: String,
    showDivider: Boolean = true,
    value: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 380.dp
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    color = AppTextMuted,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(if (compact) 126.dp else 166.dp),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { value() }
            }
            if (showDivider) {
                HorizontalDivider(color = AppTextMuted.copy(alpha = .13f))
            }
        }
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
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
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
    var expanded by rememberSaveable { mutableStateOf(false) }
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
                enter = fadeIn(tween(260)) + expandVertically(tween(360)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(260)),
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradeMetric("Clear", "C", total.clear, Modifier.weight(1f))
                        GradeMetric("Full Combo", "FC", total.fullCombo, Modifier.weight(1f))
                        GradeMetric("All Perfect", "AP", total.allPerfect, Modifier.weight(1f))
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
private fun GradeMetric(label: String, shortLabel: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(9.dp)).background(AppSurfaceRaised).padding(vertical = 11.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count.toString(), color = AppAccent, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(shortLabel, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(label, color = AppTextMuted, fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun B30Page(state: AppUiState, onRefresh: () -> Unit) {
    val snapshot = state.snapshot
    var bestNText by rememberSaveable { mutableStateOf("30") }
    var bestNExpanded by rememberSaveable { mutableStateOf(false) }
    var b30Expanded by rememberSaveable { mutableStateOf(false) }
    var p30Expanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        PageHeader("成绩一览", snapshot?.let { "RKS %.4f".format(it.totalRks) }) {
            IconButton(onClick = onRefresh, enabled = !state.isLoading) { RefreshIcon(state.isLoading) }
        }
        if (snapshot == null) {
            EmptyState("暂无 B30", "联网刷新后会在这里显示完整列表。", onRefresh)
        } else {
            val requestedBestN = bestNText.toIntOrNull()?.takeIf { it > 0 } ?: 0
            val bestCharts = remember(snapshot.scoreRecords, requestedBestN) {
                selectBestCharts(snapshot.scoreRecords, requestedBestN)
            }
            val perfectCharts = remember(snapshot.scoreRecords) {
                selectPerfectCharts(snapshot.scoreRecords)
            }
            val p30Rks = remember(snapshot.scoreRecords) {
                calculateP30Rks(snapshot.scoreRecords)
            }
            val b30PItems = remember(snapshot.items) { snapshot.items.filter { it.section == "AP" } }
            val b30BestItems = remember(snapshot.items) { snapshot.items.filter { it.section == "BEST" } }
            val b30BodyCount = if (b30Expanded) 2 + b30PItems.size + b30BestItems.size else 0
            val bestNHeaderIndex = 1 + b30BodyCount + 1
            val bestNBodyCount = if (bestNExpanded) 1 + bestCharts.size else 0
            val p30HeaderIndex = bestNHeaderIndex + 1 + bestNBodyCount + 1
            val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
            val currentSection = when {
                firstVisibleItemIndex >= p30HeaderIndex -> "P30" to p30HeaderIndex
                firstVisibleItemIndex >= bestNHeaderIndex -> "Best N" to bestNHeaderIndex
                else -> "B30" to 0
            }

            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "b30-header") {
                        RankingSectionHeader(
                            title = "B30",
                            subtitle = "P3 + Best 27",
                            expanded = b30Expanded,
                            onToggle = { b30Expanded = !b30Expanded },
                        )
                    }
                    if (b30Expanded) {
                        item(key = "b30-p3-label") { SectionLabel("P3") }
                        itemsIndexed(
                            b30PItems,
                            key = { _, item -> "b30-p3-${item.songId}-${item.difficulty}" },
                        ) { _, item ->
                            B30Row(item)
                        }
                        item(key = "b30-best-label") { SectionLabel("Best 27") }
                        itemsIndexed(
                            b30BestItems,
                            key = { _, item -> "b30-best-${item.songId}-${item.difficulty}" },
                        ) { _, item ->
                            B30Row(item)
                        }
                    }

                    item(key = "best-n-divider") { RankingSectionDivider() }
                    item(key = "best-n-header") {
                        RankingSectionHeader(
                            title = "Best N",
                            subtitle = "存档内 RKS 最高的 N 张谱面",
                            expanded = bestNExpanded,
                            onToggle = { bestNExpanded = !bestNExpanded },
                        )
                    }
                    if (bestNExpanded) {
                        item(key = "best-n-input") {
                            OutlinedTextField(
                                value = bestNText,
                                onValueChange = { value ->
                                    if (value.all(Char::isDigit)) bestNText = value
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("N 的数量") },
                                placeholder = { Text("例如：30") },
                                supportingText = {
                                    Text(
                                        when {
                                            requestedBestN == 0 -> "请输入大于 0 的整数"
                                            requestedBestN > bestCharts.size -> "存档内共 ${bestCharts.size} 张可用谱面"
                                            else -> "当前显示 ${bestCharts.size} 张谱面"
                                        },
                                    )
                                },
                                isError = bestNText.isNotEmpty() && requestedBestN == 0,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                            )
                        }
                        itemsIndexed(
                            bestCharts,
                            key = { index, item -> "best-$index-${item.songId}-${item.difficulty}" },
                        ) { index, item ->
                            SnapshotRankingRow(index + 1, item, showPushTarget = true)
                        }
                    }

                    item(key = "p30-divider") { RankingSectionDivider() }
                    item(key = "p30-header") {
                        RankingSectionHeader(
                            title = "P30",
                            subtitle = "RKS 最高的 ${perfectCharts.size} 张 All Perfect 谱面",
                            expanded = p30Expanded,
                            onToggle = { p30Expanded = !p30Expanded },
                        )
                    }
                    if (p30Expanded) {
                        item(key = "p30-rks") { P30RksSummary(p30Rks) }
                        if (perfectCharts.isEmpty()) {
                            item(key = "p30-empty") {
                                Text(
                                    "该存档内暂无 All Perfect 谱面。",
                                    color = AppTextMuted,
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            itemsIndexed(
                                perfectCharts.take(27),
                                key = { index, item -> "p30-$index-${item.songId}-${item.difficulty}" },
                            ) { index, item ->
                                SnapshotRankingRow(index + 1, item)
                            }
                            if (perfectCharts.size > 27) item(key = "p30-overflow") { OverflowDivider() }
                            itemsIndexed(
                                perfectCharts.drop(27),
                                key = { index, item -> "p30-overflow-$index-${item.songId}-${item.difficulty}" },
                            ) { index, item ->
                                SnapshotRankingRow(index + 28, item)
                            }
                        }
                    }
                }

                if (firstVisibleItemIndex > currentSection.second) {
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch { listState.animateScrollToItem(currentSection.second) }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 14.dp),
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, null)
                        Spacer(Modifier.width(6.dp))
                        Text("回到 ${currentSection.first} 顶部")
                    }
                }
            }
        }
    }
}

@Composable
private fun P30RksSummary(rks: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().border(
            width = 1.dp,
            color = AppAccent.copy(alpha = .28f),
            shape = RoundedCornerShape(10.dp),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("P30 综合 RKS", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("%.4f".format(rks), color = AppAccent, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = AppAccent, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = AppTextMuted, fontSize = 12.sp)
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
            "OVER FLOW",
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
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(43.dp).clip(RoundedCornerShape(8.dp)).background(difficultyColor(item.difficulty).copy(alpha = .16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${item.position}", fontWeight = FontWeight.Black, color = difficultyColor(item.difficulty))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.songName, fontWeight = FontWeight.Bold)
                Text(
                    "${item.difficulty} ${item.chartConstant?.let { "%.1f".format(it) } ?: "--"}  ·  ${"%,d".format(item.score)}",
                    color = AppTextMuted,
                    fontSize = 12.sp,
                )
                Text("ACC ${"%.4f".format(item.accuracy)}%${if (item.isFullCombo) "  ·  FC" else ""}", fontSize = 12.sp)
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
                Modifier.size(43.dp).clip(RoundedCornerShape(8.dp))
                    .background(difficultyColor(item.difficulty).copy(alpha = .16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(position.toString(), fontWeight = FontWeight.Black, color = difficultyColor(item.difficulty))
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
    var selectedSong by remember { mutableStateOf<SongScoreResult?>(null) }
    val listState = rememberLazyListState()
    val rows = remember(state.constantTableEntries, selectedLevel) {
        buildConstantTableRows(state.constantTableEntries, selectedLevel)
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
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "level-all") {
                ConstantLevelChip(
                    label = "全部",
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                )
            }
            items((17 downTo 1).toList(), key = { "level-$it" }) { level ->
                ConstantLevelChip(
                    label = level.toString(),
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = level },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (rows.isEmpty()) {
            EmptyState("暂无定数资料", "当前曲库中没有该等级的谱面。", {})
        } else {
            LaunchedEffect(selectedLevel) {
                listState.scrollToItem(0)
            }
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
                        animationKey = selectedLevel ?: 0,
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
        }
    }
}

private fun buildConstantTableRows(
    entries: List<ConstantTableEntry>,
    selectedLevel: Int?,
): List<ConstantTableRow> = buildList {
    val filtered = if (selectedLevel == null) {
        entries
    } else {
        entries.filter { it.chart.chartConstant?.toInt() == selectedLevel }
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
        animationSpec = tween(300),
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
            "单曲成绩",
            if (state.hasSearchedSongs) "找到 ${state.songResults.size} 首曲目" else "支持曲名、别名、曲师或曲目 ID",
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            label = { Text("搜索曲目") },
            placeholder = { Text("例如：Glaciaxion / 冰封") },
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
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = when {
                !state.hasSearchedSongs -> 0
                state.songResults.isEmpty() -> 1
                else -> 2
            },
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
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
                            enter = fadeIn(tween(220, delayMillis = (index.coerceAtMost(6) * 45))) +
                                slideInVertically(tween(220, delayMillis = (index.coerceAtMost(6) * 45))) { it / 8 },
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
        Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Search, null, tint = AppAccent, modifier = Modifier.size(48.dp))
        Text("查询单曲成绩", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
        Text(
            "可使用官方曲名或社区别名搜索，并展示存档中 EZ、HD、IN、AT 的分数、ACC 与 Ranking Score。",
            color = AppTextMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SongScoreCard(song: SongScoreResult, onClick: () -> Unit) {
    var useFallbackArtwork by remember(song.songId) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(12.dp),
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
                Column(Modifier.fillMaxWidth(.76f).padding(16.dp)) {
                    Text(song.songName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(song.composer, song.illustrator).filter(String::isNotBlank).joinToString(" · "),
                        color = AppTextMuted,
                        fontSize = 12.sp,
                    )
                    Text(song.songId, color = AppTextMuted.copy(alpha = .72f), fontSize = 10.sp)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                song.records.forEachIndexed { index, record ->
                    SongDifficultyRow(record)
                    if (index != song.records.lastIndex) {
                        HorizontalDivider(Modifier.padding(vertical = 9.dp), color = AppSurfaceRaised)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("查看曲目详情", color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ChevronRight, null, tint = AppAccent, modifier = Modifier.size(17.dp))
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
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .4f)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, backDescription, tint = Color.White)
                }
                Text(
                    "曲目详情",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Spacer(Modifier.height(190.dp))
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(AppBackground.copy(alpha = .96f))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(song.songName, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text(song.songId, color = AppTextMuted, fontSize = 11.sp)
                SongMetadataCard(song)
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
            Text("Ranking Score", color = AppTextMuted, fontSize = 9.sp)
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
private fun ImagePage(state: AppUiState, onGenerateImage: () -> Unit) {
    val context = LocalContext.current
    val image = state.imageFile
    var showImageActions by remember { mutableStateOf(false) }
    var showZoomedImage by remember(image?.absolutePath, image?.lastModified()) { mutableStateOf(false) }
    val saveScope = rememberCoroutineScope()
    val saveCurrentImage: () -> Unit = {
        image?.takeIf(File::exists)?.let { source ->
            saveScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { saveB30ImageToGallery(context, source) }
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
    Column(Modifier.fillMaxSize()) {
        PageHeader("B30 图片", "生图可能需要30秒左右时间")
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (image?.exists() == true) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(image)
                        .memoryCacheKey("b30-${image.lastModified()}-${image.length()}")
                        .build(),
                    contentDescription = "B30 图片",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppSurface)
                        .clickable { showZoomedImage = true },
                    contentScale = ContentScale.FillWidth,
                )
                Text(
                    "点击图片放大，支持双指缩放和拖动查看",
                    color = AppTextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onGenerateImage, enabled = !state.isGeneratingB30Image) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.isGeneratingB30Image) "正在生成" else "重新生成")
                    }
                    OutlinedButton(onClick = { showImageActions = true }) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(7.dp))
                        Text("保存或分享")
                    }
                }
            } else if (state.isGeneratingB30Image) {
                Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(
                            "正在后台生成 B30 成绩图… · 已用时 ${formatGenerationElapsed(state.b30ImageGenerationElapsedSeconds)}",
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            } else {
                EmptyState("尚未生成图片", "图片包含完整 B30 与曲绘，首次生成可能需要一些时间。", onGenerateImage, "生成 B30 图片")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showImageActions && image?.exists() == true) {
        AlertDialog(
            onDismissRequest = { showImageActions = false },
            title = { Text("保存或分享 B30 图片") },
            text = { Text("可以直接保存到系统相册，也可以通过系统分享给其他应用。") },
            confirmButton = {
                Button(
                    onClick = {
                        showImageActions = false
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
                        showImageActions = false
                        shareImage(context, image)
                    },
                ) { Text("系统分享") }
            },
        )
    }
    if (showZoomedImage && image?.exists() == true) {
        ZoomableB30ImageDialog(image = image, onDismiss = { showZoomedImage = false })
    }
}

@Composable
private fun ZoomableB30ImageDialog(
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
                    .memoryCacheKey("b30-zoom-${image.lastModified()}-${image.length()}")
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
private fun SettingsPage(
    state: AppUiState,
    onClearCache: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onNavigationHandleVisibilityChange: (Boolean) -> Unit,
    onB30ImageStyleChange: (B30ImageStyle) -> Unit,
    onCheckUpdate: () -> Unit,
    onRevealSessionToken: () -> Unit,
    onHideSessionToken: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmRevealSessionToken by rememberSaveable { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("设置")
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingCard(Icons.Default.CheckCircle, "版本", BuildConfig.VERSION_NAME)
            ThemeSetting(state.isDarkTheme, onThemeChange)
            B30ImageStyleSetting(state.b30ImageStyle, onB30ImageStyleChange)
            AutoRefreshSetting(state.autoRefreshOnLaunch, onAutoRefreshChange)
            AppUpdateSetting(
                enabled = state.autoCheckAppUpdates,
                isChecking = state.isCheckingAppUpdate,
                onEnabledChange = onAutoUpdateChange,
                onCheckUpdate = onCheckUpdate,
            )
            NavigationHandleSetting(
                enabled = state.showNavigationHandle,
                onEnabledChange = onNavigationHandleVisibilityChange,
            )
            SettingCard(
                Icons.Default.Lock,
                "获取 SessionToken",
                if (state.hasStoredSessionToken) "已使用 Android Keystore 加密保存在本机" else "旧版会话需重新登录一次后获取",
                onClick = { confirmRevealSessionToken = true },
            )
            SettingCard(
                Icons.Default.History,
                "更新日志",
                null,
                onClick = { showChangelog = true },
            )
            SettingCard(
                Icons.Default.Info,
                "关于",
                "开源许可、项目源码与后端项目",
                onClick = { showAbout = true },
            )
            OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Cached, null)
                Spacer(Modifier.width(8.dp))
                Text("清除本地成绩与图片缓存")
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
            Column(Modifier.padding(top = 8.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "Phi Score Query 是非官方工具，与 Pigeon Games 或 TapTap 无隶属关系。",
                    color = AppTextMuted,
                    fontSize = 12.sp,
                )
                Text(
                    "查分服务由 Next-Phi-Backend 提供。",
                    color = AppTextMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
    if (showChangelog) {
        ChangelogSheet(onDismiss = { showChangelog = false })
    }
    if (showAbout) {
        AboutSheet(onDismiss = { showAbout = false })
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
private fun ThemeSetting(isDarkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("风格模式", fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().padding(top = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeChoice(
                    label = "白日",
                    icon = Icons.Default.WbSunny,
                    selected = !isDarkTheme,
                    onClick = { onThemeChange(false) },
                    modifier = Modifier.weight(1f),
                )
                ThemeChoice(
                    label = "黑夜",
                    icon = Icons.Default.DarkMode,
                    selected = isDarkTheme,
                    onClick = { onThemeChange(true) },
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
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("B30 成绩图样式", fontWeight = FontWeight.Bold)
            Text("切换后需重新生成成绩图", color = AppTextMuted, fontSize = 12.sp)
            Row(
                Modifier.fillMaxWidth().padding(top = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeChoice(
                    label = "经典",
                    icon = Icons.Default.BarChart,
                    selected = style == B30ImageStyle.CLASSIC,
                    onClick = { onStyleChange(B30ImageStyle.CLASSIC) },
                    modifier = Modifier.weight(1f),
                )
                ThemeChoice(
                    label = "简约",
                    icon = Icons.Default.Image,
                    selected = style == B30ImageStyle.MINIMAL,
                    onClick = { onStyleChange(B30ImageStyle.MINIMAL) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AutoRefreshSetting(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("打开软件时自动更新存档", fontWeight = FontWeight.Bold)
                Text("每次启动仅自动更新一次", color = AppTextMuted, fontSize = 12.sp)
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
private fun AppUpdateSetting(
    enabled: Boolean,
    isChecking: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("启动时检查应用更新", fontWeight = FontWeight.Bold)
                    Text("发现新版本时显示更新内容", color = AppTextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            OutlinedButton(
                onClick = onCheckUpdate,
                enabled = !isChecking,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Default.SystemUpdate, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isChecking) "正在检查" else "手动检查更新")
            }
        }
    }
}

@Composable
private fun NavigationHandleSetting(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("显示侧边导航箭头", fontWeight = FontWeight.Bold)
                Text(
                    if (enabled) "箭头可上下拖动；隐藏后仍可从屏幕左侧右滑打开导航"
                    else "已隐藏；仍可从屏幕左侧向右滑动打开导航",
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
private fun ThemeChoice(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AppAccent.copy(alpha = .16f) else Color.Transparent,
            contentColor = if (selected) AppAccent else MaterialTheme.colorScheme.onSurface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) AppAccent else AppTextMuted.copy(alpha = .38f),
        ),
    ) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label)
    }
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    detail: String?,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(10.dp)) {
        Row(cardModifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AppAccent)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                detail?.let { Text(it, color = AppTextMuted, fontSize = 12.sp) }
            }
            if (onClick != null) {
                Icon(Icons.Default.ChevronRight, null, tint = AppTextMuted, modifier = Modifier.padding(start = 8.dp))
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
        enter = fadeIn(tween(300)) + slideInVertically(tween(380)) { it / 5 },
        exit = fadeOut(tween(140)),
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
                    Text(
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
        Modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.BarChart, null, tint = AppTextMuted, modifier = Modifier.size(46.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
        Text(detail, color = AppTextMuted, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Button(onClick = action) { Text(actionText) }
    }
}

@Composable
private fun RefreshIcon(loading: Boolean) {
    val transition = rememberInfiniteTransition(label = "refresh-rotation")
    val animatedRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
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

private fun fullIllustrationUrl(songId: String): String =
    "${BuildConfig.API_BASE_URL.trimEnd('/')}/_ill/ill/${Uri.encode(songId)}.png"

private fun fallbackIllustrationUrl(songId: String): String =
    "https://raw.githubusercontent.com/Catrong/phi-plugin-ill/main/illLow/${Uri.encode(songId)}.png"

private fun lowArtworkRequest(context: Context, songId: String, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(720, 380)
        .memoryCacheKey("illustration-low-$songId")
        .diskCacheKey("illustration-low-$songId")
        .crossfade(120)
        .build()

private fun fullArtworkRequest(context: Context, songId: String, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey("illustration-full-$songId")
        .diskCacheKey("illustration-full-$songId")
        .placeholderMemoryCacheKey("illustration-low-$songId")
        .crossfade(140)
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

private fun shareImage(
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
private fun saveB30ImageToGallery(
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
