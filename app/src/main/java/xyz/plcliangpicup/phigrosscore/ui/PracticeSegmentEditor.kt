package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.plcliangpicup.phigrosscore.data.PracticeRange
import xyz.plcliangpicup.phigrosscore.data.practiceSpeed
import java.util.Locale
import kotlin.math.roundToInt

internal fun practiceTimeLabel(seconds: Double): String {
    val tenths = (seconds.coerceAtLeast(0.0) * 10).roundToInt()
    return "%02d:%02d.%d".format(Locale.ROOT, tenths / 600, tenths / 10 % 60, tenths % 10)
}

@Composable
internal fun PracticeSpeedControl(step: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("播放速度", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text("%.2fx".format(Locale.ROOT, practiceSpeed(step)), color = AppAccent, fontWeight = FontWeight.Bold)
        }
        Slider(value = step.toFloat(), onValueChange = { onChange(it.roundToInt().coerceIn(10, 40)) },
            valueRange = 10f..40f, steps = 29)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onChange((step - 1).coerceAtLeast(10)) }, enabled = step > 10) { Text("−0.05x") }
            TextButton(onClick = { onChange(20) }) { Text("1.00x") }
            TextButton(onClick = { onChange((step + 1).coerceAtMost(40)) }, enabled = step < 40) { Text("+0.05x") }
        }
    }
}

@Composable
internal fun PracticeSegmentEditor(
    title: String, duration: Double, cursor: Double, range: PracticeRange,
    playing: Boolean, seeking: Boolean, looping: Boolean, speedStep: Int,
    onScrub: (Double) -> Unit, onRange: (PracticeRange) -> Unit,
    onScrubFinished: (Double) -> Unit,
    onLoop: (Boolean) -> Unit, onSpeed: (Int) -> Unit,
    onPlay: () -> Unit, onStart: () -> Unit, onSettings: () -> Unit, onExit: () -> Unit,
    preview: @Composable () -> Unit,
) {
    var showSpeed by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableStateOf<Double?>(null) }
    var adjustingStart by remember { mutableStateOf<Boolean?>(null) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val panel = Color(0xFF171A20).copy(alpha = .62f)
    val appColors = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = appColors.copy(
        surface = Color(0xFF1D2028), onSurface = Color.White,
        onSurfaceVariant = Color(0xFFCCD1DA), outline = Color(0xFF727986),
    ), typography = MaterialTheme.typography) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            preview()
            AnimatedVisibility(entered, modifier = Modifier.align(Alignment.TopEnd), enter = fadeIn(appTween(220))) {
            Row(Modifier.safeDrawingPadding().padding(end = 8.dp, top = 4.dp)) {
                IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Settings, "练习设置", Modifier.size(18.dp), tint = Color.White.copy(alpha = .8f)) }
                IconButton(onClick = onExit, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "退出练习", Modifier.size(18.dp), tint = Color.White.copy(alpha = .8f)) }
            }
            }
            AnimatedVisibility(entered, modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(appTween(280)) + slideInVertically(initialOffsetY = { it / 2 })) {
            Column(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(panel)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                AnimatedVisibility(showSpeed, enter = expandVertically(appTween(180)) + fadeIn(), exit = shrinkVertically(appTween(140)) + fadeOut()) {
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSpeed((speedStep - 1).coerceAtLeast(10)) }, enabled = speedStep > 10) { Text("−0.05x", fontSize = 11.sp) }
                        Slider(value = speedStep.toFloat(), onValueChange = { onSpeed(it.roundToInt().coerceIn(10, 40)) },
                            valueRange = 10f..40f, steps = 29, modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = .14f)))
                        TextButton(onClick = { onSpeed((speedStep + 1).coerceAtMost(40)) }, enabled = speedStep < 40) { Text("+0.05x", fontSize = 11.sp) }
                        TextButton(onClick = { onSpeed(20) }) { Text("恢复 1x", fontSize = 11.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onPlay, enabled = !seeking, modifier = Modifier.size(36.dp)) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "暂停预览" else "播放预览", Modifier.size(22.dp), tint = Color.White)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${practiceTimeLabel(cursor)} / ${practiceTimeLabel(duration)}", fontSize = 10.sp, lineHeight = 12.sp, color = Color.White.copy(alpha = .75f))
                        Slider(value = (scrubPosition ?: cursor).toFloat().coerceIn(0f, duration.toFloat()),
                            onValueChange = { scrubPosition = it.toDouble(); onScrub(it.toDouble()) },
                            onValueChangeFinished = { scrubPosition?.let(onScrubFinished); scrubPosition = null },
                            valueRange = 0f..duration.toFloat(), modifier = Modifier.fillMaxWidth().height(28.dp),
                            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = .14f)))
                    }
                    PracticeRangeMarker("起点", range.start,
                        onMark = { onRange(PracticeRange(cursor.coerceAtMost(range.end - .1).coerceAtLeast(0.0), range.end)) },
                        onAdjust = { adjustingStart = true })
                    PracticeRangeMarker("终点", range.end,
                        onMark = { onRange(PracticeRange(range.start, cursor.coerceAtLeast(range.start + .1).coerceAtMost(duration))) },
                        onAdjust = { adjustingStart = false })
                    IconButton(onClick = { onLoop(!looping) }, modifier = Modifier.size(36.dp)) {
                        Icon(if (looping) Icons.Default.Repeat else Icons.Default.LooksOne,
                            if (looping) "循环练习，点击切换单次" else "单次练习，点击切换循环", Modifier.size(19.dp),
                            tint = if (looping) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .65f))
                    }
                    TextButton(onClick = { showSpeed = !showSpeed }, contentPadding = PaddingValues(4.dp), modifier = Modifier.width(49.dp)) {
                        Text("%.2fx".format(Locale.ROOT, practiceSpeed(speedStep)), fontSize = 11.sp)
                    }
                    Button(onClick = onStart, enabled = !seeking, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(32.dp)) {
                        Text("开始", fontSize = 12.sp)
                    }
                }
            }
            }
        }
        adjustingStart?.let { start ->
            AlertDialog(onDismissRequest = { adjustingStart = null },
                containerColor = Color(0xF01D2028), title = { Text(if (start) "微调起点" else "微调终点", fontSize = 16.sp) },
                text = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        fun nudge(delta: Double) {
                            if (start) onRange(PracticeRange((range.start + delta).coerceIn(0.0, (range.end - .1).coerceAtLeast(0.0)), range.end))
                            else onRange(PracticeRange(range.start, (range.end + delta).coerceIn(minOf(range.start + .1, duration), duration)))
                        }
                        TextButton(onClick = { nudge(-.1) }) { Text("−0.1s", fontSize = 12.sp) }
                        Text(practiceTimeLabel(if (start) range.start else range.end), fontSize = 14.sp)
                        TextButton(onClick = { nudge(.1) }) { Text("+0.1s", fontSize = 12.sp) }
                    }
                },
                confirmButton = { TextButton(onClick = { adjustingStart = null }) { Text("完成", fontSize = 12.sp) } })
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PracticeRangeMarker(label: String, time: Double, onMark: () -> Unit, onAdjust: () -> Unit) {
    Column(Modifier.width(70.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .06f))
        .combinedClickable(onClick = onMark, onLongClick = onAdjust, onLongClickLabel = "微调$label")
        .padding(horizontal = 5.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("设为$label", fontSize = 9.sp, color = Color.White.copy(alpha = .6f), lineHeight = 11.sp)
        Text(practiceTimeLabel(time), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
    }
}
