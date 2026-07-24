# Plan: Swipe-Navigation, Hide-Screen, Library-Filter+Suche

_Round 5 (final) — revised nach Codex-Review; Loop am MAX_ROUNDS-Cap geschlossen, alle Findings übernommen_

## Goal

Drei UX-Features: (1) horizontales Swipen zwischen Aufnahme-Screen und Bibliothek,
(2) "HIDE"-Button während Aufnahme → schwarzer Tarn-Screen mit Uhrzeit + dezentem roten Punkt,
(3) Bibliothek bekommt Filter-Bar (Alle/Heute/Gestern) und Suchfeld über Dateinamen.

## Bestandsaufnahme

- `MainActivity.kt`: `setContent { BorTheme { Surface { Box(safeDrawing-padding) { App() } } } }`;
  `App()` hält `var screen by remember { mutableStateOf<Screen>(Screen.Record) }`, `when`-Dispatch,
  `BackHandler` pro Screen, kein Backstack. `screen` ist NICHT saveable (Bestand).
- `LibraryScreen(store, onOpen, onNewRecording, onOpenSettings, onSweep)`: `entries` privat,
  Ladeeffekt `LaunchedEffect(store)`; Multi-Select `selected: Set<Uri>`; Zip-Export nutzt `selected`.
- `RecordingEntry`: `baseName`, `dateGroup: String` ("yyyy-MM-dd"), `audioUri`, …
- `RecordScreen` Recording-Zweig: Titel-Debounce 400ms in `LaunchedEffect(titleText)`.
- `HorizontalPager` in Compose-Foundation (BOM 2024.12.01), kein neues Dependency.
- `borFieldColors()` ist aktuell `private` in `DetailScreen.kt`.

## Approach

### 0. Struktur: Hide als Root-Overlay (nicht als Screen-State)

- `var hidden by rememberSaveable { mutableStateOf(false) }` lebt in `MainActivity.setContent`
  (außerhalb der safeDrawing-Box) — überlebt Recreation; nach Prozess-Tod ist der Recorder Idle
  und der Auto-Exit (siehe 2.) beendet Hide sofort. Rendering:
  ```
  Box(fillMaxSize, background = Bor.bg) {
      Box(Modifier.windowInsetsPadding(safeDrawing)) { App(onHide = { hidden = true }) }
      if (hidden) HideScreen(onExit = { hidden = false })   // ungepolstert, echtes Vollbild
  }
  ```
  Wirkung: HideScreen malt **hinter Status-/Nav-Bar** (edge-to-edge, echtes Schwarz) UND
  `App()` bleibt komponiert → Titel-Debounce/Pager/Listen-State überleben das Verstecken.
- Edge-to-Edge gilt erzwungen erst ab Android 15: in `onCreate` zusätzlich
  `enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))` —
  explizit dunkle Bar-Styles (helle Icons), damit API < 35 weder opake Bars noch dunkle Icons
  über dem schwarzen Hide-Screen zeigt.

### 1. Swipe Record⇄Library (Pager, Buttons bleiben)

- `Screen`: `Record` + `Library` verschmelzen zu `Screen.Main`; `Settings`, `Detail` bleiben.
- **`beyondViewportPageCount = 1`**: beide Seiten bleiben dauerhaft komponiert. Damit überleben
  Titel-Debounce (400ms-`LaunchedEffect`) und Waveform-Historie das Wegswipen — kein
  Flush-on-Dispose-Mechanismus für den Pager nötig. Kosten: RecordScreen recomposed offscreen mit
  ~4 Hz (Level-Ticks) während man in der Bibliothek ist — ein kleiner Canvas, akzeptiert;
  LibraryScreen lädt offscreen NICHT (Load ist `isActive`-gegatet, s.u.).
- **Route-Wechsel (Detail/Settings) zerstört den Pager trotzdem** — zwei Absicherungen:
  a) Titel: `DisposableEffect` im Recording-Zweig flusht beim Dispose den letzten Titel
     (`rememberUpdatedState(titleText)` → `ACTION_SET_TITLE`) — kein Verlust im Debounce-Fenster,
     egal ob Pager- oder Routen-Dispose.
  b) Waveform: Historie (+ Breiten-Cap) wird nach `App()` gehoben (dort läuft auch der
     Level-Collector, gekeyt/resettet über `baseName`); `RecordScreen` bekommt
     `waveHistory: List<Float>` + `onWaveWidthPx` gereicht. `App` bleibt bei Detail/Settings
     komponiert → Historie überlebt jede Navigation während der Aufnahme.
- Seiten-Index überlebt Detail/Settings-Ausflüge und Activity-Recreation:
  `var mainPage by rememberSaveable { mutableIntStateOf(0) }` in `App()`;
  in `Screen.Main`: `rememberPagerState(initialPage = mainPage, pageCount = { 2 })`,
  Rückschreiben via `snapshotFlow { pagerState.settledPage }.collect { mainPage = it }`.
  → Rückkehr aus Detail landet wieder auf der Bibliothek (Seite 1).
- Buttons bleiben: `onOpenLibrary` → `animateScrollToPage(1)`; Library-`onNewRecording` → Seite 0.
- BackHandler-Priorität (zentralisiert dokumentiert): LibraryScreens Selection-BackHandler ist
  im Page-Content komponiert → registriert NACH dem Pager-BackHandler → gewinnt solange
  Selection aktiv (enabled-Flag). Pager-BackHandler: `enabled = settledPage == 1` → Seite 0.
  Auf Seite 0: Activity-Default. Reihenfolge: Selection schließen → Seite 0 → App verlassen.
- Library-Refresh — **eine** Revisionsquelle `var libraryRevision by remember { mutableIntStateOf(0) }`
  in `App`. LibraryScreens Ladeeffekt wird `LaunchedEffect(store, refreshToken, isActive) {
  if (isActive) load() }` — geladen wird NUR wenn die Seite gesettelt aktiv ist:
  a) Seiten-Eintritt lädt über den `isActive`-Flip (false→true rekeyt den Effect) —
     **kein** separates Revision-Inkrement beim Eintritt → kein Doppel-Load beim lazy
     Vorkomponieren oder bei Rückkehr mit initialPage 1;
  b) Aufnahme-Ende: Revision-Inkrement im bestehenden `Recording → Idle`-Collector NACH
     `sweepNow()` — deckt Stop über die Notification ab, während die Bibliothek sichtbar ist.
- **Sweep: Bestand beibehalten** (Revision der Runde-4-Zentralisierung): LibraryScreens Ladeeffekt
  behält sein serialisiertes `onSweep() → store.list()` — das ist das existierende
  Retry-Verhalten bei temporär nicht erreichbarem Ziel (Sweep schluckt Fehler; erneuter
  Library-Eintritt versucht es wieder). Der `Recording → Idle`-Collector sweept weiterhin
  (Bestand) und inkrementiert danach die Revision. Seltener Doppel-Sweep (Stop während Library
  sichtbar) ist idempotent und existiert heute schon — akzeptiert statt neuer Suppress-Mechanik.
  `onSweep` bleibt damit genutzt, kein toter Parameter.
- Aktivitäts-Flag: `LibraryScreen` bekommt `isActive: Boolean` (= `settledPage == 1`):
  - Selection-BackHandler `enabled = selecting && isActive` — ein offscreen komponierter
    Page-Rest kann Back nicht mehr stehlen;
  - `LaunchedEffect(isActive) { if (!isActive) selection leeren }` — definiert: Selection
    überlebt das Verlassen der Seite NICHT (weder Swipe noch "Neue Aufnahme").
- Query/Filter in Library als `rememberSaveable` (überlebt zusätzlich Recreation).
  Selection bleibt `remember` (Set<Uri> nicht trivial saveable) und wird beim Verlassen geleert.

### 2. Hide-Screen (`ui/HideScreen.kt`)

- Voll schwarz (`Color.Black`), ungepolstert (siehe 0).
- Mitte: Uhrzeit `HH:mm`, Monospace, `Bor.textMuted`, ~40sp. Update-Loop:
  `LaunchedEffect(Unit) { while(true) { now = "HH:mm"-String von LocalTime.now(); delay(1000) } }`
  — Sekunden-Ticker, aber `now` ist der formatierte String → Recomposition nur beim Minutenwechsel.
  Selbstkorrigierend nach Doze/Resume/Zeitzonen-Wechsel binnen 1s, kein Lifecycle-Observer nötig.
- Unten mittig (safeDrawing-Bottom-Inset beachtet, damit nicht unter der Nav-Bar):
  6dp-Punkt, statisch (kein Pulsieren — Tarnung): Recording aktiv → `Bor.accent` alpha 0.35;
  paused → `Bor.textMuted` alpha 0.35; Idle → Auto-Exit.
- Exit: Doppeltipp (`detectTapGestures(onDoubleTap)`), einfacher Tipp tut nichts;
  `BackHandler { onExit() }` als Fluchtweg; `LaunchedEffect`: wenn `RecorderState.state` Idle wird → `onExit()`.
- TalkBack: `Modifier.semantics { onClick("Aufnahme anzeigen") { onExit(); true } }` auf dem
  Root-Box — semantische Exit-Aktion für Screenreader, physischer Doppeltipp bleibt unverändert.
- Kein zusätzlicher Screen-Wake-Lock (`keepScreenOn`): Display darf ausgehen; der bestehende
  PARTIAL_WAKE_LOCK des RecorderService hält die CPU für den Foreground-Service verfügbar
  (Prozess-Tod bleibt die dokumentierte Grenze, siehe Out of scope).
- Einstieg: "HIDE"-Pill **im `RecHeader` rechts** (der Slot ist seit Entfernung des Format-Texts
  frei) — dezent (Outline, `textMuted`), nur im Recording-Zustand. Bewusst NICHT unter der
  Button-Zeile wie im Referenz-Design: vermeidet Überlauf bei kleiner Höhe/offener IME
  (Screen hat bereits viele fixe Höhen über der gewichteten Marker-Liste).
  Neuer Callback `onHide: () -> Unit` an `RecordScreen`.

### 3. Library: Filter-Bar + Suche

- Pure Logik in `domain/Model.kt`:
  ```kotlin
  enum class DateFilter { ALL, TODAY, YESTERDAY }
  fun matchesLibraryFilter(baseName: String, dateGroup: String, query: String,
                           filter: DateFilter, today: String, yesterday: String): Boolean
  ```
  Query case-insensitiv als Substring auf `baseName`; Filter via `dateGroup`-Vergleich;
  Query UND Filter kombiniert; leerer Query matcht alles. Uhr-frei → unit-testbar.
- `today` als State mit 60s-Ticker solange Library komponiert ist
  (`LaunchedEffect(Unit) { while(true) { todayState = LocalDate.now(); delay(60_000) } }`) —
  Mitternacht/Zeitzonen-Wechsel korrigiert sich binnen einer Minute auch ohne Interaktion;
  `yesterday = todayState.minusDays(1)`. Recomposition nur bei Datumswechsel (String-State).
- UI in `LibraryScreen`:
  - Suchfeld (`OutlinedTextField`, Platzhalter "Suchen…", ×-Clear bei Text), `rememberSaveable`.
  - Chips-Reihe "Alle/Heute/Gestern" im bestehenden Pill-Stil (aktiv: `accent`-Border+`textPrimary`;
    inaktiv: `border`+`textSecondary`), `rememberSaveable` (Enum-Ordinal).
  - Anwendung vor Gruppierung: `entries.filter { matchesLibraryFilter(...) }` → `groupBy` wie gehabt.
  - Empty-State-Präzedenz (exklusiv, in dieser Reihenfolge): Ziel nicht erreichbar → lädt →
    `entries` leer → "Noch keine Aufnahmen" (Bestand) → gefilterte Liste leer → "Keine Treffer"
    → gruppierte Ergebnisse. Nie zwei Meldungen gleichzeitig.
  - **Selection×Filter**: bei jeder Änderung von Query/Filter/entries wird
    `selected = selected intersect sichtbareUris` — Export kann keine unsichtbaren Dateien erfassen.
  - `borFieldColors()` von `DetailScreen.kt` nach `BorTheme.kt` als `internal` verschieben,
    beide Screens nutzen es.

## Key decisions & tradeoffs

- **Hide als Root-Overlay** statt Screen-State: echtes Vollbild hinter Systembars, kein State-Verlust
  im Recorder (Debounce läuft weiter), triviale Rückkehr.
- **Pager + Screen-State koexistieren**: kein Navigation-Compose-Dependency. Akzeptiert: zwei Mechanismen.
- **Swipe über Textfeldern kann von der Textfeld-Geste konsumiert werden** (einzeiliges TextField
  scrollt horizontal): akzeptiert — Buttons bleiben als verlässlicher Navigationsweg erhalten;
  kein Fokus-abhängiges Pager-Disabling (Komplexität ohne echten Nutzen).
- **Selection nicht saveable**: kurzlebiger Arbeitszustand, Verlust bei Disposal/Recreation akzeptiert.
- **Filterlogik pure** → Unit-Test; UI dünn.

## Out of scope (explizit)

- Prozess-Tod während Aufnahme: `RecorderState` ist prozess-global, Service `START_NOT_STICKY` —
  Recovery ist Bestandsverhalten und NICHT Teil dieses Plans.
- Activity-Recreation stellt die `Screen`-Route (insb. `Detail`) nicht wieder her — Bestandsverhalten;
  neu saveable sind nur `mainPage` und `hidden` (Hide überlebt Rotation, siehe Checkliste).
- Kein Persistieren von Suchtext/Filter über App-Neustart, keine Fuzzy-Suche, keine Marker-Suche.
- Kein FLAG_SECURE/Recents-Verstecken.
- Keine Instrumentation-/Compose-UI-Tests: Projekt hat keine solche Infrastruktur; Abdeckung der
  Übergänge erfolgt über die manuelle Checkliste unten.

## Tests / Verifikation

- Unit-Tests (`ModelTest`): `matchesLibraryFilter` — Substring case-insensitiv, ALL/TODAY/YESTERDAY,
  Kombination Query+Filter, leerer Query, kein Match.
- `./gradlew testDebugUnitTest` + `assembleDebug` grün.
- Manuelle Checkliste: Swipe hin/zurück; Buttons; Back: Selection→Seite0→Exit-Reihenfolge;
  Detail öffnen und schließen → landet auf Bibliothek; Titel tippen → sofort HIDE → Doppeltipp
  zurück → Titel vorhanden; Stop während Hide → Auto-Exit; Suche+Chips kombiniert;
  Selection setzen, dann filtern → Auswahl auf sichtbare reduziert; Rotation auf Seite 1;
  **Rotation während Hide → Hide bleibt aktiv**; **Stop per Notification während Bibliothek
  sichtbar → neue Aufnahme erscheint**; **Selection aktiv lassen, wegswipen, Back → wechselt
  Seite statt unsichtbare Selection zu räumen (Selection wurde beim Verlassen geleert)**;
  **Hide-Vollflächigkeit + helle Systembar-Icons auf einem API-<35- und einem API-35-Gerät,
  jeweils Gesten- und 3-Button-Navigation**; **Titel tippen → sofort zur Bibliothek swipen →
  Stop per Notification → Dateiname enthält den Titel**; **während Aufnahme wegswipen und
  zurück → Waveform-Historie intakt**; **Titel tippen → Library → sofort Detail/Settings öffnen
  → Stop → Dateiname enthält Titel**; **während Aufnahme Detail öffnen und zurück →
  Waveform-Historie intakt**.
