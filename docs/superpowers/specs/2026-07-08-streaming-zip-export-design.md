# Streaming ZIP Export — Design

**Goal:** Remove the double in-memory buffering in the Save/export path (`ui/LibraryScreen.kt`'s "Speichern" action) that risks OOM on long recordings (~170MB for a 4h AAC/M4A file) when multiple are exported at once.

## 1. Problem

Current path materializes two full in-memory copies:
- `data/ExportBundle.kt`'s `exportZipBytes()` calls `.readBytes()` per audio file → full `ByteArray` per recording.
- `domain/ExportZip.kt`'s `buildZip()` writes all entries into a `ByteArrayOutputStream` → full zip `ByteArray`.
- `ui/LibraryScreen.kt` holds the resulting `bytes` before writing it to the picker's `OutputStream`.

Multi-select of several long recordings can hold several hundred MB across these duplicate buffers simultaneously.

## 2. Fix

Stream directly into the destination `OutputStream` (from `contentResolver.openOutputStream(uri)`), copying each file's bytes in bounded chunks instead of materializing them.

**`domain/ExportZip.kt`** — `buildZip` becomes `writeZip`, writes into a caller-supplied `OutputStream`, and each entry's content is a lazy `() -> InputStream` opener (not a `ByteArray`). Both large audio files and small metadata (wrapped in `ByteArrayInputStream`) flow through the same `InputStream.copyTo(zipOutputStream)` path — no sealed class needed for two content shapes, since an `InputStream` supplier covers both:

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

Still pure/JVM-testable (no Android imports) — tests wrap a `ByteArrayOutputStream` as the destination and pass `ByteArrayInputStream`-backed openers as sources, then verify via `ZipInputStream`.

**`data/ExportBundle.kt`** — `exportZipBytes(): ByteArray` becomes `exportZip(..., output: OutputStream)` (returns `Unit`, writes directly). The audio opener defers `openInputStream` until `writeZip` actually reads it — no `.readBytes()` anywhere:

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

**`ui/LibraryScreen.kt`** — the save callback opens the destination `OutputStream` once and streams straight into it, removing the intermediate `bytes` variable. Existing error-handling contract from the prior review fix (commit `c5fec3f`) is unchanged: `runCatching`, failure Toast, no state reset on failure:

```kotlin
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
```

## 3. Testing

- `domain/ExportZipTest.kt`: rewritten against `writeZip(output, entries)` — same three cases (single entry round-trip, multi-entry with date-group-prefixed paths, empty list), asserting on a `ByteArrayOutputStream`'s bytes via `ZipInputStream`, with entry sources as `{ ByteArrayInputStream(...) }` lambdas instead of raw `ByteArray`.
- `data/ExportBundleTest.kt`: rewritten against `exportZip(context, store, entries, output)` — same three cases (bundles audio+json+labels, multiple entries under their own date groups, skips json/labels when no metaUri), writing into a `ByteArrayOutputStream` and asserting via `ZipInputStream` as before.
- No new test needed for "does it actually avoid buffering" — that's a structural property of the code (no `.readBytes()`/`ByteArrayOutputStream` for the full zip in the changed files), not something a JVM unit test can meaningfully assert. Verified by code review instead.
- Device-level manual re-verification (local + Drive save, single + multi-select) once the phone is reconnected — same checklist as the original Save feature, to confirm no behavioral regression.

## 4. Non-goals

- Not changing zip entry paths, naming rules, or the `LibraryScreen.kt` UI/selection logic — only the data path underneath the existing "Speichern" button.
- Not adding a memory/large-file test fixture — impractical in a JVM unit test and not needed to verify the fix (the fix is structural: no full-file `ByteArray` anywhere in the changed code).
