package com.focuslock.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fade Orange palette.
 *
 * Warm, low-chroma paper surfaces with a single ember accent. Blocks are separated by thin
 * borders instead of shadows; the result reads as compact and boxy rather than card-heavy.
 */
val Ember = Color(0xFFD9530F)
val FadeOrange = Color(0xFFF2751F)
val Apricot = Color(0xFFFFB27A)
val Cream = Color(0xFFFDEEDF)
val Paper = Color(0xFFFFF8F1)
val Ink = Color(0xFF1A1008)
val Cocoa = Color(0xFF6B5748)
val Clay = Color(0xFFC4A68C)
val Danger = Color(0xFFA62A18)

val NightPaper = Color(0xFF120C07)
val NightSurface = Color(0xFF1E150D)
val NightVariant = Color(0xFF2B1E13)
val NightOutline = Color(0xFF5A422F)
val NightText = Color(0xFFF7EADB)
val NightMuted = Color(0xFFC9AF97)
val NightEmber = Color(0xFFFF9A4D)

private val LightColors = lightColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    primaryContainer = Cream,
    onPrimaryContainer = Ink,
    secondary = Cocoa,
    onSecondary = Color.White,
    secondaryContainer = Apricot,
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Cream,
    onSurfaceVariant = Cocoa,
    outline = Clay,
    outlineVariant = Color(0xFFE9D6C3),
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFFF8DED7),
    onErrorContainer = Danger,
    scrim = Color(0xCC1A1008),
)

private val DarkColors = darkColorScheme(
    primary = NightEmber,
    onPrimary = Color(0xFF2A1200),
    primaryContainer = Color(0xFF3A2414),
    onPrimaryContainer = Color(0xFFFFD9BC),
    secondary = Apricot,
    onSecondary = Color(0xFF2A1200),
    secondaryContainer = Color(0xFF3A2414),
    onSecondaryContainer = Color(0xFFFFD9BC),
    background = NightPaper,
    onBackground = NightText,
    surface = NightPaper,
    onSurface = NightText,
    surfaceVariant = NightVariant,
    onSurfaceVariant = NightMuted,
    outline = NightOutline,
    outlineVariant = Color(0xFF3A2A1C),
    error = Color(0xFFFFB4A4),
    onError = Color(0xFF561000),
    errorContainer = Color(0xFF7A2415),
    onErrorContainer = Color(0xFFFFDAD2),
    scrim = Color(0xCC000000),
)

private val FocusShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

private val FocusTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.4.sp,
    ),
)

@Composable
fun FocusLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FocusTypography,
        shapes = FocusShapes,
        content = content,
    )
}
