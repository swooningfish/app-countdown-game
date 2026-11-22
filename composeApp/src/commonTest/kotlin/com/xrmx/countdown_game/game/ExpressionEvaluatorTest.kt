package com.xrmx.countdown_game.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExpressionEvaluatorTest {
    private val numbers = listOf(75, 50, 2, 3, 8, 7)

    private fun ok(input: String): Int =
        assertIs<ExpressionEvaluator.Result.Ok>(ExpressionEvaluator.evaluate(input, numbers)).value

    private fun error(input: String): String =
        assertIs<ExpressionEvaluator.Result.Error>(ExpressionEvaluator.evaluate(input, numbers)).message

    @Test
    fun evaluatesWithPrecedenceAndBrackets() {
        assertEquals(640, ok("(75 + 3 + 2) * 8"))
        assertEquals(81, ok("75 + 2 * 3"))
        assertEquals(231, ok("(75 + 2) × 3"))
        assertEquals(25, ok("75 ÷ 3"))
        assertEquals(68, ok("75 − 7"))
        assertEquals(68, ok("75 - 7"))
        assertEquals(400, ok("50 x 8"))
    }

    @Test
    fun ignoresAnythingAfterEquals() {
        assertEquals(400, ok("50 * 8 = 400"))
    }

    @Test
    fun rejectsNumbersNotDealtOrReused() {
        assertEquals("100 isn't one of your numbers", error("100 + 2"))
        assertEquals("75 has already been used", error("75 + 75"))
    }

    @Test
    fun rejectsFractionsAndNegatives() {
        assertEquals("75 ÷ 2 isn't a whole number", error("75 / 2"))
        assertEquals("2 − 7 isn't positive", error("2 - 7"))
        assertEquals("3 − 3 isn't positive", ExpressionEvaluator.evaluate("3 - 3", listOf(3, 3)).let { (it as ExpressionEvaluator.Result.Error).message })
    }

    @Test
    fun rejectsMalformedInput() {
        error("(75 + 2")
        error("75 +")
        error("75 ^ 2")
        error("")
    }
}
