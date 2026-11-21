package com.tappylanguage.game

import android.content.res.AssetManager
import com.tappylanguage.engine.Item
import com.tappylanguage.engine.LanguagePack
import com.tappylanguage.engine.PairPack
import com.tappylanguage.engine.TextForms
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

    /** Reads a pair pack (data-model.md 1b); null when the build has none. Texts become match keys. */
    fun loadPair(assets: AssetManager, path: String): PairPack? {
        val text = runCatching { assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } }
            .getOrNull() ?: return null
        val json = JSONObject(text)
        val array = json.getJSONArray("translations")
        val translations = (0 until array.length()).associate { i ->
            val o = array.getJSONObject(i)
            TextForms.matchKey(o.getString("language1")) to o.getString("language2")
        }
        val groups = json.optJSONArray("sameTarget")
        val sameTarget = if (groups == null) emptyList() else (0 until groups.length()).map { i ->
            val g = groups.getJSONArray(i)
            (0 until g.length()).map { TextForms.matchKey(g.getString(it)) }.toSet()
        }
        return PairPack(translations, sameTarget)
    }
}
