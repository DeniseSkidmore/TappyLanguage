package com.tappylanguage.engine

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class LevelPlan(
    val number: Int,
    val pool: List<Item>,
    val targets: List<SkillKey>,
    /** Item keys with at least one success before this level started (selection.md §9). */
    val establishedKeys: Set<String>,
)

/** Counts for the level result: clean hits over target prompts, overall and for established words. */
data class LevelTally(
    val hits: Int = 0,
    val prompts: Int = 0,
    val establishedHits: Int = 0,
    val establishedPrompts: Int = 0,
) {
    fun add(clean: Boolean, established: Boolean): LevelTally = LevelTally(
        hits + if (clean) 1 else 0,
        prompts + 1,
        establishedHits + if (clean && established) 1 else 0,
        establishedPrompts + if (established) 1 else 0,
    )
}

data class LevelOutcome(
    val successRate: Double,
    val establishedRate: Double?,
    val nextLevel: Int,
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
    /** §8: Max level = number of playable items in the pack. */
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

    fun wasPlayed(item: Item): Boolean =
        unlockedKeys(item).any { store.get(it)?.lastPlayedMs != null }

    /** §8: share of the word's letters already in played words. */
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

    /**
     * §8: the `level` played words with the highest recall (fluent included), plus one new word
     * if the pool has room and holds no unproven word yet.
     */
    fun pool(level: Int, nowMs: Long): List<Item> {
        val (played, unplayed) = pack.items.partition { wasPlayed(it) }
        val pool = played.sortedByDescending { wordRecall(it, nowMs) }.take(level).toMutableList()
        if (pool.size < level && pool.none { isNew(it) } && unplayed.isNotEmpty()) {
            pool += unplayed.maxBy { newWordScore(it, played) }
        }
        return pool
    }

    /** §8 step 2: per-skill pick weight, weakest most often. */
    fun pickWeight(key: SkillKey, nowMs: Long): Double = (1 - recall(key, nowMs)) + Params.SKILL_PICK_FLOOR

    private fun <T> weighted(choices: List<T>, weight: (T) -> Double): T {
        val weights = choices.map(weight)
        var r = random.nextDouble() * weights.sum()
        choices.forEachIndexed { i, c ->
            r -= weights[i]
            if (r <= 0) return c
        }
        return choices.last()
    }

    /** §8: pick a skill of the item, weakest most often. */
    fun pickSkill(item: Item, nowMs: Long): SkillKey = weighted(unlockedKeys(item)) { pickWeight(it, nowMs) }

    fun plan(level: Int, nowMs: Long): LevelPlan {
        val pool = pool(level, nowMs)
        val duration = levelDurationMs(level)
        val pairs = pool.flatMap { item -> unlockedKeys(item).map { item to it } }
        val targets = mutableListOf<SkillKey>()
        var plannedMs = 0.0
        var previous: String? = null
        // Steps 1–4: weighted draw over (word, skill) pairs, no back-to-back word repeat.
        while (plannedMs < duration && pairs.isNotEmpty()) {
            val choices = pairs.filter { it.first.key != previous }.ifEmpty { pairs }
            val (item, key) = weighted(choices) { pickWeight(it.second, nowMs) }
            targets += key
            plannedMs += store.getOrCreate(key).latencyAvgMs
            previous = item.key
        }
        pool.firstOrNull { isNew(it) }?.let { ensureMinPlays(targets, it, nowMs) }
        val established = pool.filterNot { isNew(it) }.map { it.key }.toSet()
        return LevelPlan(level, pool, targets, established)
    }

    /** §8 step 5: the new word gets at least NEW_WORD_MIN_PLAYS targets, avoiding back-to-back where possible. */
    private fun ensureMinPlays(targets: MutableList<SkillKey>, newItem: Item, nowMs: Long) {
        fun isNewAt(i: Int) = i in targets.indices && targets[i].itemKey == newItem.key
        val needed = min(Params.NEW_WORD_MIN_PLAYS, targets.size) - targets.indices.count { isNewAt(it) }
        if (needed <= 0) return
        val candidates = targets.indices.filterNot { isNewAt(it) }.shuffled(random)
        var left = needed
        // Pass 1 replaces only slots with no new-word neighbour (checked live); pass 2 fills whatever remains.
        for (spacedOnly in listOf(true, false)) {
            for (i in candidates) {
                if (left == 0) return
                if (isNewAt(i)) continue
                if (spacedOnly && (isNewAt(i - 1) || isNewAt(i + 1))) continue
                targets[i] = pickSkill(newItem, nowMs)
                left--
            }
        }
    }

    /** §8: weaker targets get better-known distractors. */
    fun distractorMinRecall(targetRecall: Double): Double =
        targetRecall + (1 - targetRecall) * Params.DISTRACTOR_GAP

    /**
     * §8: words above the threshold, at random. If none qualify, the best-known other played words
     * instead (none only while no other word has been played, e.g. level 1).
     */
    fun distractors(target: SkillKey, nowMs: Long, max: Int = Params.MAX_ON_SCREEN - 1): List<SkillKey> {
        val minRecall = distractorMinRecall(recall(target, nowMs))
        val others = pack.items.filter { it.key != target.itemKey }
        val qualifying = others.filter { wordRecall(it, nowMs) >= minRecall }.shuffled(random).take(max)
        val chosen = qualifying.ifEmpty {
            others.filter { wasPlayed(it) }.sortedByDescending { wordRecall(it, nowMs) }.take(max)
        }
        return chosen.map { SkillKey(it.key, target.combination, TargetCase.entries.random(random)) }
    }

    /**
     * §9: up 1 above 90% overall; else stay while established words are hit at least 80%
     * (or there were none); else down 1.
     */
    fun levelResult(level: Int, tally: LevelTally): LevelOutcome {
        val rate = if (tally.prompts == 0) 0.0 else tally.hits.toDouble() / tally.prompts
        val established = if (tally.establishedPrompts == 0) null
            else tally.establishedHits.toDouble() / tally.establishedPrompts
        val next = when {
            rate > Params.LEVEL_UP_RATE -> level + 1
            established == null || established >= Params.LEVEL_STAY_ESTABLISHED_RATE -> level
            else -> level - 1
        }.coerceIn(1, maxLevel)
        return LevelOutcome(rate, established, next)
    }

    /** Average recall of the `level` best-known words (fluent included), for the session-start rule. */
    fun knownRecallAverage(level: Int, nowMs: Long): Double {
        val top = pack.items.filterNot { isNew(it) }
            .map { wordRecall(it, nowMs) }
            .sortedDescending()
            .take(level)
        return if (top.isEmpty()) 0.0 else top.average()
    }

    /** §9 session start: scale the level by how much recall dropped since the last level ended. */
    fun sessionStartLevel(progress: ModeProgress, nowMs: Long): Int {
        if (progress.lastRecallAvg <= 0.0) return progress.levelNumber.coerceIn(1, maxLevel)
        val ratio = min(1.0, knownRecallAverage(progress.levelNumber, nowMs) / progress.lastRecallAvg)
        return (progress.levelNumber * ratio).roundToInt().coerceIn(1, maxLevel)
    }
}
