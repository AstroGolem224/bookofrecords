package com.claymachinegames.bookofrecords.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.claymachinegames.bookofrecords.domain.RecordingMeta

class SafLibrary(private val context: Context, private val treeUri: Uri) : LibraryStore {

    private val resolver get() = context.contentResolver
    private val dateDirName = Regex("""^\d{4}-\d{2}-\d{2}$""")

    private fun root(): DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri)
            ?: throw LibraryUnavailableException("Speicherordner nicht erreichbar")

    override fun list(): List<RecordingEntry> {
        val dateDirs = try {
            root().listFiles().filter { it.isDirectory && it.name?.matches(dateDirName) == true }
        } catch (e: SecurityException) {
            throw LibraryUnavailableException("Berechtigung verloren")
        }

        return dateDirs.flatMap { dir ->
            val children = dir.listFiles()
            val byBase = children.groupBy { it.name?.substringBeforeLast('.') ?: "" }
            byBase.mapNotNull { (base, files) ->
                val audio = files.firstOrNull { it.name?.endsWith(".m4a") == true } ?: return@mapNotNull null
                val metaFile = files.firstOrNull { it.name?.endsWith(".json") == true }
                val meta = metaFile?.let { readMeta(it.uri) }
                RecordingEntry(
                    audioUri = audio.uri,
                    metaUri = metaFile?.uri,
                    baseName = base,
                    addedAtEpochSec = audio.lastModified() / 1000,
                    durationMs = meta?.durationMs?.takeIf { it > 0 } ?: probeDuration(audio.uri),
                    markerCount = meta?.markers?.size ?: 0,
                    dateGroup = dir.name!!,
                )
            }
        }.sortedByDescending { it.addedAtEpochSec }
    }

    private fun probeDuration(uri: Uri): Long = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        }
    }.getOrDefault(0L)

    override fun readMeta(metaUri: Uri): RecordingMeta? = runCatching {
        resolver.openInputStream(metaUri)!!.use {
            RecordingMeta.fromJson(it.readBytes().decodeToString())
        }
    }.getOrNull()

    override fun writeMeta(metaUri: Uri, meta: RecordingMeta) {
        resolver.openOutputStream(metaUri, "wt")!!.use { it.write(meta.toJson().toByteArray()) }
    }

    override fun exportLabels(entry: RecordingEntry, meta: RecordingMeta): Uri {
        val dir = findDir(entry.dateGroup) ?: root()
        val file = dir.createFile("text/plain", "${entry.baseName}.labels.txt")
            ?: error("SAF createFile failed: ${entry.baseName}.labels.txt")
        resolver.openOutputStream(file.uri, "wt")!!.use { it.write(meta.toAudacityLabels().toByteArray()) }
        return file.uri
    }

    override fun rename(entry: RecordingEntry, newBase: String) {
        runCatching {
            DocumentsContract.renameDocument(resolver, entry.audioUri, "$newBase.m4a")
        }
        entry.metaUri?.let { metaUri ->
            val renamed = runCatching {
                DocumentsContract.renameDocument(resolver, metaUri, "$newBase.json")
            }.getOrNull()
            val effectiveUri = renamed ?: metaUri
            readMeta(effectiveUri)?.let { writeMeta(effectiveUri, it.copy(file = "$newBase.m4a")) }
        }
    }

    override fun delete(entry: RecordingEntry) {
        runCatching { DocumentFile.fromSingleUri(context, entry.audioUri)?.delete() }
        entry.metaUri?.let { runCatching { DocumentFile.fromSingleUri(context, it)?.delete() } }
    }

    private fun findDir(name: String): DocumentFile? =
        root().listFiles().firstOrNull { it.isDirectory && it.name == name }
}
