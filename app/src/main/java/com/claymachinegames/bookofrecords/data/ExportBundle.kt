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
