package com.claymachinegames.bookofrecords.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class MoverTest {

    // Uri.EMPTY is a real null under the unit-test android.jar; sweepable() never reads
    // the Uri fields, so a mock placeholder is enough to satisfy the non-null type.
    private val dummyUri: Uri = mock(Uri::class.java)

    private fun entry(baseName: String) = RecordingEntry(
        audioUri = dummyUri, metaUri = null, baseName = baseName,
        addedAtEpochSec = 0, durationMs = 1000, markerCount = 0, dateGroup = "2026-07-08",
    )

    @Test
    fun sweepableExcludesTheActiveRecording() {
        val entries = listOf(entry("a"), entry("b"), entry("c"))
        assertEquals(listOf("a", "c"), sweepable(entries, activeBaseName = "b").map { it.baseName })
    }

    @Test
    fun sweepableKeepsEverythingWhenNothingIsRecording() {
        val entries = listOf(entry("a"), entry("b"))
        assertEquals(listOf("a", "b"), sweepable(entries, activeBaseName = null).map { it.baseName })
    }

    @Test
    fun sweepableOnEmptyListIsEmpty() {
        assertEquals(emptyList<RecordingEntry>(), sweepable(emptyList(), activeBaseName = "x"))
    }
}
