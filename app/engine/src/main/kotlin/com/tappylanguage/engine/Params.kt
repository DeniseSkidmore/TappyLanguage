package com.tappylanguage.engine

/** Tunable constants from docs/requirements/selection.md. All are starting guesses. */
object Params {
    // §3 running values: success counts less than failure
    const val SUCCESS_WEIGHT = 0.1
    const val FAILURE_WEIGHT = 0.25
    const val LATENCY_WEIGHT = 0.2
    const val START_LATENCY_MS = 10_000.0
    const val TIMEOUT_LATENCY_FACTOR = 1.1

    // §4 initial difficulty
    const val LONG_WORD_LETTERS = 10
    const val LENGTH_WEIGHT = 0.7
    const val RARITY_WEIGHT = 0.3
    const val MIN_INITIAL_DIFFICULTY = 0.1

    // §5 stability
    const val START_STABILITY_DAYS = 0.1
    const val MIN_STABILITY_DAYS = 0.05
    const val GROWTH_RATE = 3.0
    const val WRONG_TAP_STABILITY_FACTOR = 0.5
    const val TIMEOUT_STABILITY_FACTOR = 0.8 // TBD in selection.md §5

    // §6 forgetting curve (FSRS-4.5 shape)
    const val FORGETTING_FACTOR = 19.0 / 81.0
    const val FORGETTING_DECAY = -0.5

    // §7 states (display label only)
    const val FLUENT_THRESHOLD = 0.85

    // §8 level building
    const val OVERLAP_BONUS = 0.2
    const val SKILL_PICK_FLOOR = 0.05
    const val TARGET_TRAVEL_SHARE = 0.5
    const val MIN_FALL_MS = 3_000.0
    const val MAX_FALL_MS = 60_000.0
    const val MAX_ON_SCREEN = 4
    const val FIRST_LEVEL_DURATION_MS = 100_000L
    const val FULL_LEVEL_DURATION_MS = 300_000L
    const val FULL_DURATION_FROM_LEVEL = 5
    const val NEW_WORD_MIN_PLAYS = 5
    const val EMPTY_TAP_SPEED_FACTOR = 0.95 // each empty tap: speed × 0.95 for the rest of the prompt

    // §9 level result
    const val LEVEL_UP_RATE = 0.9
    const val LEVEL_STAY_ESTABLISHED_RATE = 0.8
    const val SESSION_GAP_MS = 3_600_000L // resume after this long idle = new session

    const val MS_PER_DAY = 86_400_000.0
}
