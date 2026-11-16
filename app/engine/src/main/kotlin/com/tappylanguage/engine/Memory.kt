package com.tappylanguage.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Recall model and per-play updates (selection.md §2–6). Pure functions. */
object Memory {

    /** §6: FSRS-4.5 forgetting curve; 0.9 when daysSinceSuccess == stabilityDays. */
    fun forgetting(daysSinceSuccess: Double, stabilityDays: Double): Double =
        (1 + Params.FORGETTING_FACTOR * daysSinceSuccess / stabilityDays).pow(Params.FORGETTING_DECAY)

    /** §6: success average × forgetting; 0 if the skill never succeeded. */
    fun predictedRecall(skill: Skill, nowMs: Long): Double {
        val lastSuccess = skill.lastSuccessMs ?: return 0.0
        val days = max(0.0, (nowMs - lastSuccess) / Params.MS_PER_DAY)
        return skill.successAvg * forgetting(days, skill.stabilityDays)
    }

    /** §4: seeded from length and rarity. */
    fun initialDifficulty(item: Item, vocabularySize: Int): Double {
        val lengthScore = min(item.letterCount.toDouble() / Params.LONG_WORD_LETTERS, 1.0)
        val rarityScore = item.frequencyRank?.let { min(it.toDouble() / vocabularySize, 1.0) } ?: 1.0
        return (Params.LENGTH_WEIGHT * lengthScore + Params.RARITY_WEIGHT * rarityScore)
            .coerceIn(Params.MIN_INITIAL_DIFFICULTY, 1.0)
    }

    /** §2: 1 / taps on a hit; 0 on a timeout or a tapped distractor. */
    fun playScore(play: Play): Double =
        if (play.outcome == PlayOutcome.HIT) 1.0 / max(1, play.taps) else 0.0

    /** §2: latency sample, or null when latency must not change (tapped distractor). */
    fun latencySample(play: Play): Double? = when (play.outcome) {
        PlayOutcome.HIT -> play.latencyMs?.toDouble()
        PlayOutcome.TIMEOUT -> Params.TIMEOUT_LATENCY_FACTOR * play.opportunityMs
        PlayOutcome.WRONG_TAP -> null
    }

    /** §3: success counts less than failure. */
    fun weight(play: Play): Double = if (play.isSuccess) Params.SUCCESS_WEIGHT else Params.FAILURE_WEIGHT

    /** §3 + §5: fold one play into a skill's running values. */
    fun apply(skill: Skill, play: Play): Skill {
        val w = weight(play)
        val score = playScore(play)
        val recallBefore = predictedRecall(skill, play.atMs)
        val sample = latencySample(play)
        val stability = when {
            play.isSuccess ->
                skill.stabilityDays * (1 + Params.GROWTH_RATE * (1 - skill.difficulty) * (1 - recallBefore))
            play.outcome == PlayOutcome.TIMEOUT ->
                max(Params.MIN_STABILITY_DAYS, skill.stabilityDays * Params.TIMEOUT_STABILITY_FACTOR)
            else -> // hit after wrong taps, or a tapped distractor
                max(Params.MIN_STABILITY_DAYS, skill.stabilityDays * Params.WRONG_TAP_STABILITY_FACTOR)
        }
        val lw = Params.LATENCY_WEIGHT
        return skill.copy(
            successAvg = (1 - w) * skill.successAvg + w * score,
            latencyAvgMs = sample?.let { (1 - lw) * skill.latencyAvgMs + lw * it } ?: skill.latencyAvgMs,
            difficulty = (1 - w) * skill.difficulty + w * (1 - score),
            stabilityDays = stability,
            lastPlayedMs = play.atMs,
            lastSuccessMs = if (play.isSuccess) play.atMs else skill.lastSuccessMs,
        )
    }
}
