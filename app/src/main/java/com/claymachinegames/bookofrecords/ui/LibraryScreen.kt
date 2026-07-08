package com.claymachinegames.bookofrecords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.data.LibraryStore
import com.claymachinegames.bookofrecords.data.LibraryUnavailableException
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.domain.titlePartOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    store: LibraryStore,
    onOpen: (RecordingEntry) -> Unit,
    onNewRecording: () -> Unit,
    onOpenSettings: () -> Unit,
    onSweep: suspend () -> Unit,
) {
    var entries by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var unreachable by remember { mutableStateOf(false) }

    LaunchedEffect(store) {
        loaded = false
        unreachable = false
        onSweep()
        runCatching {
            withContext(Dispatchers.IO) { store.list() }
        }.onSuccess {
            entries = it
        }.onFailure { e ->
            if (e is LibraryUnavailableException) unreachable = true else throw e
        }
        loaded = true
    }

    val groups = entries.groupBy { it.dateGroup }.entries.sortedByDescending { it.key }

    Column(Modifier.fillMaxSize().background(Bor.bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bibliothek", color = Bor.textPrimary,
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, "Einstellungen", tint = Bor.textMuted)
            }
        }
        if (unreachable) {
            Column(Modifier.padding(top = 24.dp)) {
                Text("Speicherordner nicht erreichbar", color = Bor.accent, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenSettings) { Text("Zu den Einstellungen", color = Bor.textSecondary) }
            }
        } else if (loaded && entries.isEmpty()) {
            Text("Noch keine Aufnahmen.", color = Bor.textSecondary,
                modifier = Modifier.padding(top = 24.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            groups.forEach { (date, list) ->
                item(key = "header-$date") {
                    Text(date, color = Bor.textMuted,
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                }
                items(list, key = { it.audioUri }) { e -> EntryCard(e, onOpen) }
            }
        }
        OutlinedButton(
            onClick = onNewRecording,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("● Neue Aufnahme", color = Bor.accent, fontSize = 15.sp) }
    }
}

@Composable
private fun EntryCard(e: RecordingEntry, onOpen: (RecordingEntry) -> Unit) {
    val title = titlePartOf(e.baseName)?.takeIf { it.isNotEmpty() } ?: e.baseName
    val time = Regex("""_(\d{2}-\d{2})_BoR""").find(e.baseName)?.groupValues?.get(1) ?: ""
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(Bor.surface, RoundedCornerShape(8.dp))
            .border(1.dp, Bor.borderSubtle, RoundedCornerShape(8.dp))
            .clickable { onOpen(e) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, color = Bor.textPrimary, fontSize = 14.sp)
        Spacer(Modifier.height(3.dp))
        Row {
            Text(
                listOfNotNull(
                    time.takeIf { it.isNotEmpty() },
                    formatMs(e.durationMs),
                    "${e.markerCount} ◆",
                ).joinToString(" · "),
                color = Bor.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp,
            )
        }
    }
}
