package com.swooningfish.countdown.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NumberSolverTest {

    /** Replays the steps against the dealt numbers, checking every Countdown rule. */
    private fun assertValid(numbers: List<Int>, solution: NumbersSolution) {
        val pool = numbers.toMutableList()
        for (step in solution.steps) {
            assertTrue(pool.remove(step.left), "${step.left} not available in $pool for $step")
            assertTrue(pool.remove(step.right), "${step.right} not available in $pool for $step")
            if (step.op == Op.DIV) assertEquals(0, step.left % step.right, "Inexact division $step")
            assertEquals(step.op.apply(step.left, step.right), step.result, "Wrong arithmetic $step")
            assertTrue(step.result > 0, "Non-positive result $step")
            pool.add(step.result)
        }
        val final = solution.steps.lastOrNull()?.result ?: solution.value
        assertEquals(solution.value, final)
        assertTrue(solution.value in numbers || solution.steps.isNotEmpty())
        val check = ExpressionEvaluator.evaluate(solution.expression, numbers)
        assertEquals(solution.value, assertIs<ExpressionEvaluator.Result.Ok>(check, solution.expression).value)
    }

    @Test
    fun famousFourLargeTarget() {
        // 952 from 25 50 75 100 3 6, solved on the show in 1997.
        val numbers = listOf(25, 50, 75, 100, 3, 6)
        val solution = NumberSolver.solve(numbers, 952)
        assertTrue(solution.exact, "Expected exact, got ${solution.value}: ${solution.steps}")
        assertValid(numbers, solution)
    }

    @Test
    fun simpleExactSolutionUsesFewestSteps() {
        val numbers = listOf(100, 1, 2, 3, 4, 5)
        val solution = NumberSolver.solve(numbers, 105)
        assertTrue(solution.exact)
        assertEquals(1, solution.steps.size)
        assertValid(numbers, solution)
    }

    @Test
    fun targetAlreadyDealtNeedsNoSteps() {
        val solution = NumberSolver.solve(listOf(100, 1, 2, 3, 4, 5), 100)
        assertTrue(solution.exact)
        assertEquals(0, solution.steps.size)
        assertEquals("100", solution.expression)
    }

    @Test
    fun impossibleTargetReturnsClosest() {
        // The largest reachable value is (1 + 2) × (1 + 2) × 3 × 3 = 81, far below 999.
        val numbers = listOf(1, 1, 2, 2, 3, 3)
        val solution = NumberSolver.solve(numbers, 999)
        assertTrue(!solution.exact)
        assertEquals(81, solution.value)
        assertValid(numbers, solution)
    }

    @Test
    fun randomGamesAlwaysProduceValidWorking() {
        val random = Random(42)
        repeat(40) {
            val numbers = NumberRules.deal(random.nextInt(0, 5), random)
            val target = NumberRules.randomTarget(random)
            val solution = NumberSolver.solve(numbers, target)
            assertValid(numbers, solution)
        }
    }

    @Test
    fun expressionUsesBracketsOnlyWhereNeeded() {
        val steps = listOf(Step(100, Op.ADD, 6, 106), Step(106, Op.MUL, 3, 318), Step(318, Op.SUB, 75, 243))
        assertEquals("(100 + 6) × 3 − 75", NumberSolver.buildExpression(listOf(100, 6, 3, 75, 1, 2), steps, 243))
    }
}
