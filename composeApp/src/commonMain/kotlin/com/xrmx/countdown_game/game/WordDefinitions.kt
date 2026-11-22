package com.xrmx.countdown_game.game

/** One sense of a word, e.g. noun: "a fixed amount of food allowed to each person". */
data class WordSense(val partOfSpeech: String, val definition: String)

/** Reads definitions from the Free Dictionary API (dictionaryapi.dev). */
object WordDefinitions {
    private const val API = "https://api.dictionaryapi.dev/api/v2/entries/en/"

    /** Most senses shown for one word. */
    const val MAX_SENSES = 6

    fun url(word: String): String = API + word.trim().lowercase()

    /** A page to read more, also used when the API doesn't know the word. */
    fun moreUrl(word: String): String = "https://en.wiktionary.org/wiki/" + word.trim().lowercase()

    private val field = Regex("\"(partOfSpeech|definition)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /**
     * Picks the senses out of the API's JSON without a JSON library: every "definition" belongs to
     * the "partOfSpeech" that comes before it. Repeated definitions are dropped.
     */
    fun parse(json: String, limit: Int = MAX_SENSES): List<WordSense> {
        val senses = ArrayList<WordSense>()
        var partOfSpeech = ""
        for (match in field.findAll(json)) {
            val value = unescape(match.groupValues[2]).trim()
            if (match.groupValues[1] == "partOfSpeech") {
                partOfSpeech = value
            } else if (value.isNotEmpty() && senses.none { it.definition == value }) {
                senses += WordSense(partOfSpeech, value)
                if (senses.size >= limit) break
            }
        }
        return senses
    }

    private fun unescape(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) {
                sb.append(c)
                i++
                continue
            }
            when (val next = s[i + 1]) {
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                'u' -> {
                    val code = s.substring(i + 2, minOf(i + 6, s.length)).toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 6
                        continue
                    }
                    sb.append(next)
                }
                else -> sb.append(next)
            }
            i += 2
        }
        return sb.toString()
    }
}
