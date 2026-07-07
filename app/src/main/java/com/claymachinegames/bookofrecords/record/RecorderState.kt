package com.claymachinegames.bookofrecords.record

import com.claymachinegames.bookofrecords.domain.Marker
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface RecState {
    data object Idle : RecState
    data class Recording(
        val baseName: String,
        val paused: Boolean,
        val elapsedMs: Long,
        val markers: List<Marker>,
    ) : RecState
}

// ponytail: process-global flow instead of bound-service ceremony; service writes, UI reads
object RecorderState {
    val state = MutableStateFlow<RecState>(RecState.Idle)
}
