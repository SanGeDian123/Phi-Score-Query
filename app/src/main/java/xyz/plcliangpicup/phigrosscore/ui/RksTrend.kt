package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.plcliangpicup.phigrosscore.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private fun rksValue(value: Double) = String.format(Locale.US, "%.4f", value)
private fun trendDate(point: RksHistoryItem, pattern: String = "yyyy.MM.dd  HH:mm:ss"): String =
    point.epochMs?.let { DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
        ?: point.createdAt

/** Highlight only the number, with a restrained breathing glow in the current theme's accent. */
@Composable
internal fun Modifier.rksTrendGlow(enabled: Boolean): Modifier {
    if (!enabled) return this
    val accent = AppAccent
    val transition = rememberInfiniteTransition(label = "rks-guide-glow")
    val strength by transition.animateFloat(.55f, 1f,
        infiniteRepeatable(animation = appTween(1_100), repeatMode = RepeatMode.Reverse), label = "rks-guide-breath")
    return drawBehind {
        val origin = Offset(-6.dp.toPx(), -2.dp.toPx())
        val frameSize = Size(size.width + 12.dp.toPx(), size.height + 4.dp.toPx())
        val corners = CornerRadius(10.dp.toPx())
        for (layer in 8 downTo 1) {
            drawRoundRect(accent.copy(alpha = .025f * strength), origin, frameSize, corners,
                style = Stroke((layer * 2).dp.toPx()))
        }
        drawRoundRect(accent.copy(alpha = .7f + .25f * strength), origin, frameSize, corners,
            style = Stroke(1.2.dp.toPx()))
    }
}

@Composable
internal fun RksTrendGuide() {
    Text("点击查看RKS趋势图", color = AppAccent, fontSize = 12.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).testTag("rks-trend-guide"))
}

@Composable
internal fun RksTrendPage(state: AppUiState, onBack: () -> Unit, onRefresh: () -> Unit, onRangeChange: (RksTrendRange) -> Unit) {
    val range = state.rksTrendRange
    val history = state.rksHistory
    val now = state.rksHistoryWindowEndEpochMs.takeIf { it > 0L } ?: remember { System.currentTimeMillis() }
    val points = remember(history, range, now) { history?.items.orEmpty().trendPoints(range, now) }
    var chosen by remember(range) { mutableStateOf<RksHistoryItem?>(null) }
    val selected = chosen?.takeIf { it in points } ?: points.lastOrNull()
    val selectedIndex = points.indexOf(selected)
    val change = if (points.size > 1) points.last().rks - points.first().rks else null
    val accent = AppAccent
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = AppReadableMaxWidth).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = AppPageHorizontalPadding, end = AppPageHorizontalPadding, top = 16.dp)
                    .testTag("rks-trend-header"), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp).testTag("rks-trend-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回概览")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("RKS趋势图", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = onRefresh, enabled = !state.isRksHistoryLoading) {
                        Icon(Icons.Default.Refresh, "刷新 RKS 历史", tint = accent)
                    }
                }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).testTag("rks-trend-page"),
                contentPadding = PaddingValues(horizontal = AppPageHorizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            if (history != null && history.items.isNotEmpty()) item {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("当前 RKS", color = AppTextMuted, fontSize = 12.sp)
                    AnimatedRksNumber(history.currentRks, animateDecrease = true, fontSize = 44.sp,
                        lineHeight = 54.sp, fontWeight = FontWeight.Medium, color = accent)
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        TrendStat("历史最高", rksValue(history.peakRks), Modifier.weight(1f))
                        TrendStat("区间变化", change?.let(::rksChangeLabel) ?: "—", Modifier.weight(1f),
                            if ((change ?: 0.0) < -.00005) AppDanger else accent)
                        TrendStat("区间记录", "${points.size}", Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppSurfaceRaised).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    RksTrendRange.entries.forEach { option ->
                        val interaction = remember { MutableInteractionSource() }
                        Surface(onClick = { onRangeChange(option) },
                            modifier = Modifier.weight(1f).appPressMotion(interaction).testTag("rks-range-${option.name}"),
                            interactionSource = interaction,
                            shape = RoundedCornerShape(11.dp), color = if (range == option) AppSurface else AppSurfaceRaised) {
                            Box(Modifier.heightIn(min = 42.dp), contentAlignment = Alignment.Center) {
                                Text(option.label, color = if (range == option) accent else AppTextMuted,
                                    fontSize = 12.sp, fontWeight = if (range == option) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            if (state.isRksHistoryLoading) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = accent, trackColor = AppSurfaceRaised)
                    Text("正在加载区间记录 · 已读取 ${state.rksHistoryLoadedCount} 条", color = AppTextMuted, fontSize = 11.sp)
                }
            }
            state.rksHistoryError?.let { error -> item {
                Surface(shape = RoundedCornerShape(14.dp), color = AppDanger.copy(alpha = .07f)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(when {
                            history == null -> "暂时无法加载趋势"
                            !history.coversTrendRange(range, now) -> "区间记录未加载完整，已保留读取的数据"
                            else -> "更新失败，保留已加载记录"
                        }, fontSize = 13.sp)
                        Text(error, color = AppTextMuted, fontSize = 12.sp)
                        TextButton(onClick = onRefresh, enabled = !state.isRksHistoryLoading) { Text("重试") }
                    }
                }
            } }
            if (points.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(22.dp), color = AppSurface) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("变化轨迹", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            }
                            RksTrendChart(points, selectedIndex, { chosen = points[it] })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
                            AnimatedContent(selected!!, transitionSpec = { fadeIn(appTween(160)) togetherWith fadeOut(appTween(120)) },
                                label = "rks-selected-record") { point ->
                                Column(Modifier.fillMaxWidth().testTag("rks-selected-record")) {
                                    Text(trendDate(point), color = AppTextMuted, fontSize = 11.sp)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(rksValue(point.rks), fontFamily = AppNumericFont, fontSize = 27.sp,
                                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text(rksChangeLabel(point.rksJump), fontFamily = AppNumericFont, fontSize = 15.sp,
                                            color = if (point.rksJump < -.00005) AppDanger else accent)
                                    }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("较上次记录", color = AppTextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { chosen = points[selectedIndex - 1] }, enabled = selectedIndex > 0, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一条 RKS 记录")
                                        }
                                        IconButton(onClick = { chosen = points[selectedIndex + 1] }, enabled = selectedIndex in 0 until points.lastIndex, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一条 RKS 记录")
                                        }
                                    }
                                }
                            }
                            if (points.size == 1) Text("已有第一条记录，下一次同步后继续绘制趋势。", color = AppTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            } else if (!state.isRksHistoryLoading && state.rksHistoryError == null) item {
                Surface(shape = RoundedCornerShape(22.dp), color = AppSurface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = accent, modifier = Modifier.size(36.dp))
                        Text(if (history?.items.isNullOrEmpty()) "成长轨迹，从这里开始" else "这个区间还没有记录", fontSize = 16.sp)
                        Text(if (history?.items.isNullOrEmpty()) "同步成绩后，RKS 记录将在这里呈现。" else "试试切换到更长的时间范围。", color = AppTextMuted, fontSize = 12.sp)
                        if (!history?.items.isNullOrEmpty()) TextButton(onClick = { onRangeChange(RksTrendRange.ALL) }) { Text("查看全部") }
                    }
                }
            }
            if (points.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("变化明细", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("${points.size} 条记录", color = AppTextMuted, fontSize = 11.sp)
                    }
                }
                itemsIndexed(points.asReversed()) { _, point ->
                    val active = point == selected
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (active) accent.copy(alpha = .07f) else AppSurface)
                        .clickable(onClickLabel = "查看 ${trendDate(point)} 的 RKS", onClick = { chosen = point })
                        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (active) accent else AppTextMuted.copy(alpha = .4f)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(rksValue(point.rks), fontFamily = AppNumericFont, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text(trendDate(point), color = AppTextMuted, fontSize = 10.sp)
                        }
                        Text(rksChangeLabel(point.rksJump), color = if (point.rksJump < -.00005) AppDanger else if (abs(point.rksJump) < .00005) AppTextMuted else accent,
                            fontFamily = AppNumericFont, fontSize = 13.sp)
                    }
                }
            }
            item { Text("时间为成绩同步时的记录时间；未同步期间的实际达成时间无法追溯。", color = AppTextMuted, fontSize = 11.sp, lineHeight = 17.sp) }
            }
        }
    }
}

@Composable
private fun TrendStat(title: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = AppTextMuted, fontSize = 11.sp)
        Text(value, color = color, fontFamily = AppNumericFont, fontSize = 17.sp, maxLines = 1)
    }
}

@Composable
private fun RksTrendChart(points: List<RksHistoryItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val accent = AppAccent
    val muted = AppTextMuted
    val surface = AppSurface
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = muted, fontSize = 9.sp)
    val low = points.minOf { it.rks }
    val high = points.maxOf { it.rks }
    val padding = ((high - low) * .22).coerceAtLeast(.002)
    val bottom = (low - padding).coerceAtLeast(0.0)
    val top = high + padding
    val firstTime = points.first().epochMs!!
    val duration = points.last().epochMs!! - firstTime
    fun fraction(index: Int): Float = when {
        points.size == 1 -> .5f
        duration == 0L -> index.toFloat() / points.lastIndex
        else -> ((points[index].epochMs!! - firstTime).toDouble() / duration).toFloat()
    }
    val selectedX by animateFloatAsState(fraction(selectedIndex.coerceAtLeast(0)), appSpring(), label = "rks-cursor-x")
    val selectedY by animateFloatAsState(((points[selectedIndex.coerceAtLeast(0)].rks - bottom) / (top - bottom)).toFloat(), appSpring(), label = "rks-cursor-y")
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) { reveal.snapTo(0f); reveal.animateTo(1f, appTween(620)) }
    val latestOnSelect by rememberUpdatedState(onSelect)
    Canvas(Modifier.fillMaxWidth().height(210.dp).testTag("rks-trend-chart")
        .semantics { contentDescription = "RKS 趋势图，${points.size} 条记录，可使用下方按钮选择记录" }
        .pointerInput(points) {
            detectTapGestures { tap ->
                val x = ((tap.x - 51.dp.toPx()) / (size.width - 63.dp.toPx())).coerceIn(0f, 1f)
                latestOnSelect(points.indices.minBy { abs(fraction(it) - x) })
            }
        }.pointerInput(points) {
            detectHorizontalDragGestures(onDragStart = { offset ->
                val x = ((offset.x - 51.dp.toPx()) / (size.width - 63.dp.toPx())).coerceIn(0f, 1f)
                latestOnSelect(points.indices.minBy { abs(fraction(it) - x) })
            }) { change, _ ->
                change.consume()
                val x = ((change.position.x - 51.dp.toPx()) / (size.width - 63.dp.toPx())).coerceIn(0f, 1f)
                latestOnSelect(points.indices.minBy { abs(fraction(it) - x) })
            }
        }) {
        val left = 51.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val upper = 12.dp.toPx()
        val lower = size.height - 26.dp.toPx()
        fun coordinate(index: Int) = Offset(left + (right - left) * fraction(index),
            lower - ((points[index].rks - bottom) / (top - bottom)).toFloat() * (lower - upper))
        repeat(4) { index ->
            val y = upper + (lower - upper) * index / 3
            drawLine(grid, Offset(left, y), Offset(right, y), .65.dp.toPx())
            drawText(textMeasurer, rksValue(top - (top - bottom) * index / 3), Offset(0f, y - 7.dp.toPx()), style = textStyle)
        }
        val line = Path().apply { points.indices.forEach { index -> val p = coordinate(index); if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val area = Path().apply { addPath(line); lineTo(coordinate(points.lastIndex).x, lower); lineTo(coordinate(0).x, lower); close() }
        clipRect(left = left - 6.dp.toPx(), right = left + (right - left + 8.dp.toPx()) * reveal.value) {
            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = .18f), accent.copy(alpha = .01f)), upper, lower))
            drawPath(line, accent, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
            if (points.size <= 24) points.indices.forEach { index -> drawCircle(accent, 2.5.dp.toPx(), coordinate(index)) }
            val cursor = Offset(left + (right - left) * selectedX, lower - (lower - upper) * selectedY)
            drawLine(accent.copy(alpha = .25f), Offset(cursor.x, upper), Offset(cursor.x, lower), 1.dp.toPx())
            drawCircle(accent.copy(alpha = .13f), 10.dp.toPx(), cursor)
            drawCircle(surface, 5.dp.toPx(), cursor)
            drawCircle(accent, 3.4.dp.toPx(), cursor)
        }
        drawText(textMeasurer, trendDate(points.first(), "MM.dd"), Offset(left, lower + 9.dp.toPx()), style = textStyle)
        if (points.size > 1) {
            val label = textMeasurer.measure(trendDate(points.last(), "MM.dd"), textStyle)
            drawText(label, topLeft = Offset(right - label.size.width, lower + 9.dp.toPx()))
        }
    }
}
