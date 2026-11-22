package com.swooningfish.countdown.game

/**
 * In-memory word list. Words are stored in lower case; every public function accepts any case
 * and returns upper case for display.
 */
class WordDictionary(words: Collection<String>) {
    /** Words sorted longest first, then alphabetically. */
    private val words: Array<String>
    /** Letter counts of [words], 26 bytes per word. */
    private val counts: ByteArray
    private val wordSet: Set<String>

    init {
        val clean = words.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.length in 2..LetterRules.TOTAL && it.all { c -> c in 'a'..'z' } }
            .distinct()
            .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
            .toList()
        this.words = clean.toTypedArray()
        wordSet = clean.toHashSet()
        counts = ByteArray(this.words.size * 26)
        this.words.forEachIndexed { i, w -> w.forEach { c -> counts[i * 26 + (c - 'a')]++ } }
    }

    val size: Int get() = words.size

    fun contains(word: String): Boolean = word.lowercase() in wordSet

    /** All words that can be made from [letters], longest first then alphabetical, upper case. */
    fun wordsFrom(letters: String): List<String> {
        val available = letterCounts(letters)
        val maxLen = letters.length
        val result = ArrayList<String>()
        for (i in words.indices) {
            if (words[i].length > maxLen) continue
            val base = i * 26
            var ok = true
            for (c in 0 until 26) {
                if (counts[base + c] > available[c]) {
                    ok = false
                    break
                }
            }
            if (ok) result.add(words[i].uppercase())
        }
        return result
    }

    /** Words with exactly [length] letters, upper case. */
    fun wordsOfLength(length: Int): List<String> =
        words.filter { it.length == length }.map { it.uppercase() }

    fun check(word: String, letters: String): WordCheck {
        val w = word.trim().uppercase()
        return when {
            w.isEmpty() -> WordCheck.Empty
            !canSpell(w, letters) -> WordCheck.NotInLetters(w)
            !contains(w) -> WordCheck.NotInDictionary(w)
            else -> WordCheck.Valid(w)
        }
    }

    fun solve(letters: String, playerWord: String? = null, limit: Int = 20): LettersSolution {
        val all = wordsFrom(letters)
        val exclude = playerWord?.trim()?.uppercase()
        val others = all.filter { it != exclude }
        return LettersSolution(
            nineLetterWords = others.filter { it.length == LetterRules.TOTAL },
            longestWords = others.take(limit),
            maxLength = all.firstOrNull()?.length ?: 0,
        )
    }

    companion object {
        fun parse(text: String): WordDictionary = WordDictionary(text.lineSequence().toList())

        fun canSpell(word: String, letters: String): Boolean {
            val available = letterCounts(letters)
            for (ch in word.lowercase()) {
                if (ch !in 'a'..'z') return false
                if (--available[ch - 'a'] < 0) return false
            }
            return true
        }

        private fun letterCounts(letters: String): IntArray {
            val counts = IntArray(26)
            letters.lowercase().forEach { if (it in 'a'..'z') counts[it - 'a']++ }
            return counts
        }
    }
}
