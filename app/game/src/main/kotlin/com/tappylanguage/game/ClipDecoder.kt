package com.tappylanguage.game

import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A decoded clip, downmixed to mono, samples in -1..1. */
class DecodedClip(val samples: FloatArray, val sampleRate: Int)

/**
 * Decodes an asset (ogg, wav, …) to PCM with the platform codecs
 * (C#: roughly NAudio's MediaFoundationReader). Runs synchronously; call it off the UI thread.
 */
object ClipDecoder {
    private const val TIMEOUT_US = 10_000L

    fun decode(assets: AssetManager, file: String): DecodedClip {
        val extractor = MediaExtractor()
        try {
            // Safe to close the descriptor once setDataSource returns (MediaExtractor docs).
            assets.openFd(file).use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val pcm = ByteArrayOutputStream()

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (i >= 0) {
                            val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else if (o >= 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        val bytes = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.get(bytes, 0, info.size)
                        pcm.write(bytes)
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                codec.stop()
            } finally {
                codec.release()
            }
            return DecodedClip(toMono(pcm.toByteArray(), channels, encoding), sampleRate)
        } finally {
            extractor.release()
        }
    }

    private fun toMono(bytes: ByteArray, channels: Int, encoding: Int): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val float = encoding == AudioFormat.ENCODING_PCM_FLOAT
        val frames = bytes.size / ((if (float) 4 else 2) * channels)
        return FloatArray(frames) {
            var sum = 0f
            repeat(channels) { sum += if (float) buf.getFloat() else buf.getShort() / 32768f }
            sum / channels
        }
    }
}
