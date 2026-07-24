package com.claymachinegames.bookofrecords

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.claymachinegames.bookofrecords.data.LibraryStore
import com.claymachinegames.bookofrecords.data.Mover
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository
import com.claymachinegames.bookofrecords.data.SafLibrary
import com.claymachinegames.bookofrecords.data.Settings
import com.claymachinegames.bookofrecords.domain.appendLevel
import com.claymachinegames.bookofrecords.record.RecState
import com.claymachinegames.bookofrecords.record.RecorderState
import com.claymachinegames.bookofrecords.ui.Bor
import com.claymachinegames.bookofrecords.ui.BorTheme
import com.claymachinegames.bookofrecords.ui.DetailScreen
import com.claymachinegames.bookofrecords.ui.HideScreen
import com.claymachinegames.bookofrecords.ui.LibraryScreen
import com.claymachinegames.bookofrecords.ui.RecordScreen
import com.claymachinegames.bookofrecords.ui.SettingsScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Main : Screen
    data object Settings : Screen
    data class Detail(val entry: RecordingEntry) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-Edge auch vor API 35, explizit dunkle Bars (helle Icons) — der Hide-Screen
        // muss vollflächig schwarz sein, ohne opake/helle Systemleisten
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            BorTheme {
                Surface {
                    var hidden by rememberSaveable { mutableStateOf(false) }
                    // targetSdk 35 erzwingt Edge-to-Edge: bg füllt hinter die Systembars,
                    // Inhalt bleibt in der Safe-Zone (inkl. IME); Hide-Overlay liegt UNGEPOLSTERT
                    // darüber und hält App() komponiert (Debounce/Pager/Listen überleben)
                    Box(Modifier.fillMaxSize().background(Bor.bg)) {
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                            App(onHide = { hidden = true })
                        }
                        if (hidden) HideScreen(onExit = { hidden = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun App(onHide: () -> Unit) {
    val context = LocalContext.current
    val localRepo = remember { RecordingRepository(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var mainPage by rememberSaveable { mutableIntStateOf(0) }
    var libraryRevision by remember { mutableIntStateOf(0) }
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
            if (wasRecording && !isRecording) {
                sweepNow()   // Trigger: Recording -> Idle
                libraryRevision++   // deckt Stop per Notification bei sichtbarer Bibliothek ab
            }
            wasRecording = isRecording
        }
    }

    // Waveform-Historie lebt hier statt im RecordScreen: überlebt Pager-Disposal UND
    // Detail-/Settings-Ausflüge während laufender Aufnahme; Reset je Aufnahme (baseName)
    var waveHistory by remember { mutableStateOf(listOf<Float>()) }
    var waveWidthPx by remember { mutableIntStateOf(0) }
    // Cap in Komposition berechnet (Density bleibt frisch, z. B. nach Font-Scale-Wechsel);
    // rememberUpdatedState, damit der langlebige Collector immer den aktuellen Wert liest.
    // width=0 (vor erstem onSizeChanged): unbegrenzt sammeln statt auf 1 trimmen
    val waveCapBars by rememberUpdatedState(
        if (waveWidthPx == 0) Int.MAX_VALUE
        else with(LocalDensity.current) { (waveWidthPx / 3.dp.toPx()).toInt() }.coerceAtLeast(1)
    )
    LaunchedEffect(Unit) {
        var lastBase: String? = null
        RecorderState.state
            .map { it as? RecState.Recording }
            .filterNotNull()
            .distinctUntilChangedBy { it.baseName to it.elapsedMs }
            .collect { rec ->
                if (rec.baseName != lastBase) { waveHistory = emptyList(); lastBase = rec.baseName }
                waveHistory = appendLevel(waveHistory, rec.level, waveCapBars)
            }
    }

    when (val s = screen) {
        Screen.Main -> {
            val scope = rememberCoroutineScope()
            val pagerState = rememberPagerState(initialPage = mainPage, pageCount = { 2 })
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { mainPage = it }
            }
            // Back auf der Bibliothek → zurück zum Recorder; Selection-Back der Library ist im
            // Page-Content registriert (nach diesem Handler) und gewinnt solange sie aktiv ist
            BackHandler(enabled = pagerState.settledPage == 1) {
                scope.launch { pagerState.animateScrollToPage(0) }
            }
            // beyondViewportPageCount=1: beide Seiten bleiben komponiert — Titel-Debounce und
            // Library-State überleben das Swipen; Offscreen-Kosten: ~4Hz-Recomposition, akzeptiert
            HorizontalPager(state = pagerState, beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> RecordScreen(
                        hasAudioPermission = hasAudio,
                        waveHistory = waveHistory,
                        onWaveWidthPx = { waveWidthPx = it },
                        onOpenLibrary = { scope.launch { pagerState.animateScrollToPage(1) } },
                        onOpenSettings = { screen = Screen.Settings },
                        onHide = onHide,
                    )
                    else -> LibraryScreen(
                        store = libraryStore,
                        isActive = pagerState.settledPage == 1,
                        refreshToken = libraryRevision,
                        onOpen = { screen = Screen.Detail(it) },
                        onNewRecording = { scope.launch { pagerState.animateScrollToPage(0) } },
                        onOpenSettings = { screen = Screen.Settings },
                        onSweep = { sweepNow() },
                    )
                }
            }
        }
        is Screen.Detail -> {
            BackHandler { screen = Screen.Main }
            DetailScreen(store = libraryStore, entry = s.entry, onClose = { screen = Screen.Main })
        }
        Screen.Settings -> {
            BackHandler { screen = Screen.Main }
            SettingsScreen(
                onTargetChanged = { targetVersion++ },
                onClose = { screen = Screen.Main },
            )
        }
    }
}
