# Frozen Spec: Library- & Player-Screen — Visual Parity Implementation

Primärquelle: `DESIGN_SPEC_LIBRARY_PLAYER.md` (Repo-Root) — vollständiger Implementation-Contract
(Geometrie, Tokens, Glass-Rezept, Typografie, States, Checklisten). Dieses PLAN.md ergänzt
Codebase-Kontext und drei VERBINDLICHE Abweichungen. Bei Widerspruch gilt: PLAN.md > Spec-MD.

## Verbindliche Abweichungen (vom User entschieden)

1. **Player-Dock ohne TXT-Zelle**: Nur ZWEI gleiche Zellen — `TEILEN` (share) und `LÖSCHEN`
   (delete_outline-Stil, Canvas-Icon). Ein 1-dp-Divider bei 28% Alpha dazwischen. Dock-Maße
   aus der Spec (344×110 dp, Radius 22) bleiben.
2. **Waveform-Peaks als Hash-Fallback**: KEIN Audio-Decoding. Sowohl Library-Card-Previews
   (34 Bars) als auch Player-Hero-Waveform (104 Bars) nutzen deterministische Pseudo-Peaks
   aus einem stabilen Hash der Datei-Identität (`baseName` bzw. `audioUri.toString()`),
   damit nichts bei Recomposition springt. Normalisierung in die Spec-Höhenbereiche
   (Cards 3–13 dp, Hero 2–40 dp Halbhöhe). Als pure, testbare Funktion in `domain/Model.kt`
   (z. B. `pseudoPeaks(seed: String, count: Int): List<Float>` mit Werten 0..1) + Unit-Test
   (Determinismus, Wertebereich, count).
3. **Batch-Dock im Selection-Mode**: ersetzt das NEUE-AUFNAHME-Dock (nie zwei Docks):
   zwei Zellen `ZIP` (bestehender Zip-Export-Flow) und `LÖSCHEN` (Batch-Delete ALLER
   selektierten Aufnahmen — Bestätigungsdialog nach Spec-Delete-Regeln:
   `{n} Aufnahmen löschen?` / `Löschen` in destructive.confirm; Löschung über die
   bestehende Store-Delete-API in einer Coroutine, danach Liste neu laden).

## Codebase-Kontext

- Screens: `app/src/main/java/com/claymachinegames/bookofrecords/ui/LibraryScreen.kt` und
  `ui/DetailScreen.kt` (= "Player"). `ui/MarkerEditor.kt` ist der Marker-State-Holder des
  Players (Logik behalten). `ui/BorTheme.kt`: `Bor`-Tokens — `idle*`-Farben aus dem
  Startscreen-Build EXISTIEREN bereits und entsprechen den Spec-Tokens; fehlende ergänzen
  (`destructive.idle` #C8A5A8@72%, `destructive.confirm` #FF5964, `waveHi`, `blue` …),
  bestehende nicht umbenennen.
- `ui/RecordComponents.kt` enthält vom Startscreen: `AmbientBackground` (public, wird global
  in MainActivity unter allen Screens gezeichnet — Library/Player dürfen KEINEN eigenen
  opaken Hintergrund setzen), `GlassSurface` (private — public machen und wiederverwenden
  statt duplizieren), Fonts `BebasNeue`/`BarlowSemiCondensed` (private Konstanten — public
  machen oder nach BorTheme.kt ziehen), `ReferencePxPerDp`.
- Fonts liegen in `app/src/main/res/font/` (bebas_neue, barlow_semi_condensed_medium).
- Icons: material-icons-core hat nur ein Basis-Set (Settings, ArrowBack, Share vorhanden?
  prüfen — sonst Canvas). KEIN material-icons-extended. Fehlende Icons (edit, plus, delete,
  play/pause-Glyphen, Lupe) als kleine Canvas-Composables zeichnen, wie im Bestand
  (BookmarkIcon/LibraryIcon/HideIcon als Vorbild).

### Verhalten, das UNVERÄNDERT bleiben muss (nur Optik neu)

- LibraryScreen: Load-Gating (`LaunchedEffect(store, refreshToken, isActive)` mit
  `if (!isActive) return`), Selection-Clear bei `!isActive`, Selection-BackHandler
  (`selectionMode && isActive`), `selected ∩ visible`-Intersect, Filterlogik
  `matchesLibraryFilter` + `today`-Ticker, Empty-State-Präzedenz (unreachable → lädt →
  leer → "Keine Treffer"), Zip-Export via `zipLauncher`/`exportZip`, `onSweep()` vor
  `store.list()`, Karten-Metadaten-INHALT: `HH:MM h · Dauer <dauer> · <n>` — Spec sagt
  `HH-mm · duration · markerCount` und "kein Diamant": Diamant ◆ entfernen, aber
  Zeitformat `HH:MM h` und "Dauer "-Präfix BEIBEHALTEN (jüngere User-Entscheidung schlägt Spec).
- DetailScreen: MediaPlayer-Lifecycle, Seek/Position-Logik, Marker-CRUD über `MarkerEditor`
  (Chips → kompakte Marker-Rows nach Spec 6.6 sind okay, Funktionen Rename/Delete/Seek
  erhalten), Rename-Dialog-Logik (Prefill via `titlePartOf`, Save via `withTitle` —
  Doppel-Datum-Fix nicht kaputt machen), Export/Share/Delete-Flows, `onClose`-Navigation.
- Long-Press auf Library-Card → Selection-Mode (Spec 5.6) ist NEU und erwünscht;
  bisheriger "Auswählen"-Header-Toggle bleibt zusätzlich (wird zu `AUSWÄHLEN`/`FERTIG`).
- −30s/+30s-Buttons (Spec 6.5) sind NEU: Seek clamped auf [0, duration].
- Scrubbing per Drag im Hero-Card (Spec 6.4) ist NEU: visuell framerate, Commit on release.

## Proof

`./gradlew testDebugUnitTest assembleDebug` — beides BUILD SUCCESSFUL (inkl. neuem
pseudoPeaks-Test).

## Nicht-Ziele

- Kein Audio-Decoding/echte Peaks, kein Transkript, keine neuen Gradle-Dependencies,
  kein Blur-Fake, keine Änderung an RecorderService/RecordScreen/HideScreen/MainActivity
  (außer falls ein Callback-Parameter zwingend nötig wird — dann minimal),
  keine Änderung an domain-Logik außer der neuen `pseudoPeaks`-Funktion.
- Deutsche UI-Strings, deutscher Kommentar-Stil wie im Bestand.
