package com.tappylanguage.engine

import java.text.Normalizer
import java.util.Locale

object TextForms {
    private val punctuation = Regex("[\\p{P}\\p{S}]")

    /** Match key: NFC, case-folded, punctuation stripped, accents kept. Final ς folds to σ. */
    fun matchKey(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace('ς', 'σ')
            .replace(punctuation, "")
            .trim()

    /**
     * ALL CAPS display form. Greek capitals drop the tonos (and polytonic marks) but keep the diaeresis:
     * καλημέρα → ΚΑΛΗΜΕΡΑ, ευχαριστώ → ΕΥΧΑΡΙΣΤΩ.
     */
    fun allCaps(text: String, languageCode: String): String {
        val locale = Locale.forLanguageTag(languageCode)
        if (languageCode != "el") return text.uppercase(locale)
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFD)
            .filterNot { it in GREEK_CAPS_DROPPED_MARKS }
        return Normalizer.normalize(stripped.uppercase(locale), Normalizer.Form.NFC)
    }

    fun display(item: Item, case: TargetCase, languageCode: String): String = when (case) {
        TargetCase.AS_WRITTEN -> item.text
        TargetCase.ALL_CAPS -> allCaps(item.text, languageCode)
    }

    // acute/tonos, grave, perispomeni, psili, dasia (diaeresis U+0308 is kept)
    private val GREEK_CAPS_DROPPED_MARKS = setOf('́', '̀', '͂', '̓', '̔')
}
