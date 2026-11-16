package com.tappylanguage.engine

enum class Combination { LEARNING_LEARNING, KNOWN_LEARNING, LEARNING_KNOWN }

enum class TargetCase { AS_WRITTEN, ALL_CAPS }

enum class PlayOutcome { HIT, WRONG_TAP, TIMEOUT }

/** One learning-language text from a language pack (in-memory form). */
data class Item(
    val text: String,
    val audioFile: String?,
    val frequencyRank: Int?,
) {
    val key: String = TextForms.matchKey(text)
    val letterCount: Int = text.count { it.isLetter() }
}

data class LanguagePack(
    val languageCode: String,
    val vocabularySize: Int,
    val items: List<Item>,
) {
    private val byKey = items.associateBy { it.key }
    fun item(key: String): Item? = byKey[key]
}

/** Identifies a skill: item × combination × case (the language pair is implied by the pack). */
data class SkillKey(
    val itemKey: String,
    val combination: Combination,
    val targetCase: TargetCase,
)

/** Saved running values for one skill (selection.md §3). Immutable; updates return a copy. */
data class Skill(
    val key: SkillKey,
    val successAvg: Double = 0.0,
    val latencyAvgMs: Double = Params.START_LATENCY_MS,
    val difficulty: Double,
    val stabilityDays: Double = Params.START_STABILITY_DAYS,
    val lastSuccessMs: Long? = null,
    val lastPlayedMs: Long? = null,
)

/** One entry of the level play log (selection.md §1). */
data class Play(
    val skill: SkillKey,
    val outcome: PlayOutcome,
    val latencyMs: Long?,
    val opportunityMs: Long,
    val atMs: Long,
)

/** Saved per pair and content mode. */
data class ModeProgress(
    val levelNumber: Int = 1,
    val levelSuccessAvg: Double = 0.0,
    /** Average recall of the known pool at the end of the last level; used for the session-start level. */
    val lastRecallAvg: Double = 0.0,
)
