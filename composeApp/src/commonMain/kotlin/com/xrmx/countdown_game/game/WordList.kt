package com.xrmx.countdown_game.game

/** Turns a downloaded or uploaded word list into words usable in the letters round. */
object WordListParser {
    const val MIN_WORDS = 1_000

    private val lowercaseWord = Regex("^[a-z]{2,9}$")
    private val anyCaseWord = Regex("^[A-Za-z]{2,9}$")

    /**
     * Accepts one word per line. A SCOWL header (everything up to a line of `---`) is skipped.
     * Capitalised entries are treated as proper nouns and dropped, unless the whole list is in
     * capitals. Words with apostrophes, hyphens or accents are dropped.
     */
    fun parse(text: String): List<String> {
        val lines = text.lineSequence().map { it.trim() }.toList()
        val separator = lines.take(200).indexOf("---")
        val body = if (separator >= 0) lines.drop(separator + 1) else lines
        val lower = body.filter { lowercaseWord.matches(it) }
        val words = if (lower.isNotEmpty()) lower else body.filter { anyCaseWord.matches(it) }.map { it.lowercase() }
        return words.distinct().sorted()
    }
}

data class WordListInfo(
    val builtIn: Boolean,
    val source: String,
    val wordCount: Int,
    /** ISO date (yyyy-mm-dd) the list was imported, or null for the built-in list. */
    val updated: String?,
) {
    companion object {
        const val BUILT_IN_SOURCE = "Built-in SCOWL British English (size 70)"
        const val SCOWL_URL =
            "https://app.aspell.net/create?max_size=70&spelling=GBs&spelling=GBz&max_variant=0" +
                "&diacritic=strip&download=wordlist&encoding=utf-8&format=inline"
    }
}
