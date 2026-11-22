package com.xrmx.countdown_game.game

import kotlin.math.abs

/**
 * Exhaustive Countdown numbers solver.
 *
 * Searches every way of combining the numbers with + − × ÷, keeping every intermediate result a
 * positive whole number and using each number at most once. Uses iterative deepening, so the
 * solution returned uses the fewest operations among the closest results.
 */
object NumberSolver {

    fun solve(numbers: List<Int>, target: Int): NumbersSolution {
        require(numbers.isNotEmpty()) { "Need at least one number" }
        val search = Search(target, numbers.size)
        // Using a number on its own is a zero-step solution.
        numbers.forEach { search.record(it, 0) }
        val start = numbers.toIntArray()
        for (maxDepth in 1 until numbers.size) {
            if (search.bestDistance == 0) break
            search.run(start, numbers.size, 0, maxDepth)
        }
        val steps = trimUnusedSteps(search.bestSteps, search.bestValue)
        return NumbersSolution(
            target = target,
            value = search.bestValue,
            steps = steps,
            expression = buildExpression(numbers, steps, search.bestValue),
        )
    }

    private class Search(val target: Int, n: Int) {
        var bestValue = 0
        var bestDistance = Int.MAX_VALUE
        var bestSteps: List<Step> = emptyList()

        private val stepLeft = IntArray(n)
        private val stepRight = IntArray(n)
        private val stepOp = arrayOfNulls<Op>(n)
        private val stepResult = IntArray(n)
        private val buffers = Array(n) { IntArray(n) }

        fun record(value: Int, depth: Int) {
            val distance = abs(value - target)
            if (distance < bestDistance || (distance == bestDistance && depth < bestSteps.size)) {
                bestDistance = distance
                bestValue = value
                bestSteps = List(depth) { Step(stepLeft[it], stepOp[it]!!, stepRight[it], stepResult[it]) }
            }
        }

        /** Returns true once an exact solution is found at this depth limit. */
        fun run(nums: IntArray, count: Int, depth: Int, maxDepth: Int): Boolean {
            val next = buffers[depth]
            for (i in 0 until count) {
                for (j in i + 1 until count) {
                    val a = maxOf(nums[i], nums[j])
                    val b = minOf(nums[i], nums[j])
                    // Remaining numbers go first, the new result into the last slot.
                    var k = 0
                    for (m in 0 until count) if (m != i && m != j) next[k++] = nums[m]
                    for (op in OPS) {
                        val result = when (op) {
                            Op.ADD -> a + b
                            Op.SUB -> if (a > b) a - b else continue
                            Op.MUL -> if (b != 1) a * b else continue
                            Op.DIV -> if (b != 1 && a % b == 0) a / b else continue
                        }
                        stepLeft[depth] = a
                        stepRight[depth] = b
                        stepOp[depth] = op
                        stepResult[depth] = result
                        record(result, depth + 1)
                        if (bestDistance == 0 && bestSteps.size <= depth + 1) return true
                        if (depth + 1 < maxDepth && count > 2) {
                            next[k] = result
                            if (run(next, k + 1, depth + 1, maxDepth)) return true
                        }
                    }
                }
            }
            return false
        }

        companion object {
            val OPS = Op.entries.toTypedArray()
        }
    }

    /**
     * For every value 0..[maxValue], the fewest operations needed to make it exactly from [numbers]
     * (0 if it is one of the numbers), or [Int.MAX_VALUE] if it can't be made.
     */
    fun fewestSteps(numbers: List<Int>, maxValue: Int): IntArray {
        val best = IntArray(maxValue + 1) { Int.MAX_VALUE }
        numbers.forEach { if (it <= maxValue) best[it] = 0 }
        val buffers = Array(numbers.size) { IntArray(numbers.size) }

        fun explore(nums: IntArray, count: Int, depth: Int) {
            val next = buffers[depth]
            for (i in 0 until count) {
                for (j in i + 1 until count) {
                    val a = maxOf(nums[i], nums[j])
                    val b = minOf(nums[i], nums[j])
                    var k = 0
                    for (m in 0 until count) if (m != i && m != j) next[k++] = nums[m]
                    for (op in 0 until 4) {
                        val result = when (op) {
                            0 -> a + b
                            1 -> if (a > b) a - b else continue
                            2 -> if (b != 1) a * b else continue
                            else -> if (b != 1 && a % b == 0) a / b else continue
                        }
                        if (result <= maxValue && depth + 1 < best[result]) best[result] = depth + 1
                        if (count > 2) {
                            next[k] = result
                            explore(next, k + 1, depth + 1)
                        }
                    }
                }
            }
        }
        explore(numbers.toIntArray(), numbers.size, 0)
        return best
    }

    /** Drops steps whose result never feeds into the final value. */
    private fun trimUnusedSteps(steps: List<Step>, finalValue: Int): List<Step> {
        if (steps.isEmpty()) return steps
        val needed = mutableListOf(finalValue)
        val kept = ArrayList<Step>()
        for (step in steps.asReversed()) {
            if (needed.remove(step.result)) {
                needed.add(step.left)
                needed.add(step.right)
                kept.add(step)
            }
        }
        return kept.asReversed()
    }

    private data class Term(val value: Int, val text: String, val precedence: Int)

    /** Turns steps into one expression such as (100 + 6) × 3 − 75. */
    fun buildExpression(numbers: List<Int>, steps: List<Step>, value: Int): String {
        val pool = numbers.map { Term(it, it.toString(), 3) }.toMutableList()
        for (step in steps) {
            val left = pool.removeAt(pool.indexOfFirst { it.value == step.left })
            val right = pool.removeAt(pool.indexOfFirst { it.value == step.right })
            val p = step.op.precedence
            val l = if (left.precedence < p) "(${left.text})" else left.text
            val rightNeedsParens = when (step.op) {
                Op.ADD, Op.MUL -> right.precedence < p
                Op.SUB, Op.DIV -> right.precedence <= p
            }
            val r = if (rightNeedsParens) "(${right.text})" else right.text
            pool.add(Term(step.result, "$l ${step.op.symbol} $r", p))
        }
        return pool.lastOrNull { it.value == value }?.text ?: value.toString()
    }
}
