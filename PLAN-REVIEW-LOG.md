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
