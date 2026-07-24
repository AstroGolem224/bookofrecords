@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.claymachinegames.bookofrecords.ui

import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.data.LibraryStore
import com.claymachinegames.bookofrecords.data.LibraryUnavailableException
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.exportZip
import com.claymachinegames.bookofrecords.domain.DateFilter
import com.claymachinegames.bookofrecords.domain.dateFolder
import com.claymachinegames.bookofrecords.domain.downsamplePeaks
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.domain.matchesLibraryFilter
import com.claymachinegames.bookofrecords.domain.pseudoPeaks
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
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var unreachable by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var dateFilter by rememberSaveable { mutableStateOf(DateFilter.ALL) }
    var lastNewClick by remember { mutableStateOf(0L) }

    // Laden nur wenn die Seite gesettelt aktiv ist (Pager komponiert offscreen vor);
    // refreshToken deckt Aufnahme-Ende bei bereits sichtbarer Bibliothek ab.
    LaunchedEffect(store, refreshToken, isActive) {
        if (!isActive) return@LaunchedEffect
        loaded = false
        unreachable = false
        onSweep()
        runCatching { withContext(Dispatchers.IO) { store.list() } }
            .onSuccess { entries = it }
            .onFailure { e ->
                if (e is LibraryUnavailableException) unreachable = true else throw e
            }
        loaded = true
    }

    // Unsichtbare Selection darf weder Back noch einen späteren Export übernehmen.
    LaunchedEffect(isActive) {
        if (!isActive) {
            selected = emptySet()
            selectionMode = false
            showBatchDelete = false
        }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val chosen = entries.filter { it.audioUri in selected }
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

    // Ticker korrigiert Filter über Mitternacht und Zeitzonenwechsel hinweg.
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now().toString()
            delay(60_000)
        }
    }
    val yesterday = LocalDate.parse(today).minusDays(1).toString()
    val visible = entries.filter {
        matchesLibraryFilter(it.baseName, it.dateGroup, query, dateFilter, today, yesterday)
    }
    LaunchedEffect(visible) {
        val visibleUris = visible.map { it.audioUri }.toSet()
        if (!selected.all { it in visibleUris }) selected = selected intersect visibleUris
    }
    val groups = visible.groupBy { it.dateGroup }.entries.sortedByDescending { it.key }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(maxWidth.value / 412f, 1f)
        val compositionWidth = 412.dp * scale
        val listHeight = (maxHeight - 220.dp * scale - 108.dp * scale).coerceAtLeast(120.dp)

        Box(
            Modifier.width(compositionWidth).fillMaxHeight().align(Alignment.TopCenter)
        ) {
            LibraryHeader(
                selectionMode = selectionMode,
                onToggleSelection = {
                    if (selectionMode) selected = emptySet()
                    selectionMode = !selectionMode
                },
                onOpenSettings = onOpenSettings,
                scale = scale,
                modifier = Modifier.absoluteOffset(22.dp * scale, 30.dp * scale),
            )
            BorSearchField(
                value = query,
                onValueChange = { query = it },
                scale = scale,
                modifier = Modifier.absoluteOffset(22.dp * scale, 92.dp * scale)
                    .size(368.dp * scale, 56.dp * scale),
            )
            DateFilterRow(
                selected = dateFilter,
                onSelected = { dateFilter = it },
                scale = scale,
                modifier = Modifier.absoluteOffset(22.dp * scale, 162.dp * scale),
            )

            Box(
                Modifier.absoluteOffset(22.dp * scale, 220.dp * scale)
                    .size(368.dp * scale, listHeight)
            ) {
                when {
                    unreachable -> LibraryEmptyState(
                        "Speicherordner nicht erreichbar", onOpenSettings, scale
                    )
                    !loaded -> Unit
                    entries.isEmpty() -> LibraryEmptyState("Noch keine Aufnahmen.", null, scale)
                    visible.isEmpty() -> LibraryEmptyState("Keine Treffer.", null, scale)
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 122.dp * scale),
                    ) {
                        groups.forEach { (date, list) ->
                            item(key = "header-$date") {
                                Text(
                                    date.uppercase(),
                                    color = Bor.idleTextSecondary,
                                    fontFamily = BarlowSemiCondensed,
                                    fontSize = 11.sp * scale,
                                    lineHeight = 16.sp * scale,
                                    letterSpacing = 2.42.sp * scale,
                                    maxLines = 1,
                                    modifier = Modifier.height(26.dp * scale),
                                )
                            }
                            items(list, key = { it.audioUri }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    selectionMode = selectionMode,
                                    isSelected = entry.audioUri in selected,
                                    scale = scale,
                                    onOpen = onOpen,
                                    onToggle = {
                                        selected = if (entry.audioUri in selected) {
                                            selected - entry.audioUri
                                        } else {
                                            selected + entry.audioUri
                                        }
                                    },
                                    onLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectionMode = true
                                        selected = selected + entry.audioUri
                                    },
                                )
                                Spacer(Modifier.height(8.dp * scale))
                            }
                            item(key = "gap-$date") { Spacer(Modifier.height(12.dp * scale)) }
                        }
                    }
                }
            }

            if (selectionMode) {
                BatchActionDock(
                    count = selected.size,
                    scale = scale,
                    onZip = {
                        val chosen = visible.filter { it.audioUri in selected }
                        val name = if (chosen.size == 1) {
                            "${chosen.first().baseName}.zip"
                        } else {
                            "BookofRecords_${dateFolder(LocalDateTime.now())}_${chosen.size}.zip"
                        }
                        zipLauncher.launch(name)
                    },
                    onDelete = { showBatchDelete = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp * scale)
                        .size(344.dp * scale, 90.dp * scale),
                )
            } else {
                NewRecordingDock(
                    scale = scale,
                    onClick = {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNewClick >= 400) {
                            lastNewClick = now
                            onNewRecording()
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp * scale)
                        .size(344.dp * scale, 90.dp * scale),
                )
            }
        }
    }

    if (showBatchDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            containerColor = Bor.idleGlass.copy(alpha = 0.96f),
            title = {
                Text(
                    "$count Aufnahmen löschen?",
                    color = Bor.idleTextPrimary,
                    fontFamily = BarlowSemiCondensed,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = count > 0,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val chosen = entries.filter { it.audioUri in selected }
                        showBatchDelete = false
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { chosen.forEach(store::delete) }
                                onSweep()
                                withContext(Dispatchers.IO) { store.list() }
                            }.onSuccess {
                                entries = it
                                selected = emptySet()
                                selectionMode = false
                            }.onFailure {
                                Toast.makeText(
                                    context, "Löschen fehlgeschlagen", Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                ) { Text("Löschen", color = Bor.destructiveConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDelete = false }) {
                    Text("Abbrechen", color = Bor.idleTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun LibraryHeader(
    selectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onOpenSettings: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(368.dp * scale, 48.dp * scale)) {
        Text(
            "BIBLIOTHEK",
            color = Bor.idleTextPrimary,
            fontFamily = BebasNeue,
            fontSize = 38.sp * scale,
            lineHeight = 46.sp * scale,
            letterSpacing = 1.33.sp * scale,
            maxLines = 1,
        )
        TextButton(
            onClick = onToggleSelection,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.absoluteOffset(228.dp * scale, 0.dp)
                .size(84.dp * scale, 48.dp * scale),
        ) {
            Text(
                if (selectionMode) "FERTIG" else "AUSWÄHLEN",
                color = Bor.idleDockLabel,
                fontFamily = BarlowSemiCondensed,
                fontSize = 12.sp * scale,
                letterSpacing = 1.2.sp * scale,
                maxLines = 1,
            )
        }
        GlassSurface(
            scale = scale,
            radiusDp = 24f,
            modifier = Modifier.align(Alignment.TopEnd).size(48.dp * scale),
            borderStart = Bor.idleCyan.copy(alpha = 0.42f),
            borderEnd = Bor.idleViolet.copy(alpha = 0.42f),
        ) {
            Box(
                Modifier.fillMaxSize().combinedClickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Einstellungen",
                    tint = Bor.idleIcon.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp * scale),
                )
            }
        }
    }
}

@Composable
private fun BorSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        scale = scale,
        radiusDp = 18f,
        modifier = modifier,
        borderStart = Bor.idleCyan.copy(alpha = 0.58f),
        borderEnd = Bor.idleViolet.copy(alpha = 0.58f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon(scale)
            Spacer(Modifier.width(12.dp * scale))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(Bor.idleCyan),
                textStyle = TextStyle(
                    color = Bor.idleTextPrimary,
                    fontFamily = BarlowSemiCondensed,
                    fontSize = 18.sp * scale,
                    lineHeight = 24.sp * scale,
                    letterSpacing = 0.36.sp * scale,
                ),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "Suchen…",
                            color = Bor.idleTextSecondary,
                            fontFamily = BarlowSemiCondensed,
                            fontSize = 18.sp * scale,
                        )
                    }
                    inner()
                },
                modifier = Modifier.weight(1f),
            )
            if (value.isNotEmpty()) {
                Box(
                    Modifier.size(48.dp * scale).combinedClickable(onClick = { onValueChange("") }),
                    contentAlignment = Alignment.Center,
                ) {
                    CloseIcon(scale)
                }
            }
        }
    }
}

@Composable
private fun DateFilterRow(
    selected: DateFilter,
    onSelected: (DateFilter) -> Unit,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        FilterChipPill("ALLE", 76f, selected == DateFilter.ALL, scale) {
            onSelected(DateFilter.ALL)
        }
        FilterChipPill("HEUTE", 76f, selected == DateFilter.TODAY, scale) {
            onSelected(DateFilter.TODAY)
        }
        FilterChipPill("GESTERN", 88f, selected == DateFilter.YESTERDAY, scale) {
            onSelected(DateFilter.YESTERDAY)
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    width: Float,
    active: Boolean,
    scale: Float,
    onClick: () -> Unit,
) {
    GlassSurface(
        scale = scale,
        radiusDp = 19f,
        modifier = Modifier.size(width.dp * scale, 38.dp * scale),
        borderStart = if (active) Bor.idleAmber else Bor.idleCyan.copy(alpha = 0.42f),
        borderEnd = if (active) Bor.idleAmber else Bor.idleViolet.copy(alpha = 0.42f),
    ) {
        Box(
            Modifier.fillMaxSize().semantics { selected = active }
                .combinedClickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (active) Bor.idleTextPrimary else Bor.idleDockLabel,
                fontFamily = BarlowSemiCondensed,
                fontSize = 12.sp * scale,
                letterSpacing = 1.2.sp * scale,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    entry: RecordingEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    scale: Float,
    onOpen: (RecordingEntry) -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val title = titlePartOf(entry.baseName)?.takeIf { it.isNotEmpty() } ?: entry.baseName
    val time = Regex("""_(\d{2})-(\d{2})_BoR""").find(entry.baseName)
        ?.let { "${it.groupValues[1]}:${it.groupValues[2]}" }.orEmpty()
    val metadata = listOfNotNull(
        time.takeIf { it.isNotEmpty() }?.let { "$it h" },
        "Dauer ${formatMs(entry.durationMs)}",
        "${entry.markerCount}",
    ).joinToString(" · ")
    val peaks = remember(entry.audioUri, entry.peaks) {
        if (entry.peaks.isNotEmpty()) downsamplePeaks(entry.peaks, 34)
        else pseudoPeaks(entry.baseName, 34)
    }
    val interaction = remember { MutableInteractionSource() }

    GlassSurface(
        scale = scale,
        radiusDp = 16f,
        modifier = Modifier.fillMaxWidth().height(72.dp * scale)
            .semantics { selected = isSelected },
        borderStart = if (isSelected) Bor.idleAmber else Bor.idleCyan.copy(alpha = 0.42f),
        borderEnd = if (isSelected) Bor.idleAmber else Bor.idleViolet.copy(alpha = 0.42f),
    ) {
        Row(
            Modifier.fillMaxSize().combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = { if (selectionMode) onToggle() else onOpen(entry) },
                onLongClick = onLongPress,
            ).padding(horizontal = 14.dp * scale, vertical = 11.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                SelectionIndicator(isSelected, scale)
                Spacer(Modifier.width(10.dp * scale))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Bor.idleTextPrimary,
                    fontFamily = BarlowSemiCondensed,
                    fontSize = 16.sp * scale,
                    lineHeight = 21.sp * scale,
                    letterSpacing = 0.16.sp * scale,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp * scale))
                Text(
                    metadata,
                    color = Bor.idleTextSecondary,
                    fontFamily = BarlowSemiCondensed,
                    fontSize = 12.sp * scale,
                    lineHeight = 17.sp * scale,
                    letterSpacing = 0.5.sp * scale,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp * scale))
            PreviewWaveform(
                peaks = peaks,
                scale = scale,
                modifier = Modifier.size(116.dp * scale, 30.dp * scale),
            )
        }
    }
}

@Composable
private fun PreviewWaveform(peaks: List<Float>, scale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val centerY = size.height / 2f
        val step = size.width / peaks.size
        val brush = Brush.horizontalGradient(
            listOf(Bor.idleCyan, Bor.idleBlue, Bor.idleViolet),
            startX = 0f,
            endX = size.width,
        )
        peaks.forEachIndexed { index, peak ->
            val halfHeight = (3f + 10f * peak).dp.toPx() * scale
            val x = (index + 0.5f) * step
            drawLine(
                brush,
                Offset(x, centerY - halfHeight),
                Offset(x, centerY + halfHeight),
                strokeWidth = 1.dp.toPx() * scale,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, scale: Float) {
    Canvas(Modifier.size(24.dp * scale)) {
        drawCircle(
            if (selected) Bor.idleAmber else Bor.idleOutline.copy(alpha = 0.55f),
            style = Stroke(1.5.dp.toPx() * scale),
        )
        if (selected) {
            drawCircle(Bor.idleAmber, radius = 7.dp.toPx() * scale)
        }
    }
}

@Composable
private fun NewRecordingDock(scale: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(
        scale, 22f, modifier,
        Bor.idleCyan.copy(alpha = .72f), Bor.idleViolet.copy(alpha = .72f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassSurface(
                scale = scale,
                radiusDp = 29f,
                modifier = Modifier.size(220.dp * scale, 58.dp * scale),
                borderStart = Bor.idleAmber,
                borderEnd = Bor.idleAmberHi,
            ) {
                Row(
                    Modifier.fillMaxSize().semantics { contentDescription = "Neue Aufnahme starten" }
                        .combinedClickable(role = Role.Button, onClick = onClick),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlusInCircleIcon(scale)
                    Spacer(Modifier.width(10.dp * scale))
                    Text(
                        "NEUE AUFNAHME",
                        color = Bor.idleAmber,
                        fontFamily = BarlowSemiCondensed,
                        fontSize = 12.sp * scale,
                        letterSpacing = 1.2.sp * scale,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchActionDock(
    count: Int,
    scale: Float,
    onZip: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        scale, 22f, modifier,
        Bor.idleCyan.copy(alpha = .72f), Bor.idleViolet.copy(alpha = .72f),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            DockCell(
                label = "ZIP",
                enabled = count > 0,
                scale = scale,
                icon = { ZipIcon(scale) },
                onClick = onZip,
                modifier = Modifier.weight(1f),
            )
            Canvas(Modifier.width(1.dp).height(54.dp * scale)) {
                drawRect(Bor.idleOutline.copy(alpha = 0.28f))
            }
            DockCell(
                label = "LÖSCHEN",
                enabled = count > 0,
                scale = scale,
                labelColor = Bor.destructiveIdle,
                icon = { DeleteOutlineIcon(scale, Bor.destructiveIdle) },
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockCell(
    label: String,
    enabled: Boolean,
    scale: Float,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = Bor.idleDockLabel,
) {
    Column(
        modifier.fillMaxHeight().alpha(if (enabled) 1f else 0.38f)
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(7.dp * scale))
        Text(
            label,
            color = labelColor,
            fontFamily = BarlowSemiCondensed,
            fontSize = 12.sp * scale,
            letterSpacing = 1.2.sp * scale,
            maxLines = 1,
        )
    }
}

@Composable
private fun LibraryEmptyState(text: String, action: (() -> Unit)?, scale: Float) {
    Column(Modifier.padding(top = 20.dp * scale)) {
        Text(
            text,
            color = Bor.idleTextSecondary,
            fontFamily = BarlowSemiCondensed,
            fontSize = 14.sp * scale,
        )
        if (action != null) {
            TextButton(onClick = action) {
                Text("ZU DEN EINSTELLUNGEN", color = Bor.idleCyan)
            }
        }
    }
}

@Composable
private fun SearchIcon(scale: Float) {
    Canvas(Modifier.size(22.dp * scale)) {
        val stroke = 1.7.dp.toPx() * scale
        drawCircle(
            Bor.idleIcon.copy(alpha = 0.9f),
            radius = size.minDimension * 0.29f,
            center = Offset(size.width * 0.43f, size.height * 0.43f),
            style = Stroke(stroke),
        )
        drawLine(
            Bor.idleIcon.copy(alpha = 0.9f),
            Offset(size.width * 0.64f, size.height * 0.64f),
            Offset(size.width * 0.88f, size.height * 0.88f),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun CloseIcon(scale: Float) {
    Canvas(Modifier.size(18.dp * scale)) {
        val color = Bor.idleIcon.copy(alpha = 0.9f)
        val stroke = 1.6.dp.toPx() * scale
        drawLine(color, Offset(size.width * .25f, size.height * .25f),
            Offset(size.width * .75f, size.height * .75f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .75f, size.height * .25f),
            Offset(size.width * .25f, size.height * .75f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun PlusInCircleIcon(scale: Float) {
    Canvas(Modifier.size(18.dp * scale)) {
        drawCircle(Bor.idleAmber)
        val stroke = 1.6.dp.toPx() * scale
        drawLine(Bor.idleGlass, Offset(size.width * .28f, center.y),
            Offset(size.width * .72f, center.y), stroke, StrokeCap.Round)
        drawLine(Bor.idleGlass, Offset(center.x, size.height * .28f),
            Offset(center.x, size.height * .72f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ZipIcon(scale: Float) {
    Canvas(Modifier.size(26.dp * scale)) {
        val color = Bor.idleIcon.copy(alpha = 0.9f)
        val stroke = Stroke(1.5.dp.toPx() * scale, cap = StrokeCap.Round)
        drawRoundRect(
            color,
            topLeft = Offset(size.width * .22f, size.height * .08f),
            size = androidx.compose.ui.geometry.Size(size.width * .56f, size.height * .84f),
            cornerRadius = CornerRadius(2.dp.toPx() * scale),
            style = stroke,
        )
        repeat(4) { i ->
            val y = size.height * (.18f + i * .14f)
            drawLine(color, Offset(center.x - size.width * .07f, y),
                Offset(center.x + size.width * .07f, y), stroke.width, StrokeCap.Round)
        }
    }
}

@Composable
fun DeleteOutlineIcon(scale: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp * scale)) {
        val stroke = 1.55.dp.toPx() * scale
        drawLine(color, Offset(size.width * .24f, size.height * .25f),
            Offset(size.width * .76f, size.height * .25f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .40f, size.height * .15f),
            Offset(size.width * .60f, size.height * .15f), stroke, StrokeCap.Round)
        val body = Path().apply {
            moveTo(size.width * .30f, size.height * .31f)
            lineTo(size.width * .35f, size.height * .84f)
            lineTo(size.width * .65f, size.height * .84f)
            lineTo(size.width * .70f, size.height * .31f)
        }
        drawPath(body, color, style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(size.width * .44f, size.height * .40f),
            Offset(size.width * .44f, size.height * .72f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .56f, size.height * .40f),
            Offset(size.width * .56f, size.height * .72f), stroke, StrokeCap.Round)
    }
}
