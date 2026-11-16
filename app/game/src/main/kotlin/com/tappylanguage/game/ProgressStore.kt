package com.tappylanguage.game

import com.tappylanguage.engine.Combination
import com.tappylanguage.engine.ModeProgress
import com.tappylanguage.engine.Skill
import com.tappylanguage.engine.SkillKey
import com.tappylanguage.engine.SkillStore
import com.tappylanguage.engine.TargetCase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves skills and level progress as one JSON file in app storage. Prototype; Room can replace it later. */
class ProgressStore(private val file: File) {

    fun load(store: SkillStore): ModeProgress {
        if (!file.exists()) return ModeProgress()
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val skills = json.getJSONArray("skills")
            for (i in 0 until skills.length()) {
                val o = skills.getJSONObject(i)
                store.put(
                    Skill(
                        key = SkillKey(
                            o.getString("item"),
                            Combination.valueOf(o.getString("combination")),
                            TargetCase.valueOf(o.getString("case")),
                        ),
                        successAvg = o.getDouble("successAvg"),
                        latencyAvgMs = o.getDouble("latencyAvgMs"),
                        difficulty = o.getDouble("difficulty"),
                        stabilityDays = o.getDouble("stabilityDays"),
                        lastSuccessMs = if (o.isNull("lastSuccessMs")) null else o.getLong("lastSuccessMs"),
                        lastPlayedMs = if (o.isNull("lastPlayedMs")) null else o.getLong("lastPlayedMs"),
                    ),
                )
            }
            ModeProgress(
                levelNumber = json.getInt("levelNumber"),
                levelSuccessAvg = json.getDouble("levelSuccessAvg"),
                lastRecallAvg = json.optDouble("lastRecallAvg", 0.0),
            )
        } catch (e: Exception) {
            ModeProgress() // corrupt or old file: start over (prototype)
        }
    }

    fun save(store: SkillStore, progress: ModeProgress) {
        val skills = JSONArray()
        store.all().forEach { s ->
            skills.put(
                JSONObject()
                    .put("item", s.key.itemKey)
                    .put("combination", s.key.combination.name)
                    .put("case", s.key.targetCase.name)
                    .put("successAvg", s.successAvg)
                    .put("latencyAvgMs", s.latencyAvgMs)
                    .put("difficulty", s.difficulty)
                    .put("stabilityDays", s.stabilityDays)
                    .put("lastSuccessMs", s.lastSuccessMs ?: JSONObject.NULL)
                    .put("lastPlayedMs", s.lastPlayedMs ?: JSONObject.NULL),
            )
        }
        val json = JSONObject()
            .put("levelNumber", progress.levelNumber)
            .put("levelSuccessAvg", progress.levelSuccessAvg)
            .put("lastRecallAvg", progress.lastRecallAvg)
            .put("skills", skills)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString(), Charsets.UTF_8)
        tmp.renameTo(file)
    }
}
