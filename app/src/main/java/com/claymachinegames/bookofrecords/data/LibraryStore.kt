package com.claymachinegames.bookofrecords.data

import android.net.Uri
import com.claymachinegames.bookofrecords.domain.RecordingMeta

/**
 * Abstracts "where recordings live" so Library/Detail work identically against
 * the local MediaStore folder or a user-chosen SAF tree.
 */
interface LibraryStore {
    fun list(): List<RecordingEntry>
    fun readMeta(metaUri: Uri): RecordingMeta?
    fun writeMeta(metaUri: Uri, meta: RecordingMeta)
    fun exportLabels(entry: RecordingEntry, meta: RecordingMeta): Uri
    fun rename(entry: RecordingEntry, newBase: String)
    fun delete(entry: RecordingEntry)
}

/** Thrown by [LibraryStore.list] when the backing folder can't be reached (permission lost, unmounted). */
class LibraryUnavailableException(message: String) : Exception(message)
