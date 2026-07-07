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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository
import com.claymachinegames.bookofrecords.ui.BorTheme
import com.claymachinegames.bookofrecords.ui.DetailScreen
import com.claymachinegames.bookofrecords.ui.LibraryScreen
import com.claymachinegames.bookofrecords.ui.RecordScreen

sealed interface Screen {
    data object Record : Screen
    data object Library : Screen
    data class Detail(val entry: RecordingEntry) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BorTheme { Surface { App() } }
        }
    }
}

@Composable
private fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { RecordingRepository(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Record) }
    var hasAudio by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
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
    }

    when (val s = screen) {
        Screen.Record -> RecordScreen(
            hasAudioPermission = hasAudio,
            onOpenLibrary = { screen = Screen.Library },
        )
        Screen.Library -> {
            BackHandler { screen = Screen.Record }
            LibraryScreen(repo = repo, onOpen = { screen = Screen.Detail(it) })
        }
        is Screen.Detail -> {
            BackHandler { screen = Screen.Library }
            DetailScreen(repo = repo, entry = s.entry, onClose = { screen = Screen.Library })
        }
    }
}
