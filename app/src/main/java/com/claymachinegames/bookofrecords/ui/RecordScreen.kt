package com.claymachinegames.bookofrecords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.record.RecState
import com.claymachinegames.bookofrecords.record.RecorderService
import com.claymachinegames.bookofrecords.record.RecorderState
import kotlinx.coroutines.delay

@Composable
fun RecordScreen(
    hasAudioPermission: Boolean,
    waveHistory: List<Float>,
    onWaveWidthPx: (Int) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onHide: () -> Unit,
) {
    val context = LocalContext.current
    val state by RecorderState.state.collectAsState()
    fun send(action: String) {
        val intent = RecorderService.intent(context, action)
        // Nur START braucht startForegroundService; alle anderen Actions per startService,
        // sonst entsteht eine startForeground-Verpflichtung, die ein Idle-Service nicht einlöst
        if (action == RecorderService.ACTION_START) context.startForegroundService(intent)
        else context.startService(intent)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Bor.bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            is RecState.Idle -> {
                // Header: Wave-Logo + Titel/Subtitle links, Settings als Glas-Button rechts
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()) {
                    WaveLogo()
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("BOOK OF RECORDS", color = Bor.textPrimary,
                            fontSize = 16.sp, letterSpacing = 3.sp)
                        Text("AUDIO RECORDER", color = Bor.textMuted,
                            fontSize = 8.sp, letterSpacing = 4.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(44.dp)
                            .background(Bor.surface.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .border(1.dp, Bor.border, RoundedCornerShape(14.dp)),
                    ) { Icon(Icons.Filled.Settings, "Einstellungen", tint = Bor.waveCold) }
                }
                Spacer(Modifier.height(28.dp))
                IdleWaveformCard(label = "BEREIT")
                Spacer(Modifier.weight(0.5f))
                Text("00:00:00", color = Bor.textPrimary.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace, fontSize = 52.sp,
                    letterSpacing = 2.sp)
                if (!hasAudioPermission) {
                    Spacer(Modifier.height(10.dp))
                    Text("Mikrofon-Berechtigung fehlt", color = Bor.accent, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                // Glas-Bottombar: Bibliothek · Record · Hide
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                        .background(Bor.surface.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                        .border(1.dp, Bor.borderSubtle, RoundedCornerShape(28.dp))
                        .padding(vertical = 14.dp),
                ) {
                    GlassActionButton("BIBLIOTHEK", onClick = onOpenLibrary) { LibraryIcon() }
                    IconButton(
                        onClick = { send(RecorderService.ACTION_START) },
                        enabled = hasAudioPermission,
                        modifier = Modifier.size(84.dp)
                            .border(2.dp, if (hasAudioPermission) Bor.accent else Bor.textMuted,
                                CircleShape),
                    ) {
                        Box(Modifier.size(56.dp).background(
                            if (hasAudioPermission) Bor.accent else Bor.textMuted, CircleShape))
                    }
                    GlassActionButton("HIDE", onClick = onHide) { HideIcon() }
                }
                Spacer(Modifier.height(6.dp))
            }
            is RecState.Recording -> {
                var titleText by remember(s.baseName) { mutableStateOf(s.title) }
                LaunchedEffect(titleText) {
                    if (titleText != s.title) {
                        delay(400)   // debounce → Service
                        context.startService(
                            RecorderService.intent(context, RecorderService.ACTION_SET_TITLE)
                                .putExtra(RecorderService.EXTRA_TITLE, titleText))
                    }
                }
                // Dispose (Detail/Settings-Route zerstört den Pager) darf den Titel im
                // 400ms-Debounce-Fenster nicht verlieren → letzten Stand flushen
                val latestTitle by rememberUpdatedState(titleText)
                DisposableEffect(s.baseName) {
                    onDispose {
                        // nur flushen solange DIESE Aufnahme noch läuft — sonst würde der
                        // startService einen bereits gestoppten Service wiederbeleben
                        val cur = RecorderState.state.value
                        if (cur is RecState.Recording && cur.baseName == s.baseName
                            && cur.title != latestTitle) {
                            context.startService(
                                RecorderService.intent(context, RecorderService.ACTION_SET_TITLE)
                                    .putExtra(RecorderService.EXTRA_TITLE, latestTitle))
                        }
                    }
                }
                RecHeader(paused = s.paused, onHide = onHide)
                Spacer(Modifier.height(14.dp))
                TitleField(text = titleText, onChange = { titleText = it })
                Spacer(Modifier.height(12.dp))

                // Historie lebt in App() — überlebt Pager-Disposal und Detail/Settings-Ausflüge
                LiveWaveform(levels = waveHistory, onWidthPx = onWaveWidthPx)

                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)) {
                    PulsingRecDot(paused = s.paused)
                    Spacer(Modifier.width(10.dp))
                    Text(formatMs(s.elapsedMs), color = Bor.accent,
                        fontFamily = FontFamily.Monospace, fontSize = 48.sp)
                }

                DbMeter(level = s.level)
                Spacer(Modifier.height(2.dp))
                DbScale()
                Spacer(Modifier.height(10.dp))

                Text("M4A · 96 kbps · mono", color = Bor.teal,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier
                        .border(1.dp, Bor.teal.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp))
                Spacer(Modifier.height(14.dp))

                RecordButtonRow(
                    paused = s.paused,
                    onStop = {
                        // Titel mitschicken: schlägt 400ms-Debounce bei schnellem Stop
                        context.startService(
                            RecorderService.intent(context, RecorderService.ACTION_STOP)
                                .putExtra(RecorderService.EXTRA_TITLE, titleText))
                    },
                    onPauseResume = {
                        send(if (s.paused) RecorderService.ACTION_RESUME
                             else RecorderService.ACTION_PAUSE)
                    },
                    onMarker = { send(RecorderService.ACTION_MARKER) },
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Bor.borderSubtle)
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp)) {
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
private fun RecHeader(paused: Boolean, onHide: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(8.dp).background(
            if (paused) Bor.textMuted else Bor.accent, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (paused) "PAUSE" else "REC",
            color = if (paused) Bor.textMuted else Bor.accent,
            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        // dezente HIDE-Pill im freien Header-Slot (statt unter den Buttons: kein Überlauf bei IME)
        Text("HIDE", color = Bor.amber, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            modifier = Modifier
                .border(1.dp, Bor.amber.copy(alpha = 0.5f), RoundedCornerShape(50))
                .clickable(onClick = onHide)
                .padding(horizontal = 12.dp, vertical = 4.dp))
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

