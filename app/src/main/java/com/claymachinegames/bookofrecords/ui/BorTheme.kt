package com.claymachinegames.bookofrecords.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Amber/Cyan-Petrol-Schema nach Design-Mockup: blaustichiger Dunkelgrund,
// Amber als Primär-Akzent (Aktionen/Aufnahme), Petrol/Cyan als Sekundär (Chips, Glow)
object Bor {
    val bg = Color(0xFF0A1016)
    val idleBgBase = Color(0xFF01030B)
    val idleBgEnd = Color(0xFF020713)
    val idleDeepBlue = Color(0xFF001626)
    val idleDeepViolet = Color(0xFF1C0739)
    val idleGlass = Color(0xFF07111F)
    val idleGlassEnd = Color(0xFF0B071D)
    val idleControl = Color(0xFF0A1625)
    val idleTextPrimary = Color(0xFFF0F1F2)
    val idleTextSecondary = Color(0xFF5A7D9A)
    val idleDockLabel = Color(0xFF9AB7CA)
    val idleCyan = Color(0xFF00D8E8)
    val idleBlue = Color(0xFF5AA7FF)
    val idleViolet = Color(0xFF8B5CF6)
    val idleAmber = Color(0xFFF89A00)
    val idleAmberHi = Color(0xFFFFC04D)
    val idleOutline = Color(0xFF6D8AA0)
    val idleIcon = Color(0xFFB8D0DF)
    val surface = Color(0xFF121B22)
    val borderSubtle = Color(0xFF1C2A33)
    val border = Color(0xFF24363F)
    val borderStrong = Color(0xFF32505C)
    val accent = Color(0xFFF5A623)
    val onAccent = Color(0xFF1F1400)
    val levelGreen = Color(0xFF3F9142)
    val levelAmber = Color(0xFFE0A030)
    val levelYellow = Color(0xFFC9C930)
    val levelOrange = Color(0xFFE07030)
    val waveCold = Color(0xFF35D6E8)
    val amber = Color(0xFFF5A623)     // = accent (Alias für bestehende Aufrufer)
    val teal = Color(0xFF3EC8CE)
    val violet = Color(0xFF8B5CF6)    // Mockup: Waveform-Verlauf, Glow-Border
    val textPrimary = Color(0xFFF2F4F5)
    val textSecondary = Color(0xFF93A2AA)
    val textMuted = Color(0xFF56656D)
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

@Composable
internal fun borFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Bor.textPrimary, unfocusedTextColor = Bor.textPrimary,
    focusedContainerColor = Bor.bg, unfocusedContainerColor = Bor.bg,
    focusedBorderColor = Bor.borderStrong, unfocusedBorderColor = Bor.border,
    cursorColor = Bor.accent,
)
