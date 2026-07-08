package com.claymachinegames.bookofrecords.ui

import com.claymachinegames.bookofrecords.domain.Marker
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerEditorTest {

    private val meta = RecordingMeta(
        file = "a.m4a", startedAt = "x",
        markers = listOf(
            Marker(timeMs = 1000, type = "speaker", label = "Alice"),
            Marker(timeMs = 2000, type = "speaker", label = "Bob"),
        ),
    )

    private fun editor(persisted: MutableList<RecordingMeta> = mutableListOf()) =
        MarkerEditor(meta) { persisted.add(it) }

    @Test
    fun selectPopulatesEditTextThenTogglingDeselectsAndClears() {
        val e = editor()
        e.select(0)
        assertEquals(0, e.selected)
        assertEquals("Alice", e.editText)
        e.select(0)
        assertEquals(-1, e.selected)
        assertEquals("", e.editText)
    }

    @Test
    fun commitPendingWritesEditedLabelOnlyWhenChanged() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.select(0)
        e.editText = "Alice Renamed"
        e.commitPending()
        assertEquals("Alice Renamed", e.meta?.markers?.get(0)?.label)
        assertEquals(1, persisted.size)
    }

    @Test
    fun commitPendingIsNoOpWhenTextUnchanged() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.select(0)
        e.commitPending()
        assertEquals(0, persisted.size)
    }

    @Test
    fun addMarkerInsertsSortedAndPersistsAndClosesSelection() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.addMarker(timeMs = 1500, label = "Carol")
        assertEquals(3, e.meta?.markers?.size)
        assertEquals("Carol", e.meta?.markers?.get(1)?.label)
        assertEquals(-1, e.selected)
        assertEquals(1, persisted.size)
    }

    @Test
    fun addMarkerBlankLabelIsNoOp() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.addMarker(timeMs = 1500, label = "   ")
        assertEquals(2, e.meta?.markers?.size)
        assertEquals(0, persisted.size)
    }

    @Test
    fun addMarkerCommitsPendingEditBeforeInserting() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.select(0)
        e.editText = "Alice Renamed"
        e.addMarker(timeMs = 1500, label = "Carol")
        assertEquals("Alice Renamed", e.meta?.markers?.get(0)?.label)
        assertEquals(2, persisted.size)
    }

    @Test
    fun deleteMarkerRemovesAndPersistsAndClearsSelection() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.select(0)
        e.deleteMarker(0)
        assertEquals(1, e.meta?.markers?.size)
        assertEquals("Bob", e.meta?.markers?.get(0)?.label)
        assertEquals(-1, e.selected)
        assertEquals("", e.editText)
        assertEquals(1, persisted.size)
    }

    @Test
    fun deleteMarkerOutOfRangeIsNoOp() {
        val persisted = mutableListOf<RecordingMeta>()
        val e = editor(persisted)
        e.deleteMarker(99)
        assertEquals(2, e.meta?.markers?.size)
        assertEquals(0, persisted.size)
    }
}
