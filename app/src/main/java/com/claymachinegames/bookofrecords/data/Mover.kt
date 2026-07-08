package com.claymachinegames.bookofrecords.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Entries eligible for relocation: everything except the recording currently in progress. */
fun sweepable(entries: List<RecordingEntry>, activeBaseName: String?): List<RecordingEntry> =
    entries.filter { it.baseName != activeBaseName }

class Mover(private val context: Context, private val local: RecordingRepository) {

    /** Moves finished local recordings into [targetTreeUri]; leaves the active one (if any) alone. */
    suspend fun sweep(targetTreeUri: Uri, activeBaseName: String?) = withContext(Dispatchers.IO) {
        val root = runCatching { DocumentFile.fromTreeUri(context, targetTreeUri) }.getOrNull()
            ?: return@withContext
        sweepable(local.list(), activeBaseName).forEach { entry ->
            runCatching { moveOne(root, entry) }
        }
    }

    private fun moveOne(root: DocumentFile, entry: RecordingEntry) {
        val dir = findOrCreateDir(root, entry.dateGroup)
        val audioOk = copyIfNeeded(dir, "${entry.baseName}.m4a", entry.audioUri)
        val metaOk = entry.metaUri?.let { copyIfNeeded(dir, "${entry.baseName}.json", it) } ?: true
        if (audioOk && metaOk) local.delete(entry)
    }

    private fun findOrCreateDir(root: DocumentFile, name: String): DocumentFile =
        root.listFiles().firstOrNull { it.isDirectory && it.name == name }
            ?: root.createDirectory(name)
            ?: error("SAF createDirectory failed: $name")

    /** True once the destination exists and matches the source length (already moved or freshly copied). */
    private fun copyIfNeeded(dir: DocumentFile, displayName: String, sourceUri: Uri): Boolean {
        val sourceLen = length(sourceUri) ?: return false
        val existing = dir.listFiles().firstOrNull { it.name == displayName }
        if (existing != null && existing.length() == sourceLen) return true

        val mime = if (displayName.endsWith(".json")) "application/json" else "audio/mp4"
        val target = existing ?: dir.createFile(mime, displayName) ?: return false
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                input.copyTo(output)
            }
        }
        return target.length() == sourceLen
    }

    private fun length(uri: Uri): Long? = context.contentResolver.query(
        uri, arrayOf(OpenableColumns.SIZE), null, null, null,
    )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
}
