package com.xrmx.countdown_game.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordListAndWorkingTest {
    @Test
    fun parserSkipsScowlHeaderProperNounsAndOddWords() {
        val text = """
            Custom wordlist generated from http://app.aspell.net/create
            ---
            apple
            London
            it's
            café
            x
            toolongforcountdown
            banana
            apple
        """.trimIndent()
        assertEquals(listOf("apple", "banana"), WordListParser.parse(text))
    }

    @Test
    fun parserAcceptsAListInCapitals() {
        assertEquals(listOf("cat", "dog"), WordListParser.parse("DOG\nCAT\nA\n"))
    }

    @Test
    fun workingAppendAndBackspace() {
        var w = ""
        w = Working.append(w, "100")
        w = Working.append(w, "+")
        w = Working.append(w, "25")
        assertEquals("100 + 25", w)
        w = Working.backspace(w)
        assertEquals("100 +", w)
        w = Working.backspace(w)
        assertEquals("100", w)
        assertEquals("", Working.backspace(w))
    }

    @Test
    fun repeatedNumberTilesAreUsedInOrder() {
        val numbers = listOf(5, 5, 75)
        assertFalse(Working.isTileFree("5 +", numbers, 0))
        assertTrue(Working.isTileFree("5 +", numbers, 1))
        assertTrue(Working.isTileFree("5 +", numbers, 2))
        assertFalse(Working.isTileFree("5 + 5", numbers, 1))
    }

    @Test
    fun repeatedLetterTilesAreUsedInOrder() {
        val letters = "AEBAT"
        assertFalse(LetterRules.isTileFree("BA", letters, 0))
        assertTrue(LetterRules.isTileFree("BA", letters, 3))
        assertFalse(LetterRules.isTileFree("BA", letters, 2))
        assertTrue(LetterRules.isTileFree("ba", letters, 4))
        assertFalse(LetterRules.isTileFree("", letters, 9))
    }

    @Test
    fun conundrumHasOneAnswerAndIsScrambled() {
        // "senoritas" has no other anagram here; "relations" and "orientals" share letters, so neither is used.
        val dictionary = WordDictionary(listOf("senoritas", "relations", "orientals", "stone"))
        val generator = ConundrumGenerator(dictionary, Random(3))
        repeat(10) {
            val puzzle = generator.next()!!
            assertEquals("SENORITAS", puzzle.answer)
            assertNotEquals(puzzle.answer, puzzle.scrambled)
            assertEquals(puzzle.answer.toList().sorted(), puzzle.scrambled.toList().sorted())
        }
    }

    @Test
    fun noConundrumWithoutNineLetterWords() {
        assertNull(ConundrumGenerator(WordDictionary(listOf("stone", "notes"))).next())
    }

    @Test
    fun joinerDropsOversizedMessagesAndRecovers() {
        val joiner = PacketFraming.Joiner()
        val huge = "x".repeat(PacketFraming.MAX_MESSAGE_BYTES + 10)
        PacketFraming.split(huge, 200).forEach { assertNull(joiner.add(it)) }
        val packets = PacketFraming.split("hello", 3)
        val results = packets.map { joiner.add(it) }
        assertEquals("hello", results.last())
        assertTrue(results.dropLast(1).all { it == null })
    }
}
