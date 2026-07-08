package com.claymachinegames.bookofrecords.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.data.LibraryStore
import com.claymachinegames.bookofrecords.data.LibraryUnavailableException
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.exportZipBytes
import com.claymachinegames.bookofrecords.domain.dateFolder
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.domain.titlePartOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun LibraryScreen(
    store: LibraryStore,
    onOpen: (RecordingEntry) -> Unit,
    onNewRecording: () -> Unit,
    onOpenSettings: () -> Unit,
    onSweep: suspend () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var unreachable by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }

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

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val chosen = entries.filter { it.audioUri in selected }
            scope.launch {
                runCatching {
                    val bytes = exportZipBytes(context, store, chosen)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("openOutputStream returned null")
                    }
                }.onSuccess {
                    selected = emptySet()
                    selectionMode = false
                    Toast.makeText(context, "Gespeichert", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Speichern fehlgeschlagen", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (selectionMode) {
        BackHandler {
            selected = emptySet()
            selectionMode = false
        }
    }

    val groups = entries.groupBy { it.dateGroup }.entries.sortedByDescending { it.key }

    Column(Modifier.fillMaxSize().background(Bor.bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Text("${selected.size} ausgewählt", color = Bor.textPrimary,
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = emptySet(); selectionMode = false }) {
                    Text("Fertig", color = Bor.textSecondary)
                }
                TextButton(
                    onClick = {
                        val chosen = entries.filter { it.audioUri in selected }
                        val name = if (chosen.size == 1) "${chosen.first().baseName}.zip"
                                   else "BookofRecords_${dateFolder(LocalDateTime.now())}_${chosen.size}.zip"
                        zipLauncher.launch(name)
                    },
                    enabled = selected.isNotEmpty(),
                ) { Text("Speichern", color = if (selected.isNotEmpty()) Bor.accent else Bor.textMuted) }
            } else {
                Text("Bibliothek", color = Bor.textPrimary,
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectionMode = true }) {
                    Text("Auswählen", color = Bor.textSecondary)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, "Einstellungen", tint = Bor.textMuted)
                }
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
                items(list, key = { it.audioUri }) { e ->
                    EntryCard(
                        e = e,
                        selectionMode = selectionMode,
                        isSelected = e.audioUri in selected,
                        onOpen = onOpen,
                        onToggle = {
                            selected = if (e.audioUri in selected) selected - e.audioUri
                                       else selected + e.audioUri
                        },
                    )
                }
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
private fun EntryCard(
    e: RecordingEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    onOpen: (RecordingEntry) -> Unit,
    onToggle: () -> Unit,
) {
    val title = titlePartOf(e.baseName)?.takeIf { it.isNotEmpty() } ?: e.baseName
    val time = Regex("""_(\d{2}-\d{2})_BoR""").find(e.baseName)?.groupValues?.get(1) ?: ""
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(Bor.surface, RoundedCornerShape(8.dp))
            .border(1.dp, Bor.borderSubtle, RoundedCornerShape(8.dp))
            .clickable { if (selectionMode) onToggle() else onOpen(e) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Bor.accent, uncheckedColor = Bor.textMuted),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column {
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
}
