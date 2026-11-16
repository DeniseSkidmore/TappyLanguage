package com.tappylanguage.engine

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class LevelPlan(
    val number: Int,
    val pool: List<Item>,
    val targets: List<SkillKey>,
)

data class LevelOutcome(
    val successRate: Double,
    val nextLevel: Int,
    val levelSuccessAvg: Double,
)

/**
 * Builds levels from saved skills (selection.md §7–9).
 * Prototype scope: only the learning-learning combination is unlocked; both cases are unlocked from the start.
 */
class LevelPlanner(
    private val pack: LanguagePack,
    private val store: SkillStore,
    private val random: Random = Random.Default,
) {
    val maxLevel: Int get() = pack.items.size

    fun unlockedKeys(item: Item): List<SkillKey> =
        TargetCase.entries.map { SkillKey(item.key, Combination.LEARNING_LEARNING, it) }

    fun recall(key: SkillKey, nowMs: Long): Double =
        store.get(key)?.let { Memory.predictedRecall(it, nowMs) } ?: 0.0

    /** §8: a word's recall is its weakest unlocked skill. */
    fun wordRecall(item: Item, nowMs: Long): Double =
        unlockedKeys(item).minOf { recall(it, nowMs) }

    /** §7: unproven = no success yet in any unlocked skill. */
    fun isNew(item: Item): Boolean =
        unlockedKeys(item).none { store.get(it)?.lastSuccessMs != null }

    private fun wasPlayed(item: Item): Boolean =
        unlockedKeys(item).any { store.get(it)?.lastPlayedMs != null }

    /** §8: share of the word's letters already in known words. */
    fun letterOverlap(item: Item, knownItems: List<Item>): Double {
        val letters = item.key.filter { it.isLetter() }.toSet()
        if (letters.isEmpty()) return 0.0
        val known = knownItems.flatMap { it.key.filter(Char::isLetter).toList() }.toSet()
        return letters.count { it in known }.toDouble() / letters.size
    }

    fun newWordScore(item: Item, knownItems: List<Item>): Double =
        (1 - Memory.initialDifficulty(item, pack.vocabularySize)) +
            Params.OVERLAP_BONUS * letterOverlap(item, knownItems)

    /** Levels 1..5 run linearly from 100 s to 300 s, then stay at 300 s. */
    fun levelDurationMs(level: Int): Long {
        val steps = (Params.FULL_DURATION_FROM_LEVEL - 1).toLong()
        val step = (Params.FULL_LEVEL_DURATION_MS - Params.FIRST_LEVEL_DURATION_MS) / steps
        return min(Params.FULL_LEVEL_DURATION_MS, Params.FIRST_LEVEL_DURATION_MS + step * (level - 1))
    }

    /** §8: average tap lands at TARGET_TRAVEL_SHARE of the fall. */
    fun fallTimeMs(skill: Skill): Long =
        (skill.latencyAvgMs / Params.TARGET_TRAVEL_SHARE).coerceIn(Params.MIN_FALL_MS, Params.MAX_FALL_MS).toLong()

    fun pool(level: Int, nowMs: Long): List<Item> {
        val recalls = pack.items.associateWith { wordRecall(it, nowMs) }
        val nonFluent = pack.items.filter { recalls.getValue(it) < Params.FLUENT_THRESHOLD }
        val (newItems, known) = nonFluent.partition { isNew(it) }
        val pool = known.sortedByDescending { recalls.getValue(it) }.take(level).toMutableList()
        if (pool.size < level && newItems.isNotEmpty()) {
            val knownItems = pack.items.filterNot { isNew(it) }
            val started = newItems.filter { wasPlayed(it) }
            pool += started.ifEmpty { newItems }.maxBy { newWordScore(it, knownItems) }
        }
        if (pool.isEmpty()) {
            // Prototype fallback (not in selection.md): everything is fluent, so review the weakest words.
            pool += pack.items.sortedBy { recalls.getValue(it) }.take(level)
        }
        return pool
    }

    /** §8: pick a skill of the item, weakest most often. */
    fun pickSkill(item: Item, nowMs: Long): SkillKey {
        val keys = unlockedKeys(item)
        val weights = keys.map { (1 - recall(it, nowMs)) + Params.SKILL_PICK_FLOOR }
        var r = random.nextDouble() * weights.sum()
        keys.forEachIndexed { i, key ->
            r -= weights[i]
            if (r <= 0) return key
        }
        return keys.last()
    }

    fun plan(level: Int, nowMs: Long): LevelPlan {
        val pool = pool(level, nowMs)
        val duration = levelDurationMs(level)
        val targets = mutableListOf<SkillKey>()
        var plannedMs = 0.0
        var previous: Item? = null
        while (plannedMs < duration) {
            val choices = if (pool.size > 1) pool.filter { it != previous } else pool
            val item = choices[random.nextInt(choices.size)]
            val key = pickSkill(item, nowMs)
            targets += key
            plannedMs += store.getOrCreate(key).latencyAvgMs
            previous = item
        }
        return LevelPlan(level, pool, targets)
    }

    /** §8: weaker targets get better-known distractors. */
    fun distractorMinRecall(targetRecall: Double): Double =
        targetRecall + (1 - targetRecall) * Params.DISTRACTOR_GAP

    fun distractors(target: SkillKey, nowMs: Long, max: Int = Params.MAX_ON_SCREEN - 1): List<SkillKey> {
        val minRecall = distractorMinRecall(recall(target, nowMs))
        return pack.items
            .filter { it.key != target.itemKey && wordRecall(it, nowMs) >= minRecall }
            .shuffled(random)
            .take(max)
            .map { SkillKey(it.key, target.combination, TargetCase.entries.random(random)) }
    }

    /** §9: level result and next level number. */
    fun levelResult(progress: ModeProgress, hits: Int, prompts: Int): LevelOutcome {
        val rate = if (prompts == 0) 0.0 else hits.toDouble() / prompts
        val w = Params.LEVEL_SUCCESS_WEIGHT
        val next = when {
            rate > Params.LEVEL_UP_RATE -> progress.levelNumber + 1
            rate < progress.levelSuccessAvg - Params.LEVEL_DOWN_MARGIN -> progress.levelNumber - 1
            else -> progress.levelNumber
        }.coerceIn(1, maxLevel)
        return LevelOutcome(rate, next, (1 - w) * progress.levelSuccessAvg + w * rate)
    }

    /** Average recall of the `level` best-known words (fluent included), for the session-start rule. */
    fun knownRecallAverage(level: Int, nowMs: Long): Double {
        val top = pack.items.filterNot { isNew(it) }
            .map { wordRecall(it, nowMs) }
            .sortedDescending()
            .take(level)
        return if (top.isEmpty()) 0.0 else top.average()
    }

    /**
     * §9 session start, as implemented: scale the level by how much recall dropped since the last level ended.
     * (selection.md's formula used the pool's own recall, which excludes fluent words and would lower the level
     * even for a daily player; see conversation thread 17.)
     */
    fun sessionStartLevel(progress: ModeProgress, nowMs: Long): Int {
        if (progress.lastRecallAvg <= 0.0) return progress.levelNumber
        val ratio = min(1.0, knownRecallAverage(progress.levelNumber, nowMs) / progress.lastRecallAvg)
        return (progress.levelNumber * ratio).roundToInt().coerceIn(1, maxLevel)
    }
}
