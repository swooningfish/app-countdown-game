package com.xrmx.countdown_game.game

import kotlin.random.Random

data class Conundrum(val scrambled: String, val answer: String)

/**
 * Picks nine-letter words that have exactly one nine-letter anagram in the dictionary and
 * scrambles them so the scramble is not itself a word.
 */
class ConundrumGenerator(private val dictionary: WordDictionary, private val random: Random = Random.Default) {
    private val candidates: List<String> by lazy {
        val nines = dictionary.wordsOfLength(LetterRules.TOTAL)
        val groups = nines.groupingBy { it.toList().sorted().joinToString("") }.eachCount()
        nines.filter { groups[it.toList().sorted().joinToString("")] == 1 }
    }

    fun next(): Conundrum? {
        if (candidates.isEmpty()) return null
        val answer = candidates.random(random)
        var scrambled = answer
        repeat(50) {
            scrambled = answer.toList().shuffled(random).joinToString("")
            if (scrambled != answer && !dictionary.contains(scrambled)) return Conundrum(scrambled, answer)
        }
        return Conundrum(scrambled, answer)
    }
}
