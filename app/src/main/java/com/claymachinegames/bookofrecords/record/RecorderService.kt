package com.claymachinegames.bookofrecords.record

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.claymachinegames.bookofrecords.MainActivity
import com.claymachinegames.bookofrecords.R
import com.claymachinegames.bookofrecords.data.RecordingFiles
import com.claymachinegames.bookofrecords.data.RecordingRepository
import com.claymachinegames.bookofrecords.domain.Marker
import com.claymachinegames.bookofrecords.domain.MarkerClock
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import com.claymachinegames.bookofrecords.domain.dateFolder
import com.claymachinegames.bookofrecords.domain.levelFraction
import com.claymachinegames.bookofrecords.domain.recordingBaseName
import com.claymachinegames.bookofrecords.domain.sanitizeTitle
import com.claymachinegames.bookofrecords.domain.formatMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_MARKER = "marker"
        const val ACTION_STOP = "stop"
        const val ACTION_SET_TITLE = "set_title"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 1

        fun intent(context: Context, action: String): Intent =
            Intent(context, RecorderService::class.java).setAction(action)
    }

    private lateinit var repo: RecordingRepository
    private var recorder: MediaRecorder? = null
    private var files: RecordingFiles? = null
    private var fd: ParcelFileDescriptor? = null
    private var meta: RecordingMeta? = null
    private var startedLocal: LocalDateTime? = null
    private var title = ""
    private var level = 0f
    private var paused = false
    private val clock = MarkerClock(SystemClock::elapsedRealtime)
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ticker: Job? = null
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null

    // Akku leer → System-Shutdown: Recorder sauber stoppen, sonst fehlt das moov-Atom
    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { stopRecording() }
    }

    override fun onCreate() {
        super.onCreate()
        repo = RecordingRepository(this)
        registerReceiver(shutdownReceiver, IntentFilter(Intent.ACTION_SHUTDOWN))
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Aufnahme", NotificationManager.IMPORTANCE_LOW)
        )
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { stopRecording() }   // eigene Crashes: Datei noch finalisieren
            previousExceptionHandler?.uncaughtException(t, e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_MARKER -> addMarker()
            ACTION_STOP -> {
                intent.getStringExtra(EXTRA_TITLE)?.let { title = sanitizeTitle(it) }
                stopRecording()
            }
            ACTION_SET_TITLE -> {
                title = sanitizeTitle(intent.getStringExtra(EXTRA_TITLE) ?: "")
                publishState()
            }
        }
        // startForegroundService-Vertrag: läuft danach nichts, Service sofort beenden,
        // sonst ForegroundServiceDidNotStartInTimeException nach ~10s
        if (!clock.running) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (clock.running) return
        try {
            // startForeground zuerst: muss auch bei fehlschlagendem Setup innerhalb der Frist passieren
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            startedLocal = LocalDateTime.now()
            val baseName = recordingBaseName(startedLocal!!, "")
            val f = repo.createRecording(baseName, dateFolder(startedLocal!!)).also { files = it }
            val pfd = repo.openAudioForWrite(f.audioUri).also { fd = it }

            recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this)
                        else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(96_000)
                setOutputFile(pfd.fileDescriptor)
                prepare()
                start()
            }

            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bookofrecords:recording")
                .apply { acquire(5 * 60 * 60 * 1000L) }   // 5h Obergrenze

            meta = RecordingMeta(
                file = "${f.actualBase}.m4a",
                startedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
            repo.writeMeta(f.metaUri, meta!!)
            paused = false
            title = ""
            level = 0f
            clock.start()

            ticker = scope.launch {
                var i = 0
                while (true) {
                    level = if (paused) 0f
                            else runCatching { levelFraction(recorder?.maxAmplitude ?: 0) }.getOrDefault(0f)
                    publishState()
                    if (i % 4 == 0) updateNotification()
                    i++
                    delay(250)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Aufnahme konnte nicht gestartet werden", Toast.LENGTH_LONG).show()
            // Rollback: nichts Halbfertiges zurücklassen (Recorder, fd, MediaStore-Zeilen)
            runCatching { recorder?.release() }; recorder = null
            runCatching { fd?.close() }; fd = null
            wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
            files?.let { repo.discard(it) }
            files = null; meta = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseRecording() {
        if (!clock.running || paused) return
        recorder?.pause()
        clock.pause()
        paused = true
        updateNotification()
        publishState()
    }

    private fun resumeRecording() {
        if (!clock.running || !paused) return
        recorder?.resume()
        clock.resume()
        paused = false
        updateNotification()
        publishState()
    }

    private fun addMarker() {
        val m = meta ?: return
        if (!clock.running || paused) return   // konsistent mit UI: kein Marker während Pause
        meta = m.copy(markers = m.markers + Marker(timeMs = clock.elapsedMs()))
        files?.let { runCatching { repo.writeMeta(it.metaUri, meta!!) } }   // inkrementell: Crash verliert 0 Marker
        updateNotification()
        publishState()
    }

    private fun stopRecording() {
        if (!clock.running) return
        ticker?.cancel()
        val stopped = runCatching { recorder?.stop() }.isSuccess
        recorder?.release(); recorder = null
        meta = meta?.copy(durationMs = clock.elapsedMs())
        clock.stop()
        files?.let { f ->
            if (stopped) {
                val finalBase = recordingBaseName(startedLocal ?: LocalDateTime.now(), title)
                val currentBase = meta?.file?.removeSuffix(".m4a")
                if (currentBase != null && finalBase != currentBase) {
                    runCatching { repo.renameFiles(f, currentBase, finalBase) }
                        .onSuccess { meta = meta?.copy(file = "$finalBase.m4a") }
                }
                meta?.let { runCatching { repo.writeMeta(f.metaUri, it) } }
                runCatching { fd?.close() }
                runCatching { repo.publish(f.audioUri) }
            } else {
                // stop() warf → keine gültigen Samples, moov fehlt: nicht publishen, aufräumen
                runCatching { fd?.close() }
                repo.discard(f)
            }
        }
        fd = null; files = null; meta = null; paused = false
        wakeLock?.let { if (it.isHeld) it.release() }
        RecorderState.state.value = RecState.Idle
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishState() {
        if (!clock.running) return
        val m = meta ?: return
        RecorderState.state.value = RecState.Recording(
            baseName = m.file.removeSuffix(".m4a"),
            title = title,
            paused = paused,
            elapsedMs = clock.elapsedMs(),
            markers = m.markers,
            level = level,
        )
    }

    private fun buildNotification(): android.app.Notification {
        fun action(label: String, act: String) = NotificationCompat.Action(
            0, label,
            PendingIntent.getService(
                this, act.hashCode(), intent(this, act),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val markerCount = meta?.markers?.size ?: 0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (paused) "Pausiert" else "Aufnahme läuft")
            .setContentText("${formatMs(clock.elapsedMs())} · $markerCount Marker")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action("Marker", ACTION_MARKER))
            .addAction(if (paused) action("Weiter", ACTION_RESUME) else action("Pause", ACTION_PAUSE))
            .addAction(action("Stop", ACTION_STOP))
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        runCatching { stopRecording() }   // Receiver/Scope-Cleanup darf nie ausfallen
        unregisterReceiver(shutdownReceiver)
        scope.cancel()
        Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
