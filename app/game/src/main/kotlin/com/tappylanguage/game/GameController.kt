package com.tappylanguage.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.tappylanguage.engine.LanguagePack
import com.tappylanguage.engine.LevelPlan
import com.tappylanguage.engine.LevelPlanner
import com.tappylanguage.engine.LevelTally
import com.tappylanguage.engine.ModeProgress
import com.tappylanguage.engine.Params
import com.tappylanguage.engine.Play
import com.tappylanguage.engine.PlayOutcome
import com.tappylanguage.engine.SkillKey
import com.tappylanguage.engine.SkillStore
import com.tappylanguage.engine.TextForms
import kotlin.math.min
import kotlin.random.Random

/**
 * A word on screen. Position: `xFraction` of the free width (0 = left edge, 1 = right edge, word kept on screen);
 * vertical progress = (now − startMs) / fallMs.
 */
data class FallingWord(
    val id: Long,
    val skill: SkillKey,
    val text: String,
    val isTarget: Boolean,
    val xFraction: Float,
    val startMs: Long,
    val fallMs: Long,
    val wrong: Boolean = false,
)

data class Burst(val xFraction: Float, val yFraction: Float, val color: Color, val startMs: Long)

/** Level label colour (selection.md §9): down = red, up = green, unchanged = neutral. */
enum class LevelChange { DOWN, SAME, UP }

/**
 * Game loop state (requirements.md "Game play"). Compose observes the mutableState fields, like
 * INotifyPropertyChanged properties in MVVM. tick() is called once per frame.
 */
class GameController(
    private val pack: LanguagePack,
    private val progressStore: ProgressStore,
    private val playLog: PlayLog,
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
    var levelChange by mutableStateOf(LevelChange.SAME); private set
    var wave by mutableIntStateOf(1); private set
    val falling = mutableStateListOf<FallingWord>()
    val bursts = mutableStateListOf<Burst>()

    private lateinit var plan: LevelPlan
    private var targetIndex = 0
    private val plays = mutableListOf<Play>()
    private var tally = LevelTally()

    // Current prompt
    private var promptActive = false
    private var promptStartMs = 0L        // when the target entered (audio played at the group's start)
    private var promptTaps = 0            // taps on words, including the final hit
    private val promptWrongSkills = mutableListOf<SkillKey>()
    private val pendingEntries = mutableListOf<FallingWord>() // waiting to enter, in order
    private var nextEntryMs = 0L

    private var phaseEndsMs = 0L
    private var pausedFrom = Phase.PLAYING
    private var pausedAtMs = 0L
    private var nextId = 0L

    init {
        val now = System.currentTimeMillis()
        applySessionStart(progress.levelNumber)
        startLevel(now)
    }

    fun tick(now: Long) {
        if (phase == Phase.PAUSED) return // freeze the screen; togglePause() shifts start times on resume
        nowMs = now
        bursts.removeAll { now - it.startMs > BURST_MS }
        when (phase) {
            Phase.PLAYING -> {
                if (!promptActive) spawnNext(now)
                else {
                    enterPending(now)
                    falling.firstOrNull { it.isTarget }?.let { if (now - it.startMs >= it.fallMs) timeout(it, now) }
                    falling.removeAll { !it.isTarget && now - it.startMs >= it.fallMs } // distractors just leave
                }
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
            promptTaps++
            plays += Play(word.skill, PlayOutcome.HIT, now - promptStartMs, now - promptStartMs, now,
                taps = promptTaps, wrongTapSkills = promptWrongSkills.toList())
            tally = tally.add(clean = promptTaps == 1, established = word.skill.itemKey in plan.establishedKeys)
            bursts += Burst(word.xFraction, yFraction, Color(0xFF4CAF50), now)
            endPrompt()
        } else if (!word.wrong) {
            promptTaps++
            promptWrongSkills += word.skill
            plays += Play(word.skill, PlayOutcome.WRONG_TAP, null, word.fallMs, now)
            bursts += Burst(word.xFraction, yFraction, Color(0xFFE53935), now)
            val i = falling.indexOfFirst { it.id == word.id }
            if (i >= 0) falling[i] = word.copy(wrong = true)
        }
    }

    /** A tap on no word: everything on screen falls 5% slower for the rest of this prompt (no history change). */
    fun onEmptyTap() {
        if (phase != Phase.PLAYING || !promptActive) return
        val now = nowMs
        fun slower(w: FallingWord): FallingWord {
            val newFall = min((w.fallMs / Params.EMPTY_TAP_SPEED_FACTOR).toLong(), Params.MAX_FALL_MS.toLong())
            if (newFall <= w.fallMs) return w
            val p = progressOf(w, now)
            return w.copy(fallMs = newFall, startMs = now - (p * newFall).toLong()) // keep the current height
        }
        for (i in falling.indices) falling[i] = slower(falling[i])
        for (i in pendingEntries.indices) pendingEntries[i] = pendingEntries[i].let {
            it.copy(fallMs = min((it.fallMs / Params.EMPTY_TAP_SPEED_FACTOR).toLong(), Params.MAX_FALL_MS.toLong()))
        }
    }

    fun togglePause() {
        val now = System.currentTimeMillis()
        if (phase == Phase.PAUSED) {
            val idle = now - pausedAtMs
            if (idle >= Params.SESSION_GAP_MS) {
                resumeAsNewSession(now)
                return
            }
            for (i in falling.indices) falling[i] = falling[i].copy(startMs = falling[i].startMs + idle)
            promptStartMs += idle
            nextEntryMs += idle
            phaseEndsMs += idle
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

    /** Back from the menu; after an hour or more this starts a new session (see togglePause). */
    fun resume() {
        if (phase == Phase.PAUSED) togglePause()
    }

    fun progressOf(word: FallingWord, now: Long): Float =
        ((now - word.startMs).toFloat() / word.fallMs).coerceIn(0f, 1f)

    /** Session start (app launch, or resume after an hour idle): lower the level by forgotten recall. */
    private fun applySessionStart(previousLevel: Int) {
        val level = planner.sessionStartLevel(progress, System.currentTimeMillis())
        progress = progress.copy(levelNumber = level)
        setLevelChange(previousLevel, level)
    }

    private fun resumeAsNewSession(now: Long) {
        // Keep what was learned in the unfinished level, then start fresh at the session-start level.
        skills.applyAll(plays)
        playLog.write(plays, now)
        progressStore.save(skills, progress) // lastRecallAvg stays the value from the last finished level
        applySessionStart(progress.levelNumber)
        wave = 1
        startLevel(now)
    }

    private fun setLevelChange(previous: Int, next: Int) {
        levelChange = when {
            next > previous -> LevelChange.UP
            next < previous -> LevelChange.DOWN
            else -> LevelChange.SAME
        }
    }

    private fun startLevel(now: Long) {
        plan = planner.plan(progress.levelNumber, now)
        targetIndex = 0
        tally = LevelTally()
        plays.clear()
        falling.clear()
        pendingEntries.clear()
        promptActive = false
        message = ""
        levelLabel = if (progress.levelNumber >= planner.maxLevel) "Max" else progress.levelNumber.toString()
        phase = Phase.PLAYING
    }

    /** Queue the next target and its distractors; they enter one at a time in random order. */
    private fun spawnNext(now: Long) {
        if (targetIndex >= plan.targets.size) {
            endLevel(now)
            return
        }
        val key = plan.targets[targetIndex++]
        val item = pack.item(key.itemKey) ?: return
        val fallMs = planner.fallTimeMs(skills.getOrCreate(key))
        val words = mutableListOf(
            FallingWord(nextId++, key, TextForms.display(item, key.targetCase, pack.languageCode), true,
                random.nextFloat(), 0L, fallMs),
        )
        planner.distractors(key, now).forEach { d ->
            val dItem = pack.item(d.itemKey) ?: return@forEach
            words += FallingWord(nextId++, d, TextForms.display(dItem, d.targetCase, pack.languageCode), false,
                random.nextFloat(), 0L, fallMs)
        }
        falling.clear()
        pendingEntries.clear()
        pendingEntries += words.shuffled(random)
        nextEntryMs = now
        promptTaps = 0
        promptWrongSkills.clear()
        promptActive = true
        audio.play(item.audioFile) // at the start of the group, so timing doesn't reveal which word is the target
        enterPending(now)
    }

    private fun enterPending(now: Long) {
        while (pendingEntries.isNotEmpty() && now >= nextEntryMs) {
            val w = pendingEntries.removeAt(0).copy(startMs = now)
            falling += w
            nextEntryMs = now + min(MAX_ENTRY_GAP_MS, w.fallMs / ENTRY_GAP_DIVISOR)
            if (w.isTarget) promptStartMs = now // latency counts from when the target is on screen
        }
    }

    private fun timeout(target: FallingWord, now: Long) {
        plays += Play(target.skill, PlayOutcome.TIMEOUT, null, now - promptStartMs, now,
            taps = promptTaps, wrongTapSkills = promptWrongSkills.toList())
        tally = tally.add(clean = false, established = target.skill.itemKey in plan.establishedKeys)
        bursts += Burst(target.xFraction, 1f, Color(0xFFE53935), now)
        endPrompt()
        message = target.text
        phaseEndsMs = now + ANSWER_FLASH_MS
        phase = Phase.SHOW_ANSWER
    }

    private fun endPrompt() {
        falling.clear()
        pendingEntries.clear()
        promptActive = false
    }

    private fun endLevel(now: Long) {
        val before = plan.pool.associate { item ->
            item.key to planner.unlockedKeys(item).map { skills.getOrCreate(it).successAvg }.average()
        }
        skills.applyAll(plays)
        playLog.write(plays, now)
        val previous = progress.levelNumber
        val outcome = planner.levelResult(previous, tally)
        progress = ModeProgress(
            levelNumber = outcome.nextLevel,
            lastRecallAvg = planner.knownRecallAverage(outcome.nextLevel, now),
        )
        progressStore.save(skills, progress)
        setLevelChange(previous, outcome.nextLevel)
        wave = if (levelChange == LevelChange.SAME) wave + 1 else 1
        val improved = plan.pool.filter { item ->
            planner.unlockedKeys(item).map { skills.getOrCreate(it).successAvg }.average() > (before[item.key] ?: 0.0)
        }.joinToString("  ") { it.text }
        message = buildString {
            append("${(outcome.successRate * 100).toInt()}% ")
            if (improved.isNotEmpty()) append("\nImproved: $improved")
            append("\nNext: level ${if (outcome.nextLevel >= planner.maxLevel) "Max" else outcome.nextLevel}")
            if (wave > 1) append(", wave $wave")
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
        const val MAX_ENTRY_GAP_MS = 1_000L
        const val ENTRY_GAP_DIVISOR = 10L
    }
}
