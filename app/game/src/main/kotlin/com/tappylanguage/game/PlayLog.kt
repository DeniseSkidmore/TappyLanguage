package com.tappylanguage.game

import android.util.Log
import com.tappylanguage.engine.Play
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Prototype-only debug log (selection.md §1): one JSON-lines file per level in `dir`, newest 10 kept.
 * Pull with Android Studio's Device Explorer: /data/data/com.tappylanguage/files/playlogs/.
 */
class PlayLog(private val dir: File, private val keep: Int = 10) {

    fun write(plays: List<Play>, nowMs: Long) {
        if (plays.isEmpty()) return
        try {
            dir.mkdirs()
            File(dir, "level-$nowMs.jsonl").bufferedWriter(Charsets.UTF_8).use { out ->
                plays.forEach { p ->
                    out.write(
                        JSONObject()
                            .put("item", p.skill.itemKey)
                            .put("combination", p.skill.combination.name)
                            .put("case", p.skill.targetCase.name)
                            .put("outcome", p.outcome.name)
                            .put("taps", p.taps)
                            .put("latencyMs", p.latencyMs ?: JSONObject.NULL)
                            .put("opportunityMs", p.opportunityMs)
                            .put("atMs", p.atMs)
                            .put("wrongTaps", JSONArray(p.wrongTapSkills.map { it.itemKey }))
                            .toString(),
                    )
                    out.newLine()
                }
            }
            dir.listFiles { f -> f.name.startsWith("level-") && f.name.endsWith(".jsonl") }
                ?.sortedByDescending { it.name.removePrefix("level-").removeSuffix(".jsonl").toLongOrNull() ?: 0L }
                ?.drop(keep)
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w("PlayLog", "could not write play log", e) // debug aid only; never break the game
        }
    }
}
