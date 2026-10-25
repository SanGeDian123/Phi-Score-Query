package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun bRankingLabel(timeRewindMode: Boolean): String = if (timeRewindMode) "B19" else "B30"

@Composable
internal fun RankingImageTabs(pagerState: PagerState, allowBestN: Boolean = true, timeRewindMode: Boolean = false, onSelect: (Int) -> Unit) {
    val labels = when {
        timeRewindMode -> listOf("B19")
        allowBestN -> listOf("B30", "P30", "Best N")
        else -> listOf("B30", "P30")
    }
    val shape = RoundedCornerShape(8.dp)
    val gap = 8.dp
    // The pager supplies both tap animation and finger-driven progress.
    val progress = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (labels.size - 1).toFloat())
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).height(48.dp)) {
        val tabWidth = (maxWidth - gap * (labels.size - 1)) / labels.size
        val stepPx = with(LocalDensity.current) { (tabWidth + gap).toPx() }
        Box(
            Modifier.align(Alignment.CenterStart)
                .offset { IntOffset((progress * stepPx).roundToInt(), 0) }
                .width(tabWidth).height(32.dp).clip(shape)
                .background(AppAccent.copy(alpha = .14f))
                .testTag("image-tab-indicator"),
        )
        Row(Modifier.fillMaxSize().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            labels.forEachIndexed { index, label ->
                val emphasis = (1f - abs(progress - index)).coerceIn(0f, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(selected = pagerState.currentPage == index, role = Role.Tab, onClick = { onSelect(index) })
                        .testTag("image-tab-$index"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(32.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 1f - emphasis), shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = lerp(MaterialTheme.colorScheme.onSurface, AppAccent, emphasis),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.graphicsLayer {
                                scaleX = 1f + .025f * emphasis
                                scaleY = scaleX
                            },
                        )
                    }
                }
            }
        }
    }
}
