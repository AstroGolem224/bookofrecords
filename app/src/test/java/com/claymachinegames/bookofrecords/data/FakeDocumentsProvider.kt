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
