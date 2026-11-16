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

    @Test fun `about 9 hits in a row pass the fluent threshold (graph C)`() {
        var s = skill
        repeat(8) { s = Memory.apply(s, hit(s.key, T0 + it)) }
        assertTrue(s.successAvg < Params.FLUENT_THRESHOLD)
        s = Memory.apply(s, hit(s.key, T0 + 9))
        assertTrue(s.successAvg > Params.FLUENT_THRESHOLD)
    }

    @Test fun `timeout is a failure and samples 1_1 x opportunity as latency`() {
        val s = Memory.apply(skill, Play(skill.key, PlayOutcome.TIMEOUT, null, 20_000, T0))
        assertEquals(0.0, s.successAvg)
        assertEquals(0.8 * 10_000 + 0.2 * 22_000, s.latencyAvgMs, 1e-9)
        assertEquals(0.08, s.stabilityDays, 1e-9)
        assertNull(s.lastSuccessMs)
    }

    @Test fun `wrong tap halves stability and leaves latency alone`() {
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

    @Test fun `level goes up above 90 percent and down well below average`() {
        val planner = LevelPlanner(pack, SkillStore(pack))
        val progress = ModeProgress(levelNumber = 2, levelSuccessAvg = 0.8)
        assertEquals(3, planner.levelResult(progress, hits = 10, prompts = 10).nextLevel)
        assertEquals(1, planner.levelResult(progress, hits = 5, prompts = 10).nextLevel)
        assertEquals(2, planner.levelResult(progress, hits = 8, prompts = 10).nextLevel)
    }

    @Test fun `session start keeps the level for a daily player and drops it after a long break`() {
        val store = SkillStore(pack)
        pack.items.forEach { item ->
            TargetCase.entries.forEach { c ->
                store.put(Skill(SkillKey(item.key, Combination.LEARNING_LEARNING, c), successAvg = 0.9,
                    difficulty = 0.3, stabilityDays = 2.0, lastSuccessMs = T0, lastPlayedMs = T0))
            }
        }
        val planner = LevelPlanner(pack, store)
        val progress = ModeProgress(levelNumber = 3, lastRecallAvg = planner.knownRecallAverage(3, T0))
        assertEquals(3, planner.sessionStartLevel(progress, T0 + DAY / 24))
        assertTrue(planner.sessionStartLevel(progress, T0 + 60 * DAY) < 3)
    }
}
