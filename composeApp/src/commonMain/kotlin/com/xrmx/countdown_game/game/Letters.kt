package com.xrmx.countdown_game.game

import kotlin.random.Random

/** Rules of the Countdown letters round. */
object LetterRules {
    const val TOTAL = 9
    const val MIN_VOWELS = 3
    const val MIN_CONSONANTS = 4
    const val MAX_VOWELS = TOTAL - MIN_CONSONANTS
    const val MAX_CONSONANTS = TOTAL - MIN_VOWELS
    const val VOWELS = "AEIOU"

    fun isVowel(c: Char): Boolean = c.uppercaseChar() in VOWELS

    fun vowelCount(letters: String): Int = letters.count(::isVowel)

    fun consonantCount(letters: String): Int = letters.count { it.isLetter() && !isVowel(it) }

    /** Whether another vowel may be picked without breaking the 3-vowel / 4-consonant rule. */
    fun canPickVowel(letters: String, enforce: Boolean): Boolean =
        letters.length < TOTAL && (!enforce || vowelCount(letters) < MAX_VOWELS)

    fun canPickConsonant(letters: String, enforce: Boolean): Boolean =
        letters.length < TOTAL && (!enforce || consonantCount(letters) < MAX_CONSONANTS)

    /** Returns a problem description for a hand-typed selection, or null if it is valid. */
    fun validateSelection(letters: String, enforce: Boolean): String? {
        if (letters.length != TOTAL) return "Enter exactly $TOTAL letters"
        if (!letters.all { it in 'A'..'Z' }) return "Use the letters A to Z only"
        if (enforce) {
            if (vowelCount(letters) < MIN_VOWELS) return "Countdown needs at least $MIN_VOWELS vowels"
            if (consonantCount(letters) < MIN_CONSONANTS) return "Countdown needs at least $MIN_CONSONANTS consonants"
        }
        return null
    }
}

/**
 * Separate vowel and consonant piles, weighted like the letter frequencies used on the show.
 * Letters are drawn without replacement and a pile is reshuffled when it runs out.
 */
class LetterBag(private val random: Random = Random.Default) {
    private var vowels: List<Char> = emptyList()
    private var vowelIndex = 0
    private var consonants: List<Char> = emptyList()
    private var consonantIndex = 0

    init {
        shuffle()
    }

    fun shuffle() {
        vowels = expand(VOWEL_WEIGHTS).shuffled(random)
        consonants = expand(CONSONANT_WEIGHTS).shuffled(random)
        vowelIndex = 0
        consonantIndex = 0
    }

    fun drawVowel(): Char {
        if (vowelIndex >= vowels.size) {
            vowels = expand(VOWEL_WEIGHTS).shuffled(random)
            vowelIndex = 0
        }
        return vowels[vowelIndex++]
    }

    fun drawConsonant(): Char {
        if (consonantIndex >= consonants.size) {
            consonants = expand(CONSONANT_WEIGHTS).shuffled(random)
            consonantIndex = 0
        }
        return consonants[consonantIndex++]
    }

    companion object {
        val VOWEL_WEIGHTS = mapOf('A' to 15, 'E' to 21, 'I' to 13, 'O' to 13, 'U' to 5)
        val CONSONANT_WEIGHTS = mapOf(
            'B' to 2, 'C' to 3, 'D' to 6, 'F' to 2, 'G' to 3, 'H' to 2, 'J' to 1, 'K' to 1,
            'L' to 5, 'M' to 4, 'N' to 8, 'P' to 4, 'Q' to 1, 'R' to 9, 'S' to 9, 'T' to 9,
            'V' to 1, 'W' to 1, 'X' to 1, 'Y' to 1, 'Z' to 1,
        )

        private fun expand(weights: Map<Char, Int>): List<Char> =
            weights.flatMap { (letter, count) -> List(count) { letter } }
    }
}

/** Outcome of checking the word a player declared. */
sealed interface WordCheck {
    val score: Int get() = 0

    data object Empty : WordCheck
    data class NotInLetters(val word: String) : WordCheck
    data class NotInDictionary(val word: String) : WordCheck
    data class Valid(val word: String) : WordCheck {
        override val score: Int get() = Scoring.letters(word.length)
    }
}

data class LettersSolution(
    /** Every nine-letter word (full anagram) of the selection, excluding the player's word. */
    val nineLetterWords: List<String>,
    /** The longest words available, longest first, excluding the player's word. */
    val longestWords: List<String>,
    /** Length of the best word available, including the player's. */
    val maxLength: Int,
)
