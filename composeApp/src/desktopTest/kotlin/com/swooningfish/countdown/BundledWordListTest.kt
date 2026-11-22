package com.swooningfish.countdown

import com.swooningfish.countdown.game.ConundrumGenerator
import com.swooningfish.countdown.game.WordDictionary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/** Checks the real bundled word list (runs on the JVM, so it can read the file directly). */
class BundledWordListTest {
    private val dictionary by lazy {
        WordDictionary.parse(File("src/commonMain/composeResources/files/words.txt").readText())
    }

    @Test
    fun loadsBritishSpellings() {
        assertTrue(dictionary.size > 50_000, "Only ${dictionary.size} words")
        assertTrue(dictionary.contains("colour"))
        assertTrue(dictionary.contains("realise"))
        assertTrue(dictionary.contains("realize"))
    }

    @Test
    fun solvesARealSelectionQuickly() {
        val (solution, time) = measureTimedValue { dictionary.solve("RSTLNEAIO") }
        assertEquals(9, solution.maxLength, "Expected a nine-letter anagram of RSTLNEAIO, e.g. ORIENTALS")
        assertTrue(solution.longestWords.size == 20)
        assertTrue(time.inWholeMilliseconds < 2_000, "Solving took $time")
    }

    @Test
    fun conundrumsComeFromTheList() {
        val conundrum = ConundrumGenerator(dictionary).next()!!
        assertTrue(dictionary.contains(conundrum.answer))
        assertEquals(9, conundrum.scrambled.length)
    }
}
