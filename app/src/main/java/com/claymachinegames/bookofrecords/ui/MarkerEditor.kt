package com.claymachinegames.bookofrecords.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.claymachinegames.bookofrecords.domain.Marker
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import com.claymachinegames.bookofrecords.domain.insertMarkerSorted

/**
 * Owns marker CRUD and the pending-inline-edit state for DetailScreen. [onPersist] is called
 * with the updated RecordingMeta whenever markers change; the caller (DetailScreen) is
 * responsible for the actual write (I/O dispatcher, metaUri).
 *
 * Getippter Text ging bisher verloren, sobald man vor "Speichern" woanders hintippte oder den
 * Screen verließ. commitPending() is called from every place selection can change so that never
 * happens again (see DetailScreen's DisposableEffect and marker-row click handler).
 */
class MarkerEditor(initialMeta: RecordingMeta?, private val onPersist: (RecordingMeta) -> Unit) {
    var meta by mutableStateOf(initialMeta)
        private set
    var selected by mutableStateOf(-1)
        private set
    var editText by mutableStateOf("")

    fun updateMeta(newMeta: RecordingMeta?) {
        meta = newMeta
    }

    fun select(index: Int) {
        commitPending()
        val m = meta ?: return
        if (selected == index) {
            selected = -1
            editText = ""
        } else {
            selected = index
            editText = m.markers.getOrNull(index)?.label ?: ""
        }
    }

    fun commitPending() {
        val i = selected
        if (i < 0) return
        val current = meta?.markers?.getOrNull(i) ?: return
        val trimmed = editText.trim()
        if (trimmed != current.label) setLabel(i, trimmed, "speaker")
    }

    fun setLabel(index: Int, label: String, type: String) {
        val m = meta ?: return
        if (index !in m.markers.indices) return
        val markers = m.markers.toMutableList()
        markers[index] = markers[index].copy(label = label, type = type)
        persist(m.copy(markers = markers))
    }

    fun addMarker(timeMs: Long, label: String) {
        if (meta == null) return
        if (label.isBlank()) return
        commitPending()
        val m = meta ?: return
        val marker = Marker(timeMs = timeMs, type = "speaker", label = label.trim())
        persist(m.copy(markers = insertMarkerSorted(m.markers, marker)))
        selected = -1
    }

    fun deleteMarker(index: Int) {
        val m = meta ?: return
        if (index !in m.markers.indices) return
        val markers = m.markers.toMutableList().apply { removeAt(index) }
        persist(m.copy(markers = markers))
        selected = -1
        editText = ""
    }

    private fun persist(updated: RecordingMeta) {
        meta = updated
        onPersist(updated)
    }
}
