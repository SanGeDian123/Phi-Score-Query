package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.plcliangpicup.phigrosscore.data.PracticeField
import xyz.plcliangpicup.phigrosscore.data.practicePauseTarget
import kotlin.math.roundToInt
import java.util.Locale

/** HUD anchors are relative to the chart field, including on ultrawide phones.
 * Reference: a 1536 x 864 field inside the supplied 1920 x 864 screenshot.
 * Convert physical field units to dp/sp so device density cannot change the layout.
 */
@Composable
internal fun PracticeGameplayHud(
    field: PracticeField,
    score: Int,
    combo: Int,
    autoPlay: Boolean,
    pauseDoubleTap: Boolean = true,
    strictMode: Boolean = false,
    realtimeAccuracy: Double? = null,
    goodTimingLabel: String? = null,
    goodTimingAlpha: Float = 0f,
    segmentMode: Boolean = false,
    previewMode: Boolean = false,
    songName: String,
    difficulty: String,
    progress: () -> Float,
    onPause: () -> Unit,
) {
    val density = LocalDensity.current
    val scale = field.height / 864f
    val horizontalInset = field.width * (36f / 1536f)
    val left = field.left + horizontalInset
    val right = field.left + horizontalInset
    val textStyle = LocalTextStyle.current.copy(
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val scoreSize = with(density) { (42f * scale).toSp() }
    val comboSize = with(density) { (56f * scale).toSp() }
    val comboLabelSize = with(density) { (20f * scale).toSp() }
    val detailSize = with(density) { (19f * scale).toSp() }
    val titleSize = with(density) { (32f * scale).toSp() }
    val touchSize = maxOf(48.dp, with(density) { (64f * scale).toDp() })
    val touchPixels = with(density) { touchSize.toPx() }
    val pauseTarget = practicePauseTarget(field)

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val length = field.width * progress().coerceIn(0f, 1f)
            if (length > 0f) {
                drawRect(Color.White.copy(alpha = .55f), Offset(field.left, 0f), Size(length, 9f * scale))
                drawRect(Color.White, Offset(field.left + (length-2f*scale).coerceAtLeast(0f), 0f),
                    Size(minOf(length, 2f*scale), 9f*scale))
            }
        }
        // No clickable/pointerInput child here: a separate button steals a
        // finger from the chart and can cancel its entire multi-touch stream.
        // The chart owns raw touch dispatch; semantics retains accessibility.
        if (!previewMode) Box(
            modifier = Modifier.offset {
                IntOffset((pauseTarget.centerX-touchPixels/2).roundToInt(), (pauseTarget.centerY-touchPixels/2).roundToInt())
            }.size(touchSize).semantics {
                contentDescription = if (pauseDoubleTap) "双击暂停" else "暂停"
                role = Role.Button
                onClick(label = "暂停") { onPause(); true }
            }, contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(with(density) { (26f*scale).toDp() }, with(density) { (32f*scale).toDp() })) {
                val bar = size.width * .31f
                drawRect(Color.White, size = Size(bar,size.height))
                drawRect(Color.White, Offset(size.width-bar,0f), Size(bar,size.height))
            }
        }
        if (!segmentMode && !previewMode) Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(
                end = with(density) { right.toDp() }, top = with(density) { (24f*scale).toDp() }),
            horizontalAlignment = Alignment.End,
        ) {
            Text(score.toString().padStart(7,'0'),
                style = textStyle.copy(fontSize = scoreSize, lineHeight = scoreSize*1.2f), maxLines = 1)
            if (realtimeAccuracy != null) Text(
                String.format(Locale.US, "ACC %.2f%%", realtimeAccuracy),
                style = textStyle.copy(fontSize = detailSize, lineHeight = detailSize * 1.2f), maxLines = 1)
        }
        val showCombo = combo >= 3
        if (showCombo || autoPlay || goodTimingLabel != null) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = with(density) { (13f * scale).toDp() }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showCombo) Text(combo.toString(),
                    style = textStyle.copy(fontSize = comboSize, lineHeight = with(density) { (58f * scale).toSp() }), maxLines = 1)
                else Spacer(Modifier.height(with(density) { (58f * scale).toDp() }))
                if (showCombo || autoPlay) Text(if (autoPlay) "AUTOPLAY" else "COMBO",
                    style = textStyle.copy(fontSize = comboLabelSize, lineHeight = with(density) { (22f * scale).toSp() }), maxLines = 1)
                else Spacer(Modifier.height(with(density) { (22f * scale).toDp() }))
                if (goodTimingLabel != null) Text(goodTimingLabel,
                    modifier = Modifier.graphicsLayer { alpha = goodTimingAlpha },
                    style = textStyle.copy(color = Color(0xFFCAEAFF), fontSize = detailSize,
                        lineHeight = detailSize * 1.2f), maxLines = 1)
            }
        }
        if (!previewMode) Text(songName,
            modifier = Modifier.align(Alignment.BottomStart).padding(
                start = with(density) { left.toDp() },
                end = with(density) { (field.left+field.width*.30f).toDp() },
                bottom = with(density) { (24f*scale).toDp() }),
            style = textStyle.copy(fontSize = titleSize, lineHeight = titleSize*1.2f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!previewMode) Text(difficulty,
            modifier = Modifier.align(Alignment.BottomEnd).padding(
                end = with(density) { right.toDp() }, bottom = with(density) { (24f*scale).toDp() }),
            style = textStyle.copy(fontSize = titleSize, lineHeight = titleSize*1.2f), maxLines = 1)
        if (strictMode && !previewMode) Text("*严判模式",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            style = textStyle.copy(color = Color(0xFF9E9E9E), fontSize = 11.sp, lineHeight = 14.sp),
            maxLines = 1)
    }
}
