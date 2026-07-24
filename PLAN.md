# Frozen Spec: Start Screen "Book of Records" — Visual Parity Implementation

Quelle: Book_of_Records_Startscreen_Design_Spec.docx (approved implementation contract).
Ziel: Der Idle-/Startscreen der App (RecState.Idle-Zweig) wird exakt nach dieser Spec neu
implementiert. Recording-Screen, Library, Detail, Settings, HideScreen bleiben UNVERÄNDERT.

## 0. Kontext Codebase

- Repo: Android/Kotlin, Jetpack Compose (BOM 2024.12.01, material3), package
  `com.claymachinegames.bookofrecords`.
- Idle-Screen lebt im `RecState.Idle`-Zweig von `app/src/main/java/com/claymachinegames/bookofrecords/ui/RecordScreen.kt`;
  Hilfs-Composables in `ui/RecordComponents.kt` (WaveLogo, IdleWaveformCard, GlassActionButton,
  LibraryIcon, HideIcon existieren bereits und dürfen ersetzt/umgebaut werden, soweit nur der
  Idle-Screen sie nutzt — LiveWaveform/DbMeter/RecordButtonRow etc. werden vom Recording-Zweig
  genutzt und bleiben unangetastet).
- Farb-Objekt `Bor` in `ui/BorTheme.kt` — neue Tokens dort ergänzen, bestehende NICHT umbenennen.
- Fonts liegen BEREITS unter `app/src/main/res/font/bebas_neue.ttf` und
  `app/src/main/res/font/barlow_semi_condensed_medium.ttf`.
- Edge-to-Edge ist aktiv (enableEdgeToEdge mit dark transparent bars in MainActivity);
  der Screen wird innerhalb einer safeDrawing-gepolsterten Box komponiert.
- Callbacks des Idle-Zweigs (müssen erhalten bleiben): `onOpenSettings`, `onOpenLibrary`,
  `onHide`, `send(RecorderService.ACTION_START)` für Record, `hasAudioPermission`-Disable.

## 1. Koordinaten-Kontrakt

Referenz-Canvas 852×1846 px inkl. Systembars; Baseline 412×892.7 dp; 2.067961 px/dp.
`referenceScale = viewportWidthDp / 412f` — alle dp-Werte damit uniform skalieren.
KEIN unabhängiges X/Y-Stretching, kein Scroll. Auf breiteren Geräten 412-dp-Komposition
zentrieren, nur Ambient-Hintergrund expandiert. Auf kürzeren Geräten Dock über der echten
Navigation-Inset halten, nur die Negativ-Raum-Zone schrumpfen.
Layout absolut positioniert (BoxWithConstraints + absolute Offsets mit referenceScale),
NICHT als vertikal verteilte Column — der große leere Mittelbereich ist beabsichtigt.

Zonen (dp, Baseline): Header 85.6–229.2 · Waveform-Karte 265.0–442.0 · Negativraum
451.7–708.0 (LEER lassen) · Action-Dock 719.5–840.9.

## 2. Komponenten-Geometrie (Baseline-dp)

| Komponente | x | y | w | h |
|---|---|---|---|---|
| Waveform-Logo | 27.6 | 97.7 | 89.0 | 75.9 |
| Titelblock | 130.1 | 96.2 | 207.9 | 103.0 |
| Subtitle | 131.0 | 214.2 | 164.4 | 15.0 |
| Settings-Control (Kreis) | 340.4 | 85.6 | 47.4 | 47.4 |
| Waveform-Glaspanel | 22.2 | 265.0 | 368.5 | 177.0 |
| Waveform-Balkenbereich | 30.0 | 313.4 | 353.0 | 78.8 |
| Amber-Playhead | 202.6 | 290.1 | 5.8 | 126.7 |
| Action-Dock | 33.8 | 719.5 | 343.8 | 121.4 |
| Library-Control | 53.2 | 742.8 | 75.0 | 76.9 |
| Record-Hit-Target | 150.9 | 724.4 | 109.8 | 109.8 |
| Hide-Control | 281.9 | 742.8 | 77.9 | 76.9 |

Dock zentriert; Record exakt mittig im Dock; Seiten-Controls spiegeln um dieselbe Achse.

## 3. Farb-/Material-Tokens

| Token | Wert | Alpha | Nutzung |
|---|---|---|---|
| bg.base | #01030B | 100% | Canvas |
| bg.deepBlue | #001626 | 35–60% | Ambient links/unten |
| bg.deepViolet | #1C0739 | 30–52% | Ambient rechts |
| surface.glass | #07111F | 72–80% | Karte + Dock |
| surface.control | #0A1625 | 78–88% | Buttons, Settings |
| text.primary | #F0F1F2 | 100% | Titel |
| text.secondary | #5A7D9A | 100% | Subtitle |
| dockLabel | #9AB7CA | 100% | Dock-Labels |
| accent.cyan | #00D8E8 | 100% | linke Strokes/Border/Logo |
| accent.blue | #5AA7FF | 100% | Waveform-Übergang |
| accent.violet | #8B5CF6 | 100% | rechte Strokes/Border/Logo |
| accent.amber | #F89A00 | 100% | Record + Playhead |
| accent.amberHi | #FFC04D | 100% | Highlight/Endcaps |
| outline.neutral | #6D8AA0 | 55% | neutrale Glass-Outline |
| icon.inactive | #B8D0DF | 90% | Library/Hide/Settings-Icons |

Hintergrund-Verläufe (px-Referenz, umrechnen):
1. Basis: linear 180°, #01030B → #020713, ganzer Canvas.
2. Cyan-Ambient: radial, Center (−90, 1260) px, r=680 px, #003B5A @48% → transparent.
3. Violett-Ambient: radial, Center (920, 1220) px, r=720 px, #3B0A70 @40% → transparent.
4. Oben-rechts: radial, Center (835, 375) px, r=320 px, #2A0749 @24% → transparent.

Glass-Rezept: Fill linear 135° rgba(7,17,31,0.80)→rgba(11,7,29,0.72); Backdrop-Blur 13.5 dp
(Android 12+: RenderEffect-Blur 14 dp; darunter: ohne Blur akzeptiert); Border 1 dp
Gradient cyan→violett; Outer-Glow cyan links/violett rechts (24 px Blur, 18–24%);
Shadow rgba(0,0,0,0.52) y=12px blur=34px. Radius: Karte 18.9 dp, Dock 22.2 dp,
Seiten-Buttons 16.9 dp, Settings-Kreis 47.4 dp.

## 4. Typografie

| Rolle | Font (res/font) | Größe | Zeilenhöhe | Tracking | Farbe |
|---|---|---|---|---|---|
| Titel | bebas_neue | 38.7 sp | 50.3 dp | +0.035em | #F0F1F2 |
| Subtitle | barlow_semi_condensed_medium | 12.1 sp | 15.0 dp | +0.42em | #5A7D9A |
| Dock-Labels | barlow_semi_condensed_medium | 10.2 sp | 13.1 dp | +0.08em | #9AB7CA |

Titel: UPPERCASE, zweizeilig, Umbruch exakt nach "OF" → "BOOK OF" / "RECORDS",
linksbündig, Subtitle "AUDIO RECORDER" an Titel-x ausgerichtet (nicht screen-zentriert).

## 5. Brand-Waveform-Logo

Vektor 184×157 px Viewport, Stroke 9 px round cap/join, Gradient #00D8E8→#8B5CF6, Pfad:
`M 0 91 H 38 C 46 91 44 50 52 50 C 61 50 59 121 68 121 C 78 121 76 0 86 0 C 96 0 95 154 105 154 C 115 154 113 52 123 52 C 132 52 130 91 140 91 H 184`
Umsetzung als Compose-Canvas-Path (px→dp mit /2.067961) oder VectorDrawable.

## 6. Hero-Waveform (deterministisch, KEIN Mikro)

- 104 Balken; erster Balken-Center x=30.95 dp (panel-relativ ab 30.0 dp), Schritt 3.385 dp;
  Stroke 1.7 dp, StrokeCap.Round; Centerline 352.5 dp ab Screen-Top; Max-Halbhöhe 39.65 dp.
- Halbhöhen-Array (Referenz-px, ÷2.067961 → dp):
  12, 37, 31, 41, 54, 38, 24, 29, 35, 34, 22, 15, 33, 24, 53, 69, 68, 52, 26, 25, 77, 82, 75, 48, 38, 53, 69, 52, 37, 27, 44, 32, 26, 18, 14, 24, 34, 21, 25, 32, 56, 47, 32, 52, 39, 28, 48, 77, 61, 41, 50, 76, 59, 44, 24, 17, 33, 24, 15, 38, 54, 43, 22, 34, 24, 21, 16, 11, 22, 34, 36, 30, 69, 51, 36, 61, 78, 49, 28, 54, 55, 34, 36, 27, 18, 27, 30, 44, 62, 78, 49, 39, 40, 61, 70, 27, 24, 26, 25, 38, 25, 24, 18, 16
- Gradient über die GESAMTE Waveform-Breite (nicht pro Balken):
  0.00 #00D8E8 · 0.26 #22D3EE · 0.48 #C9D4FF · 0.70 #A855F7 · 1.00 #8B5CF6.
- Playhead: Kern 1.45 dp #F89A00, Glow r≈5.8 dp @45%; Endcaps 4.84 dp Ø #FFC04D,
  bei y=606 px und y=857 px (Referenz).

## 7. Controls

- Settings: 47.4-dp-Glaskreis, Zahnrad ~21.8 dp in icon.inactive, cyan/violett 1-dp-Rim.
  (Material-Icons-Extended ist NICHT als Dependency erlaubt — vorhandenes
  Icons.Filled.Settings aus material-icons-core ODER eigener Canvas.)
- Library: 75.0×76.9 dp Glas, Radius 16.9 dp, Icon ~23×21 dp (Buchrücken-Outline, Canvas),
  Label "BIBLIOTHEK" uppercase darunter, muted-cyan Rim.
- Hide: 77.9×76.9 dp Glas, Icon durchgestrichenes Auge ~26×21 dp (Canvas),
  Label "HIDE", muted-violet Rim.
- Record (Layers, außen→innen): 109.8 dp Hit-Target; 100.6 dp dunkle Scheibe
  rgba(10,15,23,0.82); 1 dp neutrale Outline #B6C7D5@65%; 2 dp Amber-Ring bei ~91.9 dp Ø;
  35.8 dp Amber-Scheibe #F89A00 mit #FFC04D-Highlight oben links; warmer radialer Glow
  (~33.8 dp r, #F89A00@20%) innerhalb der Scheibe geclippt.
- Pressed-State: scale 0.96 über 90 ms, Rim −15% Helligkeit; Release: Spring medium-low,
  kein Overshoot. Touch-Targets ≥48 dp. contentDescription: "Einstellungen",
  "Bibliothek öffnen", "Aufnahme starten", "Bildschirm verstecken".
- `hasAudioPermission == false` → Record mit 38% Content-Alpha, ohne Glow, disabled.

## 8. Verbote / Nicht-Ziele

- KEIN Timer, KEIN "BEREIT", keine Karten/Copy im Negativraum.
- Recording-Zweig, Library, Detail, Settings, HideScreen, MainActivity-Navigation,
  RecorderService: unverändert.
- Keine neuen Gradle-Dependencies (auch nicht material-icons-extended, kein Coil etc.).
- Bestehende Tests müssen grün bleiben.

## 9. Proof

`./gradlew testDebugUnitTest assembleDebug` — beides BUILD SUCCESSFUL.

## 10. Abnahme-Checkliste (visuell)

Titel zweizeilig nach "OF" umbrochen · 104-Bar-Waveform mit fixem Array + globalem Gradient ·
Amber-Playhead mittig mit runden Endcaps · Mitte leer bis auf Ambient-Glows · Dock zentriert,
Record dominant exakt mittig · Library/Hide gespiegelt mit Uppercase-Labels · Glass lesbar,
nicht opak · keine unabhängige Streckung.
