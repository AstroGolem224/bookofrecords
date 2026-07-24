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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.claymachinegames.bookofrecords.data.exportZip
import com.claymachinegames.bookofrecords.domain.DateFilter
import com.claymachinegames.bookofrecords.domain.dateFolder
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.domain.matchesLibraryFilter
import com.claymachinegames.bookofrecords.domain.titlePartOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun LibraryScreen(
    store: LibraryStore,
    isActive: Boolean,
    refreshToken: Int,
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
    var query by rememberSaveable { mutableStateOf("") }
    var dateFilter by rememberSaveable { mutableStateOf(DateFilter.ALL) }

    // Laden nur wenn die Seite gesettelt aktiv ist (Pager komponiert offscreen vor);
    // isActive-Flip false→true rekeyt den Effect = Refresh beim Seiteneintritt,
    // refreshToken deckt Aufnahme-Ende bei bereits sichtbarer Bibliothek ab
    LaunchedEffect(store, refreshToken, isActive) {
        if (!isActive) return@LaunchedEffect
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

    // Selection überlebt das Verlassen der Seite nicht (sonst kann ein offscreen
    // komponierter Rest Back stehlen oder unsichtbare Auswahl wieder auftauchen)
    LaunchedEffect(isActive) {
        if (!isActive) { selected = emptySet(); selectionMode = false }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val chosen = entries.filter { it.audioUri in selected }   // selected ⊆ visible (Intersect oben)
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            exportZip(context, store, chosen, out)
                        } ?: error("openOutputStream returned null")
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

    if (selectionMode && isActive) {
        BackHandler {
            selected = emptySet()
            selectionMode = false
        }
    }

    // today als Ticker-State: Mitternacht/Zeitzonen-Wechsel korrigiert sich binnen 1 min
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) {
        while (true) { today = LocalDate.now().toString(); delay(60_000) }
    }
    val yesterday = LocalDate.parse(today).minusDays(1).toString()

    val visible = entries.filter {
        matchesLibraryFilter(it.baseName, it.dateGroup, query, dateFilter, today, yesterday)
    }
    // Filter/Suche darf keine unsichtbaren Dateien im Export lassen
    LaunchedEffect(visible) {
        val visibleUris = visible.map { it.audioUri }.toSet()
        if (!selected.all { it in visibleUris }) selected = selected intersect visibleUris
    }

    val groups = visible.groupBy { it.dateGroup }.entries.sortedByDescending { it.key }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Text("${selected.size} ausgewählt", color = Bor.textPrimary,
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = emptySet(); selectionMode = false }) {
                    Text("Fertig", color = Bor.textSecondary)
                }
                TextButton(
                    onClick = {
                        val chosen = visible.filter { it.audioUri in selected }
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
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Suchen…", color = Bor.textMuted) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = "" }) { Text("×", color = Bor.textMuted, fontSize = 16.sp) }
                }
            },
            singleLine = true,
            colors = borFieldColors(),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row {
            FilterChipPill("Alle", dateFilter == DateFilter.ALL) { dateFilter = DateFilter.ALL }
            Spacer(Modifier.width(8.dp))
            FilterChipPill("Heute", dateFilter == DateFilter.TODAY) { dateFilter = DateFilter.TODAY }
            Spacer(Modifier.width(8.dp))
            FilterChipPill("Gestern", dateFilter == DateFilter.YESTERDAY) { dateFilter = DateFilter.YESTERDAY }
        }
        // Empty-State-Präzedenz (exklusiv): unreachable → lädt → leer → gefiltert leer → Liste
        if (unreachable) {
            Column(Modifier.padding(top = 24.dp)) {
                Text("Speicherordner nicht erreichbar", color = Bor.accent, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenSettings) { Text("Zu den Einstellungen", color = Bor.textSecondary) }
            }
        } else if (loaded && entries.isEmpty()) {
            Text("Noch keine Aufnahmen.", color = Bor.textSecondary,
                modifier = Modifier.padding(top = 24.dp))
        } else if (loaded && visible.isEmpty()) {
            Text("Keine Treffer.", color = Bor.textSecondary,
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
private fun FilterChipPill(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Bor.textPrimary else Bor.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier
            .border(1.dp, if (active) Bor.accent else Bor.border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    )
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
    val time = Regex("""_(\d{2})-(\d{2})_BoR""").find(e.baseName)
        ?.let { "${it.groupValues[1]}:${it.groupValues[2]}" } ?: ""
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
                        time.takeIf { it.isNotEmpty() }?.let { "$it h" },
                        "Dauer ${formatMs(e.durationMs)}",
                        "${e.markerCount} ◆",
                    ).joinToString(" · "),
                    color = Bor.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp,
                )
            }
        }
    }
}
