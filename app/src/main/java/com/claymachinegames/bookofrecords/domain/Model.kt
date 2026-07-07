package com.claymachinegames.bookofrecords.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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

fun defaultBaseName(now: LocalDateTime): String =
    now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))

fun formatMs(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
