package com.claymachinegames.bookofrecords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.record.RecState
import com.claymachinegames.bookofrecords.record.RecorderService
import com.claymachinegames.bookofrecords.record.RecorderState
import kotlinx.coroutines.delay

@Composable
fun RecordScreen(hasAudioPermission: Boolean, onOpenLibrary: () -> Unit) {
    val context = LocalContext.current
    val state by RecorderState.state.collectAsState()
    fun send(action: String) {
        context.startForegroundService(RecorderService.intent(context, action))
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Bor.bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            is RecState.Idle -> {
                Spacer(Modifier.height(120.dp))
                Text("BookofRecords", color = Bor.textPrimary,
                    style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { send(RecorderService.ACTION_START) },
                    enabled = hasAudioPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Bor.accent, contentColor = Bor.onAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) { Text("● Aufnahme starten", fontSize = 18.sp) }
                if (!hasAudioPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text("Mikrofon-Berechtigung fehlt", color = Bor.accent, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onOpenLibrary) {
                    Text("Bibliothek", color = Bor.textSecondary)
                }
            }
            is RecState.Recording -> {
                var titleText by remember(s.baseName) { mutableStateOf(s.title) }
                LaunchedEffect(titleText) {
                    if (titleText != s.title) {
                        delay(400)   // debounce → Service
                        context.startForegroundService(
                            RecorderService.intent(context, RecorderService.ACTION_SET_TITLE)
                                .putExtra(RecorderService.EXTRA_TITLE, titleText))
                    }
                }
                RecHeader(paused = s.paused)
                Spacer(Modifier.height(14.dp))
                TitleField(text = titleText, onChange = { titleText = it })
                Text(formatMs(s.elapsedMs), color = Bor.textPrimary,
                    fontFamily = FontFamily.Monospace, fontSize = 36.sp,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                LevelMeter(level = s.level)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { send(RecorderService.ACTION_MARKER) },
                    enabled = !s.paused,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Bor.accent, contentColor = Bor.onAccent,
                        disabledContainerColor = Bor.surface, disabledContentColor = Bor.textMuted),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                ) { Text("Marker", fontSize = 20.sp) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        send(if (s.paused) RecorderService.ACTION_RESUME
                             else RecorderService.ACTION_PAUSE)
                    }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Text(if (s.paused) "Weiter" else "Pause", color = Bor.textSecondary)
                    }
                    OutlinedButton(
                        onClick = {
                            // Titel mitschicken: schlägt 400ms-Debounce bei schnellem Stop
                            context.startForegroundService(
                                RecorderService.intent(context, RecorderService.ACTION_STOP)
                                    .putExtra(RecorderService.EXTRA_TITLE, titleText))
                        },
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f),
                    ) { Text("Stop", color = Bor.textSecondary) }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Bor.borderSubtle)
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    items(s.markers.asReversed()) { m ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(formatMs(m.timeMs), color = Bor.accent,
                                fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(m.label.ifEmpty { "(unbenannt)" },
                                color = Bor.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecHeader(paused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(8.dp).background(
            if (paused) Bor.textMuted else Bor.accent, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (paused) "PAUSE" else "REC",
            color = if (paused) Bor.textMuted else Bor.accent,
            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text("96k · mono", color = Bor.textMuted,
            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun TitleField(text: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        placeholder = { Text("Titel", color = Bor.textMuted) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Bor.textPrimary, unfocusedTextColor = Bor.textPrimary,
            focusedContainerColor = Bor.surface, unfocusedContainerColor = Bor.surface,
            focusedBorderColor = Bor.borderStrong, unfocusedBorderColor = Bor.border,
            cursorColor = Bor.accent,
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LevelMeter(level: Float) {
    val bars = 14
    val active = (level * bars).toInt()
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom, modifier = Modifier.height(24.dp)) {
        repeat(bars) { i ->
            val frac = (i + 1f) / bars
            val color = when {
                i >= active -> Bor.borderSubtle
                frac > 0.7f -> Bor.levelAmber
                else -> Bor.levelGreen
            }
            Box(Modifier.width(5.dp).height((6 + 18 * frac).dp)
                .background(color, RoundedCornerShape(1.dp)))
        }
    }
}
