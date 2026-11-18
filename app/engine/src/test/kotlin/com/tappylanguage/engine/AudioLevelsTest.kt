package com.tappylanguage.engine

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioLevelsTest {
    private val rate = 16_000

    /** `silenceMs` of near-silence (optional noise), then `toneMs` of a 220 Hz tone at `amplitude`. */
    private fun clip(silenceMs: Int, toneMs: Int, amplitude: Double, noise: Double = 0.0): FloatArray {
        val quiet = rate * silenceMs / 1000
        val loud = rate * toneMs / 1000
        return FloatArray(quiet + loud) { i ->
            if (i < quiet) (if (i % 2 == 0) noise else -noise).toFloat()
            else (amplitude * sin(2 * PI * 220 * (i - quiet) / rate)).toFloat()
        }
    }

    @Test fun `leading silence is skipped, keeping 50 ms of lead-in`() {
        val levels = AudioLevels.analyse(clip(400, 500, 0.3), rate)
        assertEquals(rate * 350 / 1000, levels.startFrame)
    }

    @Test fun `background noise below the onset threshold is skipped too`() {
        val levels = AudioLevels.analyse(clip(400, 500, 0.3, noise = 0.005), rate)
        assertEquals(rate * 350 / 1000, levels.startFrame)
    }

    @Test fun `a clip with no lead-in starts at 0`() {
        assertEquals(0, AudioLevels.analyse(clip(0, 500, 0.3), rate).startFrame)
    }

    @Test fun `quiet clips are amplified and loud clips attenuated to the same RMS`() {
        // a sine's RMS is amplitude / sqrt(2)
        val quiet = AudioLevels.analyse(clip(100, 500, 0.05), rate).gain
        val loud = AudioLevels.analyse(clip(100, 500, 0.8), rate).gain
        assertEquals(AudioLevels.TARGET_RMS / (0.05 / Math.sqrt(2.0)), quiet, 0.05)
        assertTrue(loud < 1.0)
        assertEquals(0.05 * quiet, 0.8 * loud, 0.005) // same level after gain
    }

    @Test fun `gain never pushes the peak past the limit or above the max`() {
        val spiky = clip(0, 500, 0.02).also { it[100] = 0.5f }
        val g = AudioLevels.analyse(spiky, rate).gain
        assertTrue(g * 0.5 <= AudioLevels.PEAK_LIMIT + 1e-9)
        assertTrue(AudioLevels.analyse(clip(0, 500, 0.012), rate).gain <= AudioLevels.MAX_GAIN)
    }

    @Test fun `silent clip plays unchanged`() {
        assertEquals(AudioLevels.Levels(0, 1.0), AudioLevels.analyse(FloatArray(1600), rate))
    }
}
