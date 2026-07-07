package com.claymachinegames.bookofrecords.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.claymachinegames.bookofrecords.domain.RecordingMeta

class RecordingFiles(val audioUri: Uri, val metaUri: Uri, val actualBase: String)

data class RecordingEntry(
    val audioUri: Uri,
    val metaUri: Uri?,
    val baseName: String,      // display name without extension
    val addedAtEpochSec: Long,
    val durationMs: Long,
    val markerCount: Int,
    val dateGroup: String,     // "2026-07-08" — aus Ordnername, Fallback DATE_ADDED
)

class RecordingRepository(private val context: Context) {

    private val resolver get() = context.contentResolver
    private val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    companion object {
        const val RELATIVE_PATH = "Documents/BookofRecords/"
    }

    // --- create / write ---

    fun createRecording(baseName: String, dateFolder: String): RecordingFiles {
        val audioUri = insert("$baseName.m4a", "audio/mp4", "$RELATIVE_PATH$dateFolder/", pending = true)
        val actualBase = resolver.query(audioUri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.removeSuffix(".m4a") else null
        } ?: baseName
        val metaUri = insert("$actualBase.json", "application/json", "$RELATIVE_PATH$dateFolder/", pending = false)
        return RecordingFiles(audioUri, metaUri, actualBase)
    }

    private fun insert(displayName: String, mime: String, relativePath: String, pending: Boolean): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (pending) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(filesUri, values) ?: error("MediaStore insert failed: $displayName")
    }

    fun openAudioForWrite(uri: Uri): ParcelFileDescriptor =
        resolver.openFileDescriptor(uri, "w") ?: error("openFileDescriptor failed: $uri")

    /** Clear IS_PENDING after recording finished so the file becomes visible. */
    fun publish(uri: Uri) {
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
    }

    fun writeMeta(metaUri: Uri, meta: RecordingMeta) {
        resolver.openOutputStream(metaUri, "wt")!!.use { it.write(meta.toJson().toByteArray()) }
    }

    /** Rollback helper: remove both rows of a recording that never became valid. */
    fun discard(files: RecordingFiles) {
        runCatching { resolver.delete(files.audioUri, null, null) }
        runCatching { resolver.delete(files.metaUri, null, null) }
    }

    fun readMeta(metaUri: Uri): RecordingMeta? = runCatching {
        resolver.openInputStream(metaUri)!!.use {
            RecordingMeta.fromJson(it.readBytes().decodeToString())
        }
    }.getOrNull()

    // --- list ---

    fun list(): List<RecordingEntry> {
        data class Row(val uri: Uri, val name: String, val added: Long, val relPath: String)
        val audio = mutableListOf<Row>()
        val metaByBase = mutableMapOf<String, Uri>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        // ponytail: sieht nur eigene MediaStore-Beiträge — nach Reinstall ist die Library leer, Dateien bleiben auf Disk
        resolver.query(
            filesUri, projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$RELATIVE_PATH%"), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(filesUri, c.getLong(0))
                val name = c.getString(1) ?: continue
                val relPath = c.getString(3) ?: RELATIVE_PATH
                when {
                    name.endsWith(".m4a") -> audio += Row(uri, name.removeSuffix(".m4a"), c.getLong(2), relPath)
                    name.endsWith(".json") -> metaByBase[name.removeSuffix(".json")] = uri
                }
            }
        }

        val dateRegex = Regex("""(\d{4}-\d{2}-\d{2})/?$""")
        return audio.map { row ->
            val metaUri = metaByBase[row.name]
            val meta = metaUri?.let { readMeta(it) }
            val folderDate = dateRegex.find(row.relPath.trimEnd('/'))?.groupValues?.get(1)
            RecordingEntry(
                audioUri = row.uri,
                metaUri = metaUri,
                baseName = row.name,
                addedAtEpochSec = row.added,
                durationMs = meta?.durationMs?.takeIf { it > 0 } ?: probeDuration(row.uri),
                markerCount = meta?.markers?.size ?: 0,
                dateGroup = folderDate ?: java.time.Instant.ofEpochSecond(row.added)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),
            )
        }.sortedByDescending { it.addedAtEpochSec }
    }

    private fun probeDuration(uri: Uri): Long = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        }
    }.getOrDefault(0L)

    // --- rename / delete / export ---

    fun rename(entry: RecordingEntry, newBase: String) {
        resolver.update(entry.audioUri, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$newBase.m4a")
        }, null, null)
        entry.metaUri?.let { metaUri ->
            resolver.update(metaUri, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$newBase.json")
            }, null, null)
            readMeta(metaUri)?.let { writeMeta(metaUri, it.copy(file = "$newBase.m4a")) }
        }
    }

    /** Rename both files of an in-flight recording (finalize title on stop). */
    fun renameFiles(files: RecordingFiles, oldBase: String, newBase: String) {
        resolver.update(files.audioUri, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$newBase.m4a")
        }, null, null)
        try {
            resolver.update(files.metaUri, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$newBase.json")
            }, null, null)
        } catch (e: Exception) {
            runCatching {   // Paar konsistent halten: Audio-Rename zurückdrehen
                resolver.update(files.audioUri, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$oldBase.m4a")
                }, null, null)
            }
            throw e
        }
    }

    fun delete(entry: RecordingEntry) {
        resolver.delete(entry.audioUri, null, null)
        entry.metaUri?.let { resolver.delete(it, null, null) }
    }

    /** Writes <base>.labels.txt next to the recording; returns its Uri. */
    // ponytail: repeated export creates "name (1).txt" duplicates — dedupe when it annoys
    fun exportLabels(entry: RecordingEntry, meta: RecordingMeta): Uri {
        val folder = if (entry.dateGroup.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
            "$RELATIVE_PATH${entry.dateGroup}/" else RELATIVE_PATH
        val uri = insert("${entry.baseName}.labels.txt", "text/plain", folder, pending = false)
        resolver.openOutputStream(uri, "wt")!!.use {
            it.write(meta.toAudacityLabels().toByteArray())
        }
        return uri
    }
}
