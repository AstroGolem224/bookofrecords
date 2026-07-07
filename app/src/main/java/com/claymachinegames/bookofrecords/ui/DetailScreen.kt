package com.claymachinegames.bookofrecords.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository

@Composable
fun DetailScreen(repo: RecordingRepository, entry: RecordingEntry, onClose: () -> Unit) {
    Text("Detail — Task 9")
}
