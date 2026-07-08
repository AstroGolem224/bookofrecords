# Streaming ZIP Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the double in-memory buffering in the ZIP export path (`buildZip`'s `ByteArrayOutputStream` + `exportZipBytes`'s `.readBytes()`) with direct streaming into the destination `OutputStream`, so no full recording or full zip is ever held as a single in-memory `ByteArray`.

**Architecture:** `domain/ExportZip.kt`'s `buildZip(): ByteArray` becomes `writeZip(output, entries)`, writing into a caller-supplied `OutputStream` where each entry's content is a lazily-opened `InputStream` (covers both large audio files and small metadata via `ByteArrayInputStream`, so no sealed class is needed). `data/ExportBundle.kt`'s `exportZipBytes(): ByteArray` becomes `exportZip(..., output)`, removing `.readBytes()` entirely. `ui/LibraryScreen.kt`'s save callback streams straight into the picker's `OutputStream`.

**Tech Stack:** Kotlin, `java.util.zip` (JDK, no new dependency), existing Robolectric/`FakeDocumentsProvider` test infra.

---

## Task 1: Streaming `writeZip()` (TDD)

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/domain/ExportZip.kt`
- Modify: `app/src/test/java/com/claymachinegames/bookofrecords/domain/ExportZipTest.kt`

- [ ] **Step 1: Replace the test file with the streaming-API version**

Replace the entire contents of `app/src/test/java/com/claymachinegames/bookofrecords/domain/ExportZipTest.kt` with:

```kotlin
package com.claymachinegames.bookofrecords.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
    fun writeZipRoundTripsSingleEntry() {
        val output = ByteArrayOutputStream()

        writeZip(output, listOf("2026-07-08/a.m4a" to { ByteArrayInputStream("audio-bytes".toByteArray()) }))

        val read = readZip(output.toByteArray())
        assertEquals(setOf("2026-07-08/a.m4a"), read.keys)
        assertEquals("audio-bytes", read["2026-07-08/a.m4a"])
    }

    @Test
    fun writeZipRoundTripsMultipleEntriesWithDateGroupPrefixedPaths() {
        val output = ByteArrayOutputStream()

        writeZip(output, listOf(
            "2026-07-08/a.m4a" to { ByteArrayInputStream("audio-a".toByteArray()) },
            "2026-07-08/a.json" to { ByteArrayInputStream("{}".toByteArray()) },
            "2026-07-07/b.m4a" to { ByteArrayInputStream("audio-b".toByteArray()) },
        ))

        val read = readZip(output.toByteArray())
        assertEquals(3, read.size)
        assertEquals("audio-a", read["2026-07-08/a.m4a"])
        assertEquals("{}", read["2026-07-08/a.json"])
        assertEquals("audio-b", read["2026-07-07/b.m4a"])
    }

    @Test
    fun writeZipOnEmptyListProducesValidEmptyZip() {
        val output = ByteArrayOutputStream()

        writeZip(output, emptyList())

        assertTrue(readZip(output.toByteArray()).isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*.ExportZipTest"`
Expected: compile FAIL, `writeZip` not defined yet (only the old `buildZip` exists).

- [ ] **Step 3: Replace the implementation**

Replace the entire contents of `app/src/main/java/com/claymachinegames/bookofrecords/domain/ExportZip.kt` with:

```kotlin
package com.claymachinegames.bookofrecords.domain

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Streams [entries] (path to a lazily-opened content stream) into [output] as one zip archive. */
fun writeZip(output: OutputStream, entries: List<Pair<String, () -> InputStream>>) {
    ZipOutputStream(output).use { zip ->
        entries.forEach { (path, open) ->
            zip.putNextEntry(ZipEntry(path))
            open().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*.ExportZipTest"`
Expected: PASS, 3/3.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/domain/ExportZip.kt app/src/test/java/com/claymachinegames/bookofrecords/domain/ExportZipTest.kt
git commit -m "refactor: stream writeZip() into an OutputStream instead of buffering a ByteArray"
```

---

## Task 2: Streaming `exportZip()` (TDD)

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/data/ExportBundle.kt`
- Modify: `app/src/test/java/com/claymachinegames/bookofrecords/data/ExportBundleTest.kt`

- [ ] **Step 1: Replace the test file with the streaming-API version**

Replace the entire contents of `app/src/test/java/com/claymachinegames/bookofrecords/data/ExportBundleTest.kt` with:

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
import java.io.ByteArrayOutputStream
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

    private fun namesIn(zipBytes: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) { names.add(e.name); e = zip.nextEntry }
        }
        return names
    }

    @Test
    fun exportZipBundlesAudioJsonAndLabelsPerEntry() = runBlocking {
        val dir = File(tempFolder.root, "2026-07-08").apply { mkdirs() }
        File(dir, "a.m4a").writeText("audio-bytes")
        val meta = RecordingMeta(
            file = "a.m4a", startedAt = "x",
            markers = listOf(Marker(timeMs = 1000, label = "Alice")),
        )
        File(dir, "a.json").writeText(meta.toJson())
        val entry = library.list().first()

        val output = ByteArrayOutputStream()
        exportZip(context, library, listOf(entry), output)

        assertEquals(
            setOf("2026-07-08/a.m4a", "2026-07-08/a.json", "2026-07-08/a.labels.txt"),
            namesIn(output.toByteArray()),
        )
    }

    @Test
    fun exportZipBundlesMultipleEntriesUnderTheirOwnDateGroups() = runBlocking {
        val dirA = File(tempFolder.root, "2026-07-08").apply { mkdirs() }
        File(dirA, "a.m4a").writeText("audio-a")
        File(dirA, "a.json").writeText(RecordingMeta(file = "a.m4a", startedAt = "x").toJson())
        val dirB = File(tempFolder.root, "2026-07-07").apply { mkdirs() }
        File(dirB, "b.m4a").writeText("audio-b")
        File(dirB, "b.json").writeText(RecordingMeta(file = "b.m4a", startedAt = "x").toJson())
        val entries = library.list()
        assertEquals(2, entries.size)

        val output = ByteArrayOutputStream()
        exportZip(context, library, entries, output)

        assertEquals(
            setOf(
                "2026-07-08/a.m4a", "2026-07-08/a.json", "2026-07-08/a.labels.txt",
                "2026-07-07/b.m4a", "2026-07-07/b.json", "2026-07-07/b.labels.txt",
            ),
            namesIn(output.toByteArray()),
        )
    }

    @Test
    fun exportZipSkipsJsonAndLabelsWhenEntryHasNoMetaFile() = runBlocking {
        val dir = File(tempFolder.root, "2026-07-08").apply { mkdirs() }
        File(dir, "a.m4a").writeText("audio-bytes")
        // no a.json written — entry.metaUri will be null
        val entry = library.list().first()

        val output = ByteArrayOutputStream()
        exportZip(context, library, listOf(entry), output)

        assertEquals(setOf("2026-07-08/a.m4a"), namesIn(output.toByteArray()))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*.ExportBundleTest"`
Expected: compile FAIL, `exportZip` not defined yet (only the old `exportZipBytes` exists).

- [ ] **Step 3: Replace the implementation**

Replace the entire contents of `app/src/main/java/com/claymachinegames/bookofrecords/data/ExportBundle.kt` with:

```kotlin
package com.claymachinegames.bookofrecords.data

import android.content.Context
import com.claymachinegames.bookofrecords.domain.writeZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.OutputStream

/** Streams [entries]' audio + JSON + Audacity labels into [output] as one zip archive. */
suspend fun exportZip(context: Context, store: LibraryStore, entries: List<RecordingEntry>, output: OutputStream) =
    withContext(Dispatchers.IO) {
        val zipEntries = entries.flatMap { entry ->
            val meta = entry.metaUri?.let { store.readMeta(it) }
            buildList {
                add("${entry.dateGroup}/${entry.baseName}.m4a" to { context.contentResolver.openInputStream(entry.audioUri)!! })
                meta?.let {
                    val json = it.toJson().toByteArray()
                    val labels = it.toAudacityLabels().toByteArray()
                    add("${entry.dateGroup}/${entry.baseName}.json" to { ByteArrayInputStream(json) })
                    add("${entry.dateGroup}/${entry.baseName}.labels.txt" to { ByteArrayInputStream(labels) })
                }
            }
        }
        writeZip(output, zipEntries)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*.ExportBundleTest"`
Expected: PASS, 3/3.

- [ ] **Step 5: Full suite check**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, no regressions (46 tests total, same count as before — this task renames/reshapes two functions and their tests 1:1, no test added or removed).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/data/ExportBundle.kt app/src/test/java/com/claymachinegames/bookofrecords/data/ExportBundleTest.kt
git commit -m "refactor: stream exportZip() directly, removing the full-audio-file ByteArray buffer"
```

---

## Task 3: Update `LibraryScreen.kt` call site

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/ui/LibraryScreen.kt`

- [ ] **Step 1: Replace the import and the zip-launcher callback**

Find this import near the top of `LibraryScreen.kt`:

```kotlin
import com.claymachinegames.bookofrecords.data.exportZipBytes
```

Replace it with:

```kotlin
import com.claymachinegames.bookofrecords.data.exportZip
```

Find this block (the `zipLauncher` callback body):

```kotlin
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
```

Replace it with:

```kotlin
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
```

No other lines in this file change — selection state, checkbox wiring, `BackHandler`, filename logic, and `EntryCard` are untouched.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Full suite check**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 46 tests, 0 failures (this task has no new pure logic — `LibraryScreen.kt` is Compose UI glue, verified by compile + the existing device-verification checklist in Task 4, consistent with how this screen has always been tested).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/ui/LibraryScreen.kt
git commit -m "refactor: wire LibraryScreen's Save action to the streaming exportZip()"
```

---

## Task 4: Device verification checkpoint + push

**Files:** none (verification only)

This task is a **checkpoint, not a blocker** — the phone was disconnected during planning. Run Steps 1-2 immediately (they don't need the device). Steps 3-4 wait for the phone to be reconnected; do not treat the plan as complete until they've run.

- [ ] **Step 1: Full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 46 tests, 0 failures (same count as the pre-refactor baseline — this plan renames/reshapes existing functions and their tests 1:1, no coverage added or removed).

- [ ] **Step 2: Confirm no leftover references to the old API**

Run: `grep -rn "buildZip\|exportZipBytes" app/src/`
Expected: no matches (both old names fully replaced by `writeZip`/`exportZip` in Tasks 1-3).

- [ ] **Step 3: Install and manually re-verify on device (once reconnected)**

Run: `./gradlew installDebug`

Checklist (same behavior as before this refactor — this is a regression check, not new functionality):
- Select 1 recording in Library, tap "Speichern", save locally — confirm the zip contains `.m4a`/`.json`/`.labels.txt` under the correct date-folder path, same as before.
- Select 2+ recordings spanning different dates, tap "Speichern", save to Google Drive — confirm it still succeeds end-to-end (this is the scenario most likely to surface a streaming bug, since it moves the most data).
- Confirm no crash and no visible slowdown/ANR when exporting a long recording (e.g. the ~2h42m "CRS Roadmap Open Call" entry, if present) — this is the actual scenario this refactor exists to make safe.

- [ ] **Step 4: Push**

```bash
git push
```
