package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

internal val AppContentMaxWidth = 1_040.dp
internal val AppReadableMaxWidth = 760.dp
internal val AppPageHorizontalPadding = 20.dp
internal val AppSectionSpacing = 14.dp

// UI motion only: never apply this multiplier to network polling or game clocks.
internal fun <T> appTween(
    durationMillis: Int = 300,
    delayMillis: Int = 0,
    easing: Easing = CubicBezierEasing(.22f, 0f, .2f, 1f),
): TweenSpec<T> = tween((durationMillis * 1.55f).toInt(), delayMillis, easing)

internal fun <T> appSpring(
    dampingRatio: Float = .9f,
    stiffness: Float = 500f,
    visibilityThreshold: T? = null,
): SpringSpec<T> = spring(
    dampingRatio = dampingRatio.coerceAtLeast(.85f),
    stiffness = stiffness * .58f,
    visibilityThreshold = visibilityThreshold,
)

@Composable
internal fun InAppUnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(AppDanger),
    )
}

/** A restrained physical response shared by tappable surfaces. */
@Composable
internal fun Modifier.appPressMotion(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = .985f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = appSpring(dampingRatio = .9f, stiffness = 500f),
        label = "surface-press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
