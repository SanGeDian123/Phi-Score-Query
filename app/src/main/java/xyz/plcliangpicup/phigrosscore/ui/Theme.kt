package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.plcliangpicup.phigrosscore.R

private val DarkBackground = Color(0xFF0B0C0F)
private val DarkSurface = Color(0xFF191B20)
private val DarkSurfaceRaised = Color(0xFF24262C)
private val DarkAccent = Color(0xFF76E7C7)
private val DarkText = Color(0xFFF5F7FA)
private val DarkTextMuted = Color(0xFF9AA5B5)
private val DarkOutline = Color(0xFF303846)
private val LightBackground = Color(0xFFF3F4F6)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceRaised = Color(0xFFEBEDF1)
private val LightAccent = Color(0xFF356AE6)
private val LightText = Color(0xFF191B20)
private val LightTextMuted = Color(0xFF616570)
private val LightOutline = Color(0xFFD8DBE2)
private val AppBlue = Color(0xFF79B6FF)
private val DarkDanger = Color(0xFFFF7180)

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

internal val AppNumericFont = FontFamily(
    Font(R.font.source_han_sans_saira_hybrid, FontWeight.Normal),
)
private val AppFont = AppNumericFont

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = AppFont),
        displayMedium = displayMedium.copy(fontFamily = AppFont),
        displaySmall = displaySmall.copy(fontFamily = AppFont),
        headlineLarge = headlineLarge.copy(fontFamily = AppFont, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontFamily = AppFont, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.5).sp),
        headlineSmall = headlineSmall.copy(fontFamily = AppFont, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = AppFont, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = AppFont, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = AppFont, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = AppFont, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = bodyMedium.copy(fontFamily = AppFont, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontFamily = AppFont, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontFamily = AppFont, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = AppFont, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = AppFont, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun PhigrosScoreTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val motion = appTween<Color>(durationMillis = 380)
    val background by animateColorAsState(if (darkTheme) DarkBackground else LightBackground, motion, label = "theme-background")
    val surface by animateColorAsState(if (darkTheme) DarkSurface else LightSurface, motion, label = "theme-surface")
    val surfaceRaised by animateColorAsState(if (darkTheme) DarkSurfaceRaised else LightSurfaceRaised, motion, label = "theme-surface-raised")
    val accent by animateColorAsState(if (darkTheme) DarkAccent else LightAccent, motion, label = "theme-accent")
    val text by animateColorAsState(if (darkTheme) DarkText else LightText, motion, label = "theme-text")
    val textMuted by animateColorAsState(if (darkTheme) DarkTextMuted else LightTextMuted, motion, label = "theme-muted-text")
    val outline by animateColorAsState(if (darkTheme) DarkOutline else LightOutline, motion, label = "theme-outline")
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF07152F),
            primaryContainer = accent.copy(alpha = .16f),
            onPrimaryContainer = accent,
            secondary = AppBlue,
            secondaryContainer = Color(0xFF233648),
            onSecondaryContainer = Color(0xFFDBE8FF),
            tertiary = accent,
            tertiaryContainer = Color(0xFF163B32),
            onTertiaryContainer = accent,
            surfaceTint = accent,
            surfaceContainerHighest = surfaceRaised,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceRaised,
            onSurfaceVariant = textMuted,
            surfaceContainerLow = Color(0xFF0E1218),
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceRaised,
            outline = outline,
            outlineVariant = outline.copy(alpha = .62f),
            error = DarkDanger,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = .11f),
            onPrimaryContainer = accent,
            secondary = Color(0xFF315F9D),
            secondaryContainer = Color(0xFFEAF0FC),
            onSecondaryContainer = Color(0xFF244EAE),
            tertiary = Color(0xFF52709A),
            tertiaryContainer = Color(0xFFEAF0FC),
            onTertiaryContainer = Color(0xFF244EAE),
            surfaceTint = accent,
            surfaceContainerHighest = surfaceRaised,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceRaised,
            onSurfaceVariant = textMuted,
            surfaceContainerLow = Color(0xFFF9FAFC),
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceRaised,
            outline = outline,
            outlineVariant = outline.copy(alpha = .7f),
            error = Color(0xFFB42335),
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
