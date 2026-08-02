package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import xyz.plcliangpicup.phigrosscore.R

private val DarkBackground = Color(0xFF080A0E)
private val DarkSurface = Color(0xFF11151C)
private val DarkSurfaceRaised = Color(0xFF191F29)
private val DarkAccent = Color(0xFF76E7C7)
private val DarkText = Color(0xFFF4F6FA)
private val DarkTextMuted = Color(0xFF99A3B3)
private val LightBackground = Color(0xFFF4F7FB)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceRaised = Color(0xFFE7EDF7)
private val LightAccent = Color(0xFF173F7A)
private val LightText = Color(0xFF101828)
private val LightTextMuted = Color(0xFF536176)
private val AppBlue = Color(0xFF7AB8FF)
private val DarkDanger = Color(0xFFFF6B79)

val AppBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background
val AppSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surface
val AppSurfaceRaised: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val AppAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val AppTextMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val AppDanger: Color
    @Composable get() = MaterialTheme.colorScheme.error

private val AppFont = FontFamily(
    Font(R.font.source_han_sans_saira_hybrid, FontWeight.Normal),
)

@Composable
fun PhigrosScoreTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val background by animateColorAsState(if (darkTheme) DarkBackground else LightBackground, label = "theme-background")
    val surface by animateColorAsState(if (darkTheme) DarkSurface else LightSurface, label = "theme-surface")
    val surfaceRaised by animateColorAsState(if (darkTheme) DarkSurfaceRaised else LightSurfaceRaised, label = "theme-surface-raised")
    val accent by animateColorAsState(if (darkTheme) DarkAccent else LightAccent, label = "theme-accent")
    val text by animateColorAsState(if (darkTheme) DarkText else LightText, label = "theme-text")
    val textMuted by animateColorAsState(if (darkTheme) DarkTextMuted else LightTextMuted, label = "theme-muted-text")
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF042019),
            secondary = AppBlue,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceRaised,
            onSurfaceVariant = textMuted,
            error = DarkDanger,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = Color(0xFF315F9D),
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceRaised,
            onSurfaceVariant = textMuted,
            error = Color(0xFFB42335),
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography().run {
            copy(
                displayLarge = displayLarge.copy(fontFamily = AppFont),
                displayMedium = displayMedium.copy(fontFamily = AppFont),
                displaySmall = displaySmall.copy(fontFamily = AppFont),
                headlineLarge = headlineLarge.copy(fontFamily = AppFont),
                headlineMedium = headlineMedium.copy(fontFamily = AppFont),
                headlineSmall = headlineSmall.copy(fontFamily = AppFont),
                titleLarge = titleLarge.copy(fontFamily = AppFont),
                titleMedium = titleMedium.copy(fontFamily = AppFont),
                titleSmall = titleSmall.copy(fontFamily = AppFont),
                bodyLarge = bodyLarge.copy(fontFamily = AppFont),
                bodyMedium = bodyMedium.copy(fontFamily = AppFont),
                bodySmall = bodySmall.copy(fontFamily = AppFont),
                labelLarge = labelLarge.copy(fontFamily = AppFont),
                labelMedium = labelMedium.copy(fontFamily = AppFont),
                labelSmall = labelSmall.copy(fontFamily = AppFont),
            )
        },
        content = content,
    )
}
