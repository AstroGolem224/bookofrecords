package com.claymachinegames.bookofrecords.ui

import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import com.claymachinegames.bookofrecords.domain.formatMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetailScreen(repo: RecordingRepository, entry: RecordingEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var meta by remember { mutableStateOf<RecordingMeta?>(null) }
    var selected by remember { mutableIntStateOf(-1) }
    var positionMs by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(entry.baseName) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val metaWriter = remember { Dispatchers.IO.limitedParallelism(1) }

    val player = remember {
        runCatching {
            MediaPlayer().apply {
                setDataSource(context, entry.audioUri)
                prepare()
                setOnCompletionListener { playing = false }
            }
        }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    LaunchedEffect(player) {
        if (player == null) {
            Toast.makeText(context, "Datei nicht lesbar", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        meta = withContext(Dispatchers.IO) { entry.metaUri?.let { repo.readMeta(it) } }
            ?: RecordingMeta(file = "${entry.baseName}.m4a", startedAt = "",
                             durationMs = entry.durationMs)
    }

    if (player == null) return

    LaunchedEffect(playing) {
        while (playing) { positionMs = player.currentPosition; delay(250) }
    }

    fun saveMeta(updated: RecordingMeta) {
        meta = updated
        entry.metaUri?.let { uri ->
            scope.launch(metaWriter) { runCatching { repo.writeMeta(uri, updated) } }
        }
    }
    fun setLabel(index: Int, label: String, type: String) {
        val m = meta ?: return
        val markers = m.markers.toMutableList()
        markers[index] = markers[index].copy(label = label, type = type)
        saveMeta(m.copy(markers = markers))
    }

    val duration = player.duration.coerceAtLeast(1)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(entry.baseName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        // --- Playback ---
        Text("${formatMs(positionMs.toLong())} / ${formatMs(duration.toLong())}")
        Slider(
            value = positionMs.toFloat() / duration,
            onValueChange = {
                positionMs = (it * duration).toInt()
                player.seekTo(positionMs)
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (playing) player.pause() else player.start()
                playing = !playing
            }) { Text(if (playing) "Pause" else "Play") }
            OutlinedButton(onClick = {
                meta?.let { m ->
                    val uri = repo.exportLabels(entry, m)
                    Toast.makeText(context, "Exportiert: $uri", Toast.LENGTH_SHORT).show()
                }
            }) { Text(".txt") }
            OutlinedButton(onClick = {
                val uris = arrayListOf(entry.audioUri)
                entry.metaUri?.let { uris.add(it) }
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Teilen"))
            }) { Text("Teilen") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showRename = true }) { Text("Umbenennen") }
            TextButton(onClick = { showDelete = true }) { Text("Löschen") }
        }

        Spacer(Modifier.height(16.dp))

        // --- Speaker chips: bereits vergebene Namen schnell zuweisen ---
        val speakerNames = meta?.markers
            ?.filter { it.type == "speaker" && it.label.isNotBlank() }
            ?.map { it.label }?.distinct().orEmpty()
        if (selected >= 0 && speakerNames.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                speakerNames.forEach { name ->
                    SuggestionChip(
                        onClick = { setLabel(selected, name, "speaker") },
                        label = { Text(name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }

        // --- Marker list ---
        var editText by remember { mutableStateOf("") }
        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(meta?.markers.orEmpty()) { i, m ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable {
                            selected = if (selected == i) -1 else i
                            editText = m.label
                        },
                    colors = if (selected == i)
                        CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)
                    else CardDefaults.cardColors(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatMs(m.timeMs),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clickable {
                                    player.seekTo(m.timeMs.toInt())
                                    positionMs = m.timeMs.toInt()
                                },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(m.label.ifEmpty { "(unbenannt)" })
                        }
                        if (selected == i) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                label = { Text("Sprecher / Notiz") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(onClick = {
                                setLabel(i, editText.trim(), "speaker")
                                selected = -1
                            }) { Text("Speichern") }
                        }
                    }
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Aufnahme löschen?") },
            text = { Text(entry.baseName) },
            confirmButton = {
                TextButton(onClick = {
                    playing = false
                    player.release()
                    repo.delete(entry)
                    onClose()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Abbrechen") } },
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Umbenennen") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it })
            },
            confirmButton = {
                TextButton(onClick = {
                    val newBase = renameText.trim()
                    if (newBase.isNotEmpty() && newBase != entry.baseName) {
                        runCatching { repo.rename(entry, newBase) }.onFailure {
                            Toast.makeText(context, "Umbenennen fehlgeschlagen", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showRename = false
                    onClose()   // zurück zur Library, die neu lädt
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Abbrechen") } },
        )
    }
}
