package com.claymachinegames.bookofrecords.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.claymachinegames.bookofrecords.record.RecState
import com.claymachinegames.bookofrecords.record.RecorderState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Tarn-Screen: schwarz, Uhrzeit mittig, dezenter Punkt unten solange aufgenommen wird.
 *  Exit: Doppeltipp oder Back; Auto-Exit wenn die Aufnahme endet. */
@Composable
fun HideScreen(onExit: () -> Unit) {
    val state by RecorderState.state.collectAsState()
    val recording = state as? RecState.Recording

    LaunchedEffect(recording == null) { if (recording == null) onExit() }
    BackHandler { onExit() }

    val clockFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var clock by remember { mutableStateOf(LocalTime.now().format(clockFormat)) }
    LaunchedEffect(Unit) {
        // Sekunden-Ticker, aber String-State: Recomposition nur beim Minutenwechsel;
        // selbstkorrigierend nach Doze/Resume/Zeitzonen-Wechsel
        while (true) {
            clock = LocalTime.now().format(clockFormat)
            delay(1000)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onExit() }) }
            .semantics { onClick("Aufnahme anzeigen") { onExit(); true } },
    ) {
        Text(clock, color = Bor.textMuted, fontFamily = FontFamily.Monospace, fontSize = 40.sp,
            modifier = Modifier.align(Alignment.Center))
        Box(
            Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 24.dp)
                .size(6.dp)
                .alpha(0.35f)
                .background(
                    if (recording?.paused == true) Bor.textMuted else Bor.accent,
                    CircleShape,
                ),
        )
    }
}
