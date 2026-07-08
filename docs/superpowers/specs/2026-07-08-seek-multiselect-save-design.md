# Detail Seek Buttons + Library Multi-Select Save — Design

**Goal:** Add 30s seek buttons to `DetailScreen`, a multi-select mode to `LibraryScreen`, and a "Save" action that zips selected recordings' audio+JSON+labels and hands them to the system's single-file save picker (works with Google Drive, unlike the existing persistent SAF target).

## 1. Scope (finalized via brainstorming)

- **DetailScreen**: `-30s`/`+30s` seek buttons next to Play/Pause.
- **LibraryScreen**: a visible "Auswählen" button enters multi-select mode (chosen over long-press — no long-press convention exists anywhere else in this app, and a text button costs almost nothing).
- **LibraryScreen, in selection mode**: a "Save" action bundles every selected recording's `.m4a` + `.json` + `.labels.txt` into one ZIP, then launches exactly one system save-document dialog for that ZIP (works with Drive, unlike `OpenDocumentTree`).
- **No separate Save button in DetailScreen.** Saving a single recording is just: enter Library selection mode, select one item, tap Save. The existing Share button remains DetailScreen's only "send elsewhere" mechanism — avoids a second, parallel export code path for no added capability.
- Not touched: `LibraryStore`/`Mover`/persistent SAF settings target — this is a separate, additive, per-export capability.

## 2. Seek buttons (`ui/DetailScreen.kt`)

Two text buttons flank the existing round Play/Pause button:

```kotlin
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
    TextButton(onClick = {
        positionMs = (positionMs - 30_000).coerceAtLeast(0)
        player.seekTo(positionMs)
    }) { Text("−30s", color = Bor.textSecondary) }
    // existing round Play/Pause IconButton stays exactly as-is, just gets these neighbors
    TextButton(onClick = {
        positionMs = (positionMs + 30_000).coerceAtMost(duration)
        player.seekTo(positionMs)
    }) { Text("+30s", color = Bor.textSecondary) }
}
```

Text buttons, not icons — matches this screen's existing convention (Pause/Stop/Speichern are all `TextButton`/`OutlinedButton` with text; adding `material-icons-extended` for two icons would be a large new dependency for no real benefit).

## 3. Library selection mode (`ui/LibraryScreen.kt`)

New state: `selectionMode: Boolean`, `selected: Set<Uri>` (keyed by `RecordingEntry.audioUri` — already the `LazyColumn` item key).

**Top bar:**
- Normal: existing "Bibliothek" + Settings gear, plus a new "Auswählen" `TextButton`.
- In selection mode: "N ausgewählt" + "Fertig" `TextButton` (clears `selected`, exits mode) + a "Speichern" `TextButton` (disabled when `selected.isEmpty()`). Text button, not an icon — matches the existing `.txt`-export button's style in `DetailScreen.kt` and sidesteps any uncertainty about which icons ship in this project's non-extended Material icon set.

**Row behavior:** `EntryCard` gains `selectionMode: Boolean`, `isSelected: Boolean`, `onToggle: () -> Unit` params. When `selectionMode` is true, tapping a row toggles selection (leading `Checkbox`, `CheckboxDefaults.colors(checkedColor = Bor.accent, ...)`) instead of calling `onOpen`.

**Back button:** a `BackHandler` inside `LibraryScreen`, active only while `selectionMode` is true, exits selection mode (clears `selected`) instead of letting `MainActivity`'s outer `BackHandler { screen = Screen.Record }` fire. Compose resolves the innermost active `BackHandler` first, so this needs no other wiring.

## 4. Zip + save (new file `domain/ExportZip.kt` + a thin Android-side caller)

**Pure, unit-testable core** (no Android imports — same pattern as `domain/Model.kt`):

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

**Android-side gathering** (new function, e.g. in `data/RecordingRepository.kt`'s file or a small new `data/ExportBundle.kt`):

```kotlin
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

Zip entry paths are prefixed with `dateGroup/` — reuses the app's existing on-disk folder structure, which already guarantees no name collisions between different recordings (the `YYYY-MM-DD_HH-MM_BoR_<title>` naming scheme is unique per recording), so no separate dedup logic is needed.

**UI wiring in `LibraryScreen`:**

```kotlin
val zipLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip")
) { uri ->
    if (uri != null) scope.launch {
        val bytes = exportZipBytes(context, store, entries.filter { it.audioUri in selected })
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        selected = emptySet(); selectionMode = false
        Toast.makeText(context, "Gespeichert", Toast.LENGTH_SHORT).show()
    }
}
// "Speichern" button onClick:
val chosen = entries.filter { it.audioUri in selected }
val defaultName = if (chosen.size == 1) "${chosen.first().baseName}.zip"
                  else "BookofRecords_${dateFolder(LocalDateTime.now())}_${chosen.size}.zip"
zipLauncher.launch(defaultName)
```

Selection mode exits and clears on successful save (matches the implicit "done" signal of a completed export).

## 5. Testing

- `buildZip()`: plain JUnit in `domain/ExportZipTest.kt` — round-trip via `ZipInputStream` (entry names + byte content preserved), empty-list edge case, multiple entries with `dateGroup/`-prefixed paths don't collide.
- `exportZipBytes()`: Robolectric test reusing the existing `FakeDocumentsProvider`/`ShadowDocumentsAwareContentResolver` infra from `SafLibraryTest.kt` (same pattern as that file), verifying the produced zip bytes contain the expected three files per entry.
- Seek buttons and selection-mode UI wiring: manual device/emulator verification, consistent with how every other UI change in this project has been verified (no Compose UI test framework in use here).
