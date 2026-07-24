package com.claymachinegames.bookofrecords.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random
import kotlin.math.log10

@Serializable
data class Marker(
    val timeMs: Long,
    val type: String = "note",
    val label: String = "",
)

@Serializable
data class RecordingMeta(
    val version: Int = 1,
    val file: String,
    val startedAt: String,
    val durationMs: Long = 0,
    val markers: List<Marker> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    /** Audacity label track: start<TAB>end<TAB>label, point labels (start == end). */
    fun toAudacityLabels(): String = markers.joinToString("\n") { m ->
        val s = String.format(Locale.US, "%.6f", m.timeMs / 1000.0)
        "$s\t$s\t${m.label.ifEmpty { m.type }}"
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
        fun fromJson(text: String): RecordingMeta = json.decodeFromString(serializer(), text)
    }
}

private val illegalFileChars = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

fun sanitizeTitle(raw: String): String =
    raw.replace(illegalFileChars, "").trim().replace(Regex("\\s+"), " ").take(60).trim()

fun dateFolder(start: LocalDateTime): String =
    start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

/** YYYY-MM-DD_HH-MM_BoR_<titel>; ohne Suffix wenn Titel leer. */
fun recordingBaseName(start: LocalDateTime, title: String): String {
    val prefix = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + "_BoR"
    val t = sanitizeTitle(title)
    return if (t.isEmpty()) prefix else "${prefix}_$t"
}

private val borMarker = Regex("""_BoR(_|$)""")

/** Titel-Teil eines Basisnamens; null wenn kein BoR-Namensschema (Altbestand). */
fun titlePartOf(base: String): String? {
    val m = borMarker.find(base) ?: return null
    return base.substring(m.range.first + 4).removePrefix("_")
}

fun withTitle(base: String, newTitle: String): String {
    val m = borMarker.find(base)
    val prefix = if (m != null) base.substring(0, m.range.first) + "_BoR"
                 else base + "_BoR"
    // Rename-Dialog kann den vollen BoR-Namen als "Titel" liefern — nur Titel-Teil übernehmen.
    val t = sanitizeTitle(titlePartOf(newTitle) ?: newTitle)
    return if (t.isEmpty()) prefix else "${prefix}_$t"
}

/** MediaRecorder.getMaxAmplitude (0..32767) → 0..1, logarithmisch skaliert. */
fun levelFraction(maxAmplitude: Int): Float {
    val x = (maxAmplitude.coerceIn(0, 32767)) / 32767.0
    return log10(1.0 + 9.0 * x).toFloat()
}

/** Level-Historie fürs Waveform: anhängen, vorn auf [cap] trimmen. */
fun appendLevel(history: List<Float>, level: Float, cap: Int): List<Float> {
    if (cap <= 0) return emptyList()
    return (history + level).takeLast(cap)
}

/**
 * Stabile Ersatz-Peaks für Aufnahmen ohne dekodierte Audiodaten.
 * Derselbe Datei-Schlüssel liefert immer dieselbe Folge im Bereich 0..1.
 */
fun pseudoPeaks(seed: String, count: Int): List<Float> {
    if (count <= 0) return emptyList()
    val random = Random(seed.fold(0x4D595DF4) { hash, char -> hash * 31 + char.code })
    return List(count) {
        // Etwas Grundhöhe hält auch sehr leise Balken im Glas sichtbar.
        (random.nextFloat() * 0.82f + random.nextFloat() * 0.18f).coerceIn(0f, 1f)
    }
}

/** Position eines dB-Ticks auf der log-Skala von [levelFraction] (Umkehrung: x = 10^(dB/20)). */
fun dbTickFraction(db: Int): Float =
    log10(1.0 + 9.0 * Math.pow(10.0, db / 20.0)).toFloat()

enum class MeterZone { GREEN, YELLOW, AMBER, ORANGE }

/** Farbzone eines Meter-Segments an Position [fraction] (0..1). */
fun meterZone(fraction: Float): MeterZone = when {
    fraction < 0.6f -> MeterZone.GREEN
    fraction < 0.75f -> MeterZone.YELLOW
    fraction < 0.85f -> MeterZone.AMBER
    else -> MeterZone.ORANGE
}

enum class DateFilter { ALL, TODAY, YESTERDAY }

/** Library-Filter: Query (case-insensitiver Substring auf baseName) UND Datumsfilter kombiniert.
 *  today/yesterday als "yyyy-MM-dd" vom Aufrufer — Logik bleibt uhr-frei und testbar. */
fun matchesLibraryFilter(
    baseName: String, dateGroup: String, query: String,
    filter: DateFilter, today: String, yesterday: String,
): Boolean {
    val queryOk = query.isBlank() || baseName.contains(query.trim(), ignoreCase = true)
    val dateOk = when (filter) {
        DateFilter.ALL -> true
        DateFilter.TODAY -> dateGroup == today
        DateFilter.YESTERDAY -> dateGroup == yesterday
    }
    return queryOk && dateOk
}

/** Insert [marker] keeping the list ascending by timeMs (list display + ticks stay chronological). */
fun insertMarkerSorted(markers: List<Marker>, marker: Marker): List<Marker> {
    val index = markers.indexOfFirst { it.timeMs > marker.timeMs }
    return if (index < 0) markers + marker
    else markers.toMutableList().apply { add(index, marker) }
}

fun formatMs(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
