package com.claymachinegames.bookofrecords.ui

import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.data.LibraryStore
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.domain.sanitizeTitle
import com.claymachinegames.bookofrecords.domain.titlePartOf
import com.claymachinegames.bookofrecords.domain.withTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun DetailScreen(store: LibraryStore, entry: RecordingEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val metaWriter = remember { Dispatchers.IO.limitedParallelism(1) }

    val editor = remember {
        MarkerEditor(initialMeta = null) { updated ->
            entry.metaUri?.let { uri ->
                scope.launch(metaWriter) { runCatching { store.writeMeta(uri, updated) } }
            }
        }
    }
    var positionMs by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showNewChip by remember { mutableStateOf(false) }
    var deleteMarkerIndex by remember { mutableStateOf<Int?>(null) }

    val displayTitle = titlePartOf(entry.baseName)?.takeIf { it.isNotEmpty() } ?: entry.baseName
    var renameText by remember {
        mutableStateOf(titlePartOf(entry.baseName) ?: entry.baseName)
    }

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
        val loaded = withContext(Dispatchers.IO) { entry.metaUri?.let { store.readMeta(it) } }
            ?: RecordingMeta(file = "${entry.baseName}.m4a", startedAt = "",
                             durationMs = entry.durationMs)
        editor.updateMeta(loaded)
    }
    if (player == null) return
    LaunchedEffect(playing) {
        while (playing) { positionMs = player.currentPosition; delay(250) }
    }

    DisposableEffect(Unit) { onDispose { editor.commitPending() } }

    val duration = player.duration.coerceAtLeast(1)

    Column(Modifier.fillMaxSize().background(Bor.bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Bor.textSecondary)
            }
            Text(displayTitle, color = Bor.textPrimary, fontSize = 16.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Filled.Edit, "Umbenennen", tint = Bor.textMuted)
            }
        }
        Text("${entry.baseName}.m4a", color = Bor.textMuted,
            fontFamily = FontFamily.Monospace, fontSize = 10.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))

        Text("${formatMs(positionMs.toLong())} / ${formatMs(duration.toLong())}",
            color = Bor.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))

        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val w = maxWidth
            Slider(
                value = positionMs.toFloat() / duration,
                onValueChange = {
                    positionMs = (it * duration).toInt()
                    player.seekTo(positionMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Bor.accent, activeTrackColor = Bor.accent,
                    inactiveTrackColor = Bor.borderSubtle),
            )
            editor.meta?.markers?.forEach { m ->
                val frac = (m.timeMs.toFloat() / duration).coerceIn(0f, 1f)
                Box(
                    Modifier.offset(x = w * frac - 6.dp, y = 5.dp)
                        .size(12.dp, 16.dp)
                        .clickable {
                            player.seekTo(m.timeMs.toInt())
                            positionMs = m.timeMs.toInt()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(2.dp, 10.dp).background(Bor.levelAmber))
                }
            }
        }

        Row(
            Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                positionMs = (positionMs - 30_000).coerceAtLeast(0)
                player.seekTo(positionMs)
            }) { Text("−30s", color = Bor.textSecondary) }
            IconButton(
                onClick = {
                    if (playing) player.pause() else player.start()
                    playing = !playing
                },
                modifier = Modifier.size(52.dp).background(Bor.accent, CircleShape),
            ) {
                if (playing) Text("❚❚", color = Bor.onAccent, fontSize = 14.sp)
                else Icon(Icons.Filled.PlayArrow, "Play", tint = Bor.onAccent)
            }
            TextButton(onClick = {
                positionMs = (positionMs + 30_000).coerceAtMost(duration)
                player.seekTo(positionMs)
            }) { Text("+30s", color = Bor.textSecondary) }
        }

        Row(Modifier.padding(vertical = 10.dp)) {
            Chip("+ Neu", dashed = true) { showNewChip = true }
        }

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(editor.meta?.markers.orEmpty()) { i, m ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .background(Bor.surface, RoundedCornerShape(8.dp))
                        .border(1.dp, if (editor.selected == i) Bor.accent else Bor.borderSubtle,
                            RoundedCornerShape(8.dp))
                        .clickable { editor.select(i) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatMs(m.timeMs), color = Bor.accent,
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                player.seekTo(m.timeMs.toInt())
                                positionMs = m.timeMs.toInt()
                            })
                        Spacer(Modifier.width(10.dp))
                        Text(m.label.ifEmpty { "(unbenannt)" },
                            color = if (m.label.isEmpty()) Bor.textMuted else Bor.textPrimary,
                            fontSize = 13.sp)
                    }
                    if (editor.selected == i) {
                        OutlinedTextField(
                            value = editor.editText,
                            onValueChange = { editor.editText = it },
                            label = { Text("Sprecher / Notiz", color = Bor.textMuted) },
                            colors = borFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // select(i) on the already-selected row toggles it closed, which
                            // commits the pending edit first — see MarkerEditor.select().
                            TextButton(onClick = { editor.select(i) }) {
                                Text("Speichern", color = Bor.accent)
                            }
                            IconButton(onClick = { deleteMarkerIndex = i }) {
                                Icon(Icons.Filled.Delete, "Marker löschen", tint = Bor.textMuted)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Bor.borderSubtle)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = {
                editor.meta?.let { m ->
                    val uri = store.exportLabels(entry, m)
                    Toast.makeText(context, "Exportiert: $uri", Toast.LENGTH_SHORT).show()
                }
            }) { Text(".txt", color = Bor.textSecondary, fontFamily = FontFamily.Monospace) }
            IconButton(onClick = {
                val uris = arrayListOf(entry.audioUri)
                entry.metaUri?.let { uris.add(it) }
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Teilen"))
            }) { Icon(Icons.Filled.Share, "Teilen", tint = Bor.textSecondary) }
            IconButton(onClick = { showDelete = true }) {
                Icon(Icons.Filled.Delete, "Löschen", tint = Bor.textSecondary)
            }
        }
    }

    if (showNewChip) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewChip = false },
            containerColor = Bor.surface,
            title = { Text("Neuer Marker", color = Bor.textPrimary) },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it },
                    colors = borFieldColors(), singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    editor.addMarker(timeMs = positionMs.toLong(), label = newName)
                    showNewChip = false
                }) { Text("Anlegen", color = Bor.accent) }
            },
            dismissButton = { TextButton(onClick = { showNewChip = false }) {
                Text("Abbrechen", color = Bor.textSecondary) } },
        )
    }

    if (deleteMarkerIndex != null) {
        val index = deleteMarkerIndex!!
        val label = editor.meta?.markers?.getOrNull(index)?.label?.ifEmpty { "(unbenannt)" } ?: ""
        AlertDialog(
            onDismissRequest = { deleteMarkerIndex = null },
            containerColor = Bor.surface,
            title = { Text("Marker löschen?", color = Bor.textPrimary) },
            text = { Text(label, color = Bor.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    editor.deleteMarker(index)
                    deleteMarkerIndex = null
                }) { Text("Löschen", color = Bor.accent) }
            },
            dismissButton = { TextButton(onClick = { deleteMarkerIndex = null }) {
                Text("Abbrechen", color = Bor.textSecondary) } },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Bor.surface,
            title = { Text("Aufnahme löschen?", color = Bor.textPrimary) },
            text = { Text(displayTitle, color = Bor.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    playing = false
                    player.release()
                    store.delete(entry)
                    onClose()
                }) { Text("Löschen", color = Bor.accent) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) {
                Text("Abbrechen", color = Bor.textSecondary) } },
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = Bor.surface,
            title = { Text("Umbenennen", color = Bor.textPrimary) },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it },
                    colors = borFieldColors(), singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val isBoR = titlePartOf(entry.baseName) != null
                    val newBase = if (isBoR) withTitle(entry.baseName, renameText)
                                  else sanitizeTitle(renameText)
                    if (newBase.isNotEmpty() && newBase != entry.baseName) {
                        runCatching { store.rename(entry, newBase) }.onFailure {
                            Toast.makeText(context, "Umbenennen fehlgeschlagen",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                    showRename = false
                    onClose()
                }) { Text("Speichern", color = Bor.accent) }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) {
                Text("Abbrechen", color = Bor.textSecondary) } },
        )
    }
}

@Composable
private fun Chip(label: String, dashed: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (dashed) Bor.textMuted else Bor.textPrimary,
        fontSize = 12.sp,
        modifier = Modifier.padding(end = 8.dp)
            .border(1.dp, Bor.borderStrong, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun borFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Bor.textPrimary, unfocusedTextColor = Bor.textPrimary,
    focusedContainerColor = Bor.bg, unfocusedContainerColor = Bor.bg,
    focusedBorderColor = Bor.borderStrong, unfocusedBorderColor = Bor.border,
    cursorColor = Bor.accent,
)
