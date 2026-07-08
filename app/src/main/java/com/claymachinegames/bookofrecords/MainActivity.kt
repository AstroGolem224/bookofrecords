package com.claymachinegames.bookofrecords

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.claymachinegames.bookofrecords.data.LibraryStore
import com.claymachinegames.bookofrecords.data.Mover
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository
import com.claymachinegames.bookofrecords.data.SafLibrary
import com.claymachinegames.bookofrecords.data.Settings
import com.claymachinegames.bookofrecords.record.RecState
import com.claymachinegames.bookofrecords.record.RecorderState
import com.claymachinegames.bookofrecords.ui.Bor
import com.claymachinegames.bookofrecords.ui.BorTheme
import com.claymachinegames.bookofrecords.ui.DetailScreen
import com.claymachinegames.bookofrecords.ui.LibraryScreen
import com.claymachinegames.bookofrecords.ui.RecordScreen
import com.claymachinegames.bookofrecords.ui.SettingsScreen
import kotlinx.coroutines.flow.collectLatest

sealed interface Screen {
    data object Record : Screen
    data object Library : Screen
    data object Settings : Screen
    data class Detail(val entry: RecordingEntry) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BorTheme {
                Surface {
                    // targetSdk 35 erzwingt Edge-to-Edge: bg füllt hinter die Systembars,
                    // Inhalt bleibt in der Safe-Zone (inkl. IME)
                    Box(
                        Modifier.fillMaxSize().background(Bor.bg)
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) { App() }
                }
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val localRepo = remember { RecordingRepository(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Record) }
    var hasAudio by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var targetVersion by remember { mutableStateOf(0) }   // bumped to force libraryStore recompute
    val targetUri = remember(targetVersion) { Settings.targetUri(context) }
    val libraryStore: LibraryStore = remember(targetUri) {
        targetUri?.let { SafLibrary(context, it) } ?: localRepo
    }
    val mover = remember { Mover(context, localRepo) }

    suspend fun sweepNow() {
        targetUri?.let { uri ->
            val active = (RecorderState.state.value as? RecState.Recording)?.baseName
            runCatching { mover.sweep(uri, active) }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> hasAudio = grants[Manifest.permission.RECORD_AUDIO] == true || hasAudio }

    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) launcher.launch(wanted.toTypedArray())
        sweepNow()   // Trigger: App-Start
    }

    LaunchedEffect(Unit) {
        var wasRecording = false
        RecorderState.state.collectLatest { s ->
            val isRecording = s is RecState.Recording
            if (wasRecording && !isRecording) sweepNow()   // Trigger: Recording -> Idle
            wasRecording = isRecording
        }
    }

    when (val s = screen) {
        Screen.Record -> RecordScreen(
            hasAudioPermission = hasAudio,
            onOpenLibrary = { screen = Screen.Library },
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Library -> {
            BackHandler { screen = Screen.Record }
            LibraryScreen(
                store = libraryStore,
                onOpen = { screen = Screen.Detail(it) },
                onNewRecording = { screen = Screen.Record },
                onOpenSettings = { screen = Screen.Settings },
                onSweep = { sweepNow() },
            )
        }
        is Screen.Detail -> {
            BackHandler { screen = Screen.Library }
            DetailScreen(store = libraryStore, entry = s.entry, onClose = { screen = Screen.Library })
        }
        Screen.Settings -> {
            BackHandler { screen = Screen.Record }
            SettingsScreen(
                onTargetChanged = { targetVersion++ },
                onClose = { screen = Screen.Record },
            )
        }
    }
}
