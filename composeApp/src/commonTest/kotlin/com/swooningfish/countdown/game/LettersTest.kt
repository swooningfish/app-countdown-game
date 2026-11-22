package com.swooningfish.countdown.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LettersTest {
    private val dictionary = WordDictionary(
        listOf("countdown", "count", "down", "town", "undo", "cod", "now", "won", "own", "do", "a", "Proper", "don't", "toward", "unwound"),
    )

    @Test
    fun ignoresProperNounsShortWordsAndPunctuation() {
        assertFalse(dictionary.contains("a"))
        assertFalse(dictionary.contains("don't"))
        assertTrue(dictionary.contains("PROPER")) // lower-cased on load; the build script removes capitalised words
        assertTrue(dictionary.contains("Countdown"))
    }

    @Test
    fun findsWordsLongestFirst() {
        val words = dictionary.wordsFrom("COUNTDOWN")
        assertEquals("COUNTDOWN", words.first())
        assertEquals(listOf("COUNTDOWN", "COUNT", "DOWN", "TOWN", "UNDO", "COD", "NOW", "OWN", "WON", "DO"), words)
        assertFalse("UNWOUND" in words) // needs two Us
    }

    @Test
    fun solutionExcludesPlayersWord() {
        val solution = dictionary.solve("COUNTDOWN", playerWord = "count")
        assertEquals(listOf("COUNTDOWN"), solution.nineLetterWords)
        assertFalse("COUNT" in solution.longestWords)
        assertEquals(9, solution.maxLength)
        assertEquals(3, dictionary.solve("COUNTDOWN", limit = 3).longestWords.size)
    }

    @Test
    fun checksPlayersWord() {
        assertIs<WordCheck.Valid>(dictionary.check("town", "COUNTDOWN"))
        assertEquals(4, dictionary.check("town", "COUNTDOWN").score)
        assertEquals(18, dictionary.check("countdown", "NWODTNUOC").score)
        assertIs<WordCheck.NotInDictionary>(dictionary.check("cundo", "COUNTDOWN"))
        assertIs<WordCheck.NotInLetters>(dictionary.check("toward", "COUNTDOWN"))
        assertEquals(WordCheck.Empty, dictionary.check("  ", "COUNTDOWN"))
    }

    @Test
    fun vowelConsonantRule() {
        assertTrue(LetterRules.canPickVowel("AEIO", enforce = true))
        assertFalse(LetterRules.canPickVowel("AEIOU", enforce = true))
        assertTrue(LetterRules.canPickVowel("AEIOU", enforce = false))
        assertFalse(LetterRules.canPickConsonant("BCDFGH", enforce = true))
        assertNull(LetterRules.validateSelection("COUNTDOWN", enforce = true))
        assertNotNull(LetterRules.validateSelection("BCDFGHJKA", enforce = true))
        assertNull(LetterRules.validateSelection("BCDFGHJKA", enforce = false))
        assertNotNull(LetterRules.validateSelection("SHORT", enforce = false))
    }

    @Test
    fun bagDrawsFromCorrectPiles() {
        val bag = LetterBag(Random(1))
        repeat(200) {
            assertTrue(LetterRules.isVowel(bag.drawVowel()))
            assertFalse(LetterRules.isVowel(bag.drawConsonant()))
        }
    }

    @Test
    fun scoring() {
        assertEquals(0, Scoring.letters(0))
        assertEquals(7, Scoring.letters(7))
        assertEquals(18, Scoring.letters(9))
        assertEquals(10, Scoring.numbers(500, 500))
        assertEquals(7, Scoring.numbers(500, 495))
        assertEquals(5, Scoring.numbers(500, 510))
        assertEquals(0, Scoring.numbers(500, 511))
    }

    @Test
    fun conundrumScrambleIsAnAnagram() {
        val dict = WordDictionary(listOf("countdown", "aardvarks", "ablations", "abrogates"))
        val conundrum = assertNotNull(ConundrumGenerator(dict, Random(3)).next())
        assertEquals(conundrum.answer.toList().sorted(), conundrum.scrambled.toList().sorted())
        assertNotEquals(conundrum.answer, conundrum.scrambled)
    }
}
