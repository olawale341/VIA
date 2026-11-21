package com.globalchat.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class WaPalette(
    val isDark: Boolean,
    val bg: Color,
    val chatBg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val topBar: Color,
    val outBubble: Color,
    val inBubble: Color,
    val datePill: Color,
    val divider: Color,
    val accent: Color,
    val accentText: Color,
    val accentPill: Color,
    val accentPillIcon: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val tickGray: Color,
    val tickBlue: Color,
    val linkBlue: Color,
    val danger: Color,
    val e2eeBg: Color,
    val e2eeText: Color,
    val doodleColor: Color
)

val DarkWaPalette = WaPalette(
    isDark = true,
    bg = Color(0xFF0B141A),
    chatBg = Color(0xFF0B141A),
    surface = Color(0xFF1F2C34),
    surfaceVariant = Color(0xFF2A3942),
    topBar = Color(0xFF0B141A),
    outBubble = Color(0xFF005C4B),
    inBubble = Color(0xFF1F2C34),
    datePill = Color(0xFF182229),
    divider = Color(0xFF182229),
    accent = Color(0xFF25D366),
    accentText = Color(0xFF0B141A),
    accentPill = Color(0xFF103529),
    accentPillIcon = Color(0xFFD8FDD2),
    textPrimary = Color(0xFFE9EDEF),
    textSecondary = Color(0xFF8696A0),
    tickGray = Color(0xFFB0BAC0),
    tickBlue = Color(0xFF53BDEB),
    linkBlue = Color(0xFF53BDEB),
    danger = Color(0xFFFF6B6B),
    e2eeBg = Color(0xFF182229),
    e2eeText = Color(0xFFFFD279),
    doodleColor = Color.White.copy(alpha = 0.03f)
)

val LightWaPalette = WaPalette(
    isDark = false,
    bg = Color(0xFFFFFFFF),
    chatBg = Color(0xFFEFEAE2),
    surface = Color(0xFFF0F2F5),
    surfaceVariant = Color(0xFFDFE5E7),
    topBar = Color(0xFFFFFFFF),
    outBubble = Color(0xFFD9FDD3),
    inBubble = Color(0xFFFFFFFF),
    datePill = Color(0xFFF7F8FA),
    divider = Color(0xFFF0F2F5),
    accent = Color(0xFF1DAB61),
    accentText = Color(0xFFFFFFFF),
    accentPill = Color(0xFFD8FDD2),
    accentPillIcon = Color(0xFF005C4B),
    textPrimary = Color(0xFF111B21),
    textSecondary = Color(0xFF667781),
    tickGray = Color(0xFF667781),
    tickBlue = Color(0xFF34B7F1),
    linkBlue = Color(0xFF027EB5),
    danger = Color(0xFFE53935),
    e2eeBg = Color(0xFFFFF3C4),
    e2eeText = Color(0xFF54421A),
    doodleColor = Color.Black.copy(alpha = 0.04f)
)

val LocalWaPalette = compositionLocalOf { DarkWaPalette }

@Composable
fun GlobalChatTheme(themeMode: String, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }
    val palette = if (useDark) DarkWaPalette else LightWaPalette
    val colorScheme = if (useDark) {
        darkColorScheme(
            background = palette.bg,
            surface = palette.surface,
            primary = palette.accent,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    } else {
        lightColorScheme(
            background = palette.bg,
            surface = palette.surface,
            primary = palette.accent,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    }

    CompositionLocalProvider(LocalWaPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}