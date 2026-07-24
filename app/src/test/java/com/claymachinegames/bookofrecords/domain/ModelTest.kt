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
        peaks = listOf(0.02f, 0.4567f, 1f),
    )

    @Test
    fun jsonRoundtripPreservesEverything() {
        assertEquals(meta, RecordingMeta.fromJson(meta.toJson()))
    }

    @Test
    fun fromJsonToleratesUnknownFields() {
        val json = """{"version":1,"file":"a.m4a","startedAt":"x","durationMs":0,
            "markers":[],"futureField":42}"""
        val parsed = RecordingMeta.fromJson(json)
        assertEquals("a.m4a", parsed.file)
        assertEquals(emptyList<Float>(), parsed.peaks)
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
    fun withTitleUnwrapsFullBaseNamePassedAsTitle() {
        // Rename-Dialog füllt bei unbetitelten Aufnahmen den vollen baseName vor —
        // unverändertes Speichern darf das Datum nicht duplizieren.
        assertEquals("2026-07-08_19-30_BoR",
            withTitle("2026-07-08_19-30_BoR", "2026-07-08_19-30_BoR"))
        assertEquals("2026-07-08_19-30_BoR_Neu",
            withTitle("2026-07-08_19-30_BoR", "2026-07-08_19-30_BoR_Neu"))
        assertEquals("2026-07-08_19-30_BoR_Neu",
            withTitle("2026-07-08_19-30_BoR_Session 43", "2026-07-08_19-30_BoR_Neu"))
    }

    @Test
    fun borMarkerNeedsBoundary() {
        assertEquals(null, titlePartOf("my_BoRing_song"))
        assertEquals("mix", titlePartOf("2026-07-08_19-30_BoR_mix"))
    }

    @Test
    fun appendLevelAppendsAndTrimsToCap() {
        assertEquals(listOf(0.1f, 0.2f), appendLevel(listOf(0.1f), 0.2f, cap = 5))
        assertEquals(listOf(0.2f, 0.3f), appendLevel(listOf(0.1f, 0.2f), 0.3f, cap = 2))
        assertEquals(listOf(0.9f), appendLevel(listOf(0.1f, 0.2f, 0.3f), 0.9f, cap = 1))
        assertEquals(emptyList<Float>(), appendLevel(listOf(0.1f), 0.2f, cap = 0))
    }

    @Test
    fun pseudoPeaksAreDeterministicBoundedAndSized() {
        val first = pseudoPeaks("content://aufnahme/42", 104)
        assertEquals(first, pseudoPeaks("content://aufnahme/42", 104))
        assertEquals(104, first.size)
        assertEquals(true, first.all { it in 0f..1f })
        assertEquals(emptyList<Float>(), pseudoPeaks("egal", 0))
    }

    @Test
    fun downsamplePeaksUsesBucketMaximumAndNormalizesGlobally() {
        assertEquals(
            listOf(1f, 0.4444f),
            downsamplePeaks(listOf(0.1f, 0.9f, 0.2f, 0.4f), buckets = 2),
        )
    }

    @Test
    fun downsamplePeaksReturnsRequestedBucketCount() {
        val result = downsamplePeaks((1..200).map { it.toFloat() }, buckets = 104)
        assertEquals(104, result.size)
        assertEquals(1f, result.maxOrNull())
    }

    @Test
    fun downsamplePeaksAppliesFloorAndFourDecimalRounding() {
        assertEquals(
            listOf(0.02f, 0.3333f, 1f),
            downsamplePeaks(listOf(0.001f, 1f / 3f, 1f), buckets = 3),
        )
    }

    @Test
    fun downsamplePeaksHandlesEmptyInvalidAndSilentInput() {
        assertEquals(emptyList<Float>(), downsamplePeaks(emptyList(), buckets = 104))
        assertEquals(emptyList<Float>(), downsamplePeaks(listOf(1f), buckets = 0))
        assertEquals(listOf(0f, 0f), downsamplePeaks(listOf(0f, 0f), buckets = 2))
    }

    @Test
    fun downsamplePeaksFillsShortInputByNearestIndex() {
        assertEquals(
            listOf(0.5f, 0.5f, 1f, 1f, 1f),
            downsamplePeaks(listOf(0.5f, 1f), buckets = 5),
        )
    }

    @Test
    fun downsamplePeaksIsDeterministic() {
        val samples = listOf(0.4f, 0.1f, 0.8f, 0.2f, 0.6f)
        assertEquals(
            downsamplePeaks(samples, buckets = 3),
            downsamplePeaks(samples, buckets = 3),
        )
    }

    @Test
    fun dbTickFractionMatchesLevelScale() {
        assertEquals(1f, dbTickFraction(0), 0.001f)
        assertEquals(0.0039f, dbTickFraction(-60), 0.001f)
        // monoton steigend
        val fracs = listOf(-60, -36, -24, -12, 0).map { dbTickFraction(it) }
        assertEquals(fracs, fracs.sorted())
    }

    @Test
    fun meterZoneBoundaries() {
        assertEquals(MeterZone.GREEN, meterZone(0.0f))
        assertEquals(MeterZone.GREEN, meterZone(0.59f))
        assertEquals(MeterZone.YELLOW, meterZone(0.6f))
        assertEquals(MeterZone.AMBER, meterZone(0.75f))
        assertEquals(MeterZone.ORANGE, meterZone(0.85f))
        assertEquals(MeterZone.ORANGE, meterZone(1.0f))
    }

    @Test
    fun libraryFilterMatchesQueryCaseInsensitive() {
        assertEquals(true, matchesLibraryFilter("2026-07-24_10-00_BoR_Meeting Notes", "2026-07-24",
            "meeting", DateFilter.ALL, "2026-07-24", "2026-07-23"))
        assertEquals(false, matchesLibraryFilter("2026-07-24_10-00_BoR_Meeting Notes", "2026-07-24",
            "standup", DateFilter.ALL, "2026-07-24", "2026-07-23"))
        assertEquals(true, matchesLibraryFilter("abc", "2026-07-24",
            "", DateFilter.ALL, "2026-07-24", "2026-07-23"))
    }

    @Test
    fun libraryFilterDateModes() {
        assertEquals(true, matchesLibraryFilter("a", "2026-07-24", "", DateFilter.TODAY,
            "2026-07-24", "2026-07-23"))
        assertEquals(false, matchesLibraryFilter("a", "2026-07-23", "", DateFilter.TODAY,
            "2026-07-24", "2026-07-23"))
        assertEquals(true, matchesLibraryFilter("a", "2026-07-23", "", DateFilter.YESTERDAY,
            "2026-07-24", "2026-07-23"))
        assertEquals(false, matchesLibraryFilter("a", "2026-07-22", "", DateFilter.YESTERDAY,
            "2026-07-24", "2026-07-23"))
        assertEquals(true, matchesLibraryFilter("a", "2020-01-01", "", DateFilter.ALL,
            "2026-07-24", "2026-07-23"))
    }

    @Test
    fun libraryFilterCombinesQueryAndDate() {
        // Query passt, Datum nicht → false; beide passen → true
        assertEquals(false, matchesLibraryFilter("Meeting", "2026-07-23", "meet",
            DateFilter.TODAY, "2026-07-24", "2026-07-23"))
        assertEquals(true, matchesLibraryFilter("Meeting", "2026-07-24", "meet",
            DateFilter.TODAY, "2026-07-24", "2026-07-23"))
    }

    @Test
    fun insertMarkerSortedIntoMiddle() {
        val m1 = Marker(timeMs = 1000)
        val m3 = Marker(timeMs = 3000)
        val inserted = Marker(timeMs = 2000, type = "speaker", label = "Mid")
        assertEquals(listOf(m1, inserted, m3), insertMarkerSorted(listOf(m1, m3), inserted))
    }

    @Test
    fun insertMarkerSortedAtStart() {
        val m2 = Marker(timeMs = 2000)
        val inserted = Marker(timeMs = 500)
        assertEquals(listOf(inserted, m2), insertMarkerSorted(listOf(m2), inserted))
    }

    @Test
    fun insertMarkerSortedAtEnd() {
        val m1 = Marker(timeMs = 1000)
        val inserted = Marker(timeMs = 5000)
        assertEquals(listOf(m1, inserted), insertMarkerSorted(listOf(m1), inserted))
    }

    @Test
    fun insertMarkerSortedIntoEmptyList() {
        val inserted = Marker(timeMs = 100)
        assertEquals(listOf(inserted), insertMarkerSorted(emptyList(), inserted))
    }

    @Test
    fun insertMarkerSortedTieBreaksAfterExisting() {
        val m1 = Marker(timeMs = 1000, label = "first")
        val inserted = Marker(timeMs = 1000, label = "second")
        assertEquals(listOf(m1, inserted), insertMarkerSorted(listOf(m1), inserted))
    }
}
