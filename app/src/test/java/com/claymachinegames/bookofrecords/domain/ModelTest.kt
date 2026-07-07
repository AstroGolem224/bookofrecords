package com.claymachinegames.bookofrecords.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ModelTest {

    private val meta = RecordingMeta(
        file = "2026-07-07_1930.m4a",
        startedAt = "2026-07-07T19:30:12+02:00",
        durationMs = 5_423_000,
        markers = listOf(
            Marker(timeMs = 12_500, type = "speaker", label = "Matthias"),
            Marker(timeMs = 754_000),
        ),
    )

    @Test
    fun jsonRoundtripPreservesEverything() {
        assertEquals(meta, RecordingMeta.fromJson(meta.toJson()))
    }

    @Test
    fun fromJsonToleratesUnknownFields() {
        val json = """{"version":1,"file":"a.m4a","startedAt":"x","durationMs":0,
            "markers":[],"futureField":42}"""
        assertEquals("a.m4a", RecordingMeta.fromJson(json).file)
    }

    @Test
    fun audacityLabelsUseDotDecimalAndTabSeparation() {
        val expected = "12.500000\t12.500000\tMatthias\n" +
                "754.000000\t754.000000\tnote"
        assertEquals(expected, meta.toAudacityLabels())
    }

    @Test
    fun formatMsShowsHoursOnlyWhenNeeded() {
        assertEquals("00:05", formatMs(5_000))
        assertEquals("12:34", formatMs(754_000))
        assertEquals("1:30:23", formatMs(5_423_000))
    }

    @Test
    fun sanitizeTitleStripsIllegalCharsAndTrims() {
        assertEquals("Session 43", sanitizeTitle("  Ses/si\\on: *43?\"<>|  "))
        assertEquals("", sanitizeTitle("///"))
        assertEquals("a".repeat(60), sanitizeTitle("a".repeat(80)))
    }

    @Test
    fun recordingBaseNameWithAndWithoutTitle() {
        val start = LocalDateTime.of(2026, 7, 8, 19, 30)
        assertEquals("2026-07-08_19-30_BoR_Session 43", recordingBaseName(start, "Session 43"))
        assertEquals("2026-07-08_19-30_BoR", recordingBaseName(start, ""))
        assertEquals("2026-07-08_19-30_BoR", recordingBaseName(start, "  /// "))
    }

    @Test
    fun dateFolderFormat() {
        assertEquals("2026-07-08", dateFolder(LocalDateTime.of(2026, 7, 8, 19, 30)))
    }

    @Test
    fun levelFractionNormalizesLogarithmically() {
        assertEquals(0f, levelFraction(0), 0.001f)
        assertEquals(1f, levelFraction(32767), 0.001f)
        assertEquals(0.279f, levelFraction(3277), 0.01f)
        assertEquals(0f, levelFraction(-5), 0.001f)
    }

    @Test
    fun titlePartRoundtrip() {
        assertEquals("Session 43", titlePartOf("2026-07-08_19-30_BoR_Session 43"))
        assertEquals("", titlePartOf("2026-07-08_19-30_BoR"))
        assertEquals(null, titlePartOf("2026-07-07_155810"))
        assertEquals("2026-07-08_19-30_BoR_Neu", withTitle("2026-07-08_19-30_BoR_Session 43", "Neu"))
        assertEquals("2026-07-08_19-30_BoR", withTitle("2026-07-08_19-30_BoR_Session 43", ""))
    }

    @Test
    fun borMarkerNeedsBoundary() {
        assertEquals(null, titlePartOf("my_BoRing_song"))
        assertEquals("mix", titlePartOf("2026-07-08_19-30_BoR_mix"))
    }
}
