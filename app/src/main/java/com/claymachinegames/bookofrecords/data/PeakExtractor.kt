package com.claymachinegames.bookofrecords.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.claymachinegames.bookofrecords.domain.downsamplePeaks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs

object PeakExtractor {
    private val singleFlight = Mutex()
    private const val TIMEOUT_US = 10_000L

    /**
     * Dekodiert eine Audiodatei seriell zu 16-Bit-PCM. Fehler bleiben lokal; ein verlassener
     * Player darf die Arbeit dagegen per Coroutine-Cancellation abbrechen.
     */
    suspend fun extractPeaks(
        context: Context,
        audioUri: Uri,
        buckets: Int = 104,
    ): List<Float>? {
        if (buckets <= 0) return emptyList()
        return withContext(Dispatchers.Default) {
            singleFlight.withLock {
                runCatching { decode(context, audioUri, buckets) }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    null
                }
            }
        }
    }

    private suspend fun decode(
        context: Context,
        audioUri: Uri,
        buckets: Int,
    ): List<Float> {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor().apply { setDataSource(context, audioUri, null) }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("Keine Audiospur")
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio-MIME fehlt")
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            require(durationUs > 0) { "Audiodauer fehlt" }
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val peaks = FloatArray(buckets)
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            while (!outputEnded) {
                currentCoroutineContext().ensureActive()

                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                            ?: error("Kein Codec-Eingabepuffer")
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, durationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, size, extractor.sampleTime.coerceAtLeast(0),
                                extractor.sampleFlags,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            .coerceAtLeast(1)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (info.size > 0) {
                            val output = codec.getOutputBuffer(outputIndex)
                                ?: error("Kein Codec-Ausgabepuffer")
                            output.clear()
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val pcm = output.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            var sampleIndex = 0
                            while (pcm.hasRemaining()) {
                                val frame = sampleIndex / channelCount
                                val timestampUs = info.presentationTimeUs +
                                    frame.toLong() * 1_000_000L / sampleRate
                                val bucket = (timestampUs.coerceIn(0, durationUs - 1) *
                                    buckets / durationUs).toInt()
                                val amplitude = abs(pcm.get().toInt()) / 32768f
                                if (amplitude > peaks[bucket]) peaks[bucket] = amplitude
                                sampleIndex++
                            }
                        }
                        outputEnded =
                            info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            return downsamplePeaks(peaks.toList(), buckets)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }
}
