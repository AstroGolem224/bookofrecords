package com.claymachinegames.bookofrecords.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
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
    fun buildZipRoundTripsSingleEntry() {
        val zipped = buildZip(listOf("2026-07-08/a.m4a" to "audio-bytes".toByteArray()))

        val read = readZip(zipped)

        assertEquals(setOf("2026-07-08/a.m4a"), read.keys)
        assertEquals("audio-bytes", read["2026-07-08/a.m4a"])
    }

    @Test
    fun buildZipRoundTripsMultipleEntriesWithDateGroupPrefixedPaths() {
        val zipped = buildZip(listOf(
            "2026-07-08/a.m4a" to "audio-a".toByteArray(),
            "2026-07-08/a.json" to "{}".toByteArray(),
            "2026-07-07/b.m4a" to "audio-b".toByteArray(),
        ))

        val read = readZip(zipped)

        assertEquals(3, read.size)
        assertEquals("audio-a", read["2026-07-08/a.m4a"])
        assertEquals("{}", read["2026-07-08/a.json"])
        assertEquals("audio-b", read["2026-07-07/b.m4a"])
    }

    @Test
    fun buildZipOnEmptyListProducesValidEmptyZip() {
        val zipped = buildZip(emptyList())

        assertTrue(readZip(zipped).isEmpty())
    }
}
