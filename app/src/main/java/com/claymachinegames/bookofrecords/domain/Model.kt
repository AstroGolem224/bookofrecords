package com.claymachinegames.bookofrecords.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val t = sanitizeTitle(newTitle)
    return if (t.isEmpty()) prefix else "${prefix}_$t"
}

/** MediaRecorder.getMaxAmplitude (0..32767) → 0..1, logarithmisch skaliert. */
fun levelFraction(maxAmplitude: Int): Float {
    val x = (maxAmplitude.coerceIn(0, 32767)) / 32767.0
    return log10(1.0 + 9.0 * x).toFloat()
}

fun formatMs(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
