package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.delay

// A small overshoot gives the incoming digit a soft landing, without a long spring tail.
private val RksDigitLanding = CubicBezierEasing(.2f, .85f, .25f, 1.12f)

/** Animate changed digits; callers can opt into smooth decreases as well. */
@Composable
internal fun AnimatedRksNumber(
    value: Double,
    fontSize: TextUnit,
    color: Color = LocalContentColor.current,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fractionDigits: Int = 4,
    animateDecrease: Boolean = false,
) {
    val formatted = remember(value, fractionDigits) { String.format(Locale.US, "%.${fractionDigits}f", value) }
    val style = LocalTextStyle.current.merge(TextStyle(
        fontFamily = AppNumericFont, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight,
    ))
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digitWidth = remember(measurer, style, density) {
        with(density) {
            ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }.toDp()
        }
    }
    val decimalWidth = remember(measurer, style, density) {
        with(density) { measurer.measure(".", style).size.width.toDp() }
    }
    // Keep two integer positions alive for 9.9999 -> 10.0000; an empty slot takes no width.
    val slots = maxOf(if (fractionDigits == 0) 1 else 7, formatted.length + 1)
    val highlightColor = lerp(AppAccent, Color.White, .22f)
    Row(
        Modifier.clearAndSetSemantics { text = AnnotatedString(formatted) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (position in slots - 1 downTo 0) {
            key(position) {
                val emphasis = remember { Animatable(0f) }
                var previousValue by remember { mutableDoubleStateOf(value) }
                val stagger = (slots - 1 - position) * 22
                LaunchedEffect(value) {
                    val previous = previousValue
                    previousValue = value
                    // A newer update cancels the old pulse, including its pending stagger.
                    emphasis.snapTo(0f)
                    if ((value > previous || (animateDecrease && value < previous)) && rksDigit(value, position, fractionDigits)?.isDigit() == true &&
                        rksDigit(value, position, fractionDigits) != rksDigit(previous, position, fractionDigits)) {
                        delay(stagger.toLong())
                        emphasis.animateTo(1f, appTween(65))
                        emphasis.animateTo(0f, appTween(220))
                    }
                }
                AnimatedContent(
                    targetState = value,
                    contentKey = { number -> rksDigit(number, position, fractionDigits) },
                    modifier = Modifier.clipToBounds(),
                    transitionSpec = {
                        if (targetState > initialState || (animateDecrease && targetState < initialState)) {
                            val direction = if (targetState > initialState) 1 else -1
                            (slideInVertically(appTween(250, delayMillis = stagger, easing = RksDigitLanding)) { it * direction } +
                                scaleIn(appTween(250, delayMillis = stagger, easing = RksDigitLanding), initialScale = .9f) +
                                fadeIn(appTween(120, delayMillis = stagger))) togetherWith
                                (slideOutVertically(appTween(160, delayMillis = stagger)) { -it * direction } +
                                    scaleOut(appTween(160, delayMillis = stagger), targetScale = .94f) +
                                    fadeOut(appTween(100, delayMillis = stagger)))
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    label = "rks-digit-$position",
                ) { number ->
                    val digit = rksDigit(number, position, fractionDigits)
                    if (digit == null) Spacer(Modifier.width(0.dp))
                    else Text(digit.toString(), style = style,
                        color = lerp(color, highlightColor, emphasis.value * .38f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(if (digit == '.') decimalWidth else digitWidth)
                            .graphicsLayer {
                                // Compress gently before settling; keep the measured slot width fixed.
                                scaleX = 1f - emphasis.value * .025f
                                scaleY = 1f + emphasis.value * .025f
                            })
                }
            }
        }
    }
}

private fun rksDigit(value: Double, position: Int, fractionDigits: Int): Char? =
    String.format(Locale.US, "%.${fractionDigits}f", value).let { it.getOrNull(it.lastIndex - position) }
