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
