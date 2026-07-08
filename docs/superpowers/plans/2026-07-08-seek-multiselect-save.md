# Seek Buttons + Library Multi-Select Save Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 30s seek buttons to `DetailScreen`, a multi-select mode to `LibraryScreen`, and a "Speichern" action that zips selected recordings' audio+JSON+labels and hands them to the system's single-file save picker (works with Google Drive).

**Architecture:** Two new files carry the zip logic — `domain/ExportZip.kt` (pure, JVM-testable byte-bundling) and `data/ExportBundle.kt` (thin Android-side glue reading bytes via `ContentResolver`/`LibraryStore`). `DetailScreen.kt` and `LibraryScreen.kt` get full-file replacements for their UI changes, following the pattern already used in this repo for prior UI-wiring tasks.

**Tech Stack:** Kotlin, Jetpack Compose, `java.util.zip` (JDK, no new dependency), `ActivityResultContracts.CreateDocument` (already-available `androidx.activity:activity-compose`), existing Robolectric/`FakeDocumentsProvider` test infra.

---

## Task 1: 30-second seek buttons

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/ui/DetailScreen.kt`

- [ ] **Step 1: Replace the play/pause Row**

Find this block in `DetailScreen.kt`:

```kotlin
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
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
        }
```

Replace it with:

```kotlin
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
```

No new imports needed — `TextButton`, `Arrangement` are already imported in this file.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/ui/DetailScreen.kt
git commit -m "feat: add 30s seek buttons to DetailScreen playback controls"
```

---

## Task 2: Pure zip-bundling function (TDD)

**Files:**
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/domain/ExportZip.kt`
- Test: `app/src/test/java/com/claymachinegames/bookofrecords/domain/ExportZipTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.claymachinegames.bookofrecords.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ExportZipTest {

    private fun readZip(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes().decodeToString()
                entry = zip.nextEntry
            }
        }
        return result
    }

    @Test
    fun buildZipRoundTripsSingleEntry() {
        val zipped = buildZip(listOf("2026-07-08/a.m4a" to "audio-bytes".toByteArray()))

        val read = readZip(zipped)

        assertEquals(setOf("2026-07-08/a.m4a"), read.keys)
        assertEquals("audio-bytes", read["2026-07-08/a.m4a"])
    }

    @Test
    fun buildZipRoundTripsMultipleEntriesWithDateGroupPrefixedPaths() {
        val zipped = buildZip(listOf(
            "2026-07-08/a.m4a" to "audio-a".toByteArray(),
            "2026-07-08/a.json" to "{}".toByteArray(),
            "2026-07-07/b.m4a" to "audio-b".toByteArray(),
        ))

        val read = readZip(zipped)

        assertEquals(3, read.size)
        assertEquals("audio-a", read["2026-07-08/a.m4a"])
        assertEquals("{}", read["2026-07-08/a.json"])
        assertEquals("audio-b", read["2026-07-07/b.m4a"])
    }

    @Test
    fun buildZipOnEmptyListProducesValidEmptyZip() {
        val zipped = buildZip(emptyList())

        assertTrue(readZip(zipped).isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*.ExportZipTest"`
Expected: compile FAIL, `buildZip` not defined yet.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.claymachinegames.bookofrecords.domain

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Bundles (path, bytes) pairs into one zip archive's raw bytes. */
fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray {
    val buffer = ByteArrayOutputStream()
    ZipOutputStream(buffer).use { zip ->
        entries.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return buffer.toByteArray()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*.ExportZipTest"`
Expected: PASS, 3/3.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/domain/ExportZip.kt app/src/test/java/com/claymachinegames/bookofrecords/domain/ExportZipTest.kt
git commit -m "feat: add pure buildZip() for bundling export files"
```

---

## Task 3: Android-side export bundling

**Files:**
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/data/ExportBundle.kt`
- Test: `app/src/test/java/com/claymachinegames/bookofrecords/data/ExportBundleTest.kt`

This is the thin glue that reads each `RecordingEntry`'s audio bytes (via `ContentResolver`) and metadata (via `LibraryStore.readMeta`), then hands `(path, bytes)` pairs to `buildZip()` from Task 2.

- [ ] **Step 1: Write the failing test**

This reuses the existing Robolectric test infrastructure (`FakeDocumentsProvider`, `ShadowDocumentsAwareContentResolver`) already present in `app/src/test/java/com/claymachinegames/bookofrecords/data/SafLibraryTest.kt` — same package, so both are visible here without imports (same pattern `MoverRobolectricTest.kt` already uses).

```kotlin
package com.claymachinegames.bookofrecords.data

import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import com.claymachinegames.bookofrecords.domain.Marker
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowDocumentsAwareContentResolver::class])
class ExportBundleTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var library: SafLibrary

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.rootDir = tempFolder.root
        val providerInfo = ProviderInfo().apply {
            authority = FakeDocumentsProvider.AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        }
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(providerInfo)
        library = SafLibrary(context, FakeDocumentsProvider.treeUri())
    }

    @Test
    fun exportZipBytesBundlesAudioJsonAndLabelsPerEntry() = runBlocking {
        val dir = File(tempFolder.root, "2026-07-08").apply { mkdirs() }
        File(dir, "a.m4a").writeText("audio-bytes")
        val meta = RecordingMeta(
            file = "a.m4a", startedAt = "x",
            markers = listOf(Marker(timeMs = 1000, label = "Alice")),
        )
        File(dir, "a.json").writeText(meta.toJson())
        val entry = library.list().first()

        val zipBytes = exportZipBytes(context, library, listOf(entry))

        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) { names.add(e.name); e = zip.nextEntry }
        }
        assertEquals(
            setOf("2026-07-08/a.m4a", "2026-07-08/a.json", "2026-07-08/a.labels.txt"),
            names,
        )
    }

    @Test
    fun exportZipBytesBundlesMultipleEntriesUnderTheirOwnDateGroups() = runBlocking {
        val dirA = File(tempFolder.root, "2026-07-08").apply { mkdirs() }
        File(dirA, "a.m4a").writeText("audio-a")
        File(dirA, "a.json").writeText(RecordingMeta(file = "a.m4a", startedAt = "x").toJson())
        val dirB = File(tempFolder.root, "2026-07-07").apply { mkdirs() }
        File(dirB, "b.m4a").writeText("audio-b")
        File(dirB, "b.json").writeText(RecordingMeta(file = "b.m4a", startedAt = "x").toJson())
        val entries = library.list()
        assertEquals(2, entries.size)

        val zipBytes = exportZipBytes(context, library, entries)

        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) { names.add(e.name); e = zip.nextEntry }
        }
        assertEquals(
            setOf(
                "2026-07-08/a.m4a", "2026-07-08/a.json", "2026-07-08/a.labels.txt",
                "2026-07-07/b.m4a", "2026-07-07/b.json", "2026-07-07/b.labels.txt",
            ),
            names,
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*.ExportBundleTest"`
Expected: compile FAIL, `exportZipBytes` not defined yet.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.claymachinegames.bookofrecords.data

import android.content.Context
import com.claymachinegames.bookofrecords.domain.buildZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundles [entries]' audio + JSON + Audacity labels into one zip's raw bytes. */
suspend fun exportZipBytes(context: Context, store: LibraryStore, entries: List<RecordingEntry>): ByteArray =
    withContext(Dispatchers.IO) {
        val zipEntries = entries.flatMap { entry ->
            val audioBytes = context.contentResolver.openInputStream(entry.audioUri)!!.use { it.readBytes() }
            val meta = entry.metaUri?.let { store.readMeta(it) }
            buildList {
                add("${entry.dateGroup}/${entry.baseName}.m4a" to audioBytes)
                meta?.let {
                    add("${entry.dateGroup}/${entry.baseName}.json" to it.toJson().toByteArray())
                    add("${entry.dateGroup}/${entry.baseName}.labels.txt" to it.toAudacityLabels().toByteArray())
                }
            }
        }
        buildZip(zipEntries)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*.ExportBundleTest"`
Expected: PASS, 2/2.

- [ ] **Step 5: Full suite check**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, no regressions.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/data/ExportBundle.kt app/src/test/java/com/claymachinegames/bookofrecords/data/ExportBundleTest.kt
git commit -m "feat: add exportZipBytes() to bundle recording entries for saving"
```

---

## Task 4: Library multi-select mode + Save action

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/ui/LibraryScreen.kt`

Replace the **entire file contents** with:

```kotlin
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
                val bytes = exportZipBytes(context, store, chosen)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(context, "Gespeichert", Toast.LENGTH_SHORT).show()
            }
            selected = emptySet()
            selectionMode = false
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
```

Note the structural change to `EntryCard`: the outer container changed from `Column` to `Row` (checkbox + inner `Column` for title/subtitle), needed to place the checkbox beside the existing text content. Behavior when `selectionMode` is false is unchanged (no checkbox rendered, tap opens Detail exactly as before).

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/ui/LibraryScreen.kt
git commit -m "feat: add multi-select mode + zip-based Save action to LibraryScreen"
```

---

## Task 5: Device verification + push

**Files:** none (verification only)

- [ ] **Step 1: Full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass (existing + 3 new `ExportZipTest` + 2 new `ExportBundleTest`).

- [ ] **Step 2: Install and manually verify on emulator/device**

Run: `./gradlew installDebug`

Checklist:
- Open a recording in Detail — tap `-30s`/`+30s`, confirm playback position jumps correctly and clamps at `0:00`/end of track without crashing.
- Open Library, tap "Auswählen" — confirm top bar switches to "N ausgewählt" / "Fertig" / "Speichern", checkboxes appear on rows, tapping a row toggles its checkbox instead of opening Detail.
- Select 1 recording, tap "Speichern" — confirm the system save-document picker opens with a sensible default filename (`<baseName>.zip`), save to a local folder, confirm the zip contains `.m4a`/`.json`/`.labels.txt` under a date-folder path.
- Select 2+ recordings (can span different dates), tap "Speichern" — confirm default filename is `BookofRecords_<today>_<N>.zip`, confirm the saved zip contains all selected recordings' files under their respective date-folder paths.
- If a Drive account is configured on the test device: repeat the multi-select save targeting Google Drive as the destination in the system picker — confirm it succeeds (this is the capability this whole feature exists for).
- Tap "Fertig" and confirm selection clears and top bar returns to normal. Confirm system back button while in selection mode exits selection mode (does not navigate to Record screen).

- [ ] **Step 3: Push**

```bash
git push
```
