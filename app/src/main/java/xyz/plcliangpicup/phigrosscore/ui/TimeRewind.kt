package xyz.plcliangpicup.phigrosscore.ui

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import xyz.plcliangpicup.phigrosscore.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal val timeRewindPages = setOf(AppPage.HOME, AppPage.B30, AppPage.IMAGE, AppPage.CONSTANT_TABLE, AppPage.SETTINGS)
internal const val TIME_REWIND_DURATION_MS = 5_600L
internal const val TIME_REWIND_RETURN_DURATION_MS = 1_100L
private enum class RewindScene { APP, CONFIRM, TRAVEL, RETURN }
private val RewindInk = Color(0xFF050C13)
private val RewindLight = Color(0xFFC5FFF2)

internal fun rewindDateDigits(startedAt: Long): String = DateTimeFormatter.ofPattern("yyMMdd")
    .format(Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()))

@Composable
private fun RewindImmersiveWindow() {
    val context = LocalContext.current
    DisposableEffect(context) {
        var current = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        val activity = current as? Activity
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (lightStatus != null) controller?.isAppearanceLightStatusBars = lightStatus
            if (lightNavigation != null) controller?.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

@Composable
internal fun TimeRewindSceneHost(
    state: AppUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onArrive: () -> Unit,
    onReturn: () -> Unit,
    content: @Composable (onExit: () -> Unit, settled: Boolean) -> Unit,
) {
    var returningAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.timeRewindMode) { if (!state.timeRewindMode) returningAt = null }
    val scene = when {
        returningAt != null -> RewindScene.RETURN
        state.timeRewindStartedAt != null -> RewindScene.TRAVEL
        state.timeRewindConfirmation -> RewindScene.CONFIRM
        else -> RewindScene.APP
    }
    val transition = updateTransition(scene, label = "time-rewind-scene")
    val savedState = rememberSaveableStateHolder()
    if (transition.currentState != RewindScene.APP || transition.targetState != RewindScene.APP) RewindImmersiveWindow()
    val settled = transition.currentState == RewindScene.APP && transition.targetState == RewindScene.APP && !transition.isRunning
    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState == RewindScene.APP) {
                (fadeIn(appTween(280)) + scaleIn(appTween(340), initialScale = .985f)) togetherWith
                    (fadeOut(appTween(220)) + slideOutVertically(appTween(300)) { it / 24 })
            } else if (initialState == RewindScene.APP) {
                (fadeIn(appTween(260)) + slideInVertically(appTween(340)) { it / 18 }) togetherWith
                    (fadeOut(appTween(200)) + scaleOut(appTween(320), targetScale = .975f))
            } else {
                fadeIn(appTween(240)) togetherWith fadeOut(appTween(200))
            }
        },
        contentKey = { it },
    ) { screen ->
        when (screen) {
            RewindScene.APP -> savedState.SaveableStateProvider("main-shell") {
                content({ if (state.timeRewindMode && returningAt == null) returningAt = System.currentTimeMillis() }, settled)
            }
            RewindScene.CONFIRM -> TimeRewindEntry(
                onConfirm = { if (scene == RewindScene.CONFIRM) onConfirm() },
                onDismiss = { if (scene == RewindScene.CONFIRM) onDismiss() },
            )
            RewindScene.TRAVEL -> {
                val startedAt = remember { state.timeRewindStartedAt ?: System.currentTimeMillis() }
                TimeRewindTransition(startedAt, onArrive)
            }
            RewindScene.RETURN -> {
                val startedAt = remember { returningAt ?: System.currentTimeMillis() }
                TimeRewindReturnTransition(startedAt, onReturn)
            }
        }
    }
}

@Composable
internal fun TimeRewindEntry(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val now = remember { System.currentTimeMillis() }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, appTween(300)) }
    val ambient by rememberInfiniteTransition(label = "rewind-field").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(androidx.compose.animation.core.tween(18_000, easing = LinearEasing)),
        label = "rewind-field-drift",
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(RewindInk).testTag("time-rewind-confirmation")) {
        RewindField(now, (ambient * 18_000L).toLong(), .12f)
        val wide = maxWidth >= 560.dp && maxWidth > maxHeight
        val yearSize = if (wide || maxHeight < 620.dp) 68f else 88f
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = if (wide) 32.dp else 24.dp)) {
            Row(Modifier.widthIn(max = 960.dp).fillMaxWidth().align(Alignment.CenterHorizontally).height(64.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = RewindLight.copy(alpha = .8f))
                }
                Text("PSQ", fontFamily = AppNumericFont, fontSize = 12.sp, letterSpacing = 4.sp,
                    color = RewindLight.copy(alpha = .5f), modifier = Modifier.padding(end = 12.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
                if (wide) {
                    Row(Modifier.widthIn(max = 960.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        RewindDestination(yearSize, reveal.value, Modifier.weight(1f))
                        RewindConfirmationActions(onConfirm, onDismiss, reveal.value, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(48.dp)) {
                        RewindDestination(yearSize, reveal.value)
                        RewindConfirmationActions(onConfirm, onDismiss, reveal.value)
                    }
                }
            }
        }
    }
}

@Composable
private fun RewindDestination(yearSize: Float, reveal: Float, modifier: Modifier = Modifier) {
    Column(modifier.graphicsLayer {
        alpha = reveal
        translationY = (1f - reveal) * 24.dp.toPx()
        scaleX = .975f + .025f * reveal; scaleY = scaleX
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("时间回溯", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp)
        Spacer(Modifier.height(28.dp))
        Text("2025", fontFamily = AppNumericFont, fontSize = yearSize.sp, lineHeight = (yearSize * 1.1f).sp,
            fontWeight = FontWeight.Light, color = RewindLight,
            style = TextStyle(shadow = Shadow(RewindLight.copy(alpha = .2f), Offset.Zero, 30f)))
        Text("02 . 14", fontFamily = AppNumericFont, fontSize = 30.sp, letterSpacing = 6.sp,
            color = RewindLight.copy(alpha = .8f))
        Spacer(Modifier.height(28.dp))
        Canvas(Modifier.width(168.dp).height(9.dp)) {
            drawLine(RewindLight.copy(alpha = .12f), Offset(0f, center.y), Offset(size.width, center.y))
            drawCircle(RewindLight.copy(alpha = .85f), 2.dp.toPx(), center)
        }
    }
}

@Composable
private fun RewindConfirmationActions(onConfirm: () -> Unit, onDismiss: () -> Unit, reveal: Float, modifier: Modifier = Modifier) {
    val progress = ((reveal - .18f) / .82f).coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth().graphicsLayer {
        alpha = progress; translationY = (1f - progress) * 16.dp.toPx()
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text("你将携带当前存档回到2025年2月14日，\n是否确定？", color = Color(0xFFB6C8C7),
            fontSize = 14.sp, lineHeight = 25.sp, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RewindLight.copy(alpha = .22f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RewindLight.copy(alpha = .8f))) { Text("否") }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RewindLight, contentColor = RewindInk)) { Text("是") }
        }
    }
}

@Composable
internal fun TimeRewindTransition(startedAt: Long, onComplete: () -> Unit) {
    TimeRewindTravel(startedAt, TIME_REWIND_DURATION_MS, false, onComplete)
}

@Composable
internal fun TimeRewindReturnTransition(startedAt: Long, onComplete: () -> Unit) {
    TimeRewindTravel(startedAt, TIME_REWIND_RETURN_DURATION_MS, true, onComplete)
}

@Composable
private fun TimeRewindTravel(startedAt: Long, durationMs: Long, returning: Boolean, onComplete: () -> Unit) {
    val complete by rememberUpdatedState(onComplete)
    var elapsed by remember(startedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        val firstFrame = withFrameMillis { it }
        while (elapsed < durationMs) elapsed = withFrameMillis { it - firstFrame }
        complete()
    }
    BackHandler { }
    Box(Modifier.fillMaxSize().testTag(if (returning) "time-rewind-return" else "time-rewind-travel")) {
        TimeRewindFrame(startedAt, (elapsed * TIME_REWIND_DURATION_MS / durationMs), returning)
    }
}

/** Seekable production frame: the six reels float inside a receding time field. */
@Composable
internal fun TimeRewindFrame(startedAt: Long, elapsedMs: Long, returning: Boolean = false) {
    val present = remember(startedAt) { rewindDateDigits(startedAt) }
    val source = if (returning) "250214" else present
    val target = if (returning) present else "250214"
    val dateMaxWidth = (LocalConfiguration.current.screenHeightDp * .8f).dp.coerceIn(300.dp, 740.dp)
    val p = ((elapsedMs - 600L) / 4_200f).coerceIn(0f, 1f)
    val travel = if (p < .67f) (p / .67f).pow(2) * .88f
        else .88f + (1f - (1f - (p - .67f) / .33f).pow(3)) * .12f
    val intensity = (sin(p * PI).toFloat().coerceAtLeast(0f) * p * 2f).coerceIn(0f, 1f)
    val glitch = p in .25f.. .9f && elapsedMs % 181L < 36L
    val shift = if (glitch) sin(elapsedMs * .19).toFloat() * 7f * intensity else 0f
    Box(Modifier.fillMaxSize().background(RewindInk).testTag("time-rewind-animation")) {
        RewindField(startedAt, elapsedMs, intensity)
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(.7f))
            Text(if (returning) "返 回 现 在" else "时 间 回 溯", color = RewindLight.copy(alpha = .62f), fontSize = 13.sp, letterSpacing = 6.sp)
            Spacer(Modifier.height(30.dp))
            BoxWithConstraints(Modifier.widthIn(max = dateMaxWidth).fillMaxWidth().padding(horizontal = 24.dp)) {
                val cellWidth = (maxWidth - 20.dp) / 6
                val cellHeight = cellWidth * 1.9f
                Row(Modifier.fillMaxWidth().graphicsLayer {
                    translationX = shift; rotationZ = shift * .035f
                    compositingStrategy = CompositingStrategy.Offscreen
                }.drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White, Color.White, Color.Transparent)), blendMode = BlendMode.DstIn)
                },
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    source.forEachIndexed { i, c ->
                        val first = c.digitToInt()
                        val last = target[i].digitToInt()
                        val distance = 30 + i * 10 + (if (returning) last - first + 10 else first - last + 10) % 10
                        val position = if (returning) first + distance * travel else first - distance * travel
                        val center = floor(position).toInt()
                        val fraction = position - center
                        Box(Modifier.width(cellWidth).height(cellHeight).clipToBounds(), contentAlignment = Alignment.Center) {
                            for (row in -1..1) {
                                val digit = ((center + row) % 10 + 10) % 10
                                val alpha = (1f - abs(row - fraction) * .8f).coerceIn(0f, 1f)
                                Text(digit.toString(), fontFamily = AppNumericFont, fontSize = (cellWidth.value * 1.38f).sp,
                                    fontWeight = FontWeight.Light, color = Color(0xFFE4FFF8).copy(alpha = alpha),
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(shadow = Shadow(RewindLight.copy(alpha = .5f * alpha), Offset(shift, 0f), 20f)),
                                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                                        translationY = (row - fraction) * cellHeight.toPx()
                                    })
                            }
                        }
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(cellHeight)) {
                    if (glitch) {
                        drawRect(Color(0xFF7AFFE5).copy(alpha = .6f), Offset(-shift, size.height * .38f), Size(size.width * .6f, 2.dp.toPx()))
                        drawRect(Color(0xFFFF6882).copy(alpha = .45f), Offset(size.width * .42f + shift, size.height * .7f), Size(size.width * .5f, 1.dp.toPx()))
                    }
                }
            }
            Spacer(Modifier.height(21.dp))
            Text(if (p >= 1f) "20${target.take(2)}.${target.substring(2, 4)}.${target.takeLast(2)}" else "${source.take(2)}  /  ${source.substring(2, 4)}  /  ${source.takeLast(2)}    →    ${target.take(2)}  /  ${target.substring(2, 4)}  /  ${target.takeLast(2)}",
                color = RewindLight.copy(alpha = .6f), fontFamily = AppNumericFont, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(.8f))
            Canvas(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 35.dp)) {
                val y = size.height * .5f
                drawLine(RewindLight.copy(alpha = .22f), Offset(0f, y), Offset(size.width, y))
                for (i in 0..40) {
                    val x = i / 40f * size.width
                    val h = if (i % 10 == 0) 9.dp.toPx() else 3.dp.toPx()
                    drawLine(RewindLight.copy(alpha = .35f), Offset(x, y - h), Offset(x, y + h))
                }
                drawCircle(RewindLight, 3.dp.toPx(), Offset(size.width * (if (returning) travel else 1f - travel), y))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 35.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("2025.02.14", fontFamily = AppNumericFont, color = RewindLight.copy(alpha = .6f), fontSize = 11.sp)
                Text("20${present.take(2)}.${present.substring(2, 4)}.${present.takeLast(2)}", fontFamily = AppNumericFont,
                    color = RewindLight.copy(alpha = .4f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
        val flash = ((elapsedMs - 5_200L) / 150f).coerceIn(0f, 1f) * (1f - ((elapsedMs - 5_350L) / 250f).coerceIn(0f, 1f))
        if (flash > 0) Box(Modifier.fillMaxSize().background(RewindLight.copy(alpha = flash * .3f)))
    }
}

@Composable
private fun RewindField(startedAt: Long, elapsedMs: Long, intensity: Float) {
    val context = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.source_han_sans_saira_hybrid) }
    val dates = remember(startedAt) {
        val start = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val end = LocalDate.of(2025, 2, 14)
        val span = (start.toEpochDay() - end.toEpochDay()).coerceAtLeast(0)
        (0..8).map { start.minusDays(span * it / 8).format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width * .5f, size.height * .44f)
        val radius = hypot(size.width, size.height) * .85f
        val time = elapsedMs / 1000f
        val speed = time * .13f + (time * time * .027f).coerceAtMost(.9f)
        drawRect(Brush.radialGradient(listOf(Color(0xFF174237).copy(alpha = .72f), Color(0xFF10202B).copy(alpha = .5f), RewindInk), center, radius))
        rotate(-22f, center) {
            for (i in 0..16) {
                val phase = (i / 17f + speed) % 1f
                val r = radius * (.025f + phase.pow(2) * 1.45f)
                drawOval(RewindLight.copy(alpha = (.05f + .08f * intensity) * (1f - phase)),
                    Offset(center.x - r, center.y - r * .65f), Size(r * 2, r * 1.3f), style = Stroke(if (i % 4 == 0) 1.2f else .6f))
            }
        }
        for (i in 0 until 105) {
            val angle = i * 2.399963f
            val phase = ((i * .137f + speed * (1f + (i % 7) * .12f)) % 1f).pow(2)
            val r = (.04f + phase) * radius
            val direction = Offset(cos(angle), sin(angle) * .8f)
            val start = center + direction * r
            val tail = center + direction * (r + (9f + phase * 85f) * (.2f + intensity))
            drawLine(RewindLight.copy(alpha = (.08f + phase * .4f) * (.45f + intensity * .55f)), start, tail, if (i % 9 == 0) 1.3f else .6f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface; textSize = 11.dp.toPx(); color = android.graphics.Color.rgb(128, 193, 182)
        }
        dates.forEachIndexed { i, date ->
            val phase = (i / 9f + speed * .37f) % 1f
            val side = if (i % 2 == 0) -1f else 1f
            val x = center.x + side * size.width * (.16f + phase * .45f)
            val y = center.y + (phase - .5f) * size.height * 1.35f
            paint.alpha = (30 + phase * 60).toInt()
            drawContext.canvas.nativeCanvas.drawText(date, x, y, paint)
        }
        for (y in 0..size.height.toInt() step 4) drawLine(Color.Black.copy(alpha = .11f), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()))
        if (intensity > .25f && elapsedMs % 181L < 36L) {
            val y = size.height * (.28f + ((elapsedMs % 673) / 673f) * .42f)
            drawRect(RewindLight.copy(alpha = .045f), Offset(0f, y), Size(size.width, size.height * .035f))
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f)), center, radius * .83f))
    }
}
