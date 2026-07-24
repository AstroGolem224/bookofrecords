# Plan Review Log: Recorder-Screen-Redesign (BoR)

Started 2026-07-24. MAX_ROUNDS=5. Codex model: gpt-5.6-sol (read-only).

## Round 1 — Kimi (Codex-Auth defekt, Kimi als Ersatz-Kritiker, Modell via kimi-cli 0.28.1)

11 Findings, VERDICT: REVISE. Kernpunkte:
1. Stop-Button verliert EXTRA_TITLE-Race-Fix (Regression) → onStop muss Titel mitschicken.
2. dB-Labels linear unter log-Meter = falsche Daten → inverse Abbildung f = log10(1+9·10^(dB/20)).
3. Widerspruch RecHeader ("bleibt" vs. "Format-Text entfällt").
4. Fixe Höhen können Marker-Liste erdrücken (klein/landscape).
5. Waveform-Kapazität "Breite/3dp" ohne Mechanismus → onSizeChanged.
6. Rotation löscht Waveform-Historie (nicht deklariert).
7. "Bei paused letzten Wert halten" ist tot — Service setzt level=0, elapsedMs friert ein.
8. LaunchedEffect(elapsedMs) = Restart-pro-Tick-Hack → snapshotFlow.
9. Puls-Animation auf Dot-Composable begrenzen.
10. Chip-Text inkonsistent ("· mono" ja/nein).
11. Trim-Logik als pure Function testen.

### Claude's response
Alle 11 übernommen. Landscape: v1 portrait-orientiert, obere Sektion kompaktiert (Waveform 80dp,
Zeit 48sp), Marker-Liste weight(1f); Rotation-Reset der Historie als akzeptiert deklariert (Punkt 6).

## Round 2 — Kimi

VERDICT: REVISE. 4 Findings:
1. snapshotFlow über StateFlow.value emittiert nie (Waveform friert ein) → direkt am Flow collecten.
2. cap stale (0) im langlebigen Collector → capState im collect-Lambda lesen.
3. −60-Label kollidiert mit −36 (~0.4% vs 6% Breite) → −60 droppen, −36 clampen.
4. Risiko-Sektion segnete kaputten Mechanismus ab.

### Claude's response
Alle 4 übernommen: direkter StateFlow-Collector mit distinctUntilChangedBy(elapsedMs),
capState.value im Lambda + coerceAtLeast(1), −60-Label entfernt, Risiko-Text korrigiert.

## Round 3 — Kimi

VERDICT: APPROVED. Nits (übernommen in Implementierung): dbTickFraction(−60)≈0.0039 mit Epsilon
testen; Waveform-Balken rechtsbündig am Playhead verankern (teilgefüllte Historie).

## Ergebnis
Plan nach 3 Runden konvergiert. Implementierung durch Claude (Codex-Auth defekt, Kimi war Kritiker).

---

# Plan Review Log 2: Swipe-Nav, Hide-Screen, Library-Filter+Suche

Started 2026-07-24. MAX_ROUNDS=5. Codex model: gpt-5.6-sol (read-only).

## Round 1 — Codex

VERDICT: REVISE. 15 Findings, Kernpunkte: (1) safeDrawing-Padding verhindert echtes Vollbild-Hide;
(2) Screen.Hide zerstört RecordScreen-State inkl. Titel-Debounce; (3) Pager-Reset nach Detail-Rückkehr;
(4) Page-Disposal verliert remember-State; (5/6) Library-Refresh-Mechanismus wirkungslos /
currentPage falsches Signal; (7) TextField konsumiert horizontale Gesten; (8) Back-Priorität
unspezifiziert; (9) Filter kann unsichtbare Selektion exportieren; (10) borFieldColors private;
(11) Mitternacht/Zeitzone; (12) Prozess-Tod-Behauptung unbelegt; (13) Nav-State nicht saveable;
(14) HIDE-Button-Überlauf bei IME; (15) Testplan deckt neue Übergänge nicht.

### Claude's response
Übernommen: 1+2 (Hide als Root-Overlay außerhalb safeDrawing, App bleibt komponiert),
3+13 (mainPage rememberSaveable, settledPage-Rückschreibung), 4 (Query/Filter rememberSaveable;
Selection bewusst nicht — kurzlebig, dokumentiert), 5+6 (refreshToken-Param, settledPage via
snapshotFlow), 8 (Priorität dokumentiert: Selection→Seite0→Exit, Registrier-Reihenfolge),
9 (selected ∩ sichtbare Uris), 10 (borFieldColors → internal in BorTheme.kt),
12 (explizit out of scope), 14 (HIDE-Pill in RecHeader-Slot statt unter Buttons).
Teilweise/abgelehnt mit Begründung: 7 (kein fokus-abhängiges Pager-Disabling — Buttons bleiben
verlässlicher Weg, Komplexität ohne Nutzen), 11 (frische LocalDate.now() je Filterung statt
Ticker/Broadcast-Receiver — Selbstkorrektur bei nächster Recomposition reicht),
15 (keine Instrumentation-Infra im Projekt — manuelle Checkliste erweitert).

## Round 2 — Codex

VERDICT: REVISE. 11 Findings: (1) hidden nicht saveable; (2) Vollschwarz-Claim falsch für API<35;
(3) Notification-Stop bei sichtbarer Library refresht nicht; (4) Back-Handler offscreen-Page-Interferenz;
(5) Selection-Wiederauftauchen undefiniert; (6) Hide-Uhr-Lifecycle; (7) Mitternacht ohne Recomposition;
(8) Wake-Lock-Formulierung widerspricht Service-Code; (9) Empty-State-Präzedenz; (10) Doppel-Load
bei Rückkehr auf Seite 1; (11) Checkliste unvollständig.

### Claude's response
Alle 11 übernommen, teils mit einfacheren Mechanismen: hidden→rememberSaveable; enableEdgeToEdge();
libraryRevision als einzige Quelle, inkrementiert bei settledPage-Eintritt (drop(1) gegen Doppel-Load)
UND nach sweepNow() im Recording→Idle-Collector; isActive-Flag für Library (BackHandler-Gate +
Selection-Clear beim Verlassen); Hide-Uhr als 1s-Ticker mit String-State (statt Lifecycle-Observer);
Library-today als 60s-Ticker; Wake-Lock-Text präzisiert; Empty-State-Präzedenzkette definiert;
Checkliste um 3 Szenarien erweitert.

## Round 3 — Codex

VERDICT: REVISE. 7 Findings: (1) libraryRevision ohne remember; (2) Doppel-Load bei normalem
Seiteneintritt; (3) enableEdgeToEdge ohne explizite dunkle SystemBarStyles; (4) Widerspruch
Hide/Recreation-Doku; (5) Wake-Lock-Beschreibung überverspricht; (6) TalkBack-Exit fehlt;
(7) API-übergreifende Vollbild-Checks fehlen.

### Claude's response
Alle 7 übernommen: remember ergänzt; Laden nur bei isActive (Effect-Key), Eintritt-Inkrement
gestrichen; SystemBarStyle.dark(TRANSPARENT) beidseitig; Out-of-scope-Text korrigiert (hidden
saveable); Wake-Lock-Text präzisiert; semantics-onClick "Aufnahme anzeigen"; Checkliste um
API-/Navigationsmodus-Matrix erweitert.

## Round 4 — Codex

VERDICT: REVISE. 4 Findings: (1) Titel-Verlust bei "tippen→sofort wegswipen" durch Page-Disposal;
(2) Waveform-Historie stirbt beim Wegswipen; (3) redundante Sweeps (Collector + Library-Load);
(4) Checkliste deckt Titel-Race/Waveform-Rückkehr nicht.

### Claude's response
1+2 gemeinsam gelöst via beyondViewportPageCount=1 (beide Seiten bleiben komponiert; Offscreen-Kosten
~4Hz-Recomposition dokumentiert und akzeptiert). 3 übernommen: Sweep zentralisiert in App
(Start / Recording→Idle / targetVersion-Wechsel), Library-Load listet nur noch. 4 übernommen:
2 Checklisten-Szenarien ergänzt.

## Round 5 — Codex (MAX_ROUNDS-Cap)

VERDICT: REVISE. 5 Findings: (1) Routen-Wechsel (Detail/Settings) zerstört Pager → Titel-Debounce-
Verlust; (2) Waveform-Historie stirbt bei Routen-Navigation; (3) Sweep-Zentralisierung entfernt
Retry-bei-Library-Eintritt; (4) onSweep würde toter Parameter; (5) Checkliste ohne Routen-Dispose.

### Claude's response (final, Loop geschlossen)
Kein inhaltlicher Dissens — alle 5 übernommen: (1) DisposableEffect-Titel-Flush mit
rememberUpdatedState; (2) Waveform-Historie + Collector nach App() gehoben (überlebt Routen);
(3+4) Runde-4-Zentralisierung zurückgenommen — Bestands-Sweep-vor-List in Library bleibt,
Doppel-Sweep als idempotentes Bestandsverhalten akzeptiert; (5) 2 Szenarien ergänzt.
Loop endet am Cap mit vollständig eingearbeiteten Findings; Findings-Trend 15→11→7→4→5
(Runde 5 nur Folgefehler der Runde-4-Lösung). Implementierung startet.

---

# Act 3 — Build (Startscreen-Spec aus .docx)

## Round 1 — Codex build
Codex (gpt-5.6-sol, --yolo) implementierte PLAN.md (Frozen Spec aus
Book_of_Records_Startscreen_Design_Spec.docx): IdleStartScreen als absolute 412-dp-Komposition
(BoxWithConstraints + Scale), Ambient-Radials, Glass-Rezept mit Gradient-Rim/Glow/Shadow,
Brand-Logo-Pfad, 104-Bar-Hero-Waveform mit fixem Array + globalem Gradient, Amber-Playhead,
Action-Dock (Bibliothek/Record-Layers/Hide) mit Press-Spring-States, Bebas Neue + Barlow.
Deviation (gemeldet): kein echtes Backdrop-Blur-Sampling ohne neue Dependency — Alpha-Verläufe
+ Rims + Glows stattdessen. Proof laut Codex grün.

### Claude's verdict
Diff vollständig gelesen (3 Dateien, +548/−156): spec-treu, Recording-Zweig/ausgeschlossene
Bereiche unangetastet, keine neuen Dependencies, Callbacks erhalten, Tokens nur ergänzt.
Proof selbst ausgeführt: testDebugUnitTest + assembleDebug BUILD SUCCESSFUL.
Geräte-Screenshot ausstehend (Handy getrennt). Blur-Deviation akzeptiert.

---

# Act 3 — Build (Library/Player-Spec)

## Round 1 — Codex build
Codex (gpt-5.6-sol, --yolo) implementierte PLAN.md + DESIGN_SPEC_LIBRARY_PLAYER.md:
Library (Glass-Header/Suche/Chips/Waveform-Cards/Long-Press-Selection/Batch-Dock ZIP+Löschen),
Player (Hero-Waveform mit Scrubbing, Playhead, ±30s, Playback-Cluster, Marker-Rows,
Teilen/Löschen-Dock mit Bestätigung), pseudoPeaks + Test, Tokens ergänzt, GlassSurface/Fonts
public. Deviations: kein echtes Backdrop-Blur (Vorgabe-konform), Mikroanimationen nicht
vollständig generalisiert, kein Geräte-Screenshot (kein Device in Sandbox).

### Claude's verdict
Proof selbst ausgeführt: testDebugUnitTest + assembleDebug grün. Diff-Review an
cavecrew-reviewer delegiert (45 Tool-Calls): ALLE Verhaltens-Erhaltungen aus PLAN.md
verifiziert (Load-Gating, Selection-Semantik, Rename-Fix, Zip, Sweep-vor-List,
Metadaten-Format ohne ◆, MediaPlayer-Lifecycle, Marker-CRUD, Delete-Bestätigung),
neue Features korrekt (Batch-Delete mit Fehlerbehandlung, Scrubbing geclampt, ±30s
geclampt, pseudoPeaks deterministisch). Keine Findings. Geräte-Sichttest ausstehend.
