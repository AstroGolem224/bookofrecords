package com.claymachinegames.bookofrecords.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Bor {
    val bg = Color(0xFF0C0C0E)
    val surface = Color(0xFF151517)
    val borderSubtle = Color(0xFF232326)
    val border = Color(0xFF2A2A2E)
    val borderStrong = Color(0xFF3A3A3E)
    val accent = Color(0xFFFF453A)
    val onAccent = Color(0xFF1A0503)
    val levelGreen = Color(0xFF3F9142)
    val levelAmber = Color(0xFFE0A030)
    val textPrimary = Color(0xFFF2F2F4)
    val textSecondary = Color(0xFF9A9A9E)
    val textMuted = Color(0xFF5A5A5E)
}

@Composable
fun BorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Bor.accent,
            onPrimary = Bor.onAccent,
            background = Bor.bg,
            onBackground = Bor.textPrimary,
            surface = Bor.bg,
            onSurface = Bor.textPrimary,
            surfaceVariant = Bor.surface,
            onSurfaceVariant = Bor.textSecondary,
            secondaryContainer = Bor.surface,
            onSecondaryContainer = Bor.textPrimary,
            outline = Bor.borderStrong,
            error = Bor.accent,
        ),
        content = content,
    )
}
