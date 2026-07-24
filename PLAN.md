# Frozen Spec: Echte Audio-Peaks für Library-Previews und Player-Hero-Waveform

Ziel: Waveforms zeigen echte Aufnahme-Amplituden statt `pseudoPeaks`, ohne die App zu
verlangsamen. Zwei Quellen: (A) Peaks werden bei NEUEN Aufnahmen gratis mitgeschrieben,
(B) Bestandsaufnahmen bekommen einen einmaligen Lazy-Backfill per Decode. UI blendet von
Pseudo- auf echte Peaks um, sobald vorhanden — nie blockieren, nie Spinner.

## Datenformat (KANONISCH — wird auch von der Desktop-Partner-App gelesen)

`RecordingMeta` (Meta-JSON-Sidecar) bekommt ein neues Feld:

```kotlin
val peaks: List<Float> = emptyList()   // 104 Werte, 0..1, leere Liste = "noch nicht berechnet"
```

- Exakt **104** Buckets über die Gesamtdauer, gleichmäßig verteilt, chronologisch.
- Wert = normalisiertes Maximum des Buckets: global auf das lauteste Bucket normiert
  (Maximum → 1.0), mit Floor 0.02 damit Stille sichtbar bleibt; Rundung auf 4 Dezimalstellen
  (JSON-Größe). Alles 0/leer bei komplett stiller Quelle ist zulässig.
- Abwärtskompatibel: `ignoreUnknownKeys` + Default `emptyList()` — alte Dateien/Reader
  funktionieren unverändert. Kein separates Versionsfeld (Feld-Präsenz ist das Signal).

## A) Aufnahme-Pfad (RecorderService)

- Der Service pollt bereits `getMaxAmplitude` → `levelFraction` (~4/s) für RecState.level.
  Diese Werte zusätzlich in eine interne `MutableList<Float>` akkumulieren —
  NUR wenn nicht pausiert (Service setzt level=0 bei Pause; Nullen würden die Form verfälschen).
- Speicher: unproblematisch (1 h ≈ ~14k Floats); kein Cap nötig, aber `ArrayList` reicht.
- Beim Finalisieren (Stop): `downsamplePeaks(samples, 104)` → `meta.copy(peaks = …)` in das
  ohnehin geschriebene Meta-JSON. Reset der Liste bei neuem Start.
- Keine Änderung an RecState/UI-Contract des Service.

## B) Backfill-Pfad (Bestandsaufnahmen)

Neue Datei `data/PeakExtractor.kt`:

- `suspend fun extractPeaks(context: Context, audioUri: Uri, buckets: Int = 104): List<Float>?`
  — `MediaExtractor` + `MediaCodec` (AAC/M4A) dekodieren, PCM-16-Blöcke lesen,
  pro Bucket max(|sample|) sammeln (Bucket-Grenzen über `sampleTime`/Gesamtdauer,
  Dauer aus `MediaFormat.KEY_DURATION`), am Ende wie oben normalisieren.
  Fehler → null (niemals crashen; runCatching um den gesamten Codec-Teil, Codec/Extractor
  in finally releasen).
- Läuft auf `Dispatchers.Default`; **globales Single-Flight**: ein app-weiter
  `Mutex`/`Semaphore(1)` im PeakExtractor-Objekt — nie zwei Decodes parallel.
- Trigger: DetailScreen (Player) — wenn geladene Meta `peaks.isEmpty()` UND aktuell KEINE
  Aufnahme läuft (`RecorderState.state.value is RecState.Idle`): in `rememberCoroutineScope`
  (bricht beim Verlassen ab) extrahieren, bei Erfolg `store.writeMeta(metaUri, meta.copy(peaks=…))`
  über den bestehenden `metaWriter`-Dispatcher UND den UI-State aktualisieren (Waveform
  blendet um — einfacher State-Swap reicht, keine Animation nötig).
- Kein Backfill-Sweep über die ganze Library (YAGNI); nur on-open im Player.

## C) UI-Verdrahtung

- `RecordingEntry` bekommt `val peaks: List<Float> = emptyList()`; alle `list()`-Implementierungen
  (RecordingRepository, SafLibrary) befüllen es aus dem bereits gelesenen Meta (dort kommt heute
  schon `markerCount` her — KEIN zusätzlicher IO).
- LibraryScreen-Karte: `entry.peaks` vorhanden → `downsamplePeaks(entry.peaks, 34)`,
  sonst bisheriges `pseudoPeaks(…, 34)`. `remember(entry.audioUri, entry.peaks)`.
- DetailScreen-Hero: Meta-Peaks vorhanden → direkt (104), sonst `pseudoPeaks` bis
  Backfill-Ergebnis kommt.
- Keine Optik-Änderung: gleiche Brushes/Geometrie, nur Datenquelle.

## D) Domain (pure, getestet)

In `domain/Model.kt`:

```kotlin
fun downsamplePeaks(samples: List<Float>, buckets: Int): List<Float>
```
- Leere Samples oder buckets<=0 → emptyList. samples.size <= buckets → auf buckets
  strecken/interpolieren ist NICHT nötig: dann einfach normalisieren + auf buckets auffüllen
  per Nearest-Index (einfachste korrekte Variante, dokumentieren).
- Sonst: Bucket = max der zugehörigen Sample-Range; danach global normalisieren
  (max→1.0, Floor 0.02, 4 Dezimalstellen). Max=0 → alle 0.
- Unit-Tests in ModelTest: Bucket-Anzahl, Max-Semantik, Normalisierung, Floor,
  leere Eingabe, samples<buckets-Fall, Determinismus.

## Nicht-Ziele / Verbote

- Kein Decode während laufender Aufnahme, kein Library-weiter Backfill-Sweep,
  keine neuen Gradle-Dependencies, keine UI-Umgestaltung, kein Fortschritts-UI.
- RecordScreen/HideScreen/MainActivity/Settings unangetastet.
- `pseudoPeaks` bleibt als Fallback bestehen.
- Meta-JSON-Feldname exakt `peaks` (Desktop-Partner-App liest denselben Sidecar).

## Proof

`./gradlew testDebugUnitTest assembleDebug` — beides BUILD SUCCESSFUL (inkl. neuer
downsamplePeaks-Tests).
