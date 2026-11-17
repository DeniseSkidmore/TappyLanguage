package com.tappylanguage.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val T0 = 1_700_000_000_000L
private const val DAY = 86_400_000L

private fun pack(vararg words: String) =
    LanguagePack("el", 50_000, words.mapIndexed { i, w -> Item(w, null, i + 1) })

private fun ll(text: String, case: TargetCase = TargetCase.AS_WRITTEN) =
    SkillKey(TextForms.matchKey(text), Combination.LEARNING_LEARNING, case)

private fun hit(key: SkillKey, at: Long, latency: Long = 2_000) =
    Play(key, PlayOutcome.HIT, latency, 20_000, at)

class MemoryTest {
    private val skill = Skill(ll("ναι"), difficulty = 0.3)

    @Test fun `forgetting is 0_9 when days equal stability`() {
        assertEquals(0.9, Memory.forgetting(1.0, 1.0), 1e-9)
        assertEquals(0.900, Memory.forgetting(7.0, 7.0), 1e-9)
    }

    @Test fun `forgetting matches graph A`() {
        assertEquals(0.825, Memory.forgetting(14.0, 7.0), 0.001)
        assertEquals(0.483, Memory.forgetting(14.0, 1.0), 0.001)
        assertEquals(0.172, Memory.forgetting(14.0, 0.1), 0.001)
    }

    @Test fun `recall is 0 before the first success`() {
        assertEquals(0.0, Memory.predictedRecall(skill, T0))
    }

    @Test fun `19 clean hits in a row pass the fluent threshold (graph C)`() {
        var s = skill
        repeat(18) { s = Memory.apply(s, hit(s.key, T0 + it)) }
        assertTrue(s.successAvg < Params.FLUENT_THRESHOLD)
        s = Memory.apply(s, hit(s.key, T0 + 19))
        assertTrue(s.successAvg > Params.FLUENT_THRESHOLD)
    }

    @Test fun `a failure at 0_85 drops success to about 0_64 and 9 hits recover it`() {
        var s = skill.copy(successAvg = 0.85)
        s = Memory.apply(s, Play(s.key, PlayOutcome.TIMEOUT, null, 20_000, T0))
        assertEquals(0.6375, s.successAvg, 1e-9)
        repeat(8) { s = Memory.apply(s, hit(s.key, T0 + it)) }
        assertTrue(s.successAvg < 0.85)
        s = Memory.apply(s, hit(s.key, T0 + 9))
        assertTrue(s.successAvg >= 0.85)
    }

    @Test fun `hit after one wrong tap scores 0_5 and is a failure`() {
        val play = Play(skill.key, PlayOutcome.HIT, 3_000, 20_000, T0, taps = 2)
        assertEquals(0.5, Memory.playScore(play), 1e-9)
        val s = Memory.apply(skill, play)
        assertEquals(0.25 * 0.5, s.successAvg, 1e-9)
        assertEquals(Params.MIN_STABILITY_DAYS, s.stabilityDays, 1e-9)
        assertEquals(0.8 * 10_000 + 0.2 * 3_000, s.latencyAvgMs, 1e-9) // a hit always samples latency
        assertNull(s.lastSuccessMs)
    }

    @Test fun `timeout is a failure and samples 1_1 x opportunity as latency`() {
        val s = Memory.apply(skill, Play(skill.key, PlayOutcome.TIMEOUT, null, 20_000, T0))
        assertEquals(0.0, s.successAvg)
        assertEquals(0.8 * 10_000 + 0.2 * 22_000, s.latencyAvgMs, 1e-9)
        assertEquals(0.08, s.stabilityDays, 1e-9)
        assertNull(s.lastSuccessMs)
    }

    @Test fun `tapped distractor halves stability and leaves latency alone`() {
        val s = Memory.apply(skill, Play(skill.key, PlayOutcome.WRONG_TAP, null, 20_000, T0))
        assertEquals(Params.START_LATENCY_MS, s.latencyAvgMs)
        assertEquals(Params.MIN_STABILITY_DAYS, s.stabilityDays, 1e-9) // 0.1 × 0.5 = 0.05 = floor
        val again = Memory.apply(s, Play(skill.key, PlayOutcome.WRONG_TAP, null, 20_000, T0))
        assertEquals(Params.MIN_STABILITY_DAYS, again.stabilityDays, 1e-9)
    }

    @Test fun `first hit grows stability most when the word is easy`() {
        val s = Memory.apply(skill, hit(skill.key, T0))
        assertEquals(0.1 * (1 + 3 * 0.7 * 1.0), s.stabilityDays, 1e-9)
        assertEquals(T0, s.lastSuccessMs)
    }
}

class TextFormsTest {
    @Test fun `greek all caps drops the tonos`() {
        assertEquals("ΕΥΧΑΡΙΣΤΩ", TextForms.allCaps("ευχαριστώ", "el"))
        assertEquals("ΜΕΡΑ", TextForms.allCaps("μέρα", "el"))
        assertEquals("ΚΑΛΗΜΕΡΑ", TextForms.allCaps("καλημέρα", "el"))
    }

    @Test fun `match key folds case, final sigma and punctuation`() {
        assertEquals(TextForms.matchKey("καλά"), TextForms.matchKey("Καλά!"))
        assertEquals(TextForms.matchKey("εσυς"), TextForms.matchKey("ΕΣΥΣ"))
    }
}

class LevelPlannerTest {
    private val pack = pack("ναι", "όχι", "και")

    @Test fun `level 1 is one new word about ten times`() {
        val planner = LevelPlanner(pack, SkillStore(pack), Random(1))
        val plan = planner.plan(1, T0)
        assertEquals(1, plan.pool.size)
        assertEquals(10, plan.targets.size)
        assertEquals(1, plan.targets.map { it.itemKey }.toSet().size)
        assertTrue(plan.establishedKeys.isEmpty())
    }

    /** Store where every word of `words` has a clean history with the given recall-ish success. */
    private fun known(store: SkillStore, success: Double, vararg words: String) = words.forEach { w ->
        TargetCase.entries.forEach { c ->
            store.put(Skill(ll(w, c), successAvg = success, difficulty = 0.3, stabilityDays = 30.0,
                lastSuccessMs = T0, lastPlayedMs = T0))
        }
    }

    @Test fun `fluent words stay in the pool and a new word joins when there is room`() {
        val store = SkillStore(pack)
        known(store, 0.95, "ναι", "όχι")
        val planner = LevelPlanner(pack, store, Random(4))
        val pool = planner.pool(3, T0)
        assertEquals(setOf("ναι", "όχι", "και"), pool.map { it.text }.toSet())
        assertEquals(2, planner.pool(2, T0).size) // no room: no new word
    }

    @Test fun `the new word gets at least 5 plays and fluent words still appear`() {
        val store = SkillStore(pack)
        known(store, 0.95, "ναι", "όχι")
        val planner = LevelPlanner(pack, store, Random(5))
        val plan = planner.plan(3, T0)
        val counts = plan.targets.groupingBy { it.itemKey }.eachCount()
        assertTrue((counts[TextForms.matchKey("και")] ?: 0) >= Params.NEW_WORD_MIN_PLAYS)
        assertTrue((counts[TextForms.matchKey("ναι")] ?: 0) + (counts[TextForms.matchKey("όχι")] ?: 0) > 0)
        assertEquals(setOf(TextForms.matchKey("ναι"), TextForms.matchKey("όχι")), plan.establishedKeys)
    }

    @Test fun `with no qualifying distractor the best-known played words are used`() {
        val store = SkillStore(pack)
        known(store, 0.3, "ναι")
        known(store, 0.2, "όχι")
        val planner = LevelPlanner(pack, store, Random(7))
        // target ναι (0.3) needs distractors >= 0.65: none qualify, so the played όχι falls back in; και is unplayed
        assertEquals(listOf(TextForms.matchKey("όχι")), planner.distractors(ll("ναι"), T0).map { it.itemKey })
    }

    @Test fun `no back-to-back repeats when the pool has several words`() {
        val store = SkillStore(pack)
        known(store, 0.5, "ναι", "όχι", "και")
        val plan = LevelPlanner(pack, store, Random(6)).plan(3, T0)
        plan.targets.zipWithNext().forEach { (a, b) -> assertTrue(a.itemKey != b.itemKey) }
    }

    @Test fun `level duration runs 100 s to 300 s over levels 1 to 5`() {
        val planner = LevelPlanner(pack, SkillStore(pack))
        assertEquals(listOf(100_000L, 150_000L, 200_000L, 250_000L, 300_000L, 300_000L),
            (1..6).map { planner.levelDurationMs(it) })
    }

    @Test fun `fall time puts the average tap halfway down`() {
        val planner = LevelPlanner(pack, SkillStore(pack))
        assertEquals(20_000L, planner.fallTimeMs(Skill(ll("ναι"), difficulty = 0.3)))
    }

    @Test fun `only one new word joins the pool`() {
        val store = SkillStore(pack)
        val planner = LevelPlanner(pack, store, Random(2))
        assertEquals(1, planner.pool(3, T0).count { planner.isNew(it) })
    }

    @Test fun `distractor threshold rises as the target weakens`() {
        val planner = LevelPlanner(pack, SkillStore(pack))
        assertEquals(0.5, planner.distractorMinRecall(0.0), 1e-9)
        assertEquals(0.9, planner.distractorMinRecall(0.8), 1e-9)
    }

    @Test fun `no distractors in level 1`() {
        val planner = LevelPlanner(pack, SkillStore(pack), Random(3))
        val target = planner.plan(1, T0).targets.first()
        assertTrue(planner.distractors(target, T0).isEmpty())
    }

    @Test fun `level up above 90 percent, stay while established words hold, else down 1 (thread 22 examples)`() {
        val planner = LevelPlanner(pack("α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ"), SkillStore(pack))
        fun tally(newHits: Int, newPlays: Int, estHits: Int, estPlays: Int): LevelTally {
            var t = LevelTally()
            repeat(newPlays) { t = t.add(clean = it < newHits, established = false) }
            repeat(estPlays) { t = t.add(clean = it < estHits, established = true) }
            return t
        }
        assertEquals(11, planner.levelResult(10, tally(4, 5, 24, 25)).nextLevel) // 28/30
        assertEquals(10, planner.levelResult(10, tally(2, 5, 24, 25)).nextLevel) // 26/30, est 0.96
        assertEquals(9, planner.levelResult(10, tally(5, 5, 19, 25)).nextLevel)  // est 0.76
        assertEquals(1, planner.levelResult(1, tally(0, 10, 0, 0)).nextLevel)    // no established: stay
    }

    @Test fun `session start keeps the level for a daily player and drops it after a long break`() {
        val store = SkillStore(pack)
        known(store, 0.9, "ναι", "όχι", "και")
        store.all().toList().forEach { store.put(it.copy(stabilityDays = 2.0)) }
        val planner = LevelPlanner(pack, store)
        val progress = ModeProgress(levelNumber = 3, lastRecallAvg = planner.knownRecallAverage(3, T0))
        assertEquals(3, planner.sessionStartLevel(progress, T0 + DAY / 24))
        assertTrue(planner.sessionStartLevel(progress, T0 + 60 * DAY) < 3)
    }
}
