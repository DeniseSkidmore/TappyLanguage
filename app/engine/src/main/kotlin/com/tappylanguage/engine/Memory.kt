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

    /** §2: timeouts and wrong taps are both failures for recall. */
    fun playScore(outcome: PlayOutcome): Double = if (outcome == PlayOutcome.HIT) 1.0 else 0.0

    /** §2: latency sample, or null when latency must not change (wrong tap). */
    fun latencySample(play: Play): Double? = when (play.outcome) {
        PlayOutcome.HIT -> play.latencyMs?.toDouble()
        PlayOutcome.TIMEOUT -> Params.TIMEOUT_LATENCY_FACTOR * play.opportunityMs
        PlayOutcome.WRONG_TAP -> null
    }

    /** §3 + §5: fold one play into a skill's running values. */
    fun apply(skill: Skill, play: Play): Skill {
        val w = Params.RECENCY_WEIGHT
        val score = playScore(play.outcome)
        val recallBefore = predictedRecall(skill, play.atMs)
        val sample = latencySample(play)
        val stability = when (play.outcome) {
            PlayOutcome.HIT ->
                skill.stabilityDays * (1 + Params.GROWTH_RATE * (1 - skill.difficulty) * (1 - recallBefore))
            PlayOutcome.WRONG_TAP ->
                max(Params.MIN_STABILITY_DAYS, skill.stabilityDays * Params.WRONG_TAP_STABILITY_FACTOR)
            PlayOutcome.TIMEOUT ->
                max(Params.MIN_STABILITY_DAYS, skill.stabilityDays * Params.TIMEOUT_STABILITY_FACTOR)
        }
        return skill.copy(
            successAvg = (1 - w) * skill.successAvg + w * score,
            latencyAvgMs = sample?.let { (1 - w) * skill.latencyAvgMs + w * it } ?: skill.latencyAvgMs,
            difficulty = (1 - w) * skill.difficulty + w * (1 - score),
            stabilityDays = stability,
            lastPlayedMs = play.atMs,
            lastSuccessMs = if (score == 1.0) play.atMs else skill.lastSuccessMs,
        )
    }
}
