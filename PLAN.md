# Plan: Recorder-Screen-Redesign (Stil-Adaption des Referenz-Screenshots)

_Round 2 — revised nach Kimi-Review_

## Ziel

Den `RecState.Recording`-Zweig von `RecordScreen.kt` optisch an das Referenz-Design anlehnen
(dunkles Theme, Live-Waveform, große Akzent-Zeit, segmentiertes Pegel-Meter, runde Button-Zeile),
ohne neue Datenquellen im Service zu erfinden. Idle-Zweig, Library, Detail, Settings bleiben unverändert.

## Rahmenbedingungen (aus Bestandsaufnahme)

- Verfügbare Live-Daten: `RecState.Recording(baseName, title, elapsedMs, paused, level: Float 0..1 log, markers)`.
  Level-Poll im Service ~alle 250ms; bei `paused` setzt der Service `level = 0f` und `elapsedMs` friert ein.
- Aufnahme ist **mono** (M4A/AAC 96 kbps). Kein Stereo, keine PCM-Daten.
- Theme existiert (`Bor`-Objekt, dunkel, Akzent `#FF453A`). Wird beibehalten, nur ergänzt.
- Titel-Feld und Marker-Liste sind BoR-Kernfeatures und bleiben auf dem Screen.

## Nicht-Ziele (bewusst weggelassen / deklarierte Einschränkungen)

- Kein Stereo-Meter (App ist mono) → ein Meter, volle Breite, mit dB-Skala.
- Kein "HIDE"-Button (kein Äquivalent-Feature).
- Kein echtes Sample-Waveform → Balken-Waveform aus Level-Historie.
- Keine Änderung an RecorderService/RecState — reines UI-Paket.
- Format-Chip zeigt reale Werte: **"M4A · 96 kbps · mono"** (einheitlich, auch in Schritt 3.6).
- **Rotation setzt die Waveform-Historie zurück** (remember-basiert) — akzeptiert für v1;
  Upgrade-Pfad: Historie neben `RecorderState` prozessweit halten.
- Layout ist portrait-orientiert; Landscape wird nicht gesondert gestaltet, darf aber nichts crashen
  (obere Sektion kompakt, Marker-Liste bekommt `weight(1f)` und damit den Rest).

## Änderungen

### 1. `BorTheme.kt` — Farbergänzungen
Neue Werte im `Bor`-Objekt (keine bestehenden ändern):
- `waveCold = Color(0xFF4FC3F7)` (kaltes Blau, Waveform-Anfang)
- `waveWarm = accent` (Wiederverwendung)
- `levelOrange = Color(0xFFE07030)` (Meter-Zone ≥ 0.85)

### 2. Neue reine Logik in `domain/Model.kt` (testbar)

- `fun appendLevel(history: List<Float>, level: Float, cap: Int): List<Float>` —
  hängt an, trimmt vorn auf `cap` (cap ≤ 0 → leere Liste). Unit-getestet.
- `fun dbTickFraction(db: Int): Float = log10(1.0 + 9.0 * 10.0.pow(db / 20.0)).toFloat()` —
  inverse Abbildung der log-Skala von `levelFraction`; positioniert die dB-Labels **korrekt**
  (nicht linear). Unit-getestet gegen bekannte Werte (0 dB → 1.0, −60 dB ≈ 0.004…).
- `fun meterSegmentColor(fraction: Float)`-Zuordnung als testbare `when`-Logik
  (Rückgabe als Enum/Zone `MeterZone { GREEN, YELLOW, AMBER, ORANGE }`, UI mappt Zone→Color;
  so bleibt der Test Compose-frei).

### 3. Neue Datei `ui/RecordComponents.kt` — Composables

**a) `LiveWaveform(levels: List<Float>, modifier)`**
- Canvas: vertikal gespiegelte Balken (2dp breit, 1dp Lücke), neueste rechts.
- Horizontaler Farbverlauf `waveCold`→`waveWarm` (`Brush.horizontalGradient`).
- Rechts Playhead: dünne vertikale Linie + Punkt oben in `accent`.
- Höhe 80dp. Kapazität liefert der Aufrufer (siehe 4.3).

**b) `DbMeter(level: Float, modifier)`**
- ~32 Segmente, Zone je Segment-Position via `meterSegmentColor` (grün <0.6, gelb <0.75,
  amber <0.85, orange ≥0.85).
- Darunter Labels `-36 -24 -12 0 dB` (Monospace, `textMuted`, 10sp), horizontal positioniert
  bei `dbTickFraction(db) * meterBreite` — log-korrekt. **Kein −60-Label**: es läge bei ~0.4%
  der Breite und würde mit −36 (~6%) kollidieren; −36 wird linksbündig geclampt.

**c) `PulsingRecDot(paused: Boolean)`**
- 12dp-Punkt in `accent`; `rememberInfiniteTransition` (alpha 0.4..1) lebt **nur in diesem
  Composable**, damit die 60fps-Animation nicht den Zeit-Text mit-recomposed. Bei `paused`:
  statisch, alpha 1, Farbe `textMuted`.

**d) `RecordButtonRow(paused, onStop, onPauseResume, onMarker, markerEnabled)`**
- Links: Stop — runder 64dp-Button, `surface`-Hintergrund, `Icons.Filled.Stop`.
- Mitte: Pause/Resume — 96dp-Kreis, `accent`-Ring (3dp Border), Icon Pause bzw. Record-Punkt.
- Rechts: Marker — runder 64dp-Button, Bookmark-Icon + "MARK" (10sp), disabled bei `paused`.

### 4. `RecordScreen.kt` — Recording-Zweig umbauen

Neue Reihenfolge (Column):
1. `RecHeader` **geändert**: REC/PAUSE-Punkt + Label links bleiben; der rechte Format-Text
   `96k · mono` **entfällt** (der Format-Chip in Punkt 6 übernimmt diese Info).
2. `TitleField` bleibt (inkl. bestehendem 400ms-Debounce).
3. `LiveWaveform` — Historie: `remember(s.baseName) { mutableStateOf(listOf<Float>()) }`.
   Kapazität: `Modifier.onSizeChanged` auf der Waveform → `capState: MutableState<Int>`
   (`cap = widthPx / (3dp in px)`).
   Sammeln — **direkt am StateFlow, nicht snapshotFlow** (snapshotFlow trackt `.value`-Reads
   eines kotlinx-StateFlow nicht und würde nie re-emittieren):
   `LaunchedEffect(s.baseName) { RecorderState.state
   .map { it as? RecState.Recording }.filterNotNull()
   .distinctUntilChangedBy { it.elapsedMs }
   .collect { rec -> history = appendLevel(history, rec.level, capState.value.coerceAtLeast(1)) } }`
   — `capState.value` wird **im collect-Lambda** gelesen (nie stale); vor erstem `onSizeChanged`
   greift `coerceAtLeast(1)` bis die echte Breite da ist (danach trimmt appendLevel auf echten cap).
   Bei `paused` friert `elapsedMs` ein → keine neuen Samples.
4. Zeit-Zeile: `PulsingRecDot(paused)` + `formatMs(elapsedMs)` Monospace **48sp** in `accent`.
   `formatMs` selbst bleibt unverändert (wird von Library/Detail mitgenutzt).
5. `DbMeter`.
6. Format-Chip: "M4A · 96 kbps · mono", Rounded-Border-Pill (`border`, `textSecondary`, Monospace 12sp).
7. `RecordButtonRow`. **`onStop` sendet `ACTION_STOP` MIT `EXTRA_TITLE = titleText`** —
   exakt wie heute (schlägt den 400ms-Debounce; keine Regression). `onMarker` → `ACTION_MARKER`,
   `onPauseResume` → `ACTION_PAUSE`/`ACTION_RESUME`.
8. Divider + Marker-Liste: LazyColumn mit `Modifier.weight(1f)` — bekommt allen Restplatz,
   wird auf kleinen Screens/Landscape nicht von fixen Höhen erdrückt (obere Sektion ist kompakt:
   80dp Waveform + 48sp Zeit + ~32dp Meter + Chip + 96dp Buttons).

### 5. Tests / Verifikation

- Neu in `ModelTest` (bzw. eigener Test): `appendLevel` (append, trim, cap 0),
  `dbTickFraction` (0 dB → 1f, −60 dB ≈ 0.0043f, monoton steigend),
  `meterSegmentColor`-Zonen (Grenzwerte 0.6/0.75/0.85).
- Bestehende Unit-Tests bleiben grün: `./gradlew testDebugUnitTest`.
- `@Preview` für `LiveWaveform`, `DbMeter`, `RecordButtonRow`.
- Build: `./gradlew assembleDebug` grün.
- Manueller Check: Aufnahme starten/pausieren/stoppen, Titel kurz vor Stop tippen
  (EXTRA_TITLE-Race), Marker setzen, Rotation (kein Crash, Historie-Reset akzeptiert).

## Risiken

- Recomposition-Last: Historie capped (~130 Einträge), Canvas zeichnet einmal pro State-Tick (~4/s) —
  unkritisch; Puls-Animation isoliert im Dot.
- Collector hängt direkt am `RecorderState.state`-StateFlow innerhalb `LaunchedEffect(s.baseName)`:
  überlebt Recompositions, startet je Aufnahme neu; cap wird pro Emission frisch gelesen.

## Reihenfolge

1. Model.kt-Logik (`appendLevel`, `dbTickFraction`, `MeterZone`) + Tests (TDD).
2. BorTheme-Farben + RecordComponents.kt mit Previews.
3. RecordScreen-Umbau.
4. `testDebugUnitTest` + `assembleDebug`, manuelle Sichtprüfung.
