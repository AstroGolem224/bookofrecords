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
