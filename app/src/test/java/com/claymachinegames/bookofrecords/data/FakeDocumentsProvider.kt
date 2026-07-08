package com.claymachinegames.bookofrecords.data

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

        // Root document id must be a real (non-empty) path segment: Uri collapses a trailing
        // empty segment, so DocumentsContract.buildTreeDocumentUri(AUTHORITY, "") produces a
        // Uri that Android's own DocumentsContract.getTreeDocumentId() can't parse back
        // (IllegalArgumentException — missing path segment). fileFor()/documentIdFor() already
        // treat "" as an alias for rootDir, so any real id works; DocumentsContract.buildTreeDocumentUri
        // just needs a non-empty one to round-trip correctly.
        fun treeUri(): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root")
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
        if (documentId.isEmpty() || documentId == "root") rootDir else File(rootDir, documentId)

    private fun documentIdFor(file: File): String = file.relativeTo(rootDir).path

    private fun mimeOf(file: File): String = when {
        file.isDirectory -> Document.MIME_TYPE_DIR
        file.name.endsWith(".json") -> "application/json"
        file.name.endsWith(".m4a") -> "audio/mp4"
        else -> "text/plain"
    }

    private fun valueFor(column: String, file: File): Any = when (column) {
        Document.COLUMN_DOCUMENT_ID -> documentIdFor(file)
        Document.COLUMN_DISPLAY_NAME -> file.name
        Document.COLUMN_MIME_TYPE -> mimeOf(file)
        Document.COLUMN_LAST_MODIFIED -> file.lastModified()
        Document.COLUMN_SIZE -> if (file.isFile) file.length() else 0L
        Document.COLUMN_FLAGS ->
            if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE
            else Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        else -> throw IllegalArgumentException("Unsupported column: $column")
    }

    private fun rowFor(cursor: MatrixCursor, file: File, projection: Array<out String>) {
        val row = cursor.newRow()
        projection.forEach { column -> row.add(valueFor(column, file)) }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: docProjection
        return MatrixCursor(columns).apply { rowFor(this, fileFor(documentId), columns) }
    }

    override fun queryChildDocuments(
        parentDocumentId: String, projection: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val columns = projection ?: docProjection
        return MatrixCursor(columns).apply {
            fileFor(parentDocumentId).listFiles().orEmpty().sortedBy { it.name }.forEach { rowFor(this, it, columns) }
        }
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
        if (target.exists()) {
            throw java.io.FileNotFoundException("Already exists: $displayName")
        }
        file.renameTo(target)
        return documentIdFor(target)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        // "root"/"" are aliases for rootDir itself (see fileFor()), so every real document id is
        // a descendant of them; DocumentsProvider.enforceTree() calls this to verify a document
        // actually lives under the tree's root before allowing any query/mutation against it.
        if (parentDocumentId.isEmpty() || parentDocumentId == "root") return true
        return documentId.startsWith("$parentDocumentId${File.separator}")
    }
}
