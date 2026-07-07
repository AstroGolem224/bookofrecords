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
    fun defaultBaseNameFormat() {
        assertEquals("2026-07-07_193000", defaultBaseName(LocalDateTime.of(2026, 7, 7, 19, 30)))
    }

    @Test
    fun formatMsShowsHoursOnlyWhenNeeded() {
        assertEquals("00:05", formatMs(5_000))
        assertEquals("12:34", formatMs(754_000))
        assertEquals("1:30:23", formatMs(5_423_000))
    }
}
