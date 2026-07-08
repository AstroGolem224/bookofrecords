package com.claymachinegames.bookofrecords.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ExportZipTest {

    private fun readZip(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes().decodeToString()
                entry = zip.nextEntry
            }
        }
        return result
    }

    @Test
    fun writeZipRoundTripsSingleEntry() {
        val output = ByteArrayOutputStream()

        writeZip(output, listOf("2026-07-08/a.m4a" to { ByteArrayInputStream("audio-bytes".toByteArray()) }))

        val read = readZip(output.toByteArray())
        assertEquals(setOf("2026-07-08/a.m4a"), read.keys)
        assertEquals("audio-bytes", read["2026-07-08/a.m4a"])
    }

    @Test
    fun writeZipRoundTripsMultipleEntriesWithDateGroupPrefixedPaths() {
        val output = ByteArrayOutputStream()

        writeZip(output, listOf(
            "2026-07-08/a.m4a" to { ByteArrayInputStream("audio-a".toByteArray()) },
            "2026-07-08/a.json" to { ByteArrayInputStream("{}".toByteArray()) },
            "2026-07-07/b.m4a" to { ByteArrayInputStream("audio-b".toByteArray()) },
        ))

        val read = readZip(output.toByteArray())
        assertEquals(3, read.size)
        assertEquals("audio-a", read["2026-07-08/a.m4a"])
        assertEquals("{}", read["2026-07-08/a.json"])
        assertEquals("audio-b", read["2026-07-07/b.m4a"])
    }

    @Test
    fun writeZipOnEmptyListProducesValidEmptyZip() {
        val output = ByteArrayOutputStream()

        writeZip(output, emptyList())

        assertTrue(readZip(output.toByteArray()).isEmpty())
    }
}
