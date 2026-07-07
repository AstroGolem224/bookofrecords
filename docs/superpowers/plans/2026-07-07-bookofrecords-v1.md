# BookofRecords v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android voice recorder (AAC/M4A) with tap-to-mark timestamps, sidecar JSON metadata, and Audacity-label export for a desktop transcription pipeline.

**Architecture:** Single-activity Compose app, no DB — each recording is an `.m4a` plus a same-named `.json` sidecar in `Documents/BookofRecords/` (MediaStore). A foreground service owns MediaRecorder and a pause-aware marker clock; UI observes a process-global StateFlow. Design doc: `~/Dokumente/UMBRA-Notes/DDs/BookofRecords/2026-07-07_BookofRecords_Design.md`.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), AGP 8.7.3, Gradle 8.10.2, kotlinx-serialization, minSdk 29 / target 35. No Hilt, no Room, no navigation lib.

**Repo:** `/home/itiger013/Dokumente/Github/bookofrecords` (clone of https://github.com/AstroGolem224/bookofrecords.git)

---

## File Structure

```
app/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/res/values/themes.xml
  src/main/res/drawable/ic_launcher.xml
  src/main/java/com/claymachinegames/bookofrecords/
    MainActivity.kt              # permissions, sealed-class navigation
    domain/Model.kt              # Marker, RecordingMeta, JSON, Audacity export, name/time formatting
    domain/MarkerClock.kt        # pause-aware elapsed-time clock (pure Kotlin)
    record/RecorderState.kt      # process-global StateFlow service→UI
    record/RecorderService.kt    # foreground service, MediaRecorder, notification actions
    data/RecordingRepository.kt  # MediaStore: create/list/rename/delete/export
    ui/RecordScreen.kt
    ui/LibraryScreen.kt
    ui/DetailScreen.kt
  src/test/java/com/claymachinegames/bookofrecords/domain/
    ModelTest.kt
    MarkerClockTest.kt
```

---

### Task 1: Build environment (CachyOS)

Machine has `adb` but **no Java, no Gradle, no Android SDK**.

- [ ] **Step 1: Install JDK + Gradle**

```bash
sudo pacman -S --needed jdk21-openjdk gradle
java -version   # expect: openjdk 21.x
```

- [ ] **Step 2: Install Android SDK command-line tools**

```bash
mkdir -p ~/Android/Sdk/cmdline-tools
cd ~/Android/Sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-*.zip && mv cmdline-tools latest && rm commandlinetools-linux-*.zip
```

- [ ] **Step 3: Env vars (fish)**

```bash
set -Ux ANDROID_HOME ~/Android/Sdk
fish_add_path ~/Android/Sdk/cmdline-tools/latest/bin ~/Android/Sdk/platform-tools
```

- [ ] **Step 4: Install SDK packages + accept licenses**

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --list_installed   # expect the three packages
```

No commit (nothing in repo yet).

---

### Task 2: Project scaffold — build must go green

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `local.properties`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/themes.xml`, `app/src/main/res/drawable/ic_launcher.xml`
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/MainActivity.kt`

- [ ] **Step 1: Root files**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "bookofrecords"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g
android.useAndroidX=true
kotlin.code.style=official
```

`.gitignore`:
```
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
```

`local.properties` (gitignored, machine-specific):
```properties
sdk.dir=/home/itiger013/Android/Sdk
```

- [ ] **Step 2: App module**

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.claymachinegames.bookofrecords"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.claymachinegames.bookofrecords"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
```

`app/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
    <uses-permission android:name="android.permission.WAKE_LOCK"/>

    <application
        android:label="BookofRecords"
        android:icon="@drawable/ic_launcher"
        android:theme="@style/Theme.BookofRecords">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <service
            android:name=".record.RecorderService"
            android:exported="false"
            android:foregroundServiceType="microphone"/>
    </application>
</manifest>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.BookofRecords" parent="android:Theme.Material.Light.NoActionBar"/>
</resources>
```

`app/src/main/res/drawable/ic_launcher.xml` (simple mic glyph placeholder):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#B3261E"
        android:pathData="M12,14a3,3 0 0,0 3,-3V5a3,3 0 0,0 -6,0v6a3,3 0 0,0 3,3zM17,11a5,5 0 0,1 -10,0H5a7,7 0 0,0 6,6.92V21h2v-3.08A7,7 0 0,0 19,11z"/>
</vector>
```

`app/src/main/java/com/claymachinegames/bookofrecords/MainActivity.kt`:
```kotlin
package com.claymachinegames.bookofrecords

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("BookofRecords") } }
    }
}
```

- [ ] **Step 3: Gradle wrapper + build**

```bash
cd ~/Dokumente/Github/bookofrecords
gradle wrapper --gradle-version 8.10.2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "chore: Android project scaffold (Compose, minSdk 29)"
```

---

### Task 3: Domain model + JSON sidecar (TDD)

**Files:**
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/domain/Model.kt`
- Test: `app/src/test/java/com/claymachinegames/bookofrecords/domain/ModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.claymachinegames.bookofrecords.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ModelTest {

    private val meta = RecordingMeta(
        file = "2026-07-07_1930.m4a",
        startedAt = "2026-07-07T19:30:12+02:00",
        durationMs = 5_423_000,
        markers = listOf(
            Marker(timeMs = 12_500, type = "speaker", label = "Matthias"),
            Marker(timeMs = 754_000),
        ),
    )

    @Test
    fun jsonRoundtripPreservesEverything() {
        assertEquals(meta, RecordingMeta.fromJson(meta.toJson()))
    }

    @Test
    fun fromJsonToleratesUnknownFields() {
        val json = """{"version":1,"file":"a.m4a","startedAt":"x","durationMs":0,
            "markers":[],"futureField":42}"""
        assertEquals("a.m4a", RecordingMeta.fromJson(json).file)
    }

    @Test
    fun audacityLabelsUseDotDecimalAndTabSeparation() {
        val expected = "12.500000\t12.500000\tMatthias\n" +
                "754.000000\t754.000000\tnote"
        assertEquals(expected, meta.toAudacityLabels())
    }

    @Test
    fun defaultBaseNameFormat() {
        assertEquals("2026-07-07_1930", defaultBaseName(LocalDateTime.of(2026, 7, 7, 19, 30)))
    }

    @Test
    fun formatMsShowsHoursOnlyWhenNeeded() {
        assertEquals("00:05", formatMs(5_000))
        assertEquals("12:34", formatMs(754_000))
        assertEquals("1:30:23", formatMs(5_423_000))
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `./gradlew test`
Expected: FAIL — unresolved references `RecordingMeta`, `Marker`, …

- [ ] **Step 3: Implement `Model.kt`**

```kotlin
package com.claymachinegames.bookofrecords.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class Marker(
    val timeMs: Long,
    val type: String = "note",
    val label: String = "",
)

@Serializable
data class RecordingMeta(
    val version: Int = 1,
    val file: String,
    val startedAt: String,
    val durationMs: Long = 0,
    val markers: List<Marker> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    /** Audacity label track: start<TAB>end<TAB>label, point labels (start == end). */
    fun toAudacityLabels(): String = markers.joinToString("\n") { m ->
        val s = String.format(Locale.US, "%.6f", m.timeMs / 1000.0)
        "$s\t$s\t${m.label.ifEmpty { m.type }}"
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
        fun fromJson(text: String): RecordingMeta = json.decodeFromString(serializer(), text)
    }
}

fun defaultBaseName(now: LocalDateTime): String =
    now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))

fun formatMs(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
```

- [ ] **Step 4: Run tests, verify green**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, 5 tests passed

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: recording meta model, JSON sidecar, Audacity label export"
```

---

### Task 4: MarkerClock — pause-aware elapsed time (TDD)

**Files:**
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/domain/MarkerClock.kt`
- Test: `app/src/test/java/com/claymachinegames/bookofrecords/domain/MarkerClockTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.claymachinegames.bookofrecords.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerClockTest {
    private var fakeTime = 0L
    private val clock = MarkerClock { fakeTime }

    @Test
    fun elapsedCountsFromStart() {
        fakeTime = 1_000; clock.start()
        fakeTime = 4_500
        assertEquals(3_500, clock.elapsedMs())
    }

    @Test
    fun pauseFreezesElapsed() {
        fakeTime = 0; clock.start()
        fakeTime = 10_000; clock.pause()
        fakeTime = 60_000
        assertEquals(10_000, clock.elapsedMs())
    }

    @Test
    fun resumeSubtractsPausedTime() {
        fakeTime = 0; clock.start()
        fakeTime = 10_000; clock.pause()
        fakeTime = 30_000; clock.resume()
        fakeTime = 35_000
        assertEquals(15_000, clock.elapsedMs())
    }

    @Test
    fun multiplePauseCyclesAccumulate() {
        fakeTime = 0; clock.start()
        fakeTime = 5_000; clock.pause()
        fakeTime = 10_000; clock.resume()   // 5s pausiert
        fakeTime = 15_000; clock.pause()
        fakeTime = 25_000; clock.resume()   // +10s pausiert
        fakeTime = 30_000
        assertEquals(15_000, clock.elapsedMs())
    }

    @Test
    fun doublePauseAndDoubleResumeAreIdempotent() {
        fakeTime = 0; clock.start()
        fakeTime = 5_000; clock.pause()
        fakeTime = 6_000; clock.pause()
        fakeTime = 10_000; clock.resume()
        fakeTime = 11_000; clock.resume()
        fakeTime = 20_000
        assertEquals(15_000, clock.elapsedMs())
    }

    @Test
    fun notRunningReturnsZero() {
        assertEquals(0, clock.elapsedMs())
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `./gradlew test`
Expected: FAIL — `MarkerClock` unresolved

- [ ] **Step 3: Implement `MarkerClock.kt`**

```kotlin
package com.claymachinegames.bookofrecords.domain

/**
 * Marker offsets must match audio position, so paused time is excluded.
 * Inject a monotonic clock (SystemClock::elapsedRealtime in production).
 */
class MarkerClock(private val now: () -> Long) {
    private var startedAt = 0L
    private var pausedTotal = 0L
    private var pauseStartedAt = -1L
    var running = false
        private set

    fun start() {
        startedAt = now(); pausedTotal = 0; pauseStartedAt = -1; running = true
    }

    fun pause() {
        if (running && pauseStartedAt < 0) pauseStartedAt = now()
    }

    fun resume() {
        if (running && pauseStartedAt >= 0) {
            pausedTotal += now() - pauseStartedAt
            pauseStartedAt = -1
        }
    }

    fun stop() { running = false }

    fun elapsedMs(): Long {
        if (!running) return 0
        val effectiveNow = if (pauseStartedAt >= 0) pauseStartedAt else now()
        return effectiveNow - startedAt - pausedTotal
    }
}
```

- [ ] **Step 4: Run tests, verify green**

Run: `./gradlew test`
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: pause-aware marker clock"
```

---

### Task 5: RecordingRepository — MediaStore storage

**Files:**
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/data/RecordingRepository.kt`

MediaStore is not unit-testable without Robolectric — keep this layer thin, verify manually in Task 8. All pure logic already lives in `Model.kt`.

- [ ] **Step 1: Implement `RecordingRepository.kt`**

```kotlin
package com.claymachinegames.bookofrecords.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.claymachinegames.bookofrecords.domain.RecordingMeta

class RecordingFiles(val audioUri: Uri, val metaUri: Uri)

data class RecordingEntry(
    val audioUri: Uri,
    val metaUri: Uri?,
    val baseName: String,      // display name without extension
    val addedAtEpochSec: Long,
    val durationMs: Long,
    val markerCount: Int,
)

class RecordingRepository(private val context: Context) {

    private val resolver get() = context.contentResolver
    private val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    companion object {
        const val RELATIVE_PATH = "Documents/BookofRecords/"
    }

    // --- create / write ---

    fun createRecording(baseName: String): RecordingFiles = RecordingFiles(
        audioUri = insert("$baseName.m4a", "audio/mp4", pending = true),
        metaUri = insert("$baseName.json", "application/json", pending = false),
    )

    private fun insert(displayName: String, mime: String, pending: Boolean): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            if (pending) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(filesUri, values) ?: error("MediaStore insert failed: $displayName")
    }

    fun openAudioForWrite(uri: Uri): ParcelFileDescriptor =
        resolver.openFileDescriptor(uri, "w") ?: error("openFileDescriptor failed: $uri")

    /** Clear IS_PENDING after recording finished so the file becomes visible. */
    fun publish(uri: Uri) {
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
    }

    fun writeMeta(metaUri: Uri, meta: RecordingMeta) {
        resolver.openOutputStream(metaUri, "wt")!!.use { it.write(meta.toJson().toByteArray()) }
    }

    fun readMeta(metaUri: Uri): RecordingMeta? = runCatching {
        resolver.openInputStream(metaUri)!!.use {
            RecordingMeta.fromJson(it.readBytes().decodeToString())
        }
    }.getOrNull()

    // --- list ---

    fun list(): List<RecordingEntry> {
        data class Row(val uri: Uri, val name: String, val added: Long)
        val audio = mutableListOf<Row>()
        val metaByBase = mutableMapOf<String, Uri>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        resolver.query(
            filesUri, projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(RELATIVE_PATH), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(filesUri, c.getLong(0))
                val name = c.getString(1) ?: continue
                when {
                    name.endsWith(".m4a") -> audio += Row(uri, name.removeSuffix(".m4a"), c.getLong(2))
                    name.endsWith(".json") -> metaByBase[name.removeSuffix(".json")] = uri
                }
            }
        }

        return audio.map { row ->
            val metaUri = metaByBase[row.name]
            val meta = metaUri?.let { readMeta(it) }
            RecordingEntry(
                audioUri = row.uri,
                metaUri = metaUri,
                baseName = row.name,
                addedAtEpochSec = row.added,
                durationMs = meta?.durationMs?.takeIf { it > 0 } ?: probeDuration(row.uri),
                markerCount = meta?.markers?.size ?: 0,
            )
        }.sortedByDescending { it.addedAtEpochSec }
    }

    private fun probeDuration(uri: Uri): Long = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        }
    }.getOrDefault(0L)

    // --- rename / delete / export ---

    fun rename(entry: RecordingEntry, newBase: String) {
        resolver.update(entry.audioUri, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$newBase.m4a")
        }, null, null)
        entry.metaUri?.let { metaUri ->
            resolver.update(metaUri, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$newBase.json")
            }, null, null)
            readMeta(metaUri)?.let { writeMeta(metaUri, it.copy(file = "$newBase.m4a")) }
        }
    }

    fun delete(entry: RecordingEntry) {
        resolver.delete(entry.audioUri, null, null)
        entry.metaUri?.let { resolver.delete(it, null, null) }
    }

    /** Writes <base>.labels.txt next to the recording; returns its Uri. */
    // ponytail: repeated export creates "name (1).txt" duplicates — dedupe when it annoys
    fun exportLabels(entry: RecordingEntry, meta: RecordingMeta): Uri {
        val uri = insert("${entry.baseName}.labels.txt", "text/plain", pending = false)
        resolver.openOutputStream(uri, "wt")!!.use {
            it.write(meta.toAudacityLabels().toByteArray())
        }
        return uri
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: MediaStore repository (create/list/rename/delete/export)"
```

---

### Task 6: RecorderState + RecorderService

**Files:**
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/record/RecorderState.kt`
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/record/RecorderService.kt`

- [ ] **Step 1: Implement `RecorderState.kt`**

```kotlin
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
```

- [ ] **Step 2: Implement `RecorderService.kt`**

```kotlin
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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.claymachinegames.bookofrecords.MainActivity
import com.claymachinegames.bookofrecords.R
import com.claymachinegames.bookofrecords.data.RecordingFiles
import com.claymachinegames.bookofrecords.data.RecordingRepository
import com.claymachinegames.bookofrecords.domain.Marker
import com.claymachinegames.bookofrecords.domain.MarkerClock
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import com.claymachinegames.bookofrecords.domain.defaultBaseName
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
    private var paused = false
    private val clock = MarkerClock(SystemClock::elapsedRealtime)
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ticker: Job? = null

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
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { stopRecording() }   // eigene Crashes: Datei noch finalisieren
            previous?.uncaughtException(t, e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_MARKER -> addMarker()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (clock.running) return
        val baseName = defaultBaseName(LocalDateTime.now())
        val f = repo.createRecording(baseName).also { files = it }
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
            file = "$baseName.m4a",
            startedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
        repo.writeMeta(f.metaUri, meta!!)
        paused = false
        clock.start()

        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        ticker = scope.launch {
            while (true) { publishState(); delay(1000) }
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
        if (!clock.running) return
        meta = m.copy(markers = m.markers + Marker(timeMs = clock.elapsedMs()))
        files?.let { repo.writeMeta(it.metaUri, meta!!) }   // inkrementell: Crash verliert 0 Marker
        updateNotification()
        publishState()
    }

    private fun stopRecording() {
        if (!clock.running) return
        ticker?.cancel()
        runCatching { recorder?.stop() }
        recorder?.release(); recorder = null
        meta = meta?.copy(durationMs = clock.elapsedMs())
        clock.stop()
        files?.let { f ->
            meta?.let { repo.writeMeta(f.metaUri, it) }
            runCatching { fd?.close() }
            repo.publish(f.audioUri)
        }
        fd = null; files = null
        wakeLock?.let { if (it.isHeld) it.release() }
        RecorderState.state.value = RecState.Idle
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishState() {
        val m = meta ?: return
        RecorderState.state.value = RecState.Recording(
            baseName = m.file.removeSuffix(".m4a"),
            paused = paused,
            elapsedMs = clock.elapsedMs(),
            markers = m.markers,
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
        stopRecording()
        unregisterReceiver(shutdownReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 3: Compile check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: foreground recorder service with notification marker/pause/stop"
```

---

### Task 7: Record screen + MainActivity wiring + permissions

**Files:**
- Create: `app/src/main/java/com/claymachinegames/bookofrecords/ui/RecordScreen.kt`
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/MainActivity.kt` (full replace)

- [ ] **Step 1: Implement `RecordScreen.kt`**

```kotlin
package com.claymachinegames.bookofrecords.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.record.RecState
import com.claymachinegames.bookofrecords.record.RecorderService
import com.claymachinegames.bookofrecords.record.RecorderState

@Composable
fun RecordScreen(hasAudioPermission: Boolean, onOpenLibrary: () -> Unit) {
    val context = LocalContext.current
    val state by RecorderState.state.collectAsState()
    fun send(action: String) {
        context.startForegroundService(RecorderService.intent(context, action))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            is RecState.Idle -> {
                Spacer(Modifier.height(120.dp))
                Text("BookofRecords", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { send(RecorderService.ACTION_START) },
                    enabled = hasAudioPermission,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) { Text("● Aufnahme starten", style = MaterialTheme.typography.titleLarge) }
                if (!hasAudioPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text("Mikrofon-Berechtigung fehlt", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onOpenLibrary) { Text("Bibliothek") }
            }
            is RecState.Recording -> {
                Spacer(Modifier.height(48.dp))
                Text(formatMs(s.elapsedMs), style = MaterialTheme.typography.displayLarge)
                Text(
                    if (s.paused) "Pausiert" else s.baseName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { send(RecorderService.ACTION_MARKER) },
                    enabled = !s.paused,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                ) { Text("MARKER", style = MaterialTheme.typography.headlineMedium) }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = {
                        send(if (s.paused) RecorderService.ACTION_RESUME
                             else RecorderService.ACTION_PAUSE)
                    }, modifier = Modifier.weight(1f)) {
                        Text(if (s.paused) "Weiter" else "Pause")
                    }
                    OutlinedButton(
                        onClick = { send(RecorderService.ACTION_STOP) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Stop") }
                }
                Spacer(Modifier.height(24.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(s.markers.asReversed()) { m ->
                        Text(
                            "${formatMs(m.timeMs)} — ${m.label.ifEmpty { "(unbenannt)" }}",
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Replace `MainActivity.kt`**

```kotlin
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository
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
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface { App() }
            }
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
```

`LibraryScreen`/`DetailScreen` don't exist yet — add temporary stubs so this task compiles, replaced in Tasks 8/9:

```kotlin
// app/src/main/java/com/claymachinegames/bookofrecords/ui/LibraryScreen.kt
package com.claymachinegames.bookofrecords.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository

@Composable
fun LibraryScreen(repo: RecordingRepository, onOpen: (RecordingEntry) -> Unit) {
    Text("Library — Task 8")
}
```

```kotlin
// app/src/main/java/com/claymachinegames/bookofrecords/ui/DetailScreen.kt
package com.claymachinegames.bookofrecords.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository

@Composable
fun DetailScreen(repo: RecordingRepository, entry: RecordingEntry, onClose: () -> Unit) {
    Text("Detail — Task 9")
}
```

- [ ] **Step 3: Build + install + first manual test**

```bash
./gradlew installDebug
adb shell am start -n com.claymachinegames.bookofrecords/.MainActivity
```

Manual check (device connected via adb):
1. Permission dialogs appear (microphone, notifications); grant both.
2. Start recording, speak, tap MARKER twice, pause/resume, stop.
3. Verify files:
```bash
adb shell ls /sdcard/Documents/BookofRecords/
# expect: 2026-07-07_HHMM.m4a + .json
adb shell cat /sdcard/Documents/BookofRecords/*.json
# expect: markers array with 2 entries, durationMs > 0
```
4. Record again with screen off — set markers via notification buttons; verify JSON afterwards.
5. Pull and play: `adb pull /sdcard/Documents/BookofRecords/ /tmp/bor && mpv /tmp/bor/*.m4a`

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: record screen, permission flow, screen navigation"
```

---

### Task 8: Library screen

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/ui/LibraryScreen.kt` (full replace)

- [ ] **Step 1: Implement**

```kotlin
package com.claymachinegames.bookofrecords.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository
import com.claymachinegames.bookofrecords.domain.formatMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LibraryScreen(repo: RecordingRepository, onOpen: (RecordingEntry) -> Unit) {
    var entries by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { repo.list() }
        loaded = true
    }

    val dateFmt = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Bibliothek", style = MaterialTheme.typography.headlineMedium)
        if (loaded && entries.isEmpty()) {
            Text("Noch keine Aufnahmen.", Modifier.padding(top = 24.dp))
        }
        LazyColumn(Modifier.padding(top = 16.dp)) {
            items(entries, key = { it.audioUri }) { e ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen(e) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.baseName, style = MaterialTheme.typography.titleMedium)
                        Row {
                            Text(
                                dateFmt.format(Instant.ofEpochSecond(e.addedAtEpochSec)) +
                                    "  ·  ${formatMs(e.durationMs)}  ·  ${e.markerCount} Marker",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build + manual test**

```bash
./gradlew installDebug
```
Open Bibliothek — recordings from Task 7 appear with date, duration, marker count. Tap → stub Detail.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: library screen"
```

---

### Task 9: Detail screen — playback, marker labeling, export, share

**Files:**
- Modify: `app/src/main/java/com/claymachinegames/bookofrecords/ui/DetailScreen.kt` (full replace)

- [ ] **Step 1: Implement**

```kotlin
package com.claymachinegames.bookofrecords.ui

import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.claymachinegames.bookofrecords.data.RecordingEntry
import com.claymachinegames.bookofrecords.data.RecordingRepository
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import com.claymachinegames.bookofrecords.domain.formatMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext

@Composable
fun DetailScreen(repo: RecordingRepository, entry: RecordingEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var meta by remember { mutableStateOf<RecordingMeta?>(null) }
    var selected by remember { mutableIntStateOf(-1) }
    var positionMs by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(entry.baseName) }

    val player = remember {
        MediaPlayer().apply {
            setDataSource(context, entry.audioUri)
            prepare()
            setOnCompletionListener { playing = false }
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(Unit) {
        meta = withContext(Dispatchers.IO) { entry.metaUri?.let { repo.readMeta(it) } }
            ?: RecordingMeta(file = "${entry.baseName}.m4a", startedAt = "",
                             durationMs = entry.durationMs)
    }
    LaunchedEffect(playing) {
        while (playing) { positionMs = player.currentPosition; delay(250) }
    }

    fun saveMeta(updated: RecordingMeta) {
        meta = updated
        entry.metaUri?.let { uri ->
            scope.launch(Dispatchers.IO) { repo.writeMeta(uri, updated) }
        }
    }
    fun setLabel(index: Int, label: String, type: String) {
        val m = meta ?: return
        val markers = m.markers.toMutableList()
        markers[index] = markers[index].copy(label = label, type = type)
        saveMeta(m.copy(markers = markers))
    }

    val duration = player.duration.coerceAtLeast(1)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(entry.baseName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        // --- Playback ---
        Text("${formatMs(positionMs.toLong())} / ${formatMs(duration.toLong())}")
        Slider(
            value = positionMs.toFloat() / duration,
            onValueChange = {
                positionMs = (it * duration).toInt()
                player.seekTo(positionMs)
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (playing) player.pause() else player.start()
                playing = !playing
            }) { Text(if (playing) "Pause" else "Play") }
            OutlinedButton(onClick = {
                meta?.let { m ->
                    val uri = repo.exportLabels(entry, m)
                    Toast.makeText(context, "Exportiert: $uri", Toast.LENGTH_SHORT).show()
                }
            }) { Text(".txt") }
            OutlinedButton(onClick = {
                val uris = arrayListOf(entry.audioUri)
                entry.metaUri?.let { uris.add(it) }
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Teilen"))
            }) { Text("Teilen") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showRename = true }) { Text("Umbenennen") }
            TextButton(onClick = { showDelete = true }) { Text("Löschen") }
        }

        Spacer(Modifier.height(16.dp))

        // --- Speaker chips: bereits vergebene Namen schnell zuweisen ---
        val speakerNames = meta?.markers
            ?.filter { it.type == "speaker" && it.label.isNotBlank() }
            ?.map { it.label }?.distinct().orEmpty()
        if (selected >= 0 && speakerNames.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                speakerNames.forEach { name ->
                    SuggestionChip(
                        onClick = { setLabel(selected, name, "speaker") },
                        label = { Text(name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }

        // --- Marker list ---
        var editText by remember { mutableStateOf("") }
        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(meta?.markers.orEmpty()) { i, m ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable {
                            selected = if (selected == i) -1 else i
                            editText = m.label
                        },
                    colors = if (selected == i)
                        CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)
                    else CardDefaults.cardColors(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatMs(m.timeMs),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clickable {
                                    player.seekTo(m.timeMs.toInt())
                                    positionMs = m.timeMs.toInt()
                                },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(m.label.ifEmpty { "(unbenannt)" })
                        }
                        if (selected == i) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                label = { Text("Sprecher / Notiz") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(onClick = {
                                setLabel(i, editText.trim(), "speaker")
                                selected = -1
                            }) { Text("Speichern") }
                        }
                    }
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Aufnahme löschen?") },
            text = { Text(entry.baseName) },
            confirmButton = {
                TextButton(onClick = {
                    player.release()
                    scope.launch(Dispatchers.IO) { repo.delete(entry) }
                    onClose()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Abbrechen") } },
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Umbenennen") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it })
            },
            confirmButton = {
                TextButton(onClick = {
                    val newBase = renameText.trim()
                    if (newBase.isNotEmpty() && newBase != entry.baseName) {
                        scope.launch(Dispatchers.IO) { repo.rename(entry, newBase) }
                    }
                    showRename = false
                    onClose()   // zurück zur Library, die neu lädt
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Abbrechen") } },
        )
    }
}
```

- [ ] **Step 2: Build + manual test**

```bash
./gradlew installDebug
```
1. Open a recording with markers. Play, seek via slider, tap marker time → jumps.
2. Select marker → type name → Speichern. Select another → chip with first name appears → tap chip.
3. `.txt` export → `adb shell cat "/sdcard/Documents/BookofRecords/<name>.labels.txt"` — tab-separated lines.
4. Teilen → share sheet shows m4a + json.
5. Umbenennen → both files renamed (`adb shell ls`), JSON `file` field updated.
6. Löschen → both files gone from `adb shell ls`.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: detail screen with playback, marker labeling, export, share, delete"
```

---

### Task 10: README, final verification, push

**Files:**
- Create: `README.md`

- [ ] **Step 1: README.md**

```markdown
# BookofRecords

Android voice recorder for later transcription. Records AAC/M4A with
tap-to-mark timestamps (speaker changes, notes).

## Output

`Documents/BookofRecords/` on device:

- `2026-07-07_1930.m4a` — mono AAC, 44.1 kHz, 96 kbps
- `2026-07-07_1930.json` — sidecar metadata:

​```json
{
  "version": 1,
  "file": "2026-07-07_1930.m4a",
  "startedAt": "2026-07-07T19:30:12+02:00",
  "durationMs": 5423000,
  "markers": [{ "timeMs": 754000, "type": "speaker", "label": "Matthias" }]
}
​```

- `<name>.labels.txt` — optional Audacity label track export (tab-separated).

Marker times exclude paused stretches, so they match audio position.

## Build

​```bash
./gradlew installDebug
​```

Requires Android SDK (platform 35), JDK 17+. minSdk 29.

## Why M4A, not MP3?

Android has no built-in MP3 encoder. AAC/M4A is native, better quality per
bitrate, and every transcription tool (Whisper etc.) reads it directly.
```

(Remove the `​` zero-width guards around the inner code fences when writing the file.)

- [ ] **Step 2: Full test suite + build**

```bash
./gradlew test assembleDebug
```
Expected: BUILD SUCCESSFUL, all unit tests green.

- [ ] **Step 3: Final manual checklist (on device)**

1. Fresh install (`adb uninstall` first): permission flow works; deny mic → button disabled with hint.
2. 5+ min recording, screen off, markers via notification. JSON marker count matches taps.
3. Pause 1 min mid-recording → marker after resume has offset matching audible position (spot-check in Detail playback).
4. Kill app via swipe during recording → notification stays, recording continues (service lives).
5. `adb pull` m4a+json → m4a plays on desktop, `.labels.txt` imports in Audacity (Datei → Import → Labels).

- [ ] **Step 4: Push**

```bash
git push -u origin main
```

- [ ] **Step 5: Close the tracking task in the internal PM tool**

---

## Deliberately skipped (v1)

Settings screen (bitrate hardcoded 96 kbps), search, cloud sync, in-app
transcription, tags, MP3 export, segment rotation (`setNextOutputFile`) —
upgrade path documented in design doc if m4a corruption ever shows up in
practice.

## Tracking

Tracked in the internal PM tool (project BookofRecords); one implementation
task per execution run, closed in Task 10 Step 5.
