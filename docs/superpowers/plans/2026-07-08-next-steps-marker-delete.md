# BookofRecords Next-Steps + Marker-Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close out the four "Next steps" from `~/Dokumente/UMBRA-Notes/DDs/BookofRecords/2026-07-08_Architecture_Overview.md` chapter 10 that have concrete work (Robolectric test coverage for `SafLibrary`/`Mover`, a `MarkerEditor` state-holder extracted from `DetailScreen`, two `ponytail:` papercut fixes) and add a new per-marker delete button with confirmation dialog.

**Architecture:** Marker CRUD (`commitPending`/`setLabel`/`addMarker`/new `deleteMarker`) moves out of the `DetailScreen` composable into a plain Kotlin state-holder class (`ui/MarkerEditor.kt`) using `mutableStateOf` properties — no `ViewModel`, no new lifecycle dependency, since the screen is short-lived and already manages `MediaPlayer` manually. `SafLibrary`/`Mover` get real (non-mocked) SAF behavior test coverage via Robolectric plus a minimal in-memory `DocumentsProvider` test double backed by a real JVM temp directory.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric 4.13 (new), existing JUnit4 + Mockito.

---

## Task 1: Robolectric test infrastructure

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the Robolectric dependency and unit test resources flag**

In `app/build.gradle.kts`, add inside the `android { }` block (after `kotlinOptions`):

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

And add to `dependencies { }` (after the existing `mockito-core` line):

```kotlin
    testImplementation("org.robolectric:robolectric:4.13")
```

- [ ] **Step 2: Verify existing test suite still builds and passes**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all 24 existing tests still pass (no new tests yet — this step only proves the dependency addition didn't break anything).

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "test: add Robolectric dependency for SAF-backed unit tests"
```

---

## Task 2: Fake DocumentsProvider test double

**Files:**
- Create: `app/src/test/java/com/claymachinegames/bookofrecords/data/FakeDocumentsProvider.kt`

This is a minimal `DocumentsProvider` backed by a real JVM temp directory, so `SafLibrary`/`Mover` tests exercise genuine `DocumentFile`/`DocumentsContract` calls instead of mocks. `documentId` is the path relative to the fake root, `""` for the root itself.

- [ ] **Step 1: Write the fake provider**

```kotlin
package com.claymachinegames.bookofrecords.data

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/**
 * DocumentsProvider backed by a real JVM temp directory, registered per-test via
 * Robolectric's ContentProviderController. Supports exactly the operations
 * SafLibrary/Mover issue against a SAF tree: list, read, write, create, delete, rename.
 */
class FakeDocumentsProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "com.claymachinegames.bookofrecords.faketest"
        lateinit var rootDir: File

        fun treeUri(): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "")
    }

    private val docProjection = arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_SIZE, Document.COLUMN_FLAGS,
    )

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID)).apply {
            newRow().add("root").add("")
        }

    private fun fileFor(documentId: String): File =
        if (documentId.isEmpty()) rootDir else File(rootDir, documentId)

    private fun documentIdFor(file: File): String = file.relativeTo(rootDir).path

    private fun mimeOf(file: File): String = when {
        file.isDirectory -> Document.MIME_TYPE_DIR
        file.name.endsWith(".json") -> "application/json"
        file.name.endsWith(".m4a") -> "audio/mp4"
        else -> "text/plain"
    }

    private fun rowFor(cursor: MatrixCursor, file: File) {
        cursor.newRow()
            .add(documentIdFor(file))
            .add(file.name)
            .add(mimeOf(file))
            .add(file.lastModified())
            .add(if (file.isFile) file.length() else 0L)
            .add(
                if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE
                else Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME,
            )
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(docProjection).apply { rowFor(this, fileFor(documentId)) }

    override fun queryChildDocuments(
        parentDocumentId: String, projection: Array<out String>?, sortOrder: String?,
    ): Cursor = MatrixCursor(docProjection).apply {
        fileFor(parentDocumentId).listFiles().orEmpty().sortedBy { it.name }.forEach { rowFor(this, it) }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val target = File(fileFor(parentDocumentId), displayName)
        if (mimeType == Document.MIME_TYPE_DIR) target.mkdirs() else target.createNewFile()
        return documentIdFor(target)
    }

    override fun deleteDocument(documentId: String) {
        fileFor(documentId).deleteRecursively()
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = fileFor(documentId)
        val target = File(file.parentFile, displayName)
        file.renameTo(target)
        return documentIdFor(target)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith(if (parentDocumentId.isEmpty()) "" else "$parentDocumentId${File.separator}")
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL. If a `DocumentsProvider` method signature mismatches the installed compileSdk (e.g. a missing `@Throws(FileNotFoundException::class)` on `openDocument`, or a different nullability on `projection`), fix the signature to match the compiler error — this is normal iteration against framework stub classes, not a sign the approach is wrong.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/claymachinegames/bookofrecords/data/FakeDocumentsProvider.kt
git commit -m "test: add fake DocumentsProvider for SAF-backed Robolectric tests"
```

---

## Task 3: SafLibrary Robolectric tests

**Files:**
- Create: `app/src/test/java/com/claymachinegames/bookofrecords/data/SafLibraryTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.claymachinegames.bookofrecords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafLibraryTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var library: SafLibrary

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.rootDir = tempFolder.root
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java)
            .create(FakeDocumentsProvider.AUTHORITY)
        library = SafLibrary(context, FakeDocumentsProvider.treeUri())
    }

    private fun dateDir(name: String): File = File(tempFolder.root, name).apply { mkdirs() }

    @Test
    fun listGroupsRecordingsByDateFolder() {
        val dir = dateDir("2026-07-08")
        File(dir, "a.m4a").writeText("audio")
        File(dir, "a.json").writeText(RecordingMeta(file = "a.m4a", startedAt = "x").toJson())

        val entries = library.list()

        assertEquals(1, entries.size)
        assertEquals("a", entries[0].baseName)
        assertEquals("2026-07-08", entries[0].dateGroup)
    }

    @Test
    fun listSkipsAudioFilesWithoutMatchingMeta() {
        val dir = dateDir("2026-07-08")
        File(dir, "a.m4a").writeText("audio")
        // no a.json

        val entries = library.list()

        assertEquals(1, entries.size)
        assertEquals(null, entries[0].metaUri)
        assertEquals(0, entries[0].markerCount)
    }

    @Test
    fun renameUpdatesBothAudioAndMetaFilesAndMetaFileContent() {
        val dir = dateDir("2026-07-08")
        File(dir, "old.m4a").writeText("audio")
        File(dir, "old.json").writeText(RecordingMeta(file = "old.m4a", startedAt = "x").toJson())
        val entry = library.list().first()

        library.rename(entry, "new")

        val renamedAudio = File(dir, "new.m4a")
        val renamedMeta = File(dir, "new.json")
        assertTrue(renamedAudio.exists())
        assertTrue(renamedMeta.exists())
        assertEquals("new.m4a", RecordingMeta.fromJson(renamedMeta.readText()).file)
    }

    @Test(expected = LibraryUnavailableException::class)
    fun listThrowsWhenUriIsNotAValidTreeUri() {
        // DocumentFile.fromTreeUri() returns null (SafLibrary.root() then throws) whenever the
        // Uri fails DocumentsContract.isTreeUri()'s structural check — a "document" Uri (not
        // "tree") is a real, deterministic way to trigger that without needing to fake a
        // provider that's actually unreachable.
        val notATreeUri = android.net.Uri.parse("content://${FakeDocumentsProvider.AUTHORITY}/document/not-a-tree")
        SafLibrary(context, notATreeUri).list()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail (or don't compile yet)**

Run: `./gradlew testDebugUnitTest --tests "*.SafLibraryTest"`
Expected: FAIL or compile error, since no implementation issue is expected here (SafLibrary already exists) — the goal of this task is proving the *test infrastructure* correctly exercises it. If tests fail due to a `FakeDocumentsProvider` bug (not a `SafLibrary` bug), fix `FakeDocumentsProvider.kt` from Task 2, not `SafLibrary.kt`.

- [ ] **Step 3: Fix FakeDocumentsProvider or test setup until green**

No production code in `SafLibrary.kt` should need to change — these tests characterize existing, already-correct behavior. If a genuine `SafLibrary` bug surfaces (unlikely, but possible since this is its first real test), fix it in `data/SafLibrary.kt` following this repo's TDD discipline and note the fix in the commit message.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*.SafLibraryTest"`
Expected: PASS, 4/4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/claymachinegames/bookofrecords/data/SafLibraryTest.kt
git commit -m "test: add Robolectric coverage for SafLibrary against a real DocumentsProvider"
```

---

## Task 4: Mover Robolectric tests

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/data/Mover.kt`
- Create: `app/src/test/java/com/claymachinegames/bookofrecords/data/MoverRobolectricTest.kt`

The existing `MoverTest.kt` only covers the pure `sweepable()` filter with a mocked `Uri` — keep it as-is. `Mover.sweep()` itself calls `local.list()` which queries real `MediaStore` and needs a device/emulator to exercise honestly (Robolectric's MediaStore shadow doesn't replicate `RELATIVE_PATH`/`IS_PENDING` query semantics) — that stays out of scope here. What Robolectric *can* validate for real is everything `sweep()` does once it has a list: directory creation, byte-for-byte copy, idempotency, and the delete-after-successful-copy step. Widen the three currently-`private` helpers to `internal` so tests can call them directly instead of re-deriving the same logic:

- [ ] **Step 1: Widen Mover's helper visibility from private to internal**

In `data/Mover.kt`, change:

```kotlin
    private fun moveOne(root: DocumentFile, entry: RecordingEntry) {
```
```kotlin
    private fun findOrCreateDir(root: DocumentFile, name: String): DocumentFile =
```
```kotlin
    private fun copyIfNeeded(dir: DocumentFile, displayName: String, sourceUri: Uri): Boolean {
```

to:

```kotlin
    internal fun moveOne(root: DocumentFile, entry: RecordingEntry) {
```
```kotlin
    internal fun findOrCreateDir(root: DocumentFile, name: String): DocumentFile =
```
```kotlin
    internal fun copyIfNeeded(dir: DocumentFile, displayName: String, sourceUri: Uri): Boolean {
```

(`length()` stays `private` — it's not needed directly by tests.) `internal` is visible to the `app` module's test source set by default, so no other build config changes are needed.

- [ ] **Step 2: Write the tests**

```kotlin
package com.claymachinegames.bookofrecords.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoverRobolectricTest {

    @get:Rule val rootFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var mover: Mover
    private lateinit var root: DocumentFile

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.rootDir = rootFolder.root
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java)
            .create(FakeDocumentsProvider.AUTHORITY)
        root = DocumentFile.fromTreeUri(context, FakeDocumentsProvider.treeUri())!!
        mover = Mover(context, RecordingRepository(context))
    }

    /** Both audio+meta live under the same fake provider (a "source" subdir) so that
     * moveOne()'s subsequent local.delete(entry) — which goes through ContentResolver, not
     * java.io — resolves to a real, deletable document instead of an unroutable file:// Uri. */
    private fun sourceEntry(baseName: String, audioBytes: String, metaBytes: String): RecordingEntry {
        val sourceDir = root.findFile("source") ?: root.createDirectory("source")!!
        val audio = sourceDir.createFile("audio/mp4", "$baseName.m4a")!!
        context.contentResolver.openOutputStream(audio.uri)!!.use { it.write(audioBytes.toByteArray()) }
        val meta = sourceDir.createFile("application/json", "$baseName.json")!!
        context.contentResolver.openOutputStream(meta.uri)!!.use { it.write(metaBytes.toByteArray()) }
        return RecordingEntry(
            audioUri = audio.uri, metaUri = meta.uri, baseName = baseName,
            addedAtEpochSec = 0, durationMs = 1000, markerCount = 0, dateGroup = "2026-07-08",
        )
    }

    @Test
    fun moveOneCopiesAudioAndMetaThenDeletesSourceWhenBothSucceed() {
        val entry = sourceEntry("a", "audio-bytes", """{"file":"a.m4a"}""")

        mover.moveOne(root, entry)

        val dateDir = root.findFile("2026-07-08")!!
        assertEquals("audio-bytes", File(rootFolder.root, "2026-07-08/a.m4a").readText())
        assertEquals("""{"file":"a.m4a"}""", File(rootFolder.root, "2026-07-08/a.json").readText())
        assertTrue(root.findFile("source")!!.listFiles().isEmpty())   // source deleted after successful move
    }

    @Test
    fun copyIfNeededSkipsRewriteWhenDestinationAlreadyMatchesSourceLength() {
        val entry = sourceEntry("b", "same-length!", "{}")
        val dir = root.createDirectory("2026-07-08")!!
        val existing = dir.createFile("audio/mp4", "b.m4a")!!
        context.contentResolver.openOutputStream(existing.uri)!!.use { it.write("same-length!".toByteArray()) }

        val result = mover.copyIfNeeded(dir, "b.m4a", entry.audioUri)

        assertTrue(result)
        assertEquals("same-length!", File(rootFolder.root, "2026-07-08/b.m4a").readText())
    }

    @Test
    fun findOrCreateDirReusesAnExistingDirectoryInsteadOfDuplicating() {
        val first = mover.findOrCreateDir(root, "2026-07-08")
        val second = mover.findOrCreateDir(root, "2026-07-08")

        assertEquals(first.uri, second.uri)
        assertEquals(1, root.listFiles().count { it.isDirectory && it.name == "2026-07-08" })
    }
}
```

- [ ] **Step 3: Run tests to verify they fail/compile-error first**

Run: `./gradlew testDebugUnitTest --tests "*.MoverRobolectricTest"`
Expected: compile FAIL until Step 1's visibility change lands (`moveOne`/`copyIfNeeded`/`findOrCreateDir` not visible from the test), then likely a runtime failure until `FakeDocumentsProvider` registration/paths are right. Debug against `Mover.kt`/`FakeDocumentsProvider.kt`, not the test expectations — the test bodies encode real, already-correct `Mover` behavior.

- [ ] **Step 4: Iterate until green**

Run: `./gradlew testDebugUnitTest --tests "*.MoverRobolectricTest"`
Expected: PASS, 3/3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/data/Mover.kt app/src/test/java/com/claymachinegames/bookofrecords/data/MoverRobolectricTest.kt
git commit -m "test: add Robolectric coverage for Mover.moveOne/copyIfNeeded/findOrCreateDir"
```

---

## Task 5: MarkerEditor state holder (TDD)

**Files:**
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/ui/MarkerEditor.kt`
- Test: `app/src/test/java/com/claymachinegames/bookofrecords/ui/MarkerEditorTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.claymachinegames.bookofrecords.ui

import com.claymachinegames.bookofrecords.domain.Marker
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerEditorTest {

    private val meta = RecordingMeta(
        file = "a.m4a", startedAt = "x",
        markers = listOf(
            Marker(timeMs = 1000, type = "speaker", label = "Alice"),
            Marker(timeMs = 2000, type = "speaker", label = "Bob"),
        ),
    )

    private fun editor(persisted: MutableList<RecordingMeta> = mutableListOf()) =
        MarkerEditor(meta) { persisted.add(it) }

    @Test
    fun selectPopulatesEditTextThenTogglingDeselectsAndClears() {
        val e = editor()
        e.select(0)
        assertEquals(0, e.selected)
        assertEquals("Alice", e.editText)
        e.select(0)
        assertEquals(-1, e.selected)
        assertEquals("", e.editText)
    }

    @Test
    fun commitPendingWritesEditedLabelOnlyWhenChanged() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.select(0)
        e.editText = "Alice Renamed"
        e.commitPending()
        assertEquals("Alice Renamed", e.meta?.markers?.get(0)?.label)
        assertEquals(1, persisted.size)
    }

    @Test
    fun commitPendingIsNoOpWhenTextUnchanged() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.select(0)
        e.commitPending()
        assertEquals(0, persisted.size)
    }

    @Test
    fun addMarkerInsertsSortedAndPersistsAndClosesSelection() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.addMarker(timeMs = 1500, label = "Carol")
        assertEquals(3, e.meta?.markers?.size)
        assertEquals("Carol", e.meta?.markers?.get(1)?.label)
        assertEquals(-1, e.selected)
        assertEquals(1, persisted.size)
    }

    @Test
    fun addMarkerBlankLabelIsNoOp() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.addMarker(timeMs = 1500, label = "   ")
        assertEquals(2, e.meta?.markers?.size)
        assertEquals(0, persisted.size)
    }

    @Test
    fun addMarkerCommitsPendingEditBeforeInserting() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.select(0)
        e.editText = "Alice Renamed"
        e.addMarker(timeMs = 1500, label = "Carol")
        assertEquals("Alice Renamed", e.meta?.markers?.get(0)?.label)
        assertEquals(2, persisted.size)
    }

    @Test
    fun deleteMarkerRemovesAndPersistsAndClearsSelection() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.select(0)
        e.deleteMarker(0)
        assertEquals(1, e.meta?.markers?.size)
        assertEquals("Bob", e.meta?.markers?.get(0)?.label)
        assertEquals(-1, e.selected)
        assertEquals("", e.editText)
        assertEquals(1, persisted.size)
    }

    @Test
    fun deleteMarkerOutOfRangeIsNoOp() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.deleteMarker(99)
        assertEquals(2, e.meta?.markers?.size)
        assertEquals(0, persisted.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail with "unresolved reference: MarkerEditor"**

Run: `./gradlew testDebugUnitTest --tests "*.MarkerEditorTest"`
Expected: compile FAIL, `MarkerEditor` not defined yet.

- [ ] **Step 3: Write MarkerEditor**

```kotlin
package com.claymachinegames.bookofrecords.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.claymachinegames.bookofrecords.domain.Marker
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import com.claymachinegames.bookofrecords.domain.insertMarkerSorted

/**
 * Owns marker CRUD and the pending-inline-edit state for DetailScreen. [onPersist] is called
 * with the updated RecordingMeta whenever markers change; the caller (DetailScreen) is
 * responsible for the actual write (I/O dispatcher, metaUri).
 *
 * Getipppter Text ging bisher verloren, sobald man vor "Speichern" woanders hintippte oder den
 * Screen verließ. commitPending() is called from every place selection can change so that never
 * happens again (see DetailScreen's DisposableEffect and marker-row click handler).
 */
class MarkerEditor(initialMeta: RecordingMeta?, private val onPersist: (RecordingMeta) -> Unit) {
    var meta by mutableStateOf(initialMeta)
        private set
    var selected by mutableStateOf(-1)
        private set
    var editText by mutableStateOf("")

    fun setMeta(newMeta: RecordingMeta?) {
        meta = newMeta
    }

    fun select(index: Int) {
        commitPending()
        val m = meta ?: return
        if (selected == index) {
            selected = -1
            editText = ""
        } else {
            selected = index
            editText = m.markers.getOrNull(index)?.label ?: ""
        }
    }

    fun commitPending() {
        val i = selected
        if (i < 0) return
        val current = meta?.markers?.getOrNull(i) ?: return
        val trimmed = editText.trim()
        if (trimmed != current.label) setLabel(i, trimmed, "speaker")
    }

    fun setLabel(index: Int, label: String, type: String) {
        val m = meta ?: return
        if (index !in m.markers.indices) return
        val markers = m.markers.toMutableList()
        markers[index] = markers[index].copy(label = label, type = type)
        persist(m.copy(markers = markers))
    }

    fun addMarker(timeMs: Long, label: String) {
        val m = meta ?: return
        if (label.isBlank()) return
        commitPending()
        val marker = Marker(timeMs = timeMs, type = "speaker", label = label.trim())
        persist(m.copy(markers = insertMarkerSorted(m.markers, marker)))
        selected = -1
    }

    fun deleteMarker(index: Int) {
        val m = meta ?: return
        if (index !in m.markers.indices) return
        val markers = m.markers.toMutableList().apply { removeAt(index) }
        persist(m.copy(markers = markers))
        selected = -1
        editText = ""
    }

    private fun persist(updated: RecordingMeta) {
        meta = updated
        onPersist(updated)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*.MarkerEditorTest"`
Expected: PASS, 8/8 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/ui/MarkerEditor.kt app/src/test/java/com/claymachinegames/bookofrecords/ui/MarkerEditorTest.kt
git commit -m "refactor: extract marker CRUD from DetailScreen into MarkerEditor state holder"
```

---

## Task 6: Wire MarkerEditor into DetailScreen + add marker-delete UI

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/ui/DetailScreen.kt`

This replaces `DetailScreen`'s inline `meta`/`selected`/`editText`/`saveMeta`/`setLabel`/`commitPending` with the `MarkerEditor` from Task 5, and adds the delete-marker button + confirmation dialog. Replace the **entire file contents** with the following (preserves every existing behavior — playback, rename, recording-delete, export, share — byte-for-byte except where explicitly changed):

- [ ] **Step 1: Replace the whole file**

```kotlin
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
    var showDeleteMarker by remember { mutableStateOf(false) }

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
        editor.setMeta(loaded)
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
                            TextButton(onClick = { editor.select(i) }) {
                                Text("Speichern", color = Bor.accent)
                            }
                            IconButton(onClick = { showDeleteMarker = true }) {
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

    if (showDeleteMarker) {
        val label = editor.meta?.markers?.getOrNull(editor.selected)?.label?.ifEmpty { "(unbenannt)" } ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteMarker = false },
            containerColor = Bor.surface,
            title = { Text("Marker löschen?", color = Bor.textPrimary) },
            text = { Text(label, color = Bor.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    editor.deleteMarker(editor.selected)
                    showDeleteMarker = false
                }) { Text("Löschen", color = Bor.accent) }
            },
            dismissButton = { TextButton(onClick = { showDeleteMarker = false }) {
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
```

Note what was intentionally dropped from imports: `Marker` and `insertMarkerSorted` (now only used inside `MarkerEditor.kt`, not `DetailScreen.kt` directly).

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/ui/DetailScreen.kt
git commit -m "feat: wire MarkerEditor into DetailScreen, add per-marker delete with confirmation"
```

---

## Task 7: exportLabels() dedup fix + reinstall-gap comment

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/data/RecordingRepository.kt`

- [ ] **Step 1: Fix exportLabels() to overwrite instead of creating duplicates**

Replace:

```kotlin
    /** Writes <base>.labels.txt next to the recording; returns its Uri. */
    // ponytail: repeated export creates "name (1).txt" duplicates — dedupe when it annoys
    override fun exportLabels(entry: RecordingEntry, meta: RecordingMeta): Uri {
        val folder = if (entry.dateGroup.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
            "$RELATIVE_PATH${entry.dateGroup}/" else RELATIVE_PATH
        val uri = insert("${entry.baseName}.labels.txt", "text/plain", folder, pending = false)
        resolver.openOutputStream(uri, "wt")!!.use {
            it.write(meta.toAudacityLabels().toByteArray())
        }
        return uri
    }
```

with:

```kotlin
    /** Writes <base>.labels.txt next to the recording; returns its Uri. Overwrites on re-export. */
    override fun exportLabels(entry: RecordingEntry, meta: RecordingMeta): Uri {
        val folder = if (entry.dateGroup.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
            "$RELATIVE_PATH${entry.dateGroup}/" else RELATIVE_PATH
        val displayName = "${entry.baseName}.labels.txt"
        val existing = resolver.query(
            filesUri, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(displayName, folder), null,
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(filesUri, c.getLong(0)) else null }
        val uri = existing ?: insert(displayName, "text/plain", folder, pending = false)
        resolver.openOutputStream(uri, "wt")!!.use {
            it.write(meta.toAudacityLabels().toByteArray())
        }
        return uri
    }
```

- [ ] **Step 2: Clarify the reinstall-gap comment**

Replace:

```kotlin
        // ponytail: sieht nur eigene MediaStore-Beiträge — nach Reinstall ist die Library leer, Dateien bleiben auf Disk
        // IS_PENDING=0 ausschließen: MediaStore zeigt eigene pending Zeilen der eigenen App —
        // ohne den Filter könnte Mover eine noch laufende Aufnahme erwischen und löschen (Task 4/6 Review-Fund)
```

with:

```kotlin
        // ponytail: MediaStore.Files only returns rows this app itself inserted (Android scopes
        // ownership per-app for app-private paths), so a reinstall shows an empty Library even
        // though the .m4a/.json files remain untouched on disk. A real fix needs a filesystem-level
        // rescan (walk Documents/BookofRecords/ directly and re-adopt orphaned files) — not
        // attempted; revisit if users report recordings "disappearing" after reinstalling, as
        // opposed to after deliberate deletion.
        // IS_PENDING=0 excludes this app's own in-flight recording — without it, Mover could
        // sweep (and delete the local copy of) a recording still being written (Task 4/6 review finding).
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification (no automated test — MediaStore requires a device/emulator)**

On the emulator or device: open a recording's Detail screen, tap `.txt` export twice in a row. Confirm via `adb shell content query --uri content://media/external/file --where "_display_name LIKE '%labels.txt'"` (or by browsing Files) that only one `<base>.labels.txt` exists, not a `<base>.labels (1).txt`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/claymachinegames/bookofrecords/data/RecordingRepository.kt
git commit -m "fix: exportLabels() overwrites existing export instead of creating duplicates; clarify reinstall-gap comment"
```

---

## Task 8: Device verification + graphify re-run

**Files:** none (verification only)

- [ ] **Step 1: Full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass (24 pre-existing + new MarkerEditor/SafLibrary/Mover tests).

- [ ] **Step 2: Install and manually verify on emulator/device**

Run: `./gradlew installDebug`

Checklist:
- Open a recording in Library → Detail.
- Tap a marker row, type a label, tap "Speichern" — confirm label persists (JSON sidecar updated).
- Tap "+ Neu" at a scrubbed position, name it, confirm it's inserted in chronological order in the list.
- Tap a marker row to expand it, tap the new delete icon (right of "Speichern") — confirm the confirmation dialog appears with the marker's label, "Abbrechen" does nothing, "Löschen" removes it from the list and the JSON sidecar.
- Export `.txt` twice — confirm no duplicate file (Task 7).

- [ ] **Step 3: Re-run graphify and report the delta**

```bash
cd /home/itiger013/Dokumente/Github/bookofrecords
```

Invoke the `graphify` skill against `.` again (full pass — no prior `graph.json` was kept since `graphify-out/` was added to `.gitignore` and not persisted between sessions). Compare against the findings recorded in `2026-07-08_Architecture_Overview.md` chapter 8:
- `SafLibrary` should no longer be a same-degree-but-zero-test-coverage risk — note its new test count in the report.
- `DetailScreen.kt` line count/degree should drop now that marker-editing logic moved to `MarkerEditor.kt` — note the before/after line counts (`wc -l` on both files) and whether the community-detection pass now splits marker-editing into its own community.

Report the before/after delta directly to the user — do not just assume the numbers improved.

- [ ] **Step 4: Push**

```bash
git push
```
