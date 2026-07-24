@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.claymachinegames.bookofrecords.ui

import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.data.LibraryStore
import com.claymachinegames.bookofrecords.data.PeakExtractor
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.domain.pseudoPeaks
import com.claymachinegames.bookofrecords.domain.sanitizeTitle
import com.claymachinegames.bookofrecords.domain.titlePartOf
import com.claymachinegames.bookofrecords.domain.withTitle
import com.claymachinegames.bookofrecords.record.RecState
import com.claymachinegames.bookofrecords.record.RecorderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun DetailScreen(store: LibraryStore, entry: RecordingEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
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
    var scrubbing by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showNewMarker by remember { mutableStateOf(false) }
    var markerEditorIndex by remember { mutableStateOf<Int?>(null) }
    var deleteMarkerIndex by remember { mutableStateOf<Int?>(null) }

    val displayTitle = titlePartOf(entry.baseName)?.takeIf { it.isNotEmpty() } ?: entry.baseName
    var renameText by remember {
        mutableStateOf(titlePartOf(entry.baseName)?.takeIf { it.isNotEmpty() } ?: entry.baseName)
    }
    val fallbackPeaks = remember(entry.audioUri) { pseudoPeaks(entry.audioUri.toString(), 104) }
    val peaks = editor.meta?.peaks?.takeIf { it.isNotEmpty() }
        ?: entry.peaks.takeIf { it.isNotEmpty() }
        ?: fallbackPeaks

    val player = remember {
        runCatching {
            MediaPlayer().apply {
                setDataSource(context, entry.audioUri)
                prepare()
                setOnCompletionListener {
                    playing = false
                    positionMs = duration.coerceAtLeast(0)
                }
            }
        }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }
    LaunchedEffect(player) {
        if (player == null) {
            Toast.makeText(context, "Datei nicht lesbar", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            entry.metaUri?.let { store.readMeta(it) }
        } ?: RecordingMeta(
            file = "${entry.baseName}.m4a",
            startedAt = "",
            durationMs = entry.durationMs,
        )
        editor.updateMeta(loaded)
        if (loaded.peaks.isEmpty() && RecorderState.state.value is RecState.Idle) {
            entry.metaUri?.let { metaUri ->
                scope.launch {
                    PeakExtractor.extractPeaks(context, entry.audioUri)?.let { extracted ->
                        // Marker können während des Decodes geändert werden: immer den neuesten
                        // Meta-Stand ergänzen, damit der Backfill nichts überschreibt.
                        val updated = (editor.meta ?: loaded).copy(peaks = extracted)
                        editor.updateMeta(updated)
                        withContext(metaWriter) {
                            runCatching { store.writeMeta(metaUri, updated) }
                        }
                    }
                }
            }
        }
    }
    if (player == null) return

    val duration = player.duration.coerceAtLeast(1)
    LaunchedEffect(playing, scrubbing) {
        while (playing && !scrubbing) {
            positionMs = player.currentPosition
            delay(50)
        }
    }
    DisposableEffect(Unit) {
        onDispose { editor.commitPending() }
    }

    fun seekTo(target: Int) {
        positionMs = target.coerceIn(0, duration)
        player.seekTo(positionMs)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(maxWidth.value / 412f, 1f)
        val compositionWidth = 412.dp * scale
        val markerListHeight =
            (maxHeight - 526.dp * scale - 148.dp * scale).coerceAtLeast(42.dp * scale)

        Box(
            Modifier.width(compositionWidth).fillMaxHeight().align(Alignment.TopCenter)
        ) {
            PlayerHeader(
                title = displayTitle,
                filename = "${entry.baseName}.m4a",
                onClose = onClose,
                onRename = { showRename = true },
                scale = scale,
                modifier = Modifier.absoluteOffset(22.dp * scale, 30.dp * scale),
            )
            PlayerWaveformCard(
                peaks = peaks,
                positionMs = positionMs,
                durationMs = duration,
                scale = scale,
                onScrubStart = { scrubbing = true },
                onScrub = { positionMs = it },
                onScrubEnd = {
                    player.seekTo(positionMs)
                    scrubbing = false
                },
                onSemanticSeek = { seekTo(it) },
                modifier = Modifier.absoluteOffset(22.dp * scale, 104.dp * scale)
                    .size(368.dp * scale, 220.dp * scale),
            )
            PlaybackCluster(
                playing = playing,
                scale = scale,
                onBack = { seekTo(positionMs - 30_000) },
                onToggle = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (playing) {
                        player.pause()
                    } else {
                        if (positionMs >= duration) seekTo(0)
                        player.start()
                    }
                    playing = !playing
                },
                onForward = { seekTo(positionMs + 30_000) },
                modifier = Modifier.absoluteOffset(52.dp * scale, 342.dp * scale)
                    .size(308.dp * scale, 100.dp * scale),
            )
            NewMarkerPill(
                scale = scale,
                onClick = { showNewMarker = true },
                modifier = Modifier.absoluteOffset(22.dp * scale, 464.dp * scale)
                    .size(156.dp * scale, 46.dp * scale),
            )
            LazyColumn(
                modifier = Modifier.absoluteOffset(22.dp * scale, 526.dp * scale)
                    .size(368.dp * scale, markerListHeight),
                contentPadding = PaddingValues(bottom = 8.dp * scale),
            ) {
                itemsIndexed(
                    items = editor.meta?.markers.orEmpty(),
                    key = { index, marker -> "${marker.timeMs}-$index" },
                ) { index, marker ->
                    MarkerRow(
                        time = formatMs(marker.timeMs),
                        label = marker.label.ifEmpty { "(unbenannt)" },
                        scale = scale,
                        onSeek = { seekTo(marker.timeMs.toInt()) },
                        onEdit = {
                            editor.select(index)
                            markerEditorIndex = index
                        },
                    )
                }
            }
            PlayerActionDock(
                scale = scale,
                onShare = {
                    val uris = arrayListOf(entry.audioUri)
                    entry.metaUri?.let { uris.add(it) }
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "*/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            "Teilen",
                        )
                    )
                },
                onDelete = { showDelete = true },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp * scale)
                    .size(344.dp * scale, 110.dp * scale),
            )
        }
    }

    if (showNewMarker) {
        var newName by remember { mutableStateOf("") }
        BorDialog(
            title = "Neuer Marker",
            textValue = newName,
            onTextChange = { newName = it },
            confirmLabel = "Anlegen",
            onDismiss = { showNewMarker = false },
            onConfirm = {
                editor.addMarker(positionMs.toLong(), newName)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                showNewMarker = false
            },
        )
    }

    if (markerEditorIndex != null) {
        val index = markerEditorIndex!!
        AlertDialog(
            onDismissRequest = {
                editor.commitPending()
                editor.select(index)
                markerEditorIndex = null
            },
            containerColor = Bor.idleGlass.copy(alpha = 0.96f),
            title = { Text("Marker bearbeiten", color = Bor.idleTextPrimary) },
            text = {
                BorDialogField(
                    value = editor.editText,
                    onValueChange = { editor.editText = it },
                    placeholder = "Sprecher / Notiz",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editor.commitPending()
                    editor.select(index)
                    markerEditorIndex = null
                }) { Text("Speichern", color = Bor.idleAmber) }
            },
            dismissButton = {
                TextButton(onClick = {
                    editor.commitPending()
                    editor.select(index)
                    markerEditorIndex = null
                    deleteMarkerIndex = index
                }) { Text("Löschen", color = Bor.destructiveIdle) }
            },
        )
    }

    if (deleteMarkerIndex != null) {
        val index = deleteMarkerIndex!!
        val label = editor.meta?.markers?.getOrNull(index)?.label?.ifEmpty { "(unbenannt)" }.orEmpty()
        AlertDialog(
            onDismissRequest = { deleteMarkerIndex = null },
            containerColor = Bor.idleGlass.copy(alpha = 0.96f),
            title = { Text("Marker löschen?", color = Bor.idleTextPrimary) },
            text = { Text(label, color = Bor.idleTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    editor.deleteMarker(index)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    deleteMarkerIndex = null
                }) { Text("Löschen", color = Bor.destructiveConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { deleteMarkerIndex = null }) {
                    Text("Abbrechen", color = Bor.idleTextSecondary)
                }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Bor.idleGlass.copy(alpha = 0.96f),
            title = { Text("Aufnahme löschen?", color = Bor.idleTextPrimary) },
            text = {
                Text(
                    "Aufnahme „$displayTitle“ löschen?",
                    color = Bor.idleTextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    playing = false
                    player.release()
                    store.delete(entry)
                    onClose()
                }) { Text("Löschen", color = Bor.destructiveConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Abbrechen", color = Bor.idleTextSecondary)
                }
            },
        )
    }

    if (showRename) {
        BorDialog(
            title = "Umbenennen",
            textValue = renameText,
            onTextChange = { renameText = it },
            confirmLabel = "Speichern",
            onDismiss = { showRename = false },
            onConfirm = {
                val isBoR = titlePartOf(entry.baseName) != null
                val newBase = if (isBoR) withTitle(entry.baseName, renameText)
                else sanitizeTitle(renameText)
                if (newBase.isNotEmpty() && newBase != entry.baseName) {
                    runCatching { store.rename(entry, newBase) }.onFailure {
                        Toast.makeText(
                            context, "Umbenennen fehlgeschlagen", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                showRename = false
                onClose()
            },
        )
    }
}

@Composable
private fun PlayerHeader(
    title: String,
    filename: String,
    onClose: () -> Unit,
    onRename: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(368.dp * scale, 48.dp * scale)) {
        CircleGlassButton(
            description = "Zurück",
            scale = scale,
            onClick = onClose,
            icon = { BackIcon(scale) },
            modifier = Modifier.align(Alignment.TopStart),
        )
        Column(
            Modifier.absoluteOffset(56.dp * scale, 0.dp)
                .width(244.dp * scale)
        ) {
            Text(
                title,
                color = Bor.idleTextPrimary,
                fontFamily = BarlowSemiCondensed,
                fontSize = 22.sp * scale,
                lineHeight = 28.sp * scale,
                letterSpacing = 0.22.sp * scale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                filename,
                color = Bor.idleTextSecondary,
                fontFamily = BarlowSemiCondensed,
                fontSize = 12.sp * scale,
                lineHeight = 17.sp * scale,
                letterSpacing = 0.5.sp * scale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleGlassButton(
            description = "Aufnahme umbenennen",
            scale = scale,
            onClick = onRename,
            icon = { EditIcon(scale) },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun CircleGlassButton(
    description: String,
    scale: Float,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        scale = scale,
        radiusDp = 24f,
        modifier = modifier.size(48.dp * scale),
        borderStart = Bor.idleCyan.copy(alpha = 0.42f),
        borderEnd = Bor.idleViolet.copy(alpha = 0.42f),
    ) {
        Box(
            Modifier.fillMaxSize().semantics { contentDescription = description }
                .combinedClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { icon() }
    }
}

@Composable
private fun PlayerWaveformCard(
    peaks: List<Float>,
    positionMs: Int,
    durationMs: Int,
    scale: Float,
    onScrubStart: () -> Unit,
    onScrub: (Int) -> Unit,
    onScrubEnd: () -> Unit,
    onSemanticSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        scale, 19f, modifier,
        Bor.idleCyan.copy(alpha = .72f), Bor.idleViolet.copy(alpha = .72f),
    ) {
        Canvas(
            Modifier.absoluteOffset(12.dp * scale, 25.dp * scale)
                .size(344.dp * scale, 127.dp * scale)
        ) {
            val centerY = size.height / 2f
            val step = size.width / peaks.size
            val brush = Brush.horizontalGradient(
                0f to Bor.idleCyan,
                0.26f to Color(0xFF22D3EE),
                0.48f to Bor.idleWaveHi,
                0.70f to Bor.idleVioletHi,
                1f to Bor.idleViolet,
                startX = 0f,
                endX = size.width,
            )
            peaks.forEachIndexed { index, peak ->
                val halfHeight = (2f + 38f * peak).dp.toPx() * scale
                val x = (index + .5f) * step
                drawLine(
                    brush,
                    Offset(x, centerY - halfHeight),
                    Offset(x, centerY + halfHeight),
                    strokeWidth = 1.7.dp.toPx() * scale,
                    cap = StrokeCap.Round,
                )
            }
            val fraction = positionMs.toFloat() / durationMs
            val x = fraction.coerceIn(0f, 1f) * size.width
            drawLine(
                Bor.idleAmber.copy(alpha = .45f),
                Offset(x, 0f),
                Offset(x, size.height),
                strokeWidth = 5.8.dp.toPx() * scale,
                cap = StrokeCap.Round,
            )
            drawLine(
                Bor.idleAmber,
                Offset(x, 0f),
                Offset(x, size.height),
                strokeWidth = 1.45.dp.toPx() * scale,
                cap = StrokeCap.Round,
            )
            drawCircle(Bor.idleAmberHi, 2.42.dp.toPx() * scale, Offset(x, 0f))
            drawCircle(Bor.idleAmberHi, 2.42.dp.toPx() * scale, Offset(x, size.height))
        }
        Row(
            Modifier.absoluteOffset(24.dp * scale, 174.dp * scale)
                .size(320.dp * scale, 22.dp * scale),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeLabel(formatMs(positionMs.toLong()), scale)
            TimeLabel(formatMs(durationMs.toLong()), scale)
        }
        Box(
            Modifier.fillMaxSize()
                .semantics {
                    contentDescription = "Wiedergabeposition"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = positionMs.toFloat(),
                        range = 0f..durationMs.toFloat(),
                    )
                    setProgress { target ->
                        onSemanticSeek(target.toInt().coerceIn(0, durationMs))
                        true
                    }
                }
                .pointerInput(durationMs, scale) {
                    fun update(x: Float) {
                        val waveLeft = 12.dp.toPx() * scale
                        val waveWidth = 344.dp.toPx() * scale
                        val fraction = ((x - waveLeft) / waveWidth).coerceIn(0f, 1f)
                        onScrub((fraction * durationMs).toInt())
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onScrubStart()
                            update(it.x)
                        },
                        onHorizontalDrag = { change, _ -> update(change.position.x) },
                        onDragEnd = onScrubEnd,
                        onDragCancel = onScrubEnd,
                    )
                }
        )
    }
}

@Composable
private fun TimeLabel(text: String, scale: Float) {
    Text(
        text,
        color = Bor.idleDockLabel,
        fontFamily = BarlowSemiCondensed,
        fontSize = 13.sp * scale,
        lineHeight = 18.sp * scale,
        letterSpacing = 1.82.sp * scale,
        maxLines = 1,
    )
}

@Composable
private fun PlaybackCluster(
    playing: Boolean,
    scale: Float,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        SeekButton(
            label = "−30s",
            description = "30 Sekunden zurück",
            scale = scale,
            start = Bor.idleCyan,
            end = Bor.idleOutline,
            onClick = onBack,
            modifier = Modifier.absoluteOffset(0.dp, 18.dp * scale),
        )
        PlayPauseButton(
            playing = playing,
            scale = scale,
            onClick = onToggle,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        SeekButton(
            label = "+30s",
            description = "30 Sekunden vor",
            scale = scale,
            start = Bor.idleOutline,
            end = Bor.idleViolet,
            onClick = onForward,
            modifier = Modifier.absoluteOffset(244.dp * scale, 18.dp * scale),
        )
    }
}

@Composable
private fun SeekButton(
    label: String,
    description: String,
    scale: Float,
    start: Color,
    end: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        scale = scale,
        radiusDp = 32f,
        modifier = modifier.size(64.dp * scale),
        borderStart = start.copy(alpha = .72f),
        borderEnd = end.copy(alpha = .72f),
    ) {
        Box(
            Modifier.fillMaxSize().semantics { contentDescription = description }
                .combinedClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = Bor.idleDockLabel,
                fontFamily = BarlowSemiCondensed,
                fontSize = 13.sp * scale,
                letterSpacing = .6.sp * scale,
            )
        }
    }
}

@Composable
private fun PlayPauseButton(
    playing: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(100.dp * scale).semantics {
            contentDescription = if (playing) "Pause" else "Wiedergabe"
        }.combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Bor.idleAmber.copy(alpha = .20f), Color.Transparent),
                    center = center,
                    radius = 50.dp.toPx() * scale,
                ),
                radius = 50.dp.toPx() * scale,
            )
            drawCircle(Bor.idleControl.copy(alpha = .82f), 46.dp.toPx() * scale)
            drawCircle(
                Bor.idleOutline.copy(alpha = .55f),
                46.dp.toPx() * scale,
                style = Stroke(1.dp.toPx() * scale),
            )
            drawCircle(
                Bor.idleAmber,
                41.dp.toPx() * scale,
                style = Stroke(2.dp.toPx() * scale),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Bor.idleAmberHi, Bor.idleAmber),
                    center = Offset(center.x - 8.dp.toPx() * scale, center.y - 8.dp.toPx() * scale),
                    radius = 36.dp.toPx() * scale,
                ),
                radius = 26.dp.toPx() * scale,
            )
            if (playing) {
                val barW = 5.dp.toPx() * scale
                val barH = 23.dp.toPx() * scale
                drawRoundRect(
                    Bor.idleGlass,
                    Offset(center.x - 8.dp.toPx() * scale, center.y - barH / 2),
                    Size(barW, barH),
                )
                drawRoundRect(
                    Bor.idleGlass,
                    Offset(center.x + 3.dp.toPx() * scale, center.y - barH / 2),
                    Size(barW, barH),
                )
            } else {
                val play = Path().apply {
                    moveTo(center.x - 7.dp.toPx() * scale, center.y - 12.dp.toPx() * scale)
                    lineTo(center.x + 12.dp.toPx() * scale, center.y)
                    lineTo(center.x - 7.dp.toPx() * scale, center.y + 12.dp.toPx() * scale)
                    close()
                }
                drawPath(play, Bor.idleGlass)
            }
        }
    }
}

@Composable
private fun NewMarkerPill(scale: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(
        scale = scale,
        radiusDp = 23f,
        modifier = modifier,
        borderStart = Bor.idleCyan.copy(alpha = .58f),
        borderEnd = Bor.idleViolet.copy(alpha = .58f),
    ) {
        Row(
            Modifier.fillMaxSize().semantics { contentDescription = "Neuen Marker anlegen" }
                .combinedClickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlusOutlineIcon(scale)
            Spacer(Modifier.width(8.dp * scale))
            Text(
                "NEUER MARKER",
                color = Bor.idleDockLabel,
                fontFamily = BarlowSemiCondensed,
                fontSize = 12.sp * scale,
                letterSpacing = 1.2.sp * scale,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MarkerRow(
    time: String,
    label: String,
    scale: Float,
    onSeek: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(42.dp * scale)
            .combinedClickable(onClick = onSeek, onLongClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            time,
            color = Bor.idleCyan,
            fontFamily = BarlowSemiCondensed,
            fontSize = 12.sp * scale,
            modifier = Modifier.width(58.dp * scale),
        )
        Text(
            label,
            color = Bor.idleTextPrimary,
            fontFamily = BarlowSemiCondensed,
            fontSize = 14.sp * scale,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.size(40.dp * scale).semantics { contentDescription = "Marker bearbeiten" }
                .combinedClickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) { OverflowIcon(scale) }
    }
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawRect(Bor.idleOutline.copy(alpha = .24f))
    }
}

@Composable
private fun PlayerActionDock(
    scale: Float,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        scale, 22f, modifier,
        Bor.idleCyan.copy(alpha = .72f), Bor.idleViolet.copy(alpha = .72f),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            PlayerDockCell(
                label = "TEILEN",
                scale = scale,
                icon = { ShareIcon(scale) },
                onClick = onShare,
                modifier = Modifier.weight(1f),
            )
            Canvas(Modifier.width(1.dp).height(62.dp * scale)) {
                drawRect(Bor.idleOutline.copy(alpha = .28f))
            }
            PlayerDockCell(
                label = "LÖSCHEN",
                scale = scale,
                color = Bor.destructiveIdle,
                icon = { DeleteOutlineIcon(scale, Bor.destructiveIdle) },
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlayerDockCell(
    label: String,
    scale: Float,
    color: Color = Bor.idleDockLabel,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxHeight().combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(9.dp * scale))
        Text(
            label,
            color = color,
            fontFamily = BarlowSemiCondensed,
            fontSize = 12.sp * scale,
            letterSpacing = 1.2.sp * scale,
        )
    }
}

@Composable
private fun BorDialog(
    title: String,
    textValue: String,
    onTextChange: (String) -> Unit,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bor.idleGlass.copy(alpha = .96f),
        title = { Text(title, color = Bor.idleTextPrimary) },
        text = {
            BorDialogField(
                value = textValue,
                onValueChange = onTextChange,
                placeholder = "Titel",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = Bor.idleAmber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = Bor.idleTextSecondary)
            }
        },
    )
}

@Composable
private fun BorDialogField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    GlassSurface(
        scale = 1f,
        radiusDp = 18f,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        borderStart = Bor.idleCyan.copy(alpha = .72f),
        borderEnd = Bor.idleViolet.copy(alpha = .72f),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(Bor.idleCyan),
            textStyle = TextStyle(
                color = Bor.idleTextPrimary,
                fontFamily = BarlowSemiCondensed,
                fontSize = 16.sp,
            ),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Bor.idleTextSecondary)
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun BackIcon(scale: Float) {
    Canvas(Modifier.size(24.dp * scale)) {
        val color = Bor.idleIcon.copy(alpha = .9f)
        val stroke = 1.8.dp.toPx() * scale
        drawLine(color, Offset(size.width * .2f, center.y), Offset(size.width * .82f, center.y),
            stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .2f, center.y), Offset(size.width * .45f, size.height * .25f),
            stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .2f, center.y), Offset(size.width * .45f, size.height * .75f),
            stroke, StrokeCap.Round)
    }
}

@Composable
private fun EditIcon(scale: Float) {
    Canvas(Modifier.size(22.dp * scale)) {
        val color = Bor.idleIcon.copy(alpha = .9f)
        val stroke = 2.dp.toPx() * scale
        drawLine(
            color,
            Offset(size.width * .25f, size.height * .75f),
            Offset(size.width * .72f, size.height * .28f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * .21f, size.height * .81f),
            Offset(size.width * .36f, size.height * .77f),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun PlusOutlineIcon(scale: Float) {
    Canvas(Modifier.size(20.dp * scale)) {
        val color = Bor.idleIcon.copy(alpha = .9f)
        val stroke = 1.7.dp.toPx() * scale
        drawLine(color, Offset(size.width * .2f, center.y), Offset(size.width * .8f, center.y),
            stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x, size.height * .2f), Offset(center.x, size.height * .8f),
            stroke, StrokeCap.Round)
    }
}

@Composable
private fun OverflowIcon(scale: Float) {
    Canvas(Modifier.size(20.dp * scale)) {
        repeat(3) { index ->
            drawCircle(
                Bor.idleIcon.copy(alpha = .9f),
                radius = 1.5.dp.toPx() * scale,
                center = Offset(size.width * (.25f + index * .25f), center.y),
            )
        }
    }
}

@Composable
private fun ShareIcon(scale: Float) {
    Canvas(Modifier.size(26.dp * scale)) {
        val color = Bor.idleIcon.copy(alpha = .9f)
        val stroke = 1.55.dp.toPx() * scale
        val left = Offset(size.width * .28f, center.y)
        val top = Offset(size.width * .72f, size.height * .24f)
        val bottom = Offset(size.width * .72f, size.height * .76f)
        drawLine(color, left, top, stroke, StrokeCap.Round)
        drawLine(color, left, bottom, stroke, StrokeCap.Round)
        drawCircle(color, 3.dp.toPx() * scale, left, style = Stroke(stroke))
        drawCircle(color, 3.dp.toPx() * scale, top, style = Stroke(stroke))
        drawCircle(color, 3.dp.toPx() * scale, bottom, style = Stroke(stroke))
    }
}
