<p align="center"><img src="docs/assets/logo.png" width="240" alt="BookofRecords logo"></p>

# BookofRecords

Android voice recorder for later transcription. Records AAC/M4A with
tap-to-mark timestamps (speaker changes, notes).

## Output

`Documents/BookofRecords/YYYY-MM-DD/` on device:

- `2026-07-08_19-30_BoR_Session43.m4a` — mono AAC, 44.1 kHz, 96 kbps
- `2026-07-08_19-30_BoR_Session43.json` — sidecar metadata:

```json
{
  "version": 1,
  "file": "2026-07-08_19-30_BoR_Session43.m4a",
  "startedAt": "2026-07-08T19:30:12+02:00",
  "durationMs": 5423000,
  "markers": [{ "timeMs": 754000, "type": "speaker", "label": "Matthias" }]
}
```

The title (`Session43` above) is set in-app while recording, or renamed
afterwards — both files stay in sync.

- `<name>.labels.txt` — optional Audacity label track export (tab-separated).

Marker times exclude paused stretches, so they match audio position.

## Build

```bash
./gradlew installDebug
```

Requires Android SDK (platform 35), JDK 17+. minSdk 29.

## Why M4A, not MP3?

Android has no built-in MP3 encoder. AAC/M4A is native, better quality per
bitrate, and every transcription tool (Whisper etc.) reads it directly.
