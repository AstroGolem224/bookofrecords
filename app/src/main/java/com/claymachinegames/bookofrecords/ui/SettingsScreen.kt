package com.claymachinegames.bookofrecords.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.claymachinegames.bookofrecords.data.Settings

@Composable
fun SettingsScreen(onTargetChanged: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var targetUri by remember { mutableStateOf(Settings.targetUri(context)) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Settings.setTarget(context, uri)
            targetUri = uri
            onTargetChanged()
        }
    }

    val currentLabel = remember(targetUri) {
        targetUri?.let { uri ->
            runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
                ?: "Ausgewählter Ordner"
        } ?: "Standard: Documents/BookofRecords"
    }

    Column(Modifier.fillMaxSize().background(Bor.bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Bor.textSecondary)
            }
            Text("Einstellungen", color = Bor.textPrimary, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(24.dp))
        Text("Speicherordner", color = Bor.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(currentLabel, color = Bor.textPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { picker.launch(null) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Ordner wählen", color = Bor.accent) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                Settings.setTarget(context, null)
                targetUri = null
                onTargetChanged()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Standard wiederherstellen", color = Bor.textSecondary) }
        Spacer(Modifier.height(16.dp))
        Text(
            "Google Drive kann hier nicht direkt ausgewählt werden — Drive stellt " +
                "keinen SAF-Ordnerzugriff bereit. Nutze „Teilen“ im Aufnahme-Detail, " +
                "um einzelne Dateien nach Drive zu senden.",
            color = Bor.textMuted, fontSize = 12.sp,
        )
    }
}
