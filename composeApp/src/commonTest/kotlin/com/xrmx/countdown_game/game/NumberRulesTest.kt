package com.xrmx.countdown_game.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberRulesTest {
    private val numbers = listOf(75, 50, 2, 3, 8, 7)

    @Test
    fun fewestStepsMatchesSolver() {
        val table = NumberSolver.fewestSteps(numbers, 999)
        assertEquals(0, table[75])
        assertEquals(1, table[125]) // 75 + 50
        for (target in listOf(101, 333, 512, 777, 999)) {
            val solution = NumberSolver.solve(numbers, target)
            if (solution.exact) {
                assertEquals(solution.steps.size, table[target], "fewest steps for $target")
            } else {
                assertEquals(Int.MAX_VALUE, table[target], "$target should be unreachable")
            }
        }
    }

    @Test
    fun generatedTargetsMatchDifficulty() {
        val random = Random(5)
        repeat(5) {
            val easy = NumberRules.target(numbers, TargetDifficulty.Easy, random)
            assertTrue(NumberSolver.solve(numbers, easy).steps.size <= 2, "easy $easy")
            val medium = NumberRules.target(numbers, TargetDifficulty.Medium, random)
            assertTrue(NumberSolver.solve(numbers, medium).let { it.exact && it.steps.size in 3..4 }, "medium $medium")
            val hard = NumberRules.target(numbers, TargetDifficulty.Hard, random)
            assertTrue(NumberSolver.solve(numbers, hard).let { !it.exact || it.steps.size >= 5 }, "hard $hard")
            assertTrue(NumberRules.target(numbers, TargetDifficulty.Random, random) in 101..999)
        }
    }

    @Test
    fun boardHasShowLayout() {
        val board = NumberBoard(random = Random(1))
        assertEquals(NumberBoard.ROW_SIZES, board.rows.map { it.size })
        assertEquals(NumberRules.LARGE.sorted(), board.rows[0].sorted())
        assertEquals(NumberRules.SMALL.sorted(), board.rows.drop(1).flatten().sorted())
        val nasty = NumberBoard(NumberRules.NASTY_LARGE, Random(1))
        assertEquals(listOf(12, 37, 62, 87), nasty.rows[0].sorted())
    }

    @Test
    fun selectionsTakeSixCardsAndFitTheBoard() {
        NumberSelection.entries.forEach { selection ->
            assertEquals(6, selection.rows.sum(), selection.label)
            selection.rows.forEachIndexed { row, count -> assertTrue(count <= NumberBoard.ROW_SIZES[row], selection.label) }
        }
        assertEquals(1, NumberSelection.InvertedT.large)
        assertEquals(listOf(1, 2, 1, 2), NumberSelection.Whitehall.rows)
        assertEquals(4, NumberSelection.Motown.large)
    }

    @Test
    fun wordListParserHandlesScowlAndPlainLists() {
        val scowl = "Custom wordlist\nCopyright\n---\nA\nAaron\naardvark\naardvarks\ncan't\ncolour\nzebra\nrésumé\nextraordinary\n"
        assertEquals(listOf("aardvark", "aardvarks", "colour", "zebra"), WordListParser.parse(scowl))
        assertEquals(listOf("cat", "dog"), WordListParser.parse("CAT\nDOG\nX\n"))
    }
}
