package com.claymachinegames.bookofrecords.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository

@Composable
fun LibraryScreen(repo: RecordingRepository, onOpen: (RecordingEntry) -> Unit) {
    Text("Library — Task 8")
}
