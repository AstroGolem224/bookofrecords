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
