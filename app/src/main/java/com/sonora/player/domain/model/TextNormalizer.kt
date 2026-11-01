package com.sonora.player.domain.model

import java.text.Normalizer
import java.util.Locale

/** Text helpers shared by search, grouping and duplicate detection. */
object TextNormalizer {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val spaces = Regex("\\s+")

    /** Lowercase, accent-free, single-spaced, trimmed. */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return diacritics.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .replace(spaces, " ")
            .trim()
    }

    /** Grouping key used for albums, artists and duplicate detection. */
    fun key(text: String?): String = normalize(text)
}
