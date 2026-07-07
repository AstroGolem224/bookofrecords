package com.claymachinegames.bookofrecords.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository
import com.claymachinegames.bookofrecords.domain.formatMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LibraryScreen(repo: RecordingRepository, onOpen: (RecordingEntry) -> Unit) {
    var entries by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { repo.list() }
        loaded = true
    }

    val dateFmt = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Bibliothek", style = MaterialTheme.typography.headlineMedium)
        if (loaded && entries.isEmpty()) {
            Text("Noch keine Aufnahmen.", Modifier.padding(top = 24.dp))
        }
        LazyColumn(Modifier.padding(top = 16.dp)) {
            items(entries, key = { it.audioUri }) { e ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen(e) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.baseName, style = MaterialTheme.typography.titleMedium)
                        Row {
                            Text(
                                dateFmt.format(Instant.ofEpochSecond(e.addedAtEpochSec)) +
                                    "  ·  ${formatMs(e.durationMs)}  ·  ${e.markerCount} Marker",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
