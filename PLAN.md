# Frozen Spec: Recording-Screen — Glassmorphism-Restyle im BoR-Designsystem

Quelle des Designsystems: `DESIGN_SPEC_LIBRARY_PLAYER.MD`-Kontrakt (Repo-Root:
`DESIGN_SPEC_LIBRARY_PLAYER.md`) — Abschnitte 2 (Koordinaten), 3 (Tokens/Background/Glass/
Radii/Typografie), 4 (Interaction-States) gelten UNVERÄNDERT auch hier. Dieses PLAN.md
definiert nur die Recording-spezifische Komposition. Ziel: Der `RecState.Recording`-Zweig
von `RecordScreen.kt` sieht aus wie Startscreen/Library/Player (Glaskarten, Cyan→Violett,
Amber nur für Primäraktion/REC/Playhead, Bebas/Barlow), Verhalten bleibt identisch.

## Codebase-Kontext

- `ui/RecordScreen.kt`: Recording-Zweig = Column mit RecHeader, TitleField, LiveWaveform,
  PulsingRecDot+Timer, DbMeter/DbScale, Format-Chip, RecordButtonRow, Marker-LazyColumn.
  Idle-Zweig (`IdleStartScreen`) NICHT anfassen.
- Wiederverwendbar (public): `GlassSurface`, `AmbientBackground` (global in MainActivity —
  kein eigener opaker Hintergrund), `BebasNeue`, `BarlowSemiCondensed`, `PulsingRecDot`,
  `LiveWaveform` (Level-Historie kommt als `waveHistory: List<Float>` von App()).
- Bor-Tokens: `idle*`-Familie + `destructive*` existieren. `levelGreen/Yellow/Amber/Orange`
  fürs Meter existieren.
- KRITISCHES Verhalten (unverändert): Titel-Debounce 400 ms + DisposableEffect-Flush
  (nur wenn dieselbe Aufnahme noch läuft), Stop sendet `ACTION_STOP` MIT `EXTRA_TITLE`,
  Pause/Resume/Marker-Actions, `onHide`, Level-Historie via `waveHistory`/`onWaveWidthPx`,
  Marker-Liste aus `s.markers` (neueste zuerst).

## Komposition (Column, 22-dp-Seitenrand, 368-dp-Inhaltsbreite)

1. **Header-Zeile**: links `PulsingRecDot` + Label `REC`/`PAUSE` (Barlow Medium 12 sp,
   +0.10em, REC in `idleAmber`, PAUSE in `text.control`); rechts HIDE-Pill im
   Glass-Control-Stil (Radius 18, 1-dp-Rim 42%, Label `HIDE` amber wie bisher, uppercase).
2. **Titel-Feld**: bestehendes OutlinedTextField ersetzen durch Glass-Feld im Stil des
   Library-Suchfelds (Radius 18, Glass-Fill, 1-dp-Gradient-Rim 42%, Fokus: Rim 90% +
   cyan Inner-Glow; Cursor cyan; Placeholder „Titel…“ in `text.secondary`; Barlow 18 sp).
   `BasicTextField` erlaubt (DetailScreen-Rename als Vorbild), Verhalten identisch.
3. **Live-Waveform-Glaskarte**: `GlassSurface` Radius 19, Höhe ~150 dp, Rim 72%.
   Innen `LiveWaveform` (bestehende Historien-Balken, Cyan→Violett, Playhead rechts amber)
   mit 12 dp Innenabstand. `onWaveWidthPx` weiter verdrahten.
4. **Timer-Zeile**: `formatMs(elapsedMs)` in Bebas Neue, ~44 sp, `text.primary`,
   Tracking +0.035em, zentriert. KEIN amber (Amber bleibt Primäraktion/Playhead/REC-Punkt);
   bei `paused` Alpha 0.6.
5. **Pegel-Block**: DbMeter + DbScale wie bestehend (Funktion unverändert), aber in eine
   flache Glass-Pill eingefasst (Radius 16, Rim 42%, 12 dp Padding) — Meter-Segmentfarben
   (grün/gelb/amber/orange) bleiben, inaktive Segmente `outline.neutral` @24% statt
   borderSubtle.
6. **Format-Chip**: „M4A · 96 kbps · mono“ — Stil wie Player-Zeitlabels: Barlow Medium
   13 sp, +0.14em, `text.control`, in Glass-Pill (Radius 18, Rim 42%). Kein Teal-Sonderweg
   mehr (Chip-Farbwelt = Designsystem).
7. **Steuer-Cluster** (ersetzt RecordButtonRow-Optik, gleiche Callbacks):
   - Layout wie Player-Cluster: links Stop (64-dp-Glaskreis, cyan-biased Rim, weißes
     18-dp-Rundeck-Quadrat), Mitte Pause/Resume als Primäraktion im Play-Button-Aufbau
     (100-dp-Envelope, 92-dp-Dunkelscheibe, 1-dp-Outline, 82-dp-Amber-Ring 2 dp,
     52-dp-Amber-Disc mit `idleAmberHi`-Highlight; Glyph: 2 Pause-Balken bzw. Play-Dreieck
     in `#07111F`, 24 dp), rechts Marker (64-dp-Glaskreis, violet-biased Rim,
     Bookmark-Canvas-Icon + Label `MARK` darunter, Barlow 12 sp +0.10em; disabled bei
     paused: 38% Alpha, kein Glow).
   - Press-States nach Systemregel (0.96/90 ms, Spring-Release).
8. **Marker-Liste**: kompakte Rows nach Player-Spec 6.6 (42 dp, Timestamp-Spalte 58 dp in
   `text.control` tabular, Label flexibel einzeilig ellipsiert `text.primary`,
   Separator `outline.neutral` @24%). `weight(1f)`, neueste zuerst wie bisher.
   Leer → Bereich leer lassen (kein Empty-Copy).

Abstände: 8-dp-Raster, kompakt genug, dass Marker-Liste auf Baseline ≥120 dp bekommt.
Kein Scroll der oberen Sektion; Landscape-Regel wie Bestand (nichts crasht, Liste bekommt Rest).

## Nicht-Ziele / Verbote

- Keine Verhaltensänderung (Actions, Debounce/Flush, Historie, Pager-Einbettung).
- Idle-Zweig, HideScreen, Library, Detail, Settings, MainActivity, Service, domain/, data/
  unangetastet (Ausnahme: neue kleine public Composables in RecordComponents.kt erlaubt).
- Keine neuen Dependencies, keine Icons-Extended (Canvas-Icons wie Bestand).
- Deutsche Strings/Kommentare im Bestandsstil.

## Proof

`./gradlew testDebugUnitTest assembleDebug` — beides BUILD SUCCESSFUL.

## Abnahme (visuell)

Glaskarte um Live-Waveform · Timer weiß/Bebas, nicht amber · Amber nur REC-Punkt,
HIDE-Label, Pause-Primärbutton, Playhead · Meter in Glass-Pill mit unveränderter Logik ·
Cluster gespiegelt (Stop cyan-Rim links, Marker violet-Rim rechts) · Marker-Rows kompakt ·
kein opaker Hintergrund (Ambient scheint durch).
