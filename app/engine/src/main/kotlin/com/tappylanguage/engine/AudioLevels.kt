package com.tappylanguage.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where speech starts in a clip and how much to amplify it, so clips start on time and sound
 * equally loud (thread 32). Pure math on mono samples in -1..1; the files themselves are never
 * changed, only how they are played. Kept here (plain JVM) so it unit-tests without Android.
 */
object AudioLevels {
    /** 10 ms analysis windows. */
    const val WINDOW_MS = 10
    /** Speech starts at the first window at least this share of the loudest window's RMS (−20 dB)... */
    const val ONSET_RELATIVE = 0.1
    /** ...and at least this absolute RMS (−40 dBFS), so a near-silent clip's noise isn't taken as speech. */
    const val ONSET_ABSOLUTE = 0.01
    /** Keep this much lead-in before the onset, so consonant starts aren't clipped. */
    const val LEAD_MS = 50
    /** Target RMS of the voiced part (−20 dBFS). */
    const val TARGET_RMS = 0.1
    /** Never push the loudest sample above this. */
    const val PEAK_LIMIT = 0.9
    /** Never amplify more than this (noisy, very quiet clips). */
    const val MAX_GAIN = 8.0

    data class Levels(val startFrame: Int, val gain: Double)

    fun analyse(samples: FloatArray, sampleRate: Int): Levels {
        val window = max(1, sampleRate * WINDOW_MS / 1000)
        val rms = (samples.indices step window).map { from ->
            val to = min(samples.size, from + window)
            var sum = 0.0
            for (i in from until to) sum += samples[i].toDouble() * samples[i]
            sqrt(sum / (to - from))
        }
        val loudest = rms.maxOrNull() ?: return Levels(0, 1.0)
        if (loudest <= 0.0) return Levels(0, 1.0)
        val threshold = max(loudest * ONSET_RELATIVE, ONSET_ABSOLUTE)
        val onsetWindow = rms.indexOfFirst { it >= threshold }
        if (onsetWindow < 0) return Levels(0, 1.0) // all below the floor: play as is
        val start = max(0, onsetWindow * window - sampleRate * LEAD_MS / 1000)

        val voiced = rms.filter { it >= threshold }
        val voicedRms = sqrt(voiced.sumOf { it * it } / voiced.size)
        val peak = samples.maxOf { kotlin.math.abs(it) }.toDouble()
        val gain = minOf(TARGET_RMS / voicedRms, PEAK_LIMIT / peak, MAX_GAIN)
        return Levels(start, gain)
    }
}
