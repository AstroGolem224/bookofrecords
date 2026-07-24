# Book of Records — Library & Player Design Specification

**Version:** 1.0  
**Date:** 24 July 2026  
**Target:** Android / Jetpack Compose  
**Baseline viewport:** 412 × 892.7 dp, edge-to-edge  
**Related contract:** `Book_of_Records_Startscreen_Design_Spec.docx`  
**Visual references:** `Book_of_Records_Library_Mockup.png`, `Book_of_Records_Player_Mockup.png`

## 1. Status and implementation priority

This document extends the approved **Book of Records** start-screen system to the Library and Recording Player screens. It is the implementation contract for layout, tokens, components, and states.

Priority when references differ:

1. Runtime state correctness and accessibility.
2. Geometry and tokens in this document.
3. The supplied mockups for optical balance, material, and mood.

The mockups illustrate the intended finish. Generated text antialiasing, system-bar glyphs, and small waveform details are not assets and must be rendered natively.

## 2. Shared coordinate contract

- Baseline content width: **412 dp**.
- Baseline full-screen height: **892.7 dp**, including system bars.
- Reference system status region: **0–39.7 dp**.
- Reference Android navigation region: **845.3–892.7 dp**.
- Use `WindowInsets.statusBars` and `WindowInsets.navigationBars` in production; do not hard-code real device bar heights.
- Draw edge-to-edge behind transparent system bars with light system icons.
- Horizontal app margin: **22 dp**.
- Standard content width: **368 dp**.
- Base spacing grid: **8 dp**; use 4 dp only for optical adjustment.
- Minimum interactive target: **48 × 48 dp**.
- On wider phones, center a maximum-width **412 dp** composition and expand only the ambient background.
- On shorter phones, pin the bottom dock above the real navigation inset and make the middle content scroll. Do not compress touch targets.

## 3. Shared design tokens

### 3.1 Colors

| Token | Hex / alpha | Use |
|---|---:|---|
| `bg.base` | `#01030B` | OLED-near-black canvas |
| `bg.washEnd` | `#020713` | Bottom of base wash |
| `bg.deepBlue` | `#001626` at 35–60% | Left/lower ambient light |
| `bg.deepViolet` | `#1C0739` at 30–52% | Right ambient light |
| `surface.glass` | `#07111F` at 76% | Cards, fields, docks |
| `surface.control` | `#0A1625` at 84% | Buttons and chips |
| `text.primary` | `#F0F1F2` | Titles and filenames |
| `text.secondary` | `#5A7D9A` | Secondary metadata |
| `text.control` | `#9AB7CA` | Labels and inactive controls |
| `icon.inactive` | `#B8D0DF` at 90% | Standard icons |
| `accent.cyan` | `#00D8E8` | Left waveform/rim |
| `accent.blue` | `#5AA7FF` | Waveform transition |
| `accent.waveHi` | `#C9D4FF` | Waveform midpoint highlight |
| `accent.violetHi` | `#A855F7` | Right transition |
| `accent.violet` | `#8B5CF6` | Right waveform/rim |
| `accent.amber` | `#F89A00` | Primary action and playhead |
| `accent.amberHi` | `#FFC04D` | Amber highlight/endpoints |
| `outline.neutral` | `#6D8AA0` at 55% | Non-accented outline |
| `destructive.idle` | `#C8A5A8` at 72% | Delete icon/label at rest |
| `destructive.confirm` | `#FF5964` | Confirmation state only |

Compose constants:

```kotlin
val BgBase = Color(0xFF01030B)
val SurfaceGlass = Color(0xC207111F)
val SurfaceControl = Color(0xD60A1625)
val TextPrimary = Color(0xFFF0F1F2)
val TextSecondary = Color(0xFF5A7D9A)
val TextControl = Color(0xFF9AB7CA)
val Cyan = Color(0xFF00D8E8)
val Blue = Color(0xFF5AA7FF)
val WaveHi = Color(0xFFC9D4FF)
val VioletHi = Color(0xFFA855F7)
val Violet = Color(0xFF8B5CF6)
val Amber = Color(0xFFF89A00)
val AmberHi = Color(0xFFFFC04D)
```

### 3.2 Background

Draw these layers in order:

1. Linear wash, 180°: `#01030B` → `#020713`.
2. Cyan radial glow: center `(-44 dp, 610 dp)`, radius `329 dp`, `#003B5A` at 48% → transparent.
3. Violet radial glow: center `(445 dp, 590 dp)`, radius `348 dp`, `#3B0A70` at 40% → transparent.
4. Small top-right violet glow: center `(404 dp, 181 dp)`, radius `155 dp`, `#2A0749` at 24% → transparent.

The background remains still. Do not animate ambient gradients during normal use.

### 3.3 Glass material

Default glass recipe:

- Fill: 135° linear gradient from `rgba(7,17,31,0.80)` to `rgba(11,7,29,0.72)`.
- Shape: component-specific continuous rounded rectangle.
- Rim: **1 dp**, horizontal or sweep gradient `#00D8E8 → #5AA7FF → #8B5CF6`.
- Rim alpha: **72%** for hero/dock, **42%** for cards and inactive controls.
- Backdrop blur target: **14 dp** on Android 12+.
- Fallback below Android 12: increase fill alpha to **88%**; do not fake blur with a milky overlay.
- Outer glow: cyan on left, violet on right; **12 dp blur**, 18–24% alpha.
- Shadow: black at 52%, `y = 6 dp`, `blur = 16 dp`.
- Never use a hard shadow edge or opaque grey card.

### 3.4 Radii and strokes

| Element | Radius | Rim |
|---|---:|---:|
| Hero / waveform panel | 19 dp | 1 dp |
| Bottom dock | 22 dp | 1 dp |
| Recording card | 16 dp | 0.75–1 dp |
| Search field | 18 dp | 1 dp |
| Filter/action pill | 18–23 dp | 1 dp |
| Circular glass control | 50% | 1 dp |
| Amber primary ring | 50% | 2 dp |

### 3.5 Typography

Bundle the fonts; do not substitute when visual parity is required.

| Role | Typeface | Size / line | Tracking | Color |
|---|---|---:|---:|---|
| Screen title | Bebas Neue Regular | 38 sp / 46 dp | +0.035 em | `text.primary` |
| Player recording title | Barlow Semi Condensed Medium | 22 sp / 28 dp | +0.01 em | `text.primary` |
| Card title | Barlow Semi Condensed Medium | 16 sp / 21 dp | +0.01 em | `text.primary` |
| Search text | Barlow Semi Condensed Regular | 18 sp / 24 dp | +0.02 em | `text.secondary` |
| Control label | Barlow Semi Condensed Medium | 12 sp / 16 dp | +0.10 em | `text.control` |
| Date/group label | Barlow Semi Condensed Medium | 11 sp / 16 dp | +0.22 em | `text.secondary` |
| Metadata / filename | Barlow Semi Condensed Regular | 12 sp / 17 dp | +0.08 em | `text.secondary` |
| Time readout | Barlow Semi Condensed Medium | 13 sp / 18 dp | +0.14 em | `text.control` |

All control and group labels are uppercase. Recording names preserve their original case.

## 4. Shared interaction states

| State | Treatment |
|---|---|
| Rest | Tokens defined above |
| Pressed | Scale to 0.96 in 90 ms; rim brightness −15% |
| Released | Spring to 1.0; medium-low stiffness; no overshoot |
| Focused / keyboard | 2 dp `accent.waveHi` outline outside normal rim |
| Selected | Amber rim, amber glow at 18%, primary text |
| Disabled | 38% content alpha; remove outer glow; retain minimum hit area |
| Loading | Preserve geometry; animate a subtle rim sweep, 900 ms linear loop |

Use haptic feedback for: start playback, start recording, marker creation, and destructive confirmation. Do not vibrate for ordinary list scrolling.

---

# 5. Library screen

## 5.1 Hierarchy

1. System status bar.
2. Header: `BIBLIOTHEK`, `AUSWÄHLEN`, Settings.
3. Search field.
4. Date filter chips.
5. Scrollable date-grouped recording list.
6. Fixed `NEUE AUFNAHME` action dock.
7. System navigation bar.

## 5.2 Baseline geometry

All values are dp on a 412 × 892.7 dp baseline.

| Component | x | y | w | h |
|---|---:|---:|---:|---:|
| App content bounds | 0 | 39.7 | 412 | 805.6 |
| Header title | 22 | 72 | 228 | 46 |
| `AUSWÄHLEN` action | 258 | 80 | 76 | 32 |
| Settings hit target | 342 | 71 | 48 | 48 |
| Search field | 22 | 132 | 368 | 56 |
| Filter row | 22 | 202 | 278 | 38 |
| `ALLE` chip | 22 | 202 | 76 | 38 |
| `HEUTE` chip | 106 | 202 | 76 | 38 |
| `GESTERN` chip | 190 | 202 | 88 | 38 |
| List viewport | 22 | 260 | 368 | 469 |
| First date label | 22 | 260 | 140 | 16 |
| First card | 22 | 286 | 368 | 72 |
| Card vertical gap | — | — | — | 8 |
| Second group label | 22 | after group + 20 | 140 | 16 |
| Bottom fade | 0 | 707 | 412 | 32 |
| Action dock | 34 | 737 | 344 | 90 |
| Primary action | 96 | 753 | 220 | 58 |
| Navigation region | 0 | 845.3 | 412 | 47.4 |

The list is the only vertically scrollable app region. Give it **122 dp** bottom content padding so its last item can scroll fully above the dock.

## 5.3 Header

- Title is single-line uppercase `BIBLIOTHEK`.
- `AUSWÄHLEN` is a text action with a 48 dp hit target. At rest it has no separate filled container.
- Settings uses Material Symbols Rounded `settings`, optical size **22 dp**, centered in a **48 dp** glass circle.
- Multi-select mode:
  - Title remains fixed.
  - `AUSWÄHLEN` becomes `FERTIG`.
  - Cards expose a 24 dp selection indicator at the leading edge.
  - Bottom dock changes to the contextual batch-action dock; do not stack a second dock.

## 5.4 Search

- Field padding: **16 dp horizontal**.
- Search icon: **22 dp**, `icon.inactive`.
- Icon-to-text gap: **12 dp**.
- Placeholder: `Suchen…`.
- Cursor and selection handle: `accent.cyan`.
- Focused rim: cyan-to-violet at 90% and a 10% cyan inner glow.
- Clear icon appears at the trailing edge only when text is non-empty.

## 5.5 Filter chips

- Gap: **8 dp**.
- Active `ALLE`: amber 1 dp rim, 18% amber outer glow, primary text.
- Inactive: glass control fill, 42% gradient rim, `text.control`.
- Chips never resize between states.
- Selection transition: 160 ms color tween plus the shared press animation.

## 5.6 Recording cards

Card geometry:

- Width: **368 dp**.
- Minimum height: **72 dp**.
- Internal padding: **14 dp horizontal**, **11 dp vertical**.
- Title baseline: approximately **27 dp** from card top.
- Metadata baseline: approximately **54 dp** from card top.
- Waveform preview area: trailing **116 × 30 dp**.
- Reserve at least **132 dp** trailing width when a preview is present.
- Long titles use one line and `TextOverflow.Ellipsis`.

Content:

- Title: recording display name.
- Metadata format: `HH-mm · duration · markerCount`.
- Do not show the decorative diamond from the old screen.
- Card press opens the player.
- Long press enters selection mode.

Waveform preview:

- 34 bars, 3 dp step, 1 dp stroke, round cap.
- Height range: 3–13 dp half-height.
- Use a deterministic preview derived from the audio peaks. While unavailable, derive amplitudes from a stable hash of the file ID so cards do not jump after recomposition.
- One global left-to-right gradient: cyan → blue → violet.
- No playhead in list previews.

List behavior:

- Group by local recording date.
- Sticky group headers are allowed but must use a transparent background with only a subtle base-color scrim.
- On card removal, animate placement for 180 ms; do not use a bright swipe-to-delete background unless the user initiates the swipe.

## 5.7 New recording dock

- Dock: **344 × 90 dp**, radius **22 dp**.
- Primary action: **220 × 58 dp**, radius **29 dp**.
- Prefix icon: 18 dp amber circle containing a 10 dp dark plus.
- Label: `NEUE AUFNAHME`, uppercase, `accent.amber`.
- Primary rim: 1.5 dp amber; warm glow 14 dp at 20%.
- Content description: `Neue Aufnahme starten`.
- On tap: return to/start the recording flow; prevent duplicate navigation for 400 ms.

## 5.8 Library Compose skeleton

```kotlin
Box(Modifier.fillMaxSize().drawBehind { drawBorBackground() }) {
    LibraryHeader(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusInset + 32.dp)
            .width(368.dp)
    )

    Column(
        Modifier
            .padding(top = statusInset + 92.dp)
            .width(368.dp)
            .align(Alignment.TopCenter)
    ) {
        BorSearchField()
        Spacer(14.dp)
        DateFilterRow()
        Spacer(20.dp)
        RecordingList(
            contentPadding = PaddingValues(bottom = 122.dp)
        )
    }

    NewRecordingDock(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = navigationInset + 18.dp)
            .size(344.dp, 90.dp)
    )
}
```

---

# 6. Recording player screen

## 6.1 Hierarchy

1. System status bar.
2. Header: Back, recording title/filename, Edit.
3. Hero waveform player panel.
4. Rewind / Play-Pause / Forward controls.
5. `NEUER MARKER` action.
6. Intentional negative space or marker list when markers exist.
7. Fixed export/share/delete action dock.
8. System navigation bar.

## 6.2 Baseline geometry

| Component | x | y | w | h |
|---|---:|---:|---:|---:|
| Back hit target | 22 | 70 | 48 | 48 |
| Title | 78 | 72 | 244 | 30 |
| Filename | 78 | 105 | 244 | 18 |
| Edit hit target | 342 | 70 | 48 | 48 |
| Hero player card | 22 | 144 | 368 | 220 |
| Waveform content | 34 | 179 | 344 | 108 |
| Time row | 46 | 324 | 320 | 22 |
| Play hit target | 156 | 382 | 100 | 100 |
| Rewind hit target | 52 | 400 | 64 | 64 |
| Forward hit target | 296 | 400 | 64 | 64 |
| New marker pill | 22 | 504 | 156 | 46 |
| Dynamic marker region | 22 | 566 | 368 | 142 |
| Bottom dock | 34 | 718 | 344 | 110 |
| Navigation region | 0 | 845.3 | 412 | 47.4 |

When the device is shorter than baseline, the header and player controls remain fixed; the marker region becomes a constrained scroll list. The bottom dock remains pinned above `navigationBars`.

## 6.3 Header

- Back and Edit controls: **48 dp** glass circles.
- Back icon: Material Symbols Rounded `arrow_back`, **24 dp**.
- Edit icon: Material Symbols Rounded `edit`, **22 dp**.
- Title: recording display name, single line, ellipsis.
- Filename: single line, ellipsis; never wrap under the hero card.
- Content descriptions: `Zurück`, `Aufnahme umbenennen`.

Rename behavior:

- Open a glass-styled modal sheet/dialog.
- Select the basename without selecting the extension.
- Validate forbidden filesystem characters before save.
- Keep the original recording ID stable after rename.

## 6.4 Hero waveform panel

Panel:

- **368 × 220 dp**, radius **19 dp**.
- Internal left/right padding: **12 dp**.
- Waveform centerline: **233 dp** from full-screen top at baseline.
- Waveform draw width: **344 dp**.
- Time labels sit 22 dp above the panel bottom.

Waveform:

- Preferred bar count: **104**.
- Calculate normalized RMS or peak buckets from the recording.
- Bar step: `waveformWidth / 104`.
- Stroke: **1.5–1.8 dp**, `StrokeCap.Round`.
- Minimum half-height: **2 dp**.
- Maximum half-height: **40 dp**.
- Apply one gradient across the full width:
  - 0.00 `#00D8E8`
  - 0.26 `#22D3EE`
  - 0.48 `#C9D4FF`
  - 0.70 `#A855F7`
  - 1.00 `#8B5CF6`

Playhead:

- Horizontal position: `waveformLeft + playbackFraction * waveformWidth`.
- Core width: **1.45 dp**, `accent.amber`.
- Total height: **126.7 dp**, vertically centered on the waveform centerline.
- Endpoints: **4.84 dp** diameter, `accent.amberHi`.
- Glow radius: **5.8 dp**, amber at 45%.
- Draw above the bars.
- The mockup uses a visible mid-wave position to demonstrate the component. At `00:00`, runtime position must be the first bar; time and playhead must always agree.
- Scrubbing: horizontal drag anywhere in the hero card, with 8 dp slop. Update visually at frame rate and commit to the player on release.

Time:

- Left: elapsed time.
- Right: total duration.
- Format under one hour: `mm:ss`; one hour or more: `h:mm:ss`.
- Use tabular numerals.

## 6.5 Playback cluster

Primary play/pause:

1. **100 dp** transparent hit/glow envelope.
2. **92 dp** dark translucent outer disc.
3. 1 dp neutral outer outline.
4. **82 dp** amber ring, 2 dp stroke.
5. **52 dp** amber center disc with top-left `accent.amberHi` highlight.
6. Play/pause glyph: **24 dp**, `#07111F`.
7. Warm radial glow: 34 dp radius, amber at 20%.

Rewind/forward:

- Visual and hit size: **64 dp** circles.
- Labels: `−30s` and `+30s`.
- Rewind rim biases cyan; forward rim biases violet.
- Seek clamps to `[0, duration]`.
- Double tap on waveform left/right may duplicate −30/+30 behavior but must not replace the visible controls.

Playback state:

- Playing replaces the triangle with two pause bars.
- Buffering retains outer geometry and animates the amber ring sweep.
- Completed returns playhead to end and changes primary action to replay.

## 6.6 Markers

New marker control:

- **156 × 46 dp**, radius **23 dp**.
- Plus icon: **20 dp**.
- Label: `NEUER MARKER`.
- Marker timestamp is the current playback position.

When markers exist, use the dynamic marker region:

- Compact 368 × 42 dp rows.
- Timestamp: 58 dp leading column.
- Marker label: flexible single line.
- Trailing overflow icon: 40 dp hit target.
- Row separator: `outline.neutral` at 24%.
- Tap seeks to timestamp.
- Long press or overflow opens Rename/Delete options.

When no markers exist, keep the region empty. Do not insert explanatory empty-state copy; the negative space is intentional.

## 6.7 Bottom action dock

- Dock: **344 × 110 dp**, radius **22 dp**.
- Three equal logical cells, separated by two 1 dp vertical dividers at 28% alpha.
- Icons: **26 dp**.
- Labels: uppercase, 12 sp.

| Cell | Icon | Label | Action |
|---|---|---|---|
| Left | `description` | `TXT` | Export/open transcript text |
| Center | `share` | `TEILEN` | Android share sheet |
| Right | `delete_outline` | `LÖSCHEN` | Destructive confirmation |

Delete rules:

- Idle delete control is muted, not bright red.
- First tap opens a confirmation sheet; it never deletes immediately.
- Confirmation button uses `destructive.confirm`.
- Copy: `Aufnahme „{name}“ löschen?` and `Löschen`.

## 6.8 Player Compose skeleton

```kotlin
Box(Modifier.fillMaxSize().drawBehind { drawBorBackground() }) {
    PlayerHeader(
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusInset + 30.dp)
            .width(368.dp)
    )

    PlayerWaveformCard(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusInset + 104.dp)
            .size(368.dp, 220.dp)
    )

    PlaybackCluster(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusInset + 342.dp)
            .width(308.dp)
    )

    MarkerArea(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusInset + 464.dp)
            .width(368.dp)
    )

    PlayerActionDock(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = navigationInset + 18.dp)
            .size(344.dp, 110.dp)
    )
}
```

## 6.9 Waveform drawing outline

```kotlin
Canvas(Modifier.fillMaxSize().pointerInput(duration) { detectScrubbing() }) {
    val centerY = size.height * 0.46f
    val step = size.width / peaks.size
    val waveformBrush = Brush.horizontalGradient(
        listOf(Cyan, Color(0xFF22D3EE), WaveHi, VioletHi, Violet)
    )

    peaks.forEachIndexed { index, normalized ->
        val x = (index + 0.5f) * step
        val halfHeight = lerp(2.dp.toPx(), 40.dp.toPx(), normalized)
        drawLine(
            brush = waveformBrush,
            start = Offset(x, centerY - halfHeight),
            end = Offset(x, centerY + halfHeight),
            strokeWidth = 1.7.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    val playheadX = playbackFraction.coerceIn(0f, 1f) * size.width
    drawPlayhead(
        x = playheadX,
        centerY = centerY,
        height = 126.7.dp,
        coreWidth = 1.45.dp,
        capDiameter = 4.84.dp
    )
}
```

---

# 7. Accessibility and system behavior

- Minimum contrast:
  - Primary text against glass: **7:1 target**.
  - Secondary text: **4.5:1 minimum**.
  - Large display text: **3:1 minimum**.
- Do not rely on rim color alone for active/selected state; also change label color and expose semantic selection state.
- Respect font scaling through **1.3×** without clipping:
  - Keep control labels single-line.
  - Ellipsize filenames.
  - Increase card height when necessary.
- Support TalkBack traversal in visual order.
- Waveform exposes one semantic seek control, not 104 individual bars.
- Respect `Animator duration scale = 0` and reduced-motion preferences.
- In landscape, use a centered max-width layout; do not stretch waveform bars horizontally without recalculating steps.

## 8. Performance

- Cache background brushes and waveform paths with `drawWithCache`.
- Extract audio peaks off the main thread.
- Store precomputed normalized peaks with the recording metadata.
- Avoid recomputing all cards’ waveforms during scroll.
- Blur is decorative; disable or simplify it under battery saver or unsupported API levels without changing geometry.
- Target smooth scrolling at 60/90/120 Hz.

## 9. Visual acceptance checklist

### Shared

- [ ] Edge-to-edge near-black background and transparent system bars.
- [ ] Cyan ambient light remains left/lower; violet remains right.
- [ ] Glass is translucent and dark, never opaque grey.
- [ ] Rims read cyan-left to violet-right with restrained bloom.
- [ ] Amber is reserved for the primary action, active filter, and playhead.
- [ ] Bebas Neue and Barlow Semi Condensed are bundled.
- [ ] Bottom dock is centered and pinned above the real navigation inset.
- [ ] Touch targets are at least 48 dp.

### Library

- [ ] Header, search, filters, list, and action dock match the specified geometry within ±2 dp.
- [ ] Date groups and long filenames remain readable.
- [ ] Recording cards have waveform previews and no obsolete diamond ornament.
- [ ] List content can scroll fully above the fixed dock.
- [ ] `ALLE` is amber only while selected.
- [ ] `NEUE AUFNAHME` is the sole dominant action.

### Player

- [ ] Waveform uses a single global cyan-to-violet gradient.
- [ ] Amber playhead has a thin core, visible round endpoints, and is vertically centered.
- [ ] Playhead position and elapsed time always agree.
- [ ] Play/Pause dominates the playback cluster.
- [ ] −30s and +30s are mirrored.
- [ ] Empty marker area remains visually quiet.
- [ ] Export, Share, and Delete occupy equal dock cells.
- [ ] Delete requires confirmation and is not bright red at rest.

## 10. Screenshot-diff targets

Test at a **412 × 892.7 dp** logical viewport with system regions excluded from glyph-level comparison.

| Area | Tolerance |
|---|---:|
| Major component bounds | ±2 dp |
| Text baselines | ±2 dp |
| Rim thickness | ±0.5 dp |
| Playhead x-position | ±1 dp relative to playback fraction |
| Playhead vertical center | ±1 dp |
| Glow extent | ±4 dp |
| Solid token color | ΔE ≤ 4 |
| System chrome | Platform dependent |

## 11. Delivery assets

- `Book_of_Records_Library_Mockup.png` — visual target for the Library screen.
- `Book_of_Records_Player_Mockup.png` — visual target for the Player screen.
- `Book_of_Records_Library_Player_Design_Spec.md` — this implementation contract.
- `Book_of_Records_Startscreen_Design_Spec.docx` — authoritative shared start-screen design system.

