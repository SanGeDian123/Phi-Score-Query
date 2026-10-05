package xyz.plcliangpicup.phigrosscore.ui

import xyz.plcliangpicup.phigrosscore.data.PracticeGrade
import xyz.plcliangpicup.phigrosscore.data.PracticeJudgeRules
import xyz.plcliangpicup.phigrosscore.data.PracticeDisplayedTimeline
import xyz.plcliangpicup.phigrosscore.data.PracticeHitCandidate
import xyz.plcliangpicup.phigrosscore.data.practiceChooseHit
import xyz.plcliangpicup.phigrosscore.data.practiceHitRadius
import xyz.plcliangpicup.phigrosscore.data.practiceHoldHasContact
import xyz.plcliangpicup.phigrosscore.data.practiceRecentSwipe
import xyz.plcliangpicup.phigrosscore.data.PracticeSwipe
import xyz.plcliangpicup.phigrosscore.data.PracticePauseGesture
import xyz.plcliangpicup.phigrosscore.data.practicePauseTarget
import java.util.Locale
import xyz.plcliangpicup.phigrosscore.data.practiceStartAfterPreparation
import xyz.plcliangpicup.phigrosscore.data.PracticeAutoSoundSchedule
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioManager
import android.media.PlaybackParams
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.runtime.rememberUpdatedState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CompletableDeferred

import xyz.plcliangpicup.phigrosscore.data.PracticeChartSource
import xyz.plcliangpicup.phigrosscore.data.practiceGoodTimingLabel
import xyz.plcliangpicup.phigrosscore.data.practiceRealtimeAccuracy
import xyz.plcliangpicup.phigrosscore.R
import xyz.plcliangpicup.phigrosscore.data.PracticeChart
import xyz.plcliangpicup.phigrosscore.data.PracticeChartDownloader
import xyz.plcliangpicup.phigrosscore.data.PracticeChartParser
import xyz.plcliangpicup.phigrosscore.data.PracticeDownloadProgress
import xyz.plcliangpicup.phigrosscore.data.PracticeJudgeLine
import xyz.plcliangpicup.phigrosscore.data.PracticeNote
import xyz.plcliangpicup.phigrosscore.data.PracticeNoteType
import xyz.plcliangpicup.phigrosscore.data.PracticeNoisePoint
import xyz.plcliangpicup.phigrosscore.data.PracticeNoiseRuntime
import xyz.plcliangpicup.phigrosscore.data.evaluatePracticeEvents
import xyz.plcliangpicup.phigrosscore.data.PracticeField
import xyz.plcliangpicup.phigrosscore.data.evaluatePracticeLayers
import xyz.plcliangpicup.phigrosscore.data.practiceTangentDistance
import xyz.plcliangpicup.phigrosscore.data.practiceNoteDistances
import xyz.plcliangpicup.phigrosscore.data.secondsToBeat
import xyz.plcliangpicup.phigrosscore.data.calculatePlayScoreAndAccuracy
import xyz.plcliangpicup.phigrosscore.data.calculateChartRks
import xyz.plcliangpicup.phigrosscore.data.PRACTICE_LOOP_WAIT_MS
import xyz.plcliangpicup.phigrosscore.data.practiceCarryHold
import xyz.plcliangpicup.phigrosscore.data.practiceInterpolatedAudio
import xyz.plcliangpicup.phigrosscore.data.PracticeRange
import xyz.plcliangpicup.phigrosscore.data.PracticeTripleTap
import xyz.plcliangpicup.phigrosscore.data.practiceSpeed
import xyz.plcliangpicup.phigrosscore.data.practiceVisualTime
import xyz.plcliangpicup.phigrosscore.data.practiceTimingDelta
import xyz.plcliangpicup.phigrosscore.data.practiceCandidateRadius

import xyz.plcliangpicup.phigrosscore.data.practiceEventVisualTime
import xyz.plcliangpicup.phigrosscore.data.practiceMayMiss
import xyz.plcliangpicup.phigrosscore.data.practiceHudScore
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val PracticeGold = Color(0xFFFFD45E)
private val PracticeBlue = Color(0xFF65CFFF)

@Composable
fun PracticeEntryButton(chartSource: PracticeChartSource, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choosingMode by remember { mutableStateOf(false) }
    var segmentEntry by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var clearingDownload by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<PracticeDownloadProgress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val currentDownloadJob by rememberUpdatedState(downloadJob)
    var downloadGeneration by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "practice-entry-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "practice-entry-shimmer-position",
    )
    val launchPractice = {
        val generation = ++downloadGeneration
        downloading = true
        error = null
        progress = PracticeChartDownloader.savedProgress(context, chartSource)
        downloadJob = scope.launch {
            val worker = coroutineContext[kotlinx.coroutines.Job]
            try {
                val chartFile = withContext(Dispatchers.IO) {
                    PracticeChartDownloader.ensureDownloaded(context, chartSource, onProgress = { update ->
                        ContextCompat.getMainExecutor(context).execute {
                            if (generation == downloadGeneration) progress = update
                        }
                    }, isCancelled = { worker?.isActive != true })
                }
                if (generation != downloadGeneration || worker?.isActive != true) return@launch
                context.startActivity(
                    Intent(context, PracticePlayerActivity::class.java)
                        .putExtra(PracticePlayerActivity.EXTRA_CHART_PATH, chartFile.absolutePath)
                        .putExtra(PracticePlayerActivity.EXTRA_CHART_SOURCE, xyz.plcliangpicup.phigrosscore.data.PracticeCharts.encodeSource(chartSource))
                        .putExtra(PracticePlayerActivity.EXTRA_SEGMENT_MODE, segmentEntry),
                )
                (context as? android.app.Activity)?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } catch (failure: Throwable) {
                if (generation == downloadGeneration && worker?.isActive == true &&
                    failure !is kotlinx.coroutines.CancellationException) {
                    error = failure.message ?: "谱面暂时无法下载，请稍后重试。"
                }
            } finally {
                if (generation == downloadGeneration) downloading = false
            }
        }
    }
    fun pauseDownload(deletePartial: Boolean) {
        downloadGeneration++
        downloading = false
        val oldJob = downloadJob
        oldJob?.cancel()
        PracticeChartDownloader.stopDownload(chartSource)
        if (deletePartial) scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            clearingDownload = true
            try {
                withContext(NonCancellable) {
                    oldJob?.join()
                    withContext(Dispatchers.IO) { PracticeChartDownloader.deleteStoredResources(context, listOf(chartSource)) }
                }
            } catch (failure: Exception) {
                error = failure.message ?: "无法清除下载数据"
            } finally {
                clearingDownload = false
            }
        }
    }
    DisposableEffect(chartSource) {
        onDispose {
            currentDownloadJob?.cancel()
            PracticeChartDownloader.stopDownload(chartSource)
        }
    }

    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = AppAccent.copy(alpha = .12f), spotColor = AppAccent.copy(alpha = .26f))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF173A48), Color(0xFF17334E), Color(0xFF27315E), Color(0xFF173A48)),
                    start = Offset(shimmer * 600f, 0f),
                    end = Offset(shimmer * 600f + 800f, 180f),
                ),
            )
            .clickable(enabled = !downloading && !clearingDownload, onClick = { choosingMode = true })
            .padding(horizontal = 18.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = .13f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("谱面播放与练习(测试中)", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(chartSource.difficulty, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
            }
            Text(chartSource.difficulty.substringBefore(' '), color = PracticeGold, style = MaterialTheme.typography.labelLarge)
        }
    }

    if (choosingMode) {
        Dialog(onDismissRequest = { choosingMode = false }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = AppSurface)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择播放方式", style = MaterialTheme.typography.headlineSmall)
                    Text(chartSource.title, color = AppTextMuted)
                    Button(onClick = { segmentEntry = false; choosingMode = false; launchPractice() }, modifier = Modifier.fillMaxWidth()) { Text("完整播放") }
                    Button(onClick = { segmentEntry = true; choosingMode = false; launchPractice() }, modifier = Modifier.fillMaxWidth()) { Text("分段练习") }
                }
            }
        }
    }
    if (downloading) {
        Dialog(onDismissRequest = { pauseDownload(false) }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("正在准备谱面", style = MaterialTheme.typography.headlineSmall)
                    Text("${chartSource.title} · ${chartSource.difficulty}", color = AppTextMuted, style = MaterialTheme.typography.bodyMedium)
                    val fraction = progress?.fraction
                    if (fraction == null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = AppAccent,
                            trackColor = AppSurfaceRaised,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = AppAccent,
                            trackColor = AppSurfaceRaised,
                        )
                    }
                    val received = progress?.receivedBytes ?: 0L
                    Text(
                        progress?.totalBytes?.let { "${formatMiB(received)} / ${formatMiB(it)} MB" }
                            ?: if (received > 0) "已接收 ${formatMiB(received)} MB" else "正在连接资源服务器…",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    val speed = progress?.bytesPerSecond ?: 0L
                    val attempt = progress?.attempt ?: 1
                    val eta = progress?.totalBytes?.let { total ->
                        if (speed > 0L) formatDownloadEta(((total - received).coerceAtLeast(0L) / speed).toInt()) else null
                    }
                    Text(
                        when {
                            speed > 0L -> "${(fraction?.times(100f)?.toInt() ?: 0)}% · ${formatMiB(speed)} MB/s" + (eta?.let { " · 预计剩余 $it" } ?: "")
                            attempt > 1 -> "正在恢复连接 · 第 $attempt 次尝试 · 已启用断点续传"
                            received > 0L -> "网络较慢也会继续下载；中断后可从已接收位置续传"
                            else -> "下载可能需要几分钟，完成后会自动进入谱面"
                        },
                        color = AppTextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = { pauseDownload(false) }) { Text("暂停下载") }
                        androidx.compose.material3.TextButton(onClick = { pauseDownload(true) }) { Text("取消下载") }
                    }
                }
            }
        }
    }
    if (error != null) {
        Dialog(onDismissRequest = { error = null }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("谱面暂不可用", style = MaterialTheme.typography.titleLarge)
                    Text(error.orEmpty(), color = AppTextMuted, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { error = null; launchPractice() }, modifier = Modifier.align(Alignment.End)) { Text("重试") }
                }
            }
        }
    }
}

private fun formatMiB(bytes: Long): String = "%.1f".format(bytes / (1024.0 * 1024.0))

private fun formatDownloadEta(totalSeconds: Int): String = when {
    totalSeconds < 60 -> "${totalSeconds}秒"
    totalSeconds < 3600 -> "${totalSeconds / 60}分${totalSeconds % 60}秒"
    else -> "${totalSeconds / 3600}小时${(totalSeconds % 3600) / 60}分"
}

private enum class PracticeMode { LOADING, READY, PREVIEW, PREVIEW_PLAYING, SEEKING, PREPARING, COUNT_IN, PLAYING, PAUSED, RESULT, ERROR }
private data class PracticeSeekRequest(val revision: Int, val position: Double, val after: PracticeMode)

private data class PracticeOutcome(val grade: PracticeGrade, val deltaSeconds: Double, val judgedAt: Double)
private data class PracticeEffect(
    val x: Float, val y: Float, val grade: PracticeGrade, val time: Double,
    val visual: PracticeHitVisual = PracticeHitVisual(perfect = grade == PracticeGrade.PERFECT),
)
private data class ActivePracticeHold(
    val pointerId: Long,
    val grade: PracticeGrade,
    val deltaSeconds: Double,
    val position: Offset,
    val valid: Boolean = true,
    val lastContact: Double = 0.0,
    val lastEffect: Double = Double.NEGATIVE_INFINITY,
    val carryGraceUntil: Double = Double.NEGATIVE_INFINITY,
)
private data class PracticeFinger(
    val position: Offset, val timeMs: Long, val swipe: PracticeSwipe = PracticeSwipe(),
)
private data class PracticeCalibrationReturn(
    val outcomes: Map<Int, PracticeOutcome>, val holds: Map<Int, ActivePracticeHold>,
    val armed: Map<Int, Boolean>, val combo: Int, val maxCombo: Int, val judgementFloor: Double, val autoPlay: Boolean,
)
private data class ActivePracticeGesture(val note: PracticeNote, val downPosition: Offset, val downTimeMs: Long)

private data class PracticeAssets(
    val notes: Map<PracticeNoteType, Bitmap>,
    val notesMulti: Map<PracticeNoteType, Bitmap>,
    val hitFx: Bitmap,
    val illustration: Bitmap,
    val background: Bitmap,
    val sideBackground: Bitmap,
    val hitAudio: PracticeHitAudio,
    val endingFile: File,
    val holdAtlas: Pair<Int, Int> = 50 to 50,
    val holdAtlasMulti: Pair<Int, Int> = 50 to 93,
    val fxColumns: Int = 6,
    val fxRows: Int = 5,
    val fxDuration: Double = .5,
    val fxScale: Float = .95f,
)

private data class PracticeLoaded(val chart: PracticeChart, val assets: PracticeAssets, var player: PracticeMusicPlayer,
    val noiseRenderer: PracticeNoiseRenderer?) {
    val geometry = PracticeRenderGeometry(chart)
    val noiseRuntime = PracticeNoiseRuntime(chart.blockAreas)
}

private class PracticeRenderGeometry(chart: PracticeChart) {
    val heads = DoubleArray(chart.notes.size) { chart.lines[chart.notes[it].lineIndex].travel.at(chart.notes[it].startSeconds) }
    val tails = DoubleArray(chart.notes.size) { chart.lines[chart.notes[it].lineIndex].travel.at(chart.notes[it].endSeconds) }
    val current = DoubleArray(chart.lines.size)
    val poses = Array(chart.lines.size) { PracticeLinePose(0f, 0f, 0f, 0f) }
    val cosines = FloatArray(chart.lines.size)
    val sines = FloatArray(chart.lines.size)
    private val judgePoses = Array(chart.lines.size) { PracticeLinePose(0f, 0f, 0f, 0f) }
    private val judgeTimes = DoubleArray(chart.lines.size) { Double.NaN }
    private var judgeViewport: PracticeViewport? = null
    fun judgePose(chart: PracticeChart, index: Int, time: Double, viewport: PracticeViewport): PracticeLinePose {
        if (judgeViewport != viewport) { judgeTimes.fill(Double.NaN); judgeViewport = viewport }
        if (judgeTimes[index] != time) {
            val line = chart.lines[index]
            val pose = judgePoses[index]
            pose.x = viewport.centerX + evaluatePracticeLayers(line.xLayers, time, 0f) * viewport.scale
            pose.y = viewport.centerY - evaluatePracticeLayers(line.yLayers, time, 0f) * viewport.scaleY
            pose.rotation = evaluatePracticeLayers(line.rotationLayers, time, 0f)
            judgeTimes[index] = time
        }
        return judgePoses[index]
    }
    val perfectTint = PorterDuffColorFilter(PRACTICE_HIT_PERFECT_TINT, PorterDuff.Mode.SRC_IN)
    val goodTint = PorterDuffColorFilter(PRACTICE_HIT_GOOD_TINT, PorterDuff.Mode.SRC_IN)
    val badTint = PorterDuffColorFilter(NativeColor.rgb(110,65,65), PorterDuff.Mode.SRC_IN)
    private val source = Rect()
    private val target = RectF()
    fun sourceRect(l: Int, t: Int, r: Int, b: Int): Rect { source.set(l,t,r,b); return source }
    fun targetRect(l: Float, t: Float, r: Float, b: Float): RectF { target.set(l,t,r,b); return target }
    // Keep judgment's time-sorted notes intact. Draw Hold bodies first so
    // simultaneous Flick heads cannot be covered by a Hold from another line.
    val drawOrder: List<PracticeNote> = chart.notes.filter { it.type == PracticeNoteType.HOLD } +
        chart.notes.filter { it.type != PracticeNoteType.HOLD }
}

private fun practiceNearbyNotes(chart: PracticeChart, time: Double, speed: Float): List<PracticeNote> {
    val chartTime = time - chart.offsetSeconds
    fun lowerBound(target: Double): Int {
        var low = 0; var high = chart.notes.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (chart.notes[mid].startSeconds < target) low = mid + 1 else high = mid
        }
        return low
    }
    val radius = practiceCandidateRadius(speed)
    return chart.notes.subList(lowerBound(chartTime - radius), lowerBound(chartTime + radius))
}

private fun practiceDifficultyLabel(level: String): String {
    val tier = Regex("(?i)\\b(EZ|HD|IN|AT)\\b").find(level)?.value?.uppercase() ?: "?"
    val number = Regex("[0-9]+(?:\\.[0-9]+)?\\+?").find(level)?.value ?: "?"
    val displayLevel = number.substringBefore(".") + if (number.contains(".") && number.endsWith("+")) "+" else ""
    return "$tier Lv.$displayLevel"
}
private data class PracticeViewport(val width: Float, val height: Float, val scale: Float) {
    val field = PracticeField(width, height)
    val scaleY get() = field.yScale
    val left get() = field.left
    val centerX get() = width * .5f
    val centerY get() = height * .5f
}
private data class PracticeLinePose(var x: Float, var y: Float, var rotation: Float, var alpha: Float)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PracticePlayerScreen(pezFile: File, chartSource: PracticeChartSource, segmentMode: Boolean = false, onExit: () -> Unit) {
    val context = LocalContext.current
    val playerScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val preferences = remember(context) { context.getSharedPreferences("practice_player_settings", Context.MODE_PRIVATE) }
    val noiseSettings = remember(context) { PracticeNoiseSettings(context) }
    var showNoiseCompatibilityNotice by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf<PracticeLoaded?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(PracticeMode.LOADING) }
    var chartSeconds by remember { mutableDoubleStateOf(0.0) }
    var audioDuration by remember { mutableDoubleStateOf(0.0) }
    var playToken by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(3) }
    var latencyMs by remember { mutableIntStateOf(preferences.getInt("latency_ms", 0)) }
    var noteScale by remember { mutableFloatStateOf(preferences.getFloat("note_scale", 1f).coerceIn(.6f, 1.6f)) }
    var autoPlayEnabled by remember { mutableStateOf(preferences.getBoolean("auto_play_enabled", false)) }
    var realtimeAccEnabled by remember { mutableStateOf(preferences.getBoolean("realtime_acc_enabled", false)) }
    var judgeDetailsEnabled by remember { mutableStateOf(preferences.getBoolean("judge_details_enabled", false)) }
    var strictModeEnabled by remember { mutableStateOf(preferences.getBoolean("strict_mode_enabled", false)) }
    var pauseDoubleTapEnabled by remember { mutableStateOf(preferences.getBoolean("pause_double_tap_enabled", true)) }
    val pauseGesture = remember { PracticePauseGesture() }
    var strictThisRun by remember { mutableStateOf(strictModeEnabled) }
    val judgeRules = if (strictThisRun) PracticeJudgeRules.Strict else PracticeJudgeRules.Normal
    var seekGestureEnabled by remember { mutableStateOf(preferences.getBoolean("segment_seek_gesture_enabled", true)) }
    var showSeekGuide by remember { mutableStateOf(segmentMode && seekGestureEnabled && !preferences.getBoolean("segment_seek_guide_seen", false)) }
    var autoPlayThisRun by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var calibrationOpen by remember { mutableStateOf(false) }
    var calibrationReturnCursor by remember { mutableDoubleStateOf(0.0) }
    var calibrationReturn by remember { mutableStateOf<PracticeCalibrationReturn?>(null) }
    val calibrationSpeedRequests = remember { Channel<Int>(Channel.CONFLATED) }
    var settingsAreFirstRun by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var combo by remember { mutableIntStateOf(0) }
    var goodTimingLabel by remember { mutableStateOf<String?>(null) }
    var goodTimingEvent by remember { mutableIntStateOf(0) }
    val goodTimingOpacity = remember { Animatable(0f) }
    fun showGoodTiming(deltaSeconds: Double) {
        if (!judgeDetailsEnabled) return
        goodTimingLabel = practiceGoodTimingLabel(deltaSeconds)
        goodTimingEvent++
    }
    LaunchedEffect(goodTimingEvent, judgeDetailsEnabled) {
        goodTimingOpacity.snapTo(0f)
        if (judgeDetailsEnabled && goodTimingLabel != null) {
            goodTimingOpacity.animateTo(1f, tween(140))
            delay(700)
            goodTimingOpacity.animateTo(0f, tween(360))
            goodTimingLabel = null
        }
    }
    var maxCombo by remember { mutableIntStateOf(0) }
    var resultScore by remember { mutableIntStateOf(0) }
    var resultAcc by remember { mutableDoubleStateOf(0.0) }
    var resultRks by remember { mutableDoubleStateOf(0.0) }
    val outcomes = remember(pezFile.absolutePath) { mutableStateMapOf<Int, PracticeOutcome>() }
    val effects = remember(pezFile.absolutePath) { mutableStateListOf<PracticeEffect>() }
    val activeHolds = remember(pezFile.absolutePath) { mutableStateMapOf<Int, ActivePracticeHold>() }
    val fingers = remember { mutableStateMapOf<Long, PracticeFinger>() }
    // Keep Android's raw touch lifetime separately: a blocked pointer must not
    // unlock just because it was removed from the fingers used for judgement.
    val rawFingers = remember { mutableStateMapOf<Long, Offset>() }
    val displayedTimeline = remember { PracticeDisplayedTimeline() }

    fun clearNoiseTouches() {
        rawFingers.clear()
        activeHolds.keys.toList().forEach { id -> activeHolds[id]?.let { hold ->
            activeHolds[id] = hold.copy(pointerId = -1, valid = false)
        } }
        loaded?.noiseRuntime?.resetTouches()
        loaded?.noiseRenderer?.resetTouches()
        loaded?.player?.setNoiseBlocked(false)
    }
    fun syncNoiseTouches(bundle: PracticeLoaded, atTime: Double, vp: PracticeViewport): Set<Long> {
        if (autoPlayThisRun || bundle.chart.blockAreas.isEmpty()) {
            bundle.noiseRuntime.resetTouches()
            bundle.noiseRenderer?.resetTouches()
            bundle.player.setNoiseBlocked(false)
            return emptySet()
        }
        val frame = bundle.noiseRuntime.frame(atTime - bundle.chart.offsetSeconds, vp.field.width, vp.field.height)
        val blocked = bundle.noiseRuntime.updateTouches(frame, rawFingers.mapValues { (_, point) ->
            PracticeNoisePoint(point.x - vp.field.left, vp.field.height - point.y)
        })
        blocked.forEach { fingers.remove(it) }
        bundle.player.setNoiseBlocked(blocked.isNotEmpty())
        return blocked
    }


    val lastAudioSamplePosition = remember { longArrayOf(0L) }
    val armedNotes = remember { mutableStateMapOf<Int, Boolean>() }
    var resumeRun by remember { mutableStateOf(false) }
    var endingPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var editorOpen by remember { mutableStateOf(segmentMode) }
    var range by remember { mutableStateOf(PracticeRange(0.0, .1)) }
    val rangeKey = "segment_${chartSource.fileName}"
    var looping by remember { mutableStateOf(preferences.getBoolean("${rangeKey}_loop", false)) }
    val speedKey = if (segmentMode) "segment_speed_step" else "full_speed_step"
    var speedStep by remember { mutableIntStateOf(preferences.getInt(speedKey, 20).coerceIn(10, 40)) }
    var appliedSpeedStep by remember { mutableIntStateOf(20) }
    val playbackSpeed = practiceSpeed(speedStep)
    var audioCursor by remember { mutableDoubleStateOf(0.0) }
    var judgementFloor by remember { mutableDoubleStateOf(Double.NEGATIVE_INFINITY) }
    var seekRevision by remember { mutableIntStateOf(0) }
    val seekRequests = remember { Channel<PracticeSeekRequest>(Channel.CONFLATED) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var feedbackToken by remember { mutableIntStateOf(0) }
    val tripleTap = remember { PracticeTripleTap() }
    var tapSide by remember { mutableIntStateOf(0) }
    var edgeGesture by remember { mutableStateOf(false) }
    var resumeNeedsSeek by remember { mutableStateOf(false) }
    var tapOrigin by remember { mutableStateOf(Offset.Zero) }
    var startupMessage by remember { mutableStateOf("正在准备音频…") }
    var warmedViewport by remember { mutableStateOf<IntSize?>(null) }
    var renderWarmup by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    suspend fun warmFrame() {
        if (warmedViewport == canvasSize && canvasSize != IntSize.Zero) return
        startupMessage = "正在准备画面…"
        val ready = CompletableDeferred<Unit>()
        renderWarmup = ready
        try {
            withTimeout(8_000) { ready.await() }
            // Let the hardware renderer submit the actual texture/paint work.
            withFrameNanos { }; withFrameNanos { }
            warmedViewport = canvasSize
        } finally { renderWarmup = null }
    }
    suspend fun configureAutoAudio(bundle: PracticeLoaded) {
        val enabled = autoPlayThisRun
        bundle.assets.hitAudio.scheduledAutoPlay = enabled
        bundle.player.setAutoSoundOffset(latencyMs / 1000.0 * playbackSpeed)
        val notes = if (enabled) bundle.chart.notes.filter {
            !it.fake && (calibrationOpen || it.startSeconds + bundle.chart.offsetSeconds >= judgementFloor) &&
                (calibrationOpen || editorOpen || !segmentMode || it.startSeconds + bundle.chart.offsetSeconds < practiceVisualTime(range.end, latencyMs, playbackSpeed))
        } else emptyList()
        val schedule = if (enabled) PracticeAutoSoundSchedule(
            DoubleArray(notes.size) { notes[it].startSeconds + bundle.chart.offsetSeconds },
            IntArray(notes.size) { notes[it].id },
        ) else null
        bundle.player.configureAutoSound(schedule, if (enabled) { id -> bundle.assets.hitAudio.playScheduled(bundle.chart.notes[id].type) } else null)
    }
    LaunchedEffect(autoPlayThisRun, loaded, editorOpen, calibrationOpen, range) {
        loaded?.let { configureAutoAudio(it) }
    }
    LaunchedEffect(latencyMs, speedStep, loaded) {
        loaded?.player?.setAutoSoundOffset(latencyMs / 1000.0 * playbackSpeed)
    }
    suspend fun prepareStart(position: Double) {
        val bundle = loaded ?: return
        startupMessage = "正在准备音频…"
        // Commit the loading panel before any platform audio work is scheduled.
        withFrameNanos { }
        val preparationAt = SystemClock.uptimeMillis()
        bundle.player.prepareStart((position * 1000).toLong())
        val audioReadyAt = SystemClock.uptimeMillis()
        warmFrame()
        configureAutoAudio(bundle)
        android.util.Log.i("PracticeStartup", "prepared position=$position audio=${audioReadyAt-preparationAt}ms frame=${SystemClock.uptimeMillis()-audioReadyAt}ms")
    }
    val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    fun resetAt(position: Double, preview: Boolean) {
        val bundle = loaded ?: return
        val now = practiceVisualTime(position, latencyMs, practiceSpeed(speedStep))
        audioCursor = position
        chartSeconds = now
        judgementFloor = now
        outcomes.clear(); effects.clear(); activeHolds.clear(); fingers.clear(); armedNotes.clear()
        clearNoiseTouches()
        pauseGesture.reset()
        goodTimingLabel = null
        goodTimingEvent++
        combo = if (preview) bundle.chart.notes.count {
            !it.fake && (if (it.type == PracticeNoteType.HOLD) it.endSeconds else it.startSeconds) + bundle.chart.offsetSeconds < now
        } else 0
        maxCombo = combo
        tripleTap.reset(); tapSide = 0; edgeGesture = false
        val runSpeed = practiceSpeed(speedStep)
        bundle.chart.notes.filter { !it.fake && it.startSeconds + bundle.chart.offsetSeconds < now }.forEach { note ->
            if (note.type == PracticeNoteType.HOLD && practiceCarryHold(note.startSeconds + bundle.chart.offsetSeconds, note.endSeconds + bundle.chart.offsetSeconds, now)) {
                activeHolds[note.id] = ActivePracticeHold(-1, PracticeGrade.PERFECT, 0.0, Offset.Zero,
                    lastContact = now, lastEffect = now, carryGraceUntil = now + .2 * runSpeed)
            }
        }
    }

    fun queueSeek(position: Double, after: PracticeMode) {
        val target = position.coerceIn(0.0, audioDuration.coerceAtLeast(.001))
        runCatching { loaded?.player?.pause() }
        seekRevision++
        resetAt(target, editorOpen || calibrationOpen)
        mode = PracticeMode.SEEKING
        seekRequests.trySend(PracticeSeekRequest(seekRevision, target, after))
    }

    fun setCalibrationLatency(value: Int) {
        latencyMs = value.coerceIn(-250, 250)
        loaded?.player?.setAutoSoundOffset(latencyMs / 1000.0 * playbackSpeed)
        // Rebuild the preview at its current cursor. Moving the delay backwards
        // must restore notes that AUTOPLAY already consumed at the old offset.
        resetAt(audioCursor, true)
        playToken++
    }

    fun finishCalibration() {
        preferences.edit().putInt("latency_ms", latencyMs).apply()
        calibrationOpen = false
        loaded?.player?.pause()
        autoPlayThisRun = calibrationReturn?.autoPlay ?: if (editorOpen) true else autoPlayEnabled
        queueSeek(calibrationReturnCursor, if (editorOpen) PracticeMode.PREVIEW else PracticeMode.PAUSED)
        showSettings = true
    }

    fun saveRange(value: PracticeRange) {
        range = PracticeRange.bounded(value.start, value.end, audioDuration)
        preferences.edit().putLong("${rangeKey}_start", (range.start * 1000).toLong())
            .putLong("${rangeKey}_end", (range.end * 1000).toLong()).apply()
    }

    fun returnToEditor() {
        editorOpen = true
        resumeRun = false
        autoPlayThisRun = true
        queueSeek(range.start, PracticeMode.PREVIEW)
    }

    fun startSegment() {
        strictThisRun = strictModeEnabled
        editorOpen = false
        autoPlayThisRun = autoPlayEnabled
        resumeRun = false
        countdown = 3
        queueSeek(range.start, PracticeMode.PREPARING)
    }

    fun finishSegment() {
        runCatching { loaded?.player?.pause() }
        if (looping) startSegment() else returnToEditor()
    }

    suspend fun applySpeed(step: Int): Boolean {
        val bounded = step.coerceIn(10, 40)
        val player = loaded?.player ?: return false
        val wasPlaying = mode == PracticeMode.PLAYING || mode == PracticeMode.PREVIEW_PLAYING
        val previous = practiceSpeed(appliedSpeedStep)
        return try {
            player.setSpeed(practiceSpeed(bounded), wasPlaying, latencyMs / 1000.0 * practiceSpeed(bounded))
            appliedSpeedStep = bounded
            speedStep = bounded
            preferences.edit().putInt(speedKey, bounded).apply()
            playToken++
            true
        } catch (failure: Exception) {
            runCatching { player.setSpeed(previous, wasPlaying, latencyMs / 1000.0 * previous) }
            speedStep = appliedSpeedStep
            preferences.edit().putInt(speedKey, appliedSpeedStep).apply()
            android.widget.Toast.makeText(context, "设备暂不支持该倍速，已恢复原速度", android.widget.Toast.LENGTH_SHORT).show()
            false
        }
    }

    LaunchedEffect(loaded) {
        for (step in calibrationSpeedRequests) {
            if (!calibrationOpen || mode == PracticeMode.SEEKING) continue
            if (applySpeed(step) && calibrationOpen) {
                val cursor = loaded?.player?.readPosition()?.milliseconds?.div(1000.0) ?: audioCursor
                resetAt(cursor, true)
            }
        }
    }

    LaunchedEffect(mode, playToken, speedStep) {
        if (xyz.plcliangpicup.phigrosscore.BuildConfig.DEBUG) {
            android.util.Log.d("PracticeTransport", "mode=$mode segment=$segmentMode editor=$editorOpen position=$audioCursor range=$range speed=${practiceSpeed(speedStep)} auto=$autoPlayThisRun combo=$combo rate-mode=resample")
        }
    }
    LaunchedEffect(feedbackToken) { if (seekFeedback != null) { delay(800); seekFeedback = null } }
    LaunchedEffect(loaded) {
        val bundle = loaded ?: return@LaunchedEffect
        for (request in seekRequests) {
            if (request.revision != seekRevision) continue
            try {
                startupMessage = "正在定位谱面…"
                bundle.player.seekTo((request.position * 1000).toLong())
                if (request.revision != seekRevision) continue
                resetAt(request.position, editorOpen || calibrationOpen)
                if (!calibrationOpen) calibrationReturn?.let { saved ->
                    if (!editorOpen) {
                        outcomes.putAll(saved.outcomes)
                        armedNotes.putAll(saved.armed)
                        activeHolds.clear()
                        saved.holds.forEach { (id, hold) ->
                            activeHolds[id] = hold.copy(pointerId = -1, lastContact = chartSeconds,
                                carryGraceUntil = chartSeconds + .2 * playbackSpeed)
                        }
                        combo = saved.combo; maxCombo = saved.maxCombo
                        judgementFloor = saved.judgementFloor
                    }
                    calibrationReturn = null
                }
                if (request.after == PracticeMode.PLAYING || request.after == PracticeMode.PREVIEW_PLAYING) {
                    practiceStartAfterPreparation(
                        prepare = { prepareStart(request.position) },
                        onCountdown = null,
                        start = {
                            if (request.revision == seekRevision) {
                                startupMessage = "正在启动音频…"
                                bundle.player.start()
                            }
                        },
                    )
                    if (request.revision != seekRevision) { bundle.player.pause(); continue }
                }
                playToken++
                mode = request.after
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                if (request.revision != seekRevision) continue
                loadError = "音频定位超时，请重新进入谱面。"; mode = PracticeMode.ERROR
            } catch (failure: Exception) {
                if (request.revision != seekRevision) continue
                loadError = "无法定位音频：${failure.message}"; mode = PracticeMode.ERROR
            }
        }
    }

    fun enterLoadedChart(bundle: PracticeLoaded) {
        if (segmentMode) {
            autoPlayThisRun = true
            queueSeek(range.start, if (showSeekGuide) PracticeMode.PREVIEW else PracticeMode.PREVIEW_PLAYING)
        } else if (!preferences.getBoolean("first_setup_complete", false)) {
            settingsAreFirstRun = true
            showSettings = true
        } else {
            beginPractice(
                preferences, bundle.player, outcomes, effects, activeHolds,
                onReset = { combo = 0; maxCombo = 0; chartSeconds = 0.0; resumeRun = false; fingers.clear(); armedNotes.clear() },
                onAuto = { autoPlayThisRun = it; strictThisRun = strictModeEnabled },
                onMode = { mode = it }, onToken = { playToken++ },
            )
        }
    }

    fun dismissNoiseCompatibilityNotice() {
        noiseSettings.markNoticeSeen()
        showNoiseCompatibilityNotice = false
        loaded?.let { enterLoadedChart(it) }
    }

    LaunchedEffect(pezFile.absolutePath) {
        mode = PracticeMode.LOADING
        loadError = null
        val result = runCatching {
            withContext(Dispatchers.IO) { loadPractice(context, pezFile) }
        }
        result.onSuccess { bundle ->
            loaded = bundle
            audioDuration = bundle.player.duration.coerceAtLeast(1) / 1000.0
            range = PracticeRange.bounded(
                preferences.getLong("${rangeKey}_start", 0L) / 1000.0,
                preferences.getLong("${rangeKey}_end", (audioDuration * 1000).toLong()) / 1000.0, audioDuration)
            mode = PracticeMode.READY
            applySpeed(speedStep)
            if (noiseSettings.shouldShowNotice(android.os.Build.VERSION.SDK_INT, bundle.chart.blockAreas.isNotEmpty())) {
                showNoiseCompatibilityNotice = true
            } else enterLoadedChart(bundle)
        }.onFailure { failure ->
            loadError = failure.message ?: "谱面读取失败"
            mode = PracticeMode.ERROR
        }
    }

    DisposableEffect(loaded) {
        // Capture this effect's resource, not the mutable state delegate: the
        // initial null effect must never release the newly loaded bundle.
        val owned = loaded
        onDispose {
            owned?.player?.release()
            owned?.assets?.hitAudio?.release()
            owned?.noiseRenderer?.release()
        }
    }
    DisposableEffect(endingPlayer) {
        val owned = endingPlayer
        onDispose { runCatching { owned?.release() } }
    }

    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 72) }
    DisposableEffect(tone) { onDispose { runCatching { tone.release() } } }

    LaunchedEffect(playToken, mode == PracticeMode.PREPARING || mode == PracticeMode.COUNT_IN) {
        val bundle = loaded ?: return@LaunchedEffect
        if (mode != PracticeMode.PREPARING) return@LaunchedEffect
        try {
            val target = if (segmentMode || resumeRun) audioCursor else 0.0
            if (!resumeRun) resetAt(target, false)
            practiceStartAfterPreparation(
                prepare = { prepareStart(target) },
                onCountdown = { number ->
                    countdown = number
                    mode = PracticeMode.COUNT_IN
                    if (number > 0) runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 95) }
                },
                start = { bundle.player.start() },
            )
            activeHolds.keys.toList().forEach { id -> activeHolds[id]?.let {
                activeHolds[id] = it.copy(lastContact = chartSeconds)
            } }
            mode = PracticeMode.PLAYING
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
            bundle.player.pause()
            loadError = "播放准备超时，请重新进入谱面。"; mode = PracticeMode.ERROR
        } catch (failure: Throwable) {
            android.util.Log.e("PracticePlayer", "Playback preparation failed", failure)
            bundle.player.pause()
            loadError = "无法准备谱面播放：${failure.message}"
            mode = PracticeMode.ERROR
        }
    }

    LaunchedEffect(mode, playToken, loaded) {
        val bundle = loaded ?: return@LaunchedEffect
        if (mode != PracticeMode.PLAYING && mode != PracticeMode.PREVIEW_PLAYING) return@LaunchedEffect
        val previewRun = mode == PracticeMode.PREVIEW_PLAYING
        var pendingIndex = 0
        var lastNow = chartSeconds
        var lastLatencyMs = latencyMs
        try {
            while (mode == PracticeMode.PLAYING || mode == PracticeMode.PREVIEW_PLAYING) {
            // Rendering and input extrapolate the same cached audio snapshot in
            // the same uptime timebase, including after pause/seek/rate changes.
            val audioSample = bundle.player.readPosition()
            val sampledPosition = audioSample.milliseconds.coerceAtLeast(0).toLong()
            lastAudioSamplePosition[0] = sampledPosition
            val rawAudioSeconds = sampledPosition / 1000.0
            val interpolated = if (audioSample.advancing) practiceInterpolatedAudio(rawAudioSeconds,
                (SystemClock.uptimeMillis() - audioSample.uptimeMs) / 1000.0, playbackSpeed) else rawAudioSeconds

            val visual = practiceVisualTime(interpolated, latencyMs, playbackSpeed)
            val now = if (lastLatencyMs != latencyMs) visual else max(lastNow, visual)
            lastLatencyMs = latencyMs
            audioCursor = interpolated.coerceIn(0.0, audioDuration)
            lastNow = now
            chartSeconds = now
            val vp = viewportFor(canvasSize)
            // Moving/appearing regions can block a stationary finger between
            // MotionEvents, so run the same noise gate before every judgement.
            syncNoiseTouches(bundle, now, vp)
            val laneRadius = practiceHitRadius(vp.field.width)
            val frameUptime = SystemClock.uptimeMillis()
            val visibleTime = displayedTimeline.at(frameUptime, now)
            fun resolve(note: PracticeNote, grade: PracticeGrade, delta: Double) {
                addPracticeOutcome(note, grade, delta, now, bundle.chart, bundle.assets, vp, outcomes, effects) { successful ->
                    combo = if (successful) combo + 1 else 0
                    maxCombo = max(maxCombo, combo)
                }
                if (grade == PracticeGrade.GOOD && note.type != PracticeNoteType.HOLD) showGoodTiming(delta)
            }
            while (pendingIndex < bundle.chart.notes.size && bundle.chart.notes[pendingIndex].let {
                it.fake || it.startSeconds + bundle.chart.offsetSeconds < judgementFloor || it.id in outcomes || it.id in activeHolds
            }) pendingIndex++
            for (index in pendingIndex until bundle.chart.notes.size) {
                val note = bundle.chart.notes[index]
                if (note.startSeconds + bundle.chart.offsetSeconds > now + judgeRules.bad * playbackSpeed) break
                if (note.fake || note.id in outcomes || note.startSeconds + bundle.chart.offsetSeconds < judgementFloor) continue
                val target = note.startSeconds + bundle.chart.offsetSeconds
                if (segmentMode && !previewRun && target >= practiceVisualTime(range.end, latencyMs, playbackSpeed)) break
                val dt = practiceTimingDelta(now - target, playbackSpeed)
                if (autoPlayThisRun && dt >= 0 && note.id !in activeHolds) {
                    if (note.type == PracticeNoteType.HOLD) {
                        activeHolds[note.id] = ActivePracticeHold(-1, PracticeGrade.PERFECT, 0.0, Offset.Zero, lastContact = now, lastEffect = now)
                        emitPracticeHit(note, PracticeGrade.PERFECT, now, bundle.chart, bundle.assets, vp, effects, true)
                    } else resolve(note, PracticeGrade.PERFECT, 0.0)
                }
                if (note.id in outcomes || note.id in activeHolds) continue
                if (note.type == PracticeNoteType.DRAG && abs(dt) <= judgeRules.good && fingers.values.any {
                    practiceJudgeDistance(bundle.chart, note, visibleTime, vp, it.position, bundle.geometry) <= laneRadius
                }) armedNotes[note.id] = true
                if (note.type == PracticeNoteType.FLICK && note.id !in armedNotes && abs(dt) <= judgeRules.good) {
                    val swipeFinger = fingers.entries.firstOrNull { (_, finger) ->
                        !finger.swipe.consumed && practiceRecentSwipe(finger.swipe.lastSwipeMs, frameUptime) &&
                            practiceJudgeDistance(bundle.chart, note, visibleTime, vp, finger.position, bundle.geometry) <= laneRadius
                    }
                    if (swipeFinger != null) {
                        armedNotes[note.id] = true
                        fingers[swipeFinger.key] = swipeFinger.value.copy(swipe = swipeFinger.value.swipe.consume())
                    }
                }
                if (note.id in armedNotes && dt >= 0) {
                    resolve(note, PracticeGrade.PERFECT, 0.0)
                    armedNotes.remove(note.id)
                } else if (dt > judgeRules.bad && practiceMayMiss(
                    practiceVisualTime(rawAudioSeconds, latencyMs, playbackSpeed), target, playbackSpeed, judgeRules.bad))
                    resolve(note, PracticeGrade.MISS, dt)
            }
            activeHolds.toMap().forEach { (id, oldHold) ->
                val note = bundle.chart.notes[id]
                val end = if (segmentMode && !previewRun) min(note.endSeconds + bundle.chart.offsetSeconds, practiceVisualTime(range.end, latencyMs, playbackSpeed)) else note.endSeconds + bundle.chart.offsetSeconds
                val contact = autoPlayThisRun || practiceHoldHasContact(oldHold.pointerId, fingers) {
                    practiceJudgeDistance(bundle.chart, note, now, vp, it.position, bundle.geometry) <= laneRadius
                }
                val nearEnd = now >= end - judgeRules.bad * playbackSpeed
                var hold = if (contact || nearEnd) oldHold.copy(lastContact = now, valid = true) else oldHold.copy(valid = false)
                if (!nearEnd && now >= hold.carryGraceUntil && (now - hold.lastContact) / playbackSpeed > .05) {
                    resolve(note, PracticeGrade.MISS, hold.deltaSeconds)
                    activeHolds.remove(id)
                } else if (now >= end) {
                    resolve(note, hold.grade, hold.deltaSeconds)
                    activeHolds.remove(id)
                } else {
                    if (now - hold.lastEffect >= .15) {
                        emitPracticeHit(note, hold.grade, now, bundle.chart, bundle.assets, vp, effects, false)
                        hold = hold.copy(lastEffect = now)
                    }
                    activeHolds[id] = hold
                }
            }
            effects.removeAll { now - it.time > maxOf(bundle.assets.fxDuration, PRACTICE_HIT_LIFETIME_SECONDS) || now < it.time }
            if (segmentMode && !previewRun && interpolated >= range.end) {
                finishSegment()
                break
            }
            if (previewRun && (interpolated >= audioDuration || !bundle.player.isPlaying)) {
                bundle.player.pause()
                audioCursor = audioDuration
                mode = PracticeMode.PREVIEW
                break
            }
            if (!segmentMode && !editorOpen && !calibrationOpen &&
                (rawAudioSeconds >= audioDuration - .04 || (!bundle.player.isPlaying && rawAudioSeconds >= audioDuration - .25))) {
                bundle.player.pause()
                val good = outcomes.values.count { it.grade == PracticeGrade.GOOD }
                val perfect = outcomes.values.count { it.grade == PracticeGrade.PERFECT }
                val bad = outcomes.values.count { it.grade == PracticeGrade.BAD }
                val miss = bundle.chart.notes.count { !it.fake } - perfect - good - bad
                val score = calculatePlayScoreAndAccuracy(
                    noteCount = bundle.chart.notes.count { !it.fake },
                    perfectCount = perfect,
                    goodCount = good,
                    badCount = bad,
                    missCount = miss,
                    maxCombo = maxCombo,
                )
                resultScore = score.score
                resultAcc = score.accuracy
                resultRks = calculateChartRks(chartSource.chartConstant, score.accuracy)
                mode = PracticeMode.RESULT
                endingPlayer?.release()
                endingPlayer = runCatching {
                    MediaPlayer().apply {
                        setDataSource(bundle.assets.endingFile.absolutePath)
                        setOnCompletionListener { if (endingPlayer === it) endingPlayer = null }
                        prepare()
                        start()
                    }
                }.getOrNull()
                break
            }
            withFrameNanos { }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            android.util.Log.e("PracticePlayer", "Playback loop failed", failure)
            loadError = "谱面播放发生错误（${failure.javaClass.simpleName}" +
                (failure.message?.let { ": $it" } ?: "") + "），请重试。"
            runCatching { bundle.player.pause() }
            mode = PracticeMode.ERROR
        }
    }

    val viewport = viewportFor(canvasSize)
    val noteCountForHud = remember(loaded) { loaded?.chart?.notes?.count { !it.fake } ?: 0 }
    val perfectCountForHud by remember { derivedStateOf { outcomes.values.count { it.grade == PracticeGrade.PERFECT } } }
    val goodCountForHud by remember { derivedStateOf { outcomes.values.count { it.grade == PracticeGrade.GOOD } } }
    val badCountForHud by remember { derivedStateOf { outcomes.values.count { it.grade == PracticeGrade.BAD } } }
    val missCountForHud by remember { derivedStateOf { outcomes.values.count { it.grade == PracticeGrade.MISS } } }
    val realtimeAccForHud = practiceRealtimeAccuracy(
        perfectCountForHud, goodCountForHud, badCountForHud, missCountForHud)
    val judgeLineColor by remember { derivedStateOf { when {
        outcomes.values.any { it.grade == PracticeGrade.BAD || it.grade == PracticeGrade.MISS } -> NativeColor.WHITE
        (outcomes.values.any { it.grade == PracticeGrade.GOOD } || activeHolds.values.any { it.grade == PracticeGrade.GOOD }) -> NativeColor.rgb(202, 234, 255)
        else -> NativeColor.rgb(255, 237, 165)
    } } }
    val scoreForHud = practiceHudScore(!segmentMode && !editorOpen && !calibrationOpen,
        noteCountForHud, perfectCountForHud, goodCountForHud, badCountForHud, maxCombo)
    LaunchedEffect(playToken, mode) { displayedTimeline.clear() }
    LaunchedEffect(playToken) { goodTimingLabel = null; goodTimingEvent++ }
    LaunchedEffect(mode, playToken, pauseDoubleTapEnabled) { pauseGesture.reset() }
    LaunchedEffect(mode, loaded, autoPlayThisRun) {
        if (mode != PracticeMode.PLAYING || autoPlayThisRun) clearNoiseTouches()
    }
    val framePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    fun pausePractice() {
        if (mode == PracticeMode.PLAYING || mode == PracticeMode.PREPARING || mode == PracticeMode.COUNT_IN || mode == PracticeMode.SEEKING) {
            resumeNeedsSeek = mode == PracticeMode.SEEKING
            seekRevision++
            loaded?.player?.pause()
            fingers.clear(); clearNoiseTouches(); pauseGesture.reset()
            mode = PracticeMode.PAUSED
        }
    }
    val touchHandler by rememberUpdatedState<(MotionEvent) -> Boolean> { event ->
        val bundle = loaded
        val field = viewportFor(canvasSize).field
        val pauseTarget = practicePauseTarget(field)
        val pauseTouchEnabled = bundle != null && !editorOpen && !calibrationOpen &&
            (mode == PracticeMode.PLAYING || mode == PracticeMode.COUNT_IN)
        if (pauseTouchEnabled) {
            when {
                event.actionMasked == MotionEvent.ACTION_CANCEL || event.pointerCount > 1 -> pauseGesture.reset()
                event.actionMasked == MotionEvent.ACTION_DOWN -> pauseGesture.down(
                    event.getPointerId(0).toLong(), event.x, event.y, event.eventTime, pauseTarget.contains(event.x, event.y))
                event.actionMasked == MotionEvent.ACTION_MOVE -> {
                    for (sample in 0..event.historySize) {
                        val x = if (sample == event.historySize) event.x else event.getHistoricalX(0, sample)
                        val y = if (sample == event.historySize) event.y else event.getHistoricalY(0, sample)
                        pauseGesture.move(event.getPointerId(0).toLong(), x, y, pauseTarget.contains(x, y), touchSlop)
                    }
                }
            }
        } else pauseGesture.reset()
        val side = when {
            event.y < field.height * .4f || event.y > field.height * .6f || event.x < field.left || event.x > field.left + field.width -> 0
            event.x < field.left + field.width * .15f -> -1
            event.x > field.left + field.width * .85f -> 1
            else -> 0
        }
        var consumed = false
        if (segmentMode && seekGestureEnabled && !editorOpen && mode == PracticeMode.PLAYING) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tapSide = side; edgeGesture = side != 0; tapOrigin = Offset(event.x, event.y)
                    if (side == 0) tripleTap.reset()
                    consumed = false
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> { consumed = false; tripleTap.reset(); tapSide = 0; if (event.actionMasked == MotionEvent.ACTION_CANCEL) edgeGesture = false }
                MotionEvent.ACTION_MOVE -> {
                    consumed = false
                    if ((Offset(event.x, event.y) - tapOrigin).getDistance() > touchSlop || side != tapSide) { tripleTap.reset(); tapSide = 0 }
                }
                MotionEvent.ACTION_UP -> {
                    consumed = false
                    if (tapSide != 0 && side == tapSide && event.eventTime - event.downTime <= 250 &&
                        (Offset(event.x, event.y) - tapOrigin).getDistance() <= touchSlop && tripleTap.tap(side, event.eventTime)) {
                        consumed = true
                        val destination = range.seek(audioCursor, side * 3.0)
                        seekFeedback = if (side < 0) "−3 秒" else "+3 秒"; feedbackToken++
                        if (destination >= range.end) finishSegment() else queueSeek(destination, PracticeMode.PLAYING)
                    } else if (event.eventTime - event.downTime > 250) tripleTap.reset()
                    tapSide = 0; edgeGesture = false
                }
            }
        }
        val handled = if (consumed) {
            true
        } else if (bundle == null || mode != PracticeMode.PLAYING || autoPlayThisRun) {
            segmentMode && seekGestureEnabled && !editorOpen && mode == PracticeMode.PLAYING && (edgeGesture || event.actionMasked == MotionEvent.ACTION_UP)
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            // CANCEL can come from Android/Compose multi-touch arbitration; it
            // is not a pause request. Discard input ownership and allow a brief
            // Hold re-contact window. Actual app backgrounding pauses below.
            fingers.clear(); clearNoiseTouches(); armedNotes.clear(); pauseGesture.reset()
            tripleTap.reset(); tapSide = 0; edgeGesture = false
            val audioPosition = bundle.player.position
            val now = practiceEventVisualTime(audioPosition.milliseconds.toLong(), audioPosition.uptimeMs,
                event.eventTime, latencyMs, playbackSpeed, audioPosition.advancing)
            activeHolds.keys.toList().forEach { id -> activeHolds[id]?.let { hold ->
                activeHolds[id] = hold.copy(pointerId = -1, valid = false,
                    carryGraceUntil = max(hold.carryGraceUntil, now + .2 * playbackSpeed))
            } }
            true
        } else {
            val vp = viewportFor(canvasSize)
            val laneRadius = practiceHitRadius(vp.field.width)
            val touchAudioPosition = runCatching { bundle.player.readPosition() }.getOrDefault(bundle.player.position)
            val touchSampleUptime = touchAudioPosition.uptimeMs
            val touchSamplePosition = touchAudioPosition.milliseconds.toLong()
            val now = practiceEventVisualTime(touchSamplePosition, touchSampleUptime,
                event.eventTime, latencyMs, playbackSpeed, touchAudioPosition.advancing)
            fun candidate(position: Offset, flick: Boolean, atTime: Double, sampleUptime: Long): PracticeNote? {
                val poseTime = displayedTimeline.at(sampleUptime, chartSeconds)
                val notes = practiceNearbyNotes(bundle.chart, atTime, playbackSpeed).filter {
                    !it.fake && it.startSeconds + bundle.chart.offsetSeconds >= judgementFloor &&
                    (!segmentMode || it.startSeconds + bundle.chart.offsetSeconds < practiceVisualTime(range.end, latencyMs, playbackSpeed)) &&
                    it.id !in outcomes && it.id !in activeHolds && it.id !in armedNotes &&
                    (if (flick) it.type == PracticeNoteType.FLICK else it.type == PracticeNoteType.TAP || it.type == PracticeNoteType.HOLD) &&
                    abs(practiceTimingDelta(atTime - (it.startSeconds + bundle.chart.offsetSeconds), playbackSpeed)) <=
                        (if (it.type == PracticeNoteType.TAP) judgeRules.bad else judgeRules.good)
                }
                val matches = notes.map { PracticeHitCandidate(it.id, it.startSeconds + bundle.chart.offsetSeconds,
                    practiceJudgeDistance(bundle.chart, it, poseTime, vp, position, bundle.geometry)) }.filter { it.distance <= laneRadius }
                val id = practiceChooseHit(matches, atTime, playbackSpeed, vp.field.width, judgeRules.good)
                return notes.firstOrNull { it.id == id }
            }
            fun contact(pointerId: Long, position: Offset, atTime: Double, sampleUptime: Long) {
                val poseTime = displayedTimeline.at(sampleUptime, chartSeconds)
                practiceNearbyNotes(bundle.chart, atTime, playbackSpeed).forEach { note ->
                    val target = note.startSeconds + bundle.chart.offsetSeconds
                    if (!note.fake && note.type == PracticeNoteType.DRAG && target >= judgementFloor &&
                        (!segmentMode || target < practiceVisualTime(range.end, latencyMs, playbackSpeed)) &&
                        note.id !in outcomes && abs(practiceTimingDelta(atTime-target, playbackSpeed)) <= judgeRules.good &&
                        practiceJudgeDistance(bundle.chart, note, poseTime, vp, position, bundle.geometry) <= laneRadius) {
                        armedNotes[note.id] = true
                        pauseGesture.reset()
                        tripleTap.reset(); tapSide = 0
                    }
                }
                activeHolds.toMap().forEach { (id, hold) ->
                    if (hold.pointerId == pointerId ||
                        practiceJudgeDistance(bundle.chart, bundle.chart.notes[id], poseTime, vp, position, bundle.geometry) <= laneRadius) {
                        activeHolds[id] = hold.copy(lastContact = max(hold.lastContact, atTime), valid = true)
                        pauseGesture.reset()
                        tripleTap.reset(); tapSide = 0
                    }
                }
            }
            fun arm(note: PracticeNote, atTime: Double) {
                pauseGesture.reset()
                val target = note.startSeconds + bundle.chart.offsetSeconds
                if (atTime < target) { armedNotes[note.id] = true; return }
                // A late Drag/Flick can resolve during input dispatch rather than
                // waiting for the render coroutine's next frame.
                armedNotes.remove(note.id)
                addPracticeOutcome(note, PracticeGrade.PERFECT, 0.0, atTime,
                    bundle.chart, bundle.assets, vp, outcomes, effects) { success ->
                    combo = if (success) combo + 1 else 0
                    maxCombo = max(maxCombo, combo)
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                fingers.clear()
                clearNoiseTouches()
                (context as? android.app.Activity)?.window?.decorView?.requestUnbufferedDispatch(event)
            }
            val lifting = if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
            val pressing = if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) event.actionIndex else -1
            // Process each historical batch for all pointers before any note.
            // Otherwise a second finger could temporarily disappear from the
            // raw map, or a move through a noise area could still produce a hit.
            for (sample in 0..event.historySize) {
                val sampleTime = if (sample == event.historySize) event.eventTime else event.getHistoricalEventTime(sample)
                val sampleVisualTime = practiceEventVisualTime(touchSamplePosition, touchSampleUptime,
                    sampleTime, latencyMs, playbackSpeed, touchAudioPosition.advancing)
                for (index in 0 until event.pointerCount) {
                    rawFingers[event.getPointerId(index).toLong()] = if (sample == event.historySize)
                        Offset(event.getX(index), event.getY(index)) else
                        Offset(event.getHistoricalX(index, sample), event.getHistoricalY(index, sample))
                }
                val blocked = syncNoiseTouches(bundle, sampleVisualTime, vp)
                for (index in 0 until event.pointerCount) {
                    val id = event.getPointerId(index).toLong()
                    val samplePosition = rawFingers.getValue(id)
                    if (id in blocked) continue
                    val previous = fingers[id]
                    var swipe = previous?.swipe ?: PracticeSwipe()
                    contact(id, samplePosition, sampleVisualTime, sampleTime)
                    armedNotes.keys.toList().forEach { noteId ->
                        val note = bundle.chart.notes[noteId]
                        if (sampleVisualTime >= note.startSeconds + bundle.chart.offsetSeconds) arm(note, sampleVisualTime)
                    }
                    if (previous != null && sampleTime > previous.timeMs) {
                        val movement = samplePosition - previous.position
                        swipe = swipe.sample(movement.x, movement.y, sampleTime - previous.timeMs, sampleTime, vp.field.width)
                        if (!swipe.consumed && practiceRecentSwipe(swipe.lastSwipeMs, sampleTime)) candidate(samplePosition, true,
                            sampleVisualTime, sampleTime)?.let {
                            tripleTap.reset(); tapSide = 0
                            arm(it, sampleVisualTime)
                            swipe = swipe.consume()
                        }
                    }
                    fingers[id] = PracticeFinger(samplePosition, sampleTime, swipe)
                    if (index == pressing && sample == event.historySize) candidate(samplePosition,false,now,event.eventTime)?.let { note ->
                    pauseGesture.reset()
                    tripleTap.reset(); tapSide = 0
                    val delta = practiceTimingDelta(now-(note.startSeconds+bundle.chart.offsetSeconds), playbackSpeed)
                    val grade = judgeRules.grade(delta) ?: return@let
                    if (note.type == PracticeNoteType.HOLD) {
                        activeHolds[note.id] = ActivePracticeHold(id,grade,delta,samplePosition,lastContact=now,lastEffect=now)
                        emitPracticeHit(note,grade,now,bundle.chart,bundle.assets,vp,effects,true)
                        if (grade == PracticeGrade.GOOD) showGoodTiming(delta)
                    } else addPracticeOutcome(note,grade,delta,now,bundle.chart,bundle.assets,vp,outcomes,effects) { success ->
                        combo = if (success) combo+1 else 0
                        maxCombo = max(maxCombo,combo)
                    }
                    if (note.type != PracticeNoteType.HOLD && grade == PracticeGrade.GOOD) showGoodTiming(delta)
                    }
                }
            }
            if (lifting >= 0) {
                val id = event.getPointerId(lifting).toLong()
                fingers.remove(id); rawFingers.remove(id)
                activeHolds.keys.toList().forEach { noteId -> activeHolds[noteId]?.let { hold ->
                    if (hold.pointerId == id) activeHolds[noteId] = hold.copy(pointerId = -1, valid = false)
                } }
                syncNoiseTouches(bundle, now, vp)
            }
            true
        }
        // Run after note judgement so a note hit wins even inside the HUD zone.
        if (pauseTouchEnabled && event.actionMasked == MotionEvent.ACTION_UP &&
            pauseGesture.up(event.getPointerId(0).toLong(), event.x, event.y, event.eventTime,
                pauseTarget.contains(event.x, event.y), touchSlop, pauseDoubleTapEnabled)) {
            pausePractice()
        }
        handled || pauseTouchEnabled
    }
    val pointerModifier = Modifier.pointerInteropFilter { touchHandler(it) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, loaded) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) loaded?.player?.suspendOutput()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE &&
                (mode == PracticeMode.PLAYING || mode == PracticeMode.PREPARING || mode == PracticeMode.COUNT_IN || mode == PracticeMode.PREVIEW_PLAYING || mode == PracticeMode.SEEKING)) {
                resumeNeedsSeek = mode == PracticeMode.SEEKING
                runCatching { loaded?.player?.suspendOutput() }
                fingers.clear(); clearNoiseTouches(); seekRevision++
                armedNotes.clear(); pauseGesture.reset()
                mode = if (editorOpen) PracticeMode.PREVIEW else PracticeMode.PAUSED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = mode != PracticeMode.LOADING && mode != PracticeMode.ERROR) {
        when {
            calibrationOpen -> finishCalibration()
            editorOpen -> { loaded?.player?.pause(); seekRevision++; onExit() }
            mode == PracticeMode.PLAYING || mode == PracticeMode.PREPARING || mode == PracticeMode.COUNT_IN || mode == PracticeMode.SEEKING -> {
                pausePractice()
            }
            mode == PracticeMode.PAUSED -> { if (segmentMode) returnToEditor() else showExitConfirm = true }
            mode == PracticeMode.RESULT -> showExitConfirm = true
            else -> Unit
        }
    }

    val renderField: @Composable () -> Unit = {
    Box(Modifier.fillMaxSize().background(Color(0xFF101116))) {
        loaded?.assets?.background?.let { bitmap ->
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = .24f,
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .50f)))
        loaded?.assets?.sideBackground?.let { PracticeSideBackground(it, viewport.field) }
        Canvas(
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .then(pointerModifier),
        ) {
            val bundle = loaded ?: return@Canvas
            displayedTimeline.record(SystemClock.uptimeMillis(), chartSeconds)
            drawIntoCanvas { composeCanvas ->
                if (renderWarmup != null) {
                    framePaint.alpha = 255
                    framePaint.colorFilter = null
                    (bundle.assets.notes.values + bundle.assets.notesMulti.values + bundle.assets.hitFx).forEachIndexed { index, bitmap ->
                        composeCanvas.nativeCanvas.drawBitmap(bitmap, null,
                            RectF(index * 4f, 0f, index * 4f + 3f, 3f), framePaint)
                    }
                }
                val drawScene: (NativeCanvas) -> Unit = { sceneCanvas -> drawPracticeFrame(
                    canvas = sceneCanvas,
                    viewport = viewportFor(IntSize(size.width.toInt(), size.height.toInt())),
                    chart = bundle.chart,
                    assets = bundle.assets,
                    judgeLineColor = judgeLineColor,
                    time = chartSeconds,
                    noteScale = noteScale,
                    outcomes = outcomes,
                    effects = if (renderWarmup != null) listOf(
                        PracticeEffect(2f, 2f, PracticeGrade.PERFECT, chartSeconds),
                        PracticeEffect(6f, 2f, PracticeGrade.GOOD, chartSeconds),
                    ) else effects,
                    paint = framePaint,
                    geometry = bundle.geometry,
                ) }
                val noiseRenderer = bundle.noiseRenderer
                if (noiseRenderer == null) drawScene(composeCanvas.nativeCanvas)
                else noiseRenderer.drawScene(composeCanvas.nativeCanvas,
                    bundle.noiseRuntime.frame(chartSeconds - bundle.chart.offsetSeconds, viewport.field.width, viewport.field.height),
                    viewport.field.left, viewport.field.width, viewport.field.height,
                    rawFingers.filterKeys { bundle.noiseRuntime.isFingerBlocked(it) }.mapValues { (_, point) ->
                        PracticeNoisePoint(point.x - viewport.field.left, viewport.field.height - point.y)
                    }, bundle.assets.background, viewport.width, warmUp = renderWarmup != null, scene = drawScene)
                renderWarmup?.complete(Unit)
            }
        }
        if ((editorOpen || calibrationOpen) && loaded != null) {
            PracticeGameplayHud(
                field = viewport.field, score = 0, combo = combo, autoPlay = true,
                segmentMode = true, previewMode = true, songName = "", difficulty = "",
                progress = { (audioCursor / audioDuration.coerceAtLeast(.001)).toFloat() }, onPause = {},
            )
        }

    }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF101116))) {
        if (calibrationOpen && loaded != null) {
            renderField()
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xB0181B21)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        if (mode == PracticeMode.PREVIEW_PLAYING) { loaded?.player?.pause(); mode = PracticeMode.PREVIEW }
                        else queueSeek(if (audioCursor >= audioDuration - .01) 0.0 else audioCursor, PracticeMode.PREVIEW_PLAYING)
                    }) { Icon(if (mode == PracticeMode.PREVIEW_PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "播放或暂停校准谱面", tint = Color.White) }
                    Text("调整延迟", color = Color.White, modifier = Modifier.weight(1f))
                    Text("${if (latencyMs > 0) "+" else ""}$latencyMs ms", color = AppAccent)
                    androidx.compose.material3.TextButton(onClick = { finishCalibration() }) { Text("完成") }
                }
                Slider(value = latencyMs.toFloat(), onValueChange = { setCalibrationLatency(it.roundToInt()) }, valueRange = -250f..250f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    androidx.compose.material3.TextButton(onClick = { setCalibrationLatency(latencyMs - 1) }) { Text("−1 ms") }
                    androidx.compose.material3.TextButton(onClick = { setCalibrationLatency(0) }) { Text("重置") }
                    androidx.compose.material3.TextButton(onClick = { setCalibrationLatency(latencyMs + 1) }) { Text("+1 ms") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("播放速度", color = Color.White, fontSize = 12.sp)
                    androidx.compose.material3.TextButton(onClick = { calibrationSpeedRequests.trySend((speedStep - 1).coerceAtLeast(10)) },
                        enabled = speedStep > 10 && mode != PracticeMode.SEEKING) { Text("−0.05x") }
                    Slider(value = speedStep.toFloat(), onValueChange = { calibrationSpeedRequests.trySend(it.roundToInt()) },
                        valueRange = 10f..40f, steps = 29, enabled = mode != PracticeMode.SEEKING,
                        modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { calibrationSpeedRequests.trySend((speedStep + 1).coerceAtMost(40)) },
                        enabled = speedStep < 40 && mode != PracticeMode.SEEKING) { Text("+0.05x") }
                    androidx.compose.material3.TextButton(onClick = { calibrationSpeedRequests.trySend(20) }, enabled = mode != PracticeMode.SEEKING) {
                        Text("%.2fx".format(Locale.ROOT, playbackSpeed))
                    }
                }
            }
        } else if (editorOpen && loaded != null) {
            PracticeSegmentEditor(
                title = chartSource.title, duration = audioDuration, cursor = audioCursor,
                range = range, playing = mode == PracticeMode.PREVIEW_PLAYING, seeking = mode == PracticeMode.SEEKING,
                looping = looping, speedStep = speedStep,
                onScrub = {
                    runCatching { loaded?.player?.pause() }
                    seekRevision++
                    mode = PracticeMode.SEEKING
                    resetAt(it, true)
                },
                onScrubFinished = { queueSeek(it, PracticeMode.PREVIEW) },
                onRange = { saveRange(it) },
                onLoop = { looping = it; preferences.edit().putBoolean("${rangeKey}_loop", it).apply() },
                onSpeed = { step ->
                    if (mode == PracticeMode.PREVIEW_PLAYING) { loaded?.player?.pause(); mode = PracticeMode.PREVIEW }
                    playerScope.launch { if (applySpeed(step)) queueSeek(audioCursor, PracticeMode.PREVIEW) }
                },
                onPlay = {
                    if (mode == PracticeMode.PREVIEW_PLAYING) { loaded?.player?.pause(); mode = PracticeMode.PREVIEW }
                    else queueSeek(if (audioCursor >= audioDuration - .01) 0.0 else audioCursor, PracticeMode.PREVIEW_PLAYING)
                },
                onStart = { startSegment() },
                onSettings = { loaded?.player?.pause(); seekRevision++; mode = PracticeMode.PREVIEW; settingsAreFirstRun = false; showSettings = true },
                onExit = { seekRevision++; loaded?.player?.pause(); onExit() },
                preview = renderField,
            )
        } else renderField()
        if (!editorOpen && !calibrationOpen && mode !in setOf(PracticeMode.LOADING, PracticeMode.ERROR, PracticeMode.RESULT)) {
            PracticeGameplayHud(
                field = viewport.field,
                score = scoreForHud,
                combo = combo,
                autoPlay = autoPlayThisRun,
                pauseDoubleTap = pauseDoubleTapEnabled,
                strictMode = strictThisRun,
                realtimeAccuracy = if (realtimeAccEnabled) realtimeAccForHud else null,
                goodTimingLabel = if (judgeDetailsEnabled) goodTimingLabel else null,
                goodTimingAlpha = goodTimingOpacity.value,
                segmentMode = segmentMode,
                songName = loaded?.chart?.name.orEmpty(),
                difficulty = practiceDifficultyLabel(loaded?.chart?.level.orEmpty()),
                progress = { if (segmentMode) ((audioCursor - range.start) / (range.end - range.start)).toFloat() else (audioCursor / audioDuration.coerceAtLeast(.001)).toFloat() },
                onPause = { pausePractice() },
            )
            if (mode == PracticeMode.COUNT_IN) {
                AnimatedVisibility(
                    visible = countdown > 0,
                    modifier = Modifier.align(Alignment.Center),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Text(countdown.toString(), color = Color.White, fontSize = 64.sp, style = MaterialTheme.typography.displayLarge)
                }
            }
        }

        if (!editorOpen && seekFeedback != null) {
            Text(seekFeedback.orEmpty(), color = Color.White, fontSize = 26.sp,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = .6f)).padding(20.dp))
        }
        if (mode == PracticeMode.LOADING) {
            PracticeCenterPanel(title = "正在载入谱面", subtitle = "正在解码音乐与载入谱面画面…")
        }
        if (mode == PracticeMode.PREPARING) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .88f)).clickable { }) {
                PracticeCenterPanel(title = "正在准备播放", subtitle = startupMessage)
            }
        }
        if (mode == PracticeMode.ERROR) {
            PracticeMessagePanel(title = "谱面无法播放", message = loadError.orEmpty(), primary = "返回", onPrimary = onExit)
        }
        if (mode == PracticeMode.PAUSED) {
            PracticePausePanel(
                chartSource = chartSource,
                backgroundArtwork = loaded?.assets?.background,
                segmentMode = segmentMode,
                onResume = { resumeRun = true; countdown = 3; if (resumeNeedsSeek) { resumeNeedsSeek = false; queueSeek(audioCursor, PracticeMode.PREPARING) } else { playToken++; mode = PracticeMode.PREPARING } },
                onRestart = {
                    if (segmentMode) startSegment() else beginPractice(
                        preferences, loaded?.player, outcomes, effects, activeHolds,
                        onReset = { combo = 0; maxCombo = 0; chartSeconds = 0.0; resumeRun = false; fingers.clear(); armedNotes.clear() },
                        onAuto = { autoPlayThisRun = it; strictThisRun = strictModeEnabled }, onMode = { mode = it }, onToken = { playToken++ },
                    )
                },
                onSettings = { settingsAreFirstRun = false; showSettings = true },
                onExit = { if (segmentMode) returnToEditor() else showExitConfirm = true },
            )
        }
        if (mode == PracticeMode.RESULT) {
            PracticeResultPanel(
                artwork = loaded?.assets?.illustration,
                blurredArtwork = loaded?.assets?.background,
                songName = loaded?.chart?.name.orEmpty(),
                difficulty = practiceDifficultyLabel(loaded?.chart?.level.orEmpty()),
                score = resultScore,
                accuracy = resultAcc,
                rks = resultRks,
                perfect = outcomes.values.count { it.grade == PracticeGrade.PERFECT },
                goodEarly = outcomes.values.count { it.grade == PracticeGrade.GOOD && it.deltaSeconds < 0 },
                goodLate = outcomes.values.count { it.grade == PracticeGrade.GOOD && it.deltaSeconds >= 0 },
                bad = outcomes.values.count { it.grade == PracticeGrade.BAD },
                miss = outcomes.values.count { it.grade == PracticeGrade.MISS } +
                    ((loaded?.chart?.notes?.count { !it.fake } ?: 0) - outcomes.size).coerceAtLeast(0),
                maxCombo = maxCombo,
                auto = autoPlayThisRun,
                strict = strictThisRun,
                speed = playbackSpeed,
                onRetry = {
                    endingPlayer?.release(); endingPlayer = null
                    beginPractice(
                        preferences, loaded?.player, outcomes, effects, activeHolds,
                        onAuto = { autoPlayThisRun = it; strictThisRun = strictModeEnabled }, onMode = { mode = it }, onToken = { playToken++ },
                        onReset = { combo = 0; maxCombo = 0; chartSeconds = 0.0; resumeRun = false; fingers.clear(); armedNotes.clear() },
                    )
                    queueSeek(0.0, PracticeMode.PREPARING)
                },
                onExit = { showExitConfirm = true },
            )
        }
    }


    if (showNoiseCompatibilityNotice && loaded != null) {
        AlertDialog(
            onDismissRequest = { dismissNoiseCompatibilityNotice() },
            title = { Text("噪域显示提示") },
            text = { Text(PRACTICE_NOISE_COMPATIBILITY_NOTICE) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { dismissNoiseCompatibilityNotice() }) { Text("知道了") }
            },
        )
    }
    if (showSeekGuide && loaded != null && !showSettings && !showNoiseCompatibilityNotice) {
        AlertDialog(
            onDismissRequest = { showSeekGuide = false; preferences.edit().putBoolean("segment_seek_guide_seen", true).apply() },
            title = { Text("快进与快退") },
            text = { Text("分段练习时，在谱面左侧或右侧的中间区域连续点击三次，可快退或快进 3 秒。可在练习设置中关闭。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showSeekGuide = false
                    preferences.edit().putBoolean("segment_seek_guide_seen", true).apply()
                }) { Text("知道了") }
            },
        )
    }
    if (showSettings) {
        PracticeSettingsDialog(
            backgroundArtwork = loaded?.assets?.background,
            initialDelayMs = latencyMs,
            initialNoteScale = noteScale,
            initialAutoPlay = autoPlayEnabled,
            initialRealtimeAcc = realtimeAccEnabled,
            initialJudgeDetails = judgeDetailsEnabled,
            initialStrictMode = strictModeEnabled,
            initialPauseDoubleTap = pauseDoubleTapEnabled,
            initialSpeedStep = speedStep,
            initialSeekGestureEnabled = seekGestureEnabled,
            showSeekGestureOption = segmentMode,
            requireChoice = settingsAreFirstRun,
            onCalibrate = {
                calibrationReturnCursor = audioCursor
                calibrationReturn = PracticeCalibrationReturn(outcomes.toMap(), activeHolds.toMap(), armedNotes.toMap(),
                    combo, maxCombo, judgementFloor, autoPlayThisRun)
                showSettings = false
                calibrationOpen = true
                autoPlayThisRun = true
                queueSeek(0.0, PracticeMode.PREVIEW_PLAYING)
            },
            onSave = { delayValue, sizeValue, autoValue, newSpeedStep, gestureValue, accValue, detailsValue, strictValue, pauseDoubleTapValue ->
                playerScope.launch {
                runCatching {
                    applySpeed(newSpeedStep)
                    latencyMs = delayValue
                    chartSeconds = practiceVisualTime(audioCursor, delayValue, practiceSpeed(speedStep))
                    noteScale = sizeValue
                    autoPlayEnabled = autoValue
                    realtimeAccEnabled = accValue
                    judgeDetailsEnabled = detailsValue
                    strictModeEnabled = strictValue
                    pauseDoubleTapEnabled = pauseDoubleTapValue
                    seekGestureEnabled = gestureValue
                    if (gestureValue && segmentMode && !preferences.getBoolean("segment_seek_guide_seen", false)) showSeekGuide = true
                    autoPlayThisRun = if (editorOpen) true else autoValue
                    preferences.edit()
                        .putInt("latency_ms", delayValue)
                        .putFloat("note_scale", sizeValue)
                        .putBoolean("auto_play_enabled", autoValue)
                        .putBoolean("realtime_acc_enabled", accValue)
                        .putBoolean("judge_details_enabled", detailsValue)
                        .putBoolean("strict_mode_enabled", strictValue)
                        .putBoolean("pause_double_tap_enabled", pauseDoubleTapValue)
                        .putBoolean("segment_seek_gesture_enabled", gestureValue)
                        .putBoolean("first_setup_complete", true)
                        .apply()
                    showSettings = false
                    if (editorOpen) queueSeek(audioCursor, PracticeMode.PREVIEW)
                    if (settingsAreFirstRun) {
                        settingsAreFirstRun = false
                        beginPractice(
                            preferences, loaded?.player, outcomes, effects, activeHolds,
                            onReset = { combo = 0; maxCombo = 0; chartSeconds = 0.0; resumeRun = false; fingers.clear(); armedNotes.clear() },
                            onAuto = { autoPlayThisRun = it; strictThisRun = strictModeEnabled }, onMode = { mode = it }, onToken = { playToken++ },
                        )
                    }
                }.onFailure { failure ->
                    android.util.Log.e("PracticePlayer", "Settings save/start failed", failure)
                    showSettings = false
                    loadError = "无法保存设置或启动谱面（${failure.javaClass.simpleName}" +
                        (failure.message?.let { ": $it" } ?: "") + "），请重试。"
                    mode = PracticeMode.ERROR
                }
                }
            },
            onDismiss = {
                showSettings = false
                if (settingsAreFirstRun) {
                    settingsAreFirstRun = false
                    loaded?.player?.pause()
                    onExit()
                }
            },
        )
    }
    if (showExitConfirm) {
        PracticeConfirmDialog(
            title = "退出练习？",
            message = "本次游玩进度不会保存。",
            confirmText = "退出",
            onDismiss = { showExitConfirm = false },
            onConfirm = { endingPlayer?.release(); loaded?.player?.pause(); onExit() },
        )
    }
}

private fun beginPractice(
    preferences: android.content.SharedPreferences,
    player: PracticeMusicPlayer?,
    outcomes: MutableMap<Int, PracticeOutcome>,
    effects: MutableList<PracticeEffect>,
    holds: MutableMap<Int, ActivePracticeHold>,
    forceAuto: Boolean? = null,
    onReset: () -> Unit = {},
    onAuto: (Boolean) -> Unit,
    onMode: (PracticeMode) -> Unit,
    onToken: () -> Unit,
) {
    if (runCatching { player?.isPlaying == true }.getOrDefault(false)) {
        runCatching { player?.pause() }
    }
    outcomes.clear()
    effects.clear()
    holds.clear()
    onReset()
    val auto = forceAuto ?: preferences.getBoolean("auto_play_enabled", false)
    onAuto(auto)
    onMode(PracticeMode.PREPARING)
    onToken()
}

@Composable
private fun PracticeSettingsDialog(
    backgroundArtwork: Bitmap?,
    initialDelayMs: Int,
    initialNoteScale: Float,
    initialAutoPlay: Boolean,
    initialRealtimeAcc: Boolean,
    initialJudgeDetails: Boolean,
    initialStrictMode: Boolean,
    initialPauseDoubleTap: Boolean,
    initialSpeedStep: Int,
    initialSeekGestureEnabled: Boolean,
    showSeekGestureOption: Boolean,
    requireChoice: Boolean,
    onCalibrate: () -> Unit,
    onSave: (Int, Float, Boolean, Int, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var delayValue by remember(initialDelayMs) { mutableIntStateOf(initialDelayMs) }
    var scaleValue by remember(initialNoteScale) { mutableFloatStateOf(initialNoteScale) }
    var autoValue by remember(initialAutoPlay) { mutableStateOf(initialAutoPlay) }
    var realtimeAccValue by remember(initialRealtimeAcc) { mutableStateOf(initialRealtimeAcc) }
    var judgeDetailsValue by remember(initialJudgeDetails) { mutableStateOf(initialJudgeDetails) }
    var strictValue by remember(initialStrictMode) { mutableStateOf(initialStrictMode) }
    var pauseDoubleTapValue by remember(initialPauseDoubleTap) { mutableStateOf(initialPauseDoubleTap) }
    var speedValue by remember(initialSpeedStep) { mutableIntStateOf(initialSpeedStep) }
    var seekGestureValue by remember(initialSeekGestureEnabled) { mutableStateOf(initialSeekGestureEnabled) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false,
    )) {
        Box(Modifier.fillMaxSize()) {
            PracticeGlassBackdrop(backgroundArtwork)
            PracticeGlassTheme {
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val compact = maxWidth < 700.dp
                    val leftSettings: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PracticeGlassGroup {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("判定延迟", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    Text("${if (delayValue > 0) "+" else ""}$delayValue ms", color = AppAccent)
                                }
                                Slider(value = delayValue.toFloat(), onValueChange = { delayValue = it.roundToInt() }, valueRange = -250f..250f)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    androidx.compose.material3.TextButton(onClick = { delayValue = (delayValue - 1).coerceAtLeast(-250) }) { Text("− 1 ms") }
                                    androidx.compose.material3.TextButton(onClick = { delayValue = 0 }) { Text("重置") }
                                    androidx.compose.material3.TextButton(onClick = { delayValue = (delayValue + 1).coerceAtMost(250) }) { Text("+ 1 ms") }
                                }
                            }
                            PracticeGlassGroup {
                                Row {
                                    Text("音符大小", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    Text("${(scaleValue * 100).roundToInt()}%", color = AppAccent)
                                }
                                Slider(value = scaleValue, onValueChange = { scaleValue = it }, valueRange = .6f..1.6f, steps = 19)
                            }
                            PracticeGlassGroup { PracticeSpeedControl(speedValue) { speedValue = it } }
                        }
                    }
                    val rightSettings: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PracticeGlassGroup {
                                Row(Modifier.fillMaxWidth().clickable(onClick = onCalibrate).padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, null, tint = AppAccent)
                                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                        Text("调整延迟", style = MaterialTheme.typography.titleSmall)
                                        Text("边播放边校准", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = .78f))
                                }
                            }
                            PracticeGlassGroup {
                                PracticeGlassToggle("严判模式", strictValue) { strictValue = it }
                                PracticeGlassToggle("AUTOPLAY", autoValue) { autoValue = it }
                                PracticeGlassToggle("实时ACC显示", realtimeAccValue) { realtimeAccValue = it }
                                PracticeGlassToggle("判定详情显示", judgeDetailsValue) { judgeDetailsValue = it }
                                PracticeGlassToggle("双击暂停（防误触）", pauseDoubleTapValue) { pauseDoubleTapValue = it }
                                if (showSeekGestureOption) PracticeGlassToggle("三连击快进快退", seekGestureValue) { seekGestureValue = it }
                            }
                        }
                    }
                    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 20.dp else 36.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("谱面播放设置", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭设置", tint = Color.White) }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (compact) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            leftSettings(); rightSettings()
                        } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { leftSettings() }
                            Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { rightSettings() }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(if (requireChoice) "退出练习" else "取消") }
                            Button(onClick = { onSave(delayValue, scaleValue, autoValue, speedValue, seekGestureValue, realtimeAccValue, judgeDetailsValue, strictValue, pauseDoubleTapValue) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = .18f), contentColor = Color.White)) {
                                Text(if (requireChoice) "保存并开始" else "保存")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeGlassGroup(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(Color.White.copy(alpha = .10f))
        .border(1.dp, Color.White.copy(alpha = .16f), shape)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun PracticeGlassToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PracticeCenterPanel(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .68f)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.CircularProgressIndicator(color = AppAccent)
        Spacer(Modifier.height(20.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PracticeMessagePanel(title: String, message: String, primary: String, onPrimary: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .78f)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onPrimary) { Text(primary) }
    }
}

@Composable
private fun PracticeGlassTheme(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = colors.copy(
        primary = Color(0xFFB6E3FB), onPrimary = Color.White,
        surface = Color(0xFF1A2534), onSurface = Color.White,
        onSurfaceVariant = Color(0xFFD2DFE9), outline = Color(0xFF9EAAB6),
    ), typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

@Composable
private fun PracticeGlassBackdrop(artwork: Bitmap?) {
    Box(Modifier.fillMaxSize().background(Color(0xFF101824))) {
        if (artwork != null) Image(remember(artwork) { artwork.asImageBitmap() }, null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(Color(0xBB0C1521), Color(0x700B1621), Color(0xA70B111B)))))
    }
}

@Composable
private fun PracticePausePanel(chartSource: PracticeChartSource, backgroundArtwork: Bitmap?,
    segmentMode: Boolean = false, onResume: () -> Unit, onRestart: () -> Unit,
    onSettings: () -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        PracticeGlassBackdrop(backgroundArtwork)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            AppAccent.copy(alpha = .24f), AppAccent.copy(alpha = .08f), AppAccent.copy(alpha = .18f)))))
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compact = maxWidth < 640.dp
            val content = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)
            if (compact) Column(content.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center) {
                PracticePauseHeading(chartSource)
                Spacer(Modifier.height(24.dp))
                PracticePauseActions(segmentMode, onResume, onRestart, onSettings, onExit)
            } else Row(content, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Box(Modifier.weight(1f)) { PracticePauseHeading(chartSource) }
                Box(Modifier.widthIn(min = 260.dp, max = 320.dp)) {
                    PracticePauseActions(segmentMode, onResume, onRestart, onSettings, onExit)
                }
            }
        }
    }
}

@Composable
private fun PracticePauseHeading(chartSource: PracticeChartSource) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp, 3.dp).background(AppAccent))
        Text("已暂停", color = Color.White, style = MaterialTheme.typography.displayMedium)
        Text("${chartSource.title} · ${chartSource.difficulty}",
            color = Color.White.copy(alpha = .76f), style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PracticePauseActions(segmentMode: Boolean, onResume: () -> Unit,
    onRestart: () -> Unit, onSettings: () -> Unit, onExit: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PracticeGlassAction("继续", Icons.Default.PlayArrow, true, onResume)
        PracticeGlassAction("重来", Icons.Default.Refresh, false, onRestart)
        PracticeGlassAction("设置", Icons.Default.Settings, false, onSettings)
        PracticeGlassAction(if (segmentMode) "返回选段" else "退出", Icons.Default.Close, false, onExit)
    }
}

@Composable
private fun PracticeGlassAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().clip(shape)
        .background(if (primary) AppAccent.copy(alpha = .26f) else Color.White.copy(alpha = .10f))
        .border(1.dp, if (primary) AppAccent.copy(alpha = .54f) else Color.White.copy(alpha = .17f), shape)
        .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = Color.White)
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PracticeConfirmDialog(title: String, message: String, confirmText: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(message, color = AppTextMuted, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) { Text("继续练习") }
                    Button(onClick = onConfirm) { Text(confirmText) }
                }
            }
        }
    }
}

private suspend fun loadPractice(context: Context, pezFile: File): PracticeLoaded {
    val chart = PracticeChartParser.load(context, pezFile)
    val assetsRoot = "practice/"
    fun readBitmap(name: String): Bitmap = context.assets.open(assetsRoot + name).use {
        checkNotNull(BitmapFactory.decodeStream(it)) { "无法读取练习资源 $name" }
    }
    val noteBitmaps = mapOf(
        PracticeNoteType.TAP to readBitmap("click.png"),
        PracticeNoteType.HOLD to readBitmap("hold.png"),
        PracticeNoteType.FLICK to readBitmap("flick.png"),
        PracticeNoteType.DRAG to readBitmap("drag.png"),
    )
    val multiBitmaps = mapOf(
        PracticeNoteType.TAP to readBitmap("click_mh.png"),
        PracticeNoteType.HOLD to readBitmap("hold_mh.png"),
        PracticeNoteType.FLICK to readBitmap("flick_mh.png"),
        PracticeNoteType.DRAG to readBitmap("drag_mh.png"),
    )
    val hitFx = readBitmap("hit_fx.png")
    PracticeHitMotion.prepare()
    // Request sprite upload during loading instead of on the first judged note.
    (noteBitmaps.values + multiBitmaps.values + hitFx).forEach { bitmap ->
        runCatching { bitmap.prepareToDraw() }
    }
    val illustration = BitmapFactory.decodeFile(chart.illustrationFile.absolutePath)
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).also { it.eraseColor(NativeColor.BLACK) }
    val soundDirectory = File(context.cacheDir, "practice-audio").apply { mkdirs() }
    fun copyAsset(name: String): File {
        val file = File(soundDirectory, name)
        if (!file.isFile || file.length() == 0L) {
            context.assets.open(assetsRoot + name).use { input -> file.outputStream().use(input::copyTo) }
        }
        return file
    }
    val click = copyAsset("click.ogg")
    val drag = copyAsset("drag.ogg")
    val flick = copyAsset("flick.ogg")
    val ending = copyAsset("ending.mp3")
    val hitAudio = PracticeHitAudio.load(click, drag, flick)
    var ownedPlayer: PracticeMusicPlayer? = null
    try {
    val player = createPracticeMediaPlayer(chart.musicFile).also {
        ownedPlayer = it
        it.onPlaybackState = hitAudio::setEnabled
    }
    val info = context.assets.open("practice/info.yml").bufferedReader().use { it.readText() }
    fun pair(key: String, fallback: Pair<Int, Int>): Pair<Int, Int> {
        val match = Regex("(?m)^" + key + ":\\s*\\[([0-9]+),\\s*([0-9]+)\\]").find(info) ?: return fallback
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }
    fun number(key: String, fallback: Double): Double = Regex("(?m)^"+key+":\\s*([0-9.]+)")
        .find(info)?.groupValues?.get(1)?.toDoubleOrNull() ?: fallback
    val fx = pair("hitFx", 6 to 5)
    val centerBlurRadius = (minOf(illustration.width, illustration.height) / 12).coerceIn(2, 128)
    val sideBlurRadius = (minOf(illustration.width, illustration.height) / 8).coerceIn(2, 192)
    return PracticeLoaded(chart, PracticeAssets(noteBitmaps, multiBitmaps, hitFx, illustration,
        xyz.plcliangpicup.phigrosscore.data.smoothArtworkBlur(illustration, radiusOverride = centerBlurRadius),
        xyz.plcliangpicup.phigrosscore.data.smoothArtworkBlur(illustration, radiusOverride = sideBlurRadius),
        hitAudio, ending,
        holdAtlas = pair("holdAtlas", 50 to 50), holdAtlasMulti = pair("holdAtlasMH", 50 to 93),
        fxColumns = fx.first.coerceAtLeast(1), fxRows = fx.second.coerceAtLeast(1),
        fxDuration = number("hitFxDuration", .5).coerceAtLeast(.01), fxScale = number("hitFxScale", .95).toFloat()), player,
        if (chart.blockAreas.isNotEmpty()) PracticeNoiseRenderer(context,
            compatibilityMode = PracticeNoiseSettings(context).compatibilityMode) else null)
    } catch (failure: Throwable) {
        withContext(NonCancellable + Dispatchers.Main.immediate) { ownedPlayer?.release() }
        hitAudio.release()
        throw failure
    }
}

private suspend fun createPracticeMediaPlayer(musicFile: File): PracticeMusicPlayer =
    PracticeMusicPlayer.load(musicFile)

private fun addPracticeOutcome(
    note: PracticeNote,
    grade: PracticeGrade,
    deltaSeconds: Double,
    time: Double,
    chart: PracticeChart,
    assets: PracticeAssets,
    viewport: PracticeViewport,
    outcomes: MutableMap<Int, PracticeOutcome>,
    effects: MutableList<PracticeEffect>,
    onCombo: (Boolean) -> Unit,
) {
    if (note.fake || note.id in outcomes) return
    outcomes[note.id] = PracticeOutcome(grade, deltaSeconds, time)
    if (grade == PracticeGrade.PERFECT || grade == PracticeGrade.GOOD) {
        if (note.type != PracticeNoteType.HOLD) emitPracticeHit(note, grade, time, chart, assets, viewport, effects, true)
    }
    onCombo(grade == PracticeGrade.PERFECT || grade == PracticeGrade.GOOD)
}

private fun emitPracticeHit(
    note: PracticeNote, grade: PracticeGrade, time: Double, chart: PracticeChart,
    assets: PracticeAssets, viewport: PracticeViewport, effects: MutableList<PracticeEffect>, sound: Boolean,
) {
    if (grade != PracticeGrade.PERFECT && grade != PracticeGrade.GOOD) return
    val point = practiceNotePoint(chart, note, time, viewport, onLine = true)
    effects += PracticeEffect(point.x, point.y, grade, time)
    if (sound) assets.hitAudio.play(note.type)
}


private fun viewportFor(size: IntSize): PracticeViewport {
    val width = size.width.coerceAtLeast(1).toFloat()
    val height = size.height.coerceAtLeast(1).toFloat()
    return PracticeViewport(width, height, PracticeField(width, height).xScale)
}

private fun linePose(line: PracticeJudgeLine, time: Double, viewport: PracticeViewport): PracticeLinePose = PracticeLinePose(
    x = viewport.centerX + evaluatePracticeLayers(line.xLayers, time, 0f) * viewport.scale,
    y = viewport.centerY - evaluatePracticeLayers(line.yLayers, time, 0f) * viewport.scaleY,
    rotation = evaluatePracticeLayers(line.rotationLayers, time, 0f),
    alpha = evaluatePracticeLayers(line.alphaLayers, time, 255f) / 255f,
)

private fun localNoteTravel(chart: PracticeChart, note: PracticeNote, time: Double, viewport: PracticeViewport, tail: Boolean = false): Float {
    val line = chart.lines[note.lineIndex]
    val chartTime = time - chart.offsetSeconds
    val distances = practiceNoteDistances(note, chartTime, line.travel.at(note.startSeconds),
        line.travel.at(note.endSeconds), line.travel.at(chartTime), viewport.height)
    return if (tail) distances.tail else distances.head
}

private fun practiceNotePoint(chart: PracticeChart, note: PracticeNote, time: Double, viewport: PracticeViewport, onLine: Boolean = false): Offset {
    val pose = linePose(chart.lines[note.lineIndex], time - chart.offsetSeconds, viewport)
    val localX = note.positionX * viewport.scale
    val localY = if (onLine) 0f else (if (note.above) -1f else 1f) * localNoteTravel(chart, note, time, viewport)
    val angle = Math.toRadians(pose.rotation.toDouble())
    return Offset(pose.x + localX * cos(angle).toFloat() - localY * sin(angle).toFloat(),
        pose.y + localX * sin(angle).toFloat() + localY * cos(angle).toFloat())
}

/** Only tangent distance matters; the entire perpendicular lane is touchable. */
private fun practiceJudgeDistance(chart: PracticeChart, note: PracticeNote, time: Double, viewport: PracticeViewport, touch: Offset, geometry: PracticeRenderGeometry): Float {
    val pose = geometry.judgePose(chart, note.lineIndex, time - chart.offsetSeconds, viewport)
    return practiceTangentDistance(touch.x, touch.y, pose.x, pose.y, pose.rotation, note.positionX * viewport.scale)
}

private fun pointSegmentDistance(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.001f) return (point - start).getDistance()
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return (point - Offset(start.x + t * dx, start.y + t * dy)).getDistance()
}

private fun drawPracticeFrame(
    canvas: NativeCanvas, viewport: PracticeViewport, chart: PracticeChart,
    assets: PracticeAssets, judgeLineColor: Int, time: Double, noteScale: Float,
    outcomes: Map<Int, PracticeOutcome>, effects: List<PracticeEffect>, paint: Paint, geometry: PracticeRenderGeometry,
) {
    val field = viewport.field
    val chartTime = time - chart.offsetSeconds
    val poses = geometry.poses
    val lineCos = geometry.cosines
    val lineSin = geometry.sines
    chart.lines.forEachIndexed { index, line ->
        val pose = poses[index]
        pose.x = viewport.centerX + evaluatePracticeLayers(line.xLayers, chartTime, 0f) * viewport.scale
        pose.y = viewport.centerY - evaluatePracticeLayers(line.yLayers, chartTime, 0f) * viewport.scaleY
        pose.rotation = evaluatePracticeLayers(line.rotationLayers, chartTime, 0f)
        pose.alpha = evaluatePracticeLayers(line.alphaLayers, chartTime, 255f) / 255f
        geometry.current[index] = line.travel.at(chartTime)
        val angle = Math.toRadians(pose.rotation.toDouble())
        lineCos[index] = cos(angle).toFloat(); lineSin[index] = sin(angle).toFloat()
    }
    paint.colorFilter = null
    val saved = canvas.save()
    canvas.clipRect(field.left, 0f, field.left + field.width, field.height)
    // Lines have their own opacity; an invisible line can still carry visible notes.
    poses.forEach { pose ->
        if (pose.alpha <= 0f) return@forEach
        val state = canvas.save()
        canvas.translate(pose.x, pose.y)
        canvas.rotate(pose.rotation)
        paint.color = judgeLineColor
        paint.alpha = (pose.alpha.coerceIn(0f, 1f) * 255).roundToInt()
        paint.strokeWidth = field.width * .004f
        val reach = kotlin.math.hypot(field.width, field.height) * 2
        canvas.drawLine(-reach, 0f, reach, 0f, paint)
        canvas.restoreToCount(state)
    }
    geometry.drawOrder.forEach { note ->
        val outcome = outcomes[note.id]
        val hold = note.type == PracticeNoteType.HOLD
        val target = note.startSeconds + chart.offsetSeconds
        val end = note.endSeconds + chart.offsetSeconds
        if (time < target - note.visibleTime) return@forEach
        if (hold && time >= end) return@forEach
        if (!hold && (outcome?.grade == PracticeGrade.PERFECT || outcome?.grade == PracticeGrade.GOOD)) return@forEach
        if (!hold && time > (outcome?.judgedAt ?: target) + .16 && time > target) return@forEach
        val line = chart.lines[note.lineIndex]
        val pose = poses[note.lineIndex]
        if (pose.alpha < 0) return@forEach
        val distances = practiceNoteDistances(note, chartTime, geometry.heads[note.id],
            geometry.tails[note.id], geometry.current[note.lineIndex], viewport.height)
        val headDistance = distances.head
        val tailDistance = distances.tail
        if (line.cover && time < target && (if (hold) tailDistance else headDistance) < -1f) return@forEach
        val bitmap = (if (note.isMulti) assets.notesMulti else assets.notes).getValue(note.type)
        val noteWidth = field.noteWidth * note.size * noteScale * bitmap.width / assets.notes.getValue(PracticeNoteType.TAP).width
        val x = note.positionX * viewport.scale
        val c = lineCos[note.lineIndex]; val sn = lineSin[note.lineIndex]
        val side = if (note.above) -1f else 1f
        val headX = pose.x+x*c-headDistance*side*sn
        val headY = pose.y+x*sn+headDistance*side*c
        val tailX = pose.x+x*c-tailDistance*side*sn
        val tailY = pose.y+x*sn+tailDistance*side*c
        // Conservative bounds include the entire Hold segment and its caps.
        // Do not use time-only culling: reverse-speed notes can re-enter view.
        val margin = noteWidth
        if (max(headX,tailX)+margin < field.left || min(headX,tailX)-margin > field.left+field.width ||
            max(headY,tailY)+margin < 0 || min(headY,tailY)-margin > field.height) return@forEach
        val state = canvas.save()
        canvas.translate(pose.x, pose.y)
        canvas.rotate(pose.rotation)
        canvas.scale(1f, if (note.above) 1f else -1f)
        if (line.cover && hold) canvas.clipRect(-100000f, -100000f, 100000f, 1f)
        var opacity = note.alpha / 255f
        if (outcome?.grade == PracticeGrade.MISS) opacity *= if (hold) .45f else (1 - ((time-outcome.judgedAt)/.16).toFloat()).coerceIn(0f,1f)
        if (outcome?.grade == PracticeGrade.BAD) {
            opacity *= (1 - ((time-outcome.judgedAt)/.16).toFloat()).coerceIn(0f,1f)
            paint.colorFilter = geometry.badTint
        } else paint.colorFilter = null
        paint.alpha = (opacity * 255).roundToInt().coerceIn(0,255)
        if (hold) {
            val atlas = if (note.isMulti) assets.holdAtlasMulti else assets.holdAtlas
            val tailPixels = atlas.first.coerceIn(1, bitmap.height-2)
            val headPixels = atlas.second.coerceIn(1, bitmap.height-tailPixels-1)
            val headY = -headDistance
            val tailY = -tailDistance
            val headHeight = noteWidth * headPixels / bitmap.width
            val tailHeight = noteWidth * tailPixels / bitmap.width
            if (tailY != headY) {
                // Signed travel also supports reversed speed without dropping the body.
                val bodyState = canvas.save()
                canvas.translate(0f, headY)
                canvas.scale(1f, if (tailY < headY) 1f else -1f)
                val length = abs(tailY - headY)
                canvas.drawBitmap(bitmap, geometry.sourceRect(0,tailPixels,bitmap.width,bitmap.height-headPixels),
                    geometry.targetRect(x-noteWidth/2,-length,x+noteWidth/2,0f),paint)
                canvas.drawBitmap(bitmap, geometry.sourceRect(0,0,bitmap.width,tailPixels),
                    geometry.targetRect(x-noteWidth/2,-length-tailHeight,x+noteWidth/2,-length),paint)
                canvas.restoreToCount(bodyState)
            }
            if (time < target) canvas.drawBitmap(bitmap, geometry.sourceRect(0,bitmap.height-headPixels,bitmap.width,bitmap.height),
                geometry.targetRect(x-noteWidth/2,headY,x+noteWidth/2,headY+headHeight),paint)
        } else {
            val h = noteWidth * bitmap.height / bitmap.width
            canvas.drawBitmap(bitmap,null,geometry.targetRect(x-noteWidth/2,-headDistance-h/2,x+noteWidth/2,-headDistance+h/2),paint)
        }
        paint.colorFilter=null
        canvas.restoreToCount(state)
    }
    effects.forEach { effect ->
        val elapsed = time-effect.time
        if (elapsed < 0 || elapsed >= maxOf(assets.fxDuration, PRACTICE_HIT_LIFETIME_SECONDS)) return@forEach
        val size = field.noteWidth * noteScale * assets.fxScale * 1.6f
        val tint = if (effect.grade==PracticeGrade.PERFECT) PRACTICE_HIT_PERFECT_TINT else PRACTICE_HIT_GOOD_TINT
        effect.visual.draw(canvas, effect.x, effect.y, size, elapsed.toFloat(), assets.fxDuration.toFloat(),
            assets.hitFx, assets.fxColumns, assets.fxRows, tint,
            if (effect.grade == PracticeGrade.PERFECT) geometry.perfectTint else geometry.goodTint,
            paint, geometry.sourceRect(0, 0, 0, 0), geometry.targetRect(0f, 0f, 0f, 0f))
    }
    canvas.restoreToCount(saved)
}
