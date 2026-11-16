package com.tappylanguage.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.tappylanguage.engine.LanguagePack
import com.tappylanguage.engine.LevelPlan
import com.tappylanguage.engine.LevelPlanner
import com.tappylanguage.engine.ModeProgress
import com.tappylanguage.engine.Params
import com.tappylanguage.engine.Play
import com.tappylanguage.engine.PlayOutcome
import com.tappylanguage.engine.SkillKey
import com.tappylanguage.engine.SkillStore
import com.tappylanguage.engine.TextForms
import kotlin.random.Random

data class FallingWord(
    val id: Long,
    val skill: SkillKey,
    val text: String,
    val isTarget: Boolean,
    val lane: Int,
    val startMs: Long,
    val fallMs: Long,
    val wrong: Boolean = false,
)

data class Burst(val xFraction: Float, val yFraction: Float, val color: Color, val startMs: Long)

/**
 * Game loop state (requirements.md "Game play"). Compose observes the mutableState fields, like
 * INotifyPropertyChanged properties in MVVM. tick() is called once per frame.
 */
class GameController(
    private val pack: LanguagePack,
    private val progressStore: ProgressStore,
    private val audio: AudioPlayer,
    private val random: Random = Random.Default,
) {
    enum class Phase { PLAYING, SHOW_ANSWER, BETWEEN_LEVELS, PAUSED }

    private val skills = SkillStore(pack)
    private val planner = LevelPlanner(pack, skills, random)
    private var progress: ModeProgress = progressStore.load(skills)

    var phase by mutableStateOf(Phase.PLAYING); private set
    var nowMs by mutableLongStateOf(System.currentTimeMillis()); private set
    var message by mutableStateOf(""); private set
    var levelLabel by mutableStateOf(""); private set
    val falling = mutableStateListOf<FallingWord>()
    val bursts = mutableStateListOf<Burst>()

    private lateinit var plan: LevelPlan
    private var targetIndex = 0
    private val plays = mutableListOf<Play>()
    private var prompts = 0
    private var hits = 0
    private var promptStartMs = 0L
    private var promptWrongTapped = false
    private var promptActive = false
    private var phaseEndsMs = 0L
    private var pausedFrom = Phase.PLAYING
    private var pausedAtMs = 0L
    private var nextId = 0L

    init {
        val now = System.currentTimeMillis()
        progress = progress.copy(levelNumber = planner.sessionStartLevel(progress, now))
        startLevel(now)
    }

    fun tick(now: Long) {
        if (phase == Phase.PAUSED) return // freeze the screen; togglePause() shifts start times on resume
        nowMs = now
        bursts.removeAll { now - it.startMs > BURST_MS }
        when (phase) {
            Phase.PLAYING -> {
                if (!promptActive) spawnNext(now)
                else falling.firstOrNull { it.isTarget }?.let { if (now - it.startMs >= it.fallMs) timeout(it, now) }
            }
            Phase.SHOW_ANSWER -> if (now >= phaseEndsMs) { message = ""; phase = Phase.PLAYING }
            Phase.BETWEEN_LEVELS -> if (now >= phaseEndsMs) startLevel(now)
            Phase.PAUSED -> Unit
        }
    }

    fun onTap(word: FallingWord) {
        if (phase != Phase.PLAYING || !promptActive) return
        val now = nowMs
        val yFraction = progressOf(word, now)
        if (word.isTarget) {
            val clean = !promptWrongTapped
            recordTarget(word, if (clean) PlayOutcome.HIT else PlayOutcome.WRONG_TAP, now, if (clean) now - promptStartMs else null)
            if (clean) hits++
            bursts += Burst(laneCenter(word.lane), yFraction, Color(0xFF4CAF50), now)
            falling.clear()
            promptActive = false
        } else if (!word.wrong) {
            promptWrongTapped = true
            plays += Play(word.skill, PlayOutcome.WRONG_TAP, null, word.fallMs, now)
            bursts += Burst(laneCenter(word.lane), yFraction, Color(0xFFE53935), now)
            val i = falling.indexOfFirst { it.id == word.id }
            if (i >= 0) falling[i] = word.copy(wrong = true)
        }
    }

    fun togglePause() {
        val now = System.currentTimeMillis()
        if (phase == Phase.PAUSED) {
            val shift = now - pausedAtMs
            for (i in falling.indices) falling[i] = falling[i].copy(startMs = falling[i].startMs + shift)
            promptStartMs += shift
            phaseEndsMs += shift
            phase = pausedFrom
        } else {
            pausedFrom = phase
            pausedAtMs = now
            phase = Phase.PAUSED
        }
    }

    fun pause() {
        if (phase != Phase.PAUSED) togglePause()
    }

    fun progressOf(word: FallingWord, now: Long): Float =
        ((now - word.startMs).toFloat() / word.fallMs).coerceIn(0f, 1f)

    private fun laneCenter(lane: Int) = (lane + 0.5f) / Params.MAX_ON_SCREEN

    private fun startLevel(now: Long) {
        plan = planner.plan(progress.levelNumber, now)
        targetIndex = 0
        prompts = 0
        hits = 0
        plays.clear()
        falling.clear()
        promptActive = false
        message = ""
        levelLabel = if (progress.levelNumber >= planner.maxLevel) "Max" else progress.levelNumber.toString()
        phase = Phase.PLAYING
    }

    private fun spawnNext(now: Long) {
        if (targetIndex >= plan.targets.size) {
            endLevel(now)
            return
        }
        val key = plan.targets[targetIndex++]
        val item = pack.item(key.itemKey) ?: return
        val fallMs = planner.fallTimeMs(skills.getOrCreate(key))
        val distractors = planner.distractors(key, now)
        val lanes = (0 until Params.MAX_ON_SCREEN).shuffled(random)
        falling.clear()
        falling += FallingWord(nextId++, key, TextForms.display(item, key.targetCase, pack.languageCode), true, lanes[0], now, fallMs)
        distractors.forEachIndexed { i, d ->
            val dItem = pack.item(d.itemKey) ?: return@forEachIndexed
            falling += FallingWord(nextId++, d, TextForms.display(dItem, d.targetCase, pack.languageCode), false, lanes[i + 1], now, fallMs)
        }
        promptStartMs = now
        promptWrongTapped = false
        promptActive = true
        prompts++
        audio.play(item.audioFile)
    }

    private fun timeout(target: FallingWord, now: Long) {
        recordTarget(target, if (promptWrongTapped) PlayOutcome.WRONG_TAP else PlayOutcome.TIMEOUT, now, null)
        bursts += Burst(laneCenter(target.lane), 1f, Color(0xFFE53935), now)
        falling.clear()
        promptActive = false
        message = target.text
        phaseEndsMs = now + ANSWER_FLASH_MS
        phase = Phase.SHOW_ANSWER
    }

    private fun recordTarget(word: FallingWord, outcome: PlayOutcome, now: Long, latencyMs: Long?) {
        plays += Play(word.skill, outcome, latencyMs, word.fallMs, now)
    }

    private fun endLevel(now: Long) {
        val before = plan.pool.associate { item ->
            item.key to planner.unlockedKeys(item).map { skills.getOrCreate(it).successAvg }.average()
        }
        skills.applyAll(plays)
        val outcome = planner.levelResult(progress, hits, prompts)
        progress = ModeProgress(
            levelNumber = outcome.nextLevel,
            levelSuccessAvg = outcome.levelSuccessAvg,
            lastRecallAvg = planner.knownRecallAverage(outcome.nextLevel, now),
        )
        progressStore.save(skills, progress)
        val improved = plan.pool.filter { item ->
            planner.unlockedKeys(item).map { skills.getOrCreate(it).successAvg }.average() > (before[item.key] ?: 0.0)
        }.joinToString("  ") { it.text }
        message = buildString {
            append("${(outcome.successRate * 100).toInt()}% ")
            if (improved.isNotEmpty()) append("\nImproved: $improved")
            append("\nNext: level ${if (outcome.nextLevel >= planner.maxLevel) "Max" else outcome.nextLevel}")
        }
        falling.clear()
        bursts += Burst(0.5f, 0.4f, Color(0xFFFFC107), now)
        phaseEndsMs = now + BETWEEN_LEVELS_MS
        phase = Phase.BETWEEN_LEVELS
    }

    companion object {
        const val BURST_MS = 600L
        const val ANSWER_FLASH_MS = 3_000L
        const val BETWEEN_LEVELS_MS = 3_000L
    }
}
