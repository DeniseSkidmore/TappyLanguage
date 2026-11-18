package com.tappylanguage.game

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.tappylanguage.engine.AudioLevels
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Plays one prompt clip at a time from assets. Each clip is decoded once, its leading silence
 * skipped and its volume normalised (AudioLevels), then played from memory with AudioTrack.
 * The asset files themselves are never modified.
 */
class AudioPlayer(private val assets: AssetManager) {
    private class Prepared(val pcm: ShortArray, val sampleRate: Int)

    private val clips = ConcurrentHashMap<String, Prepared>()
    /** Background decoder (C#: a single-threaded TaskScheduler). */
    private val loader = Executors.newSingleThreadExecutor()
    private var track: AudioTrack? = null

    /** Decode clips ahead of time so the first play isn't delayed. */
    fun preload(files: Collection<String>) {
        files.forEach { f ->
            loader.execute {
                try { prepare(f) } catch (e: Exception) { Log.w("AudioPlayer", "Cannot decode $f", e) }
            }
        }
    }

    private fun prepare(file: String): Prepared = clips[file] ?: run {
        val clip = ClipDecoder.decode(assets, file)
        val levels = AudioLevels.analyse(clip.samples, clip.sampleRate)
        val pcm = ShortArray(clip.samples.size - levels.startFrame) { i ->
            (clip.samples[levels.startFrame + i] * levels.gain * 32767).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        Prepared(pcm, clip.sampleRate).also { clips[file] = it }
    }

    fun play(file: String?) {
        if (file == null) return
        stop()
        try {
            val clip = prepare(file) // normally cached by preload; decodes here otherwise
            if (clip.pcm.isEmpty()) return
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(clip.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(clip.pcm.size * 2)
                .build()
                .apply {
                    write(clip.pcm, 0, clip.pcm.size)
                    play()
                }
        } catch (e: Exception) {
            Log.w("AudioPlayer", "Cannot play $file", e) // missing download: game continues silently
        }
    }

    private fun stop() {
        track?.release()
        track = null
    }

    fun release() {
        stop()
        loader.shutdownNow()
    }
}
