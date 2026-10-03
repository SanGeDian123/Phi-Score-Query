package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import xyz.plcliangpicup.phigrosscore.R

@Composable
internal fun PracticeResultPanel(
    artwork: Bitmap?, blurredArtwork: Bitmap?, songName: String, difficulty: String,
    score: Int, accuracy: Double, rks: Double,
    perfect: Int, goodEarly: Int, goodLate: Int, bad: Int, miss: Int,
    maxCombo: Int, auto: Boolean, strict: Boolean, speed: Float,
    onRetry: () -> Unit, onExit: () -> Unit,
) {
    val good = goodEarly + goodLate
    val rankIcon = when {
        score >= 1_000_000 && good + bad + miss == 0 -> R.drawable.grade_ap
        bad + miss == 0 -> R.drawable.grade_fc
        score >= 960_000 -> R.drawable.grade_v
        score >= 920_000 -> R.drawable.grade_s
        score >= 880_000 -> R.drawable.grade_a
        score >= 820_000 -> R.drawable.grade_b
        score >= 700_000 -> R.drawable.grade_c
        else -> R.drawable.grade_f
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val ink = if (dark) Color(0xFFF1F5FF) else Color(0xFF17253A)
    val muted = ink.copy(alpha = .62f)
    val accent = if (dark) Color(0xFFB5D5FF) else Color(0xFF255BC6)
    val perfectColor = if (dark) Color(0xFFF3D390) else Color(0xFF8A631D)
    val goodColor = if (dark) Color(0xFFB5DFFF) else Color(0xFF276A9E)
    val badColor = if (dark) Color(0xFFF1B291) else Color(0xFFA65030)
    val missColor = if (dark) Color(0xFFFFA5BA) else Color(0xFFAC3556)
    val backdrop = if (dark) Color(0xFF0C1320) else Color(0xFFDCE5F1)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val reveal by animateFloatAsState(if (entered) 1f else 0f, appTween(320), label = "result-reveal")
    val displayedScore by animateIntAsState(if (entered) score else 0, appTween(580), label = "result-score")
    CompositionLocalProvider(LocalContentColor provides ink) {
        Box(Modifier.fillMaxSize().background(backdrop)) {
            (artwork ?: blurredArtwork)?.let {
                Image(remember(it) { it.asImageBitmap() }, null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alpha = if (dark) .82f else .70f)
            }
            // Keep the illustration visible; fade only behind the score and controls.
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                backdrop.copy(alpha = if (dark) .08f else .12f),
                backdrop.copy(alpha = if (dark) .42f else .48f),
                backdrop.copy(alpha = if (dark) .78f else .86f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                backdrop.copy(alpha = .15f), Color.Transparent,
                backdrop.copy(alpha = if (dark) .86f else .90f)))))
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val tight = maxHeight < 430.dp
                val stacked = maxWidth < 620.dp
                val gap = if (tight) 12.dp else 22.dp
                val song: @Composable () -> Unit = {
                    Column {
                        Box(Modifier.width(38.dp).height(3.dp).background(accent))
                        Spacer(Modifier.height(if (tight) 12.dp else 20.dp))
                        Text(songName, color = ink, fontFamily = AppNumericFont,
                            fontSize = if (tight) 26.sp else 34.sp,
                            lineHeight = if (tight) 32.sp else 42.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(if (tight) 10.dp else 16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ResultBadge(difficulty, accent, dark)
                            ResultBadge(String.format(Locale.ROOT, "%.2f×", speed), ink, dark)
                            if (strict) ResultBadge("严判模式", perfectColor, dark)
                            if (auto) ResultBadge("AUTOPLAY", ink, dark)
                        }
                    }
                }
                val performance: @Composable () -> Unit = {
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("SCORE", color = muted, letterSpacing = 3.sp, style = MaterialTheme.typography.labelSmall)
                                Text(displayedScore.toString().padStart(7, '0'), color = ink,
                                    fontFamily = AppNumericFont, fontWeight = FontWeight.SemiBold,
                                    fontSize = if (tight) 48.sp else 68.sp,
                                    lineHeight = if (tight) 56.sp else 80.sp, letterSpacing = (-2).sp)
                            }
                            Image(painterResource(rankIcon), "成绩等级", Modifier.size(if (tight) 64.dp else 98.dp),
                                colorFilter = if (dark) null else ColorFilter.tint(ink))
                        }
                        Spacer(Modifier.height(if (tight) 8.dp else 20.dp))
                        HorizontalDivider(color = ink.copy(alpha = .18f))
                        Spacer(Modifier.height(if (tight) 10.dp else 18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ResultMetric("ACC", String.format(Locale.ROOT, "%.4f%%", accuracy), muted, tight, Modifier.weight(1.25f))
                            ResultMetric("单曲 RKS", String.format(Locale.ROOT, "%.4f", rks), muted, tight, Modifier.weight(1f))
                            ResultMetric("最大连击", maxCombo.toString(), muted, tight, Modifier.weight(1f))
                        }
                    }
                }
                Column(Modifier.align(Alignment.Center).fillMaxSize().widthIn(max = 1120.dp)
                    .padding(horizontal = if (tight) 24.dp else 36.dp, vertical = if (tight) 14.dp else 24.dp)
                    .graphicsLayer { alpha = reveal; translationY = (1 - reveal) * 12.dp.toPx() }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("练习结算", color = ink, style = MaterialTheme.typography.titleMedium)
                            Box(Modifier.width(1.dp).height(12.dp).background(ink.copy(alpha = .28f)))
                            Text("RESULT", color = muted, letterSpacing = 3.sp, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = onExit, modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(ink.copy(alpha = .07f))) { Icon(Icons.Default.Close, "退出结算", tint = ink, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(gap))
                    if (stacked) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp)) { song(); performance() }
                    else Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (tight) 30.dp else 48.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(.45f).padding(bottom = if (tight) 0.dp else 10.dp), contentAlignment = Alignment.BottomStart) { song() }
                        Box(Modifier.weight(.55f)) { performance() }
                    }
                    Spacer(Modifier.height(gap))
                    val shape = RoundedCornerShape(if (tight) 18.dp else 22.dp)
                    Column(Modifier.fillMaxWidth().clip(shape)
                        .background(if (dark) Color(0xFF101F32).copy(alpha = .46f) else Color.White.copy(alpha = .42f))
                        .border(1.dp, if (dark) Color.White.copy(alpha = .15f) else Color.White.copy(alpha = .58f), shape)
                        .padding(horizontal = if (tight) 16.dp else 22.dp, vertical = if (tight) 10.dp else 14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ResultJudgement("Perfect", perfect, perfectColor, muted, tight, Modifier.weight(1f))
                            ResultJudgement("Good", good, goodColor, muted, tight, Modifier.weight(1f),
                                detail = "Early $goodEarly\nLate $goodLate")
                            ResultJudgement("Bad", bad, badColor, muted, tight, Modifier.weight(1f))
                            ResultJudgement("Miss", miss, missColor, muted, tight, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(if (tight) 4.dp else 8.dp))
                        Text("${perfect + good + bad + miss} NOTES", color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(if (tight) 10.dp else 16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onExit, colors = ButtonDefaults.textButtonColors(contentColor = ink)) { Text("返回曲目") }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = onRetry, shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (dark) Color(0xFFC2DAFF) else Color(0xFF285BBE),
                                contentColor = if (dark) Color(0xFF14253F) else Color.White),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("再来一局")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultBadge(label: String, color: Color, dark: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    Text(label, Modifier.clip(shape).background(if (dark) color.copy(alpha = .13f) else Color.White.copy(alpha = .40f))
        .border(1.dp, color.copy(alpha = .15f), shape).padding(horizontal = 9.dp, vertical = 4.dp),
        color = color, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun ResultMetric(label: String, value: String, muted: Color, tight: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = muted, style = MaterialTheme.typography.labelSmall)
        Text(value, fontFamily = AppNumericFont, fontWeight = FontWeight.Medium,
            fontSize = if (tight) 16.sp else 20.sp, lineHeight = if (tight) 20.sp else 26.sp, maxLines = 1)
    }
}

@Composable
private fun ResultJudgement(label: String, value: Int, color: Color, muted: Color, tight: Boolean,
    modifier: Modifier, detail: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = muted, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(value.toString(), color = color, fontFamily = AppNumericFont, fontWeight = FontWeight.Medium,
                fontSize = if (tight) 25.sp else 30.sp, lineHeight = if (tight) 30.sp else 38.sp)
            if (detail != null) Text(detail, color = muted, style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}
