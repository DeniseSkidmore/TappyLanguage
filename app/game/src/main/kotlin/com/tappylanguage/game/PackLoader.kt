package com.tappylanguage.game

import android.content.res.AssetManager
import com.tappylanguage.engine.Item
import com.tappylanguage.engine.LanguagePack
import org.json.JSONObject

/** Reads a pack JSON file (the "file" layer) into engine objects (the "in memory" layer). */
object PackLoader {
    fun load(assets: AssetManager, path: String): LanguagePack {
        val json = JSONObject(assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() })
        val array = json.getJSONArray("items")
        val items = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Item(
                text = o.getString("text"),
                audioFile = o.optString("audio").ifEmpty { null },
                frequencyRank = if (o.has("frequencyRank")) o.getInt("frequencyRank") else null,
            )
        }
        return LanguagePack(json.getString("language"), json.getInt("vocabularySize"), items)
    }
}
