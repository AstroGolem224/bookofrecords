package com.claymachinegames.bookofrecords.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.record.RecState
import com.claymachinegames.bookofrecords.record.RecorderService
import com.claymachinegames.bookofrecords.record.RecorderState

@Composable
fun RecordScreen(hasAudioPermission: Boolean, onOpenLibrary: () -> Unit) {
    val context = LocalContext.current
    val state by RecorderState.state.collectAsState()
    fun send(action: String) {
        context.startForegroundService(RecorderService.intent(context, action))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            is RecState.Idle -> {
                Spacer(Modifier.height(120.dp))
                Text("BookofRecords", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { send(RecorderService.ACTION_START) },
                    enabled = hasAudioPermission,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) { Text("● Aufnahme starten", style = MaterialTheme.typography.titleLarge) }
                if (!hasAudioPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text("Mikrofon-Berechtigung fehlt", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onOpenLibrary) { Text("Bibliothek") }
            }
            is RecState.Recording -> {
                Spacer(Modifier.height(48.dp))
                Text(formatMs(s.elapsedMs), style = MaterialTheme.typography.displayLarge)
                Text(
                    if (s.paused) "Pausiert" else s.baseName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { send(RecorderService.ACTION_MARKER) },
                    enabled = !s.paused,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                ) { Text("MARKER", style = MaterialTheme.typography.headlineMedium) }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = {
                        send(if (s.paused) RecorderService.ACTION_RESUME
                             else RecorderService.ACTION_PAUSE)
                    }, modifier = Modifier.weight(1f)) {
                        Text(if (s.paused) "Weiter" else "Pause")
                    }
                    OutlinedButton(
                        onClick = { send(RecorderService.ACTION_STOP) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Stop") }
                }
                Spacer(Modifier.height(24.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(s.markers.asReversed()) { m ->
                        Text(
                            "${formatMs(m.timeMs)} — ${m.label.ifEmpty { "(unbenannt)" }}",
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
