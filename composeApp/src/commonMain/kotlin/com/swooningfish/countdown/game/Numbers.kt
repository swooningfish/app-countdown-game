package com.swooningfish.countdown.game

import kotlin.random.Random

/** Rules of the Countdown numbers round. */
object NumberRules {
    const val COUNT = 6
    const val MAX_LARGE = 4
    const val MIN_TARGET = 101
    const val MAX_TARGET = 999
    val LARGE = listOf(25, 50, 75, 100)
    val SMALL = (1..10).flatMap { listOf(it, it) }

    /** Deals [large] numbers from the top row and the rest from the small numbers. */
    fun deal(large: Int, random: Random = Random.Default): List<Int> {
        require(large in 0..MAX_LARGE) { "Choose 0 to $MAX_LARGE large numbers" }
        return LARGE.shuffled(random).take(large) + SMALL.shuffled(random).take(COUNT - large)
    }

    fun randomTarget(random: Random = Random.Default): Int = random.nextInt(MIN_TARGET, MAX_TARGET + 1)

    /** Returns a problem with hand-entered numbers, or null when they are usable. */
    fun validateNumbers(numbers: List<Int>): String? = when {
        numbers.size != COUNT -> "Enter exactly $COUNT numbers"
        numbers.any { it !in 1..1000 } -> "Numbers must be between 1 and 1000"
        else -> null
    }

    fun validateTarget(target: Int?): String? = when {
        target == null -> "Enter a whole number"
        target !in 1..9999 -> "Target must be between 1 and 9999"
        else -> null
    }
}

enum class Op(val symbol: String, val precedence: Int) {
    ADD("+", 1), SUB("−", 1), MUL("×", 2), DIV("÷", 2);

    fun apply(a: Int, b: Int): Int = when (this) {
        ADD -> a + b
        SUB -> a - b
        MUL -> a * b
        DIV -> a / b
    }
}

data class Step(val left: Int, val op: Op, val right: Int, val result: Int) {
    override fun toString(): String = "$left ${op.symbol} $right = $result"
}

data class NumbersSolution(
    val target: Int,
    val value: Int,
    val steps: List<Step>,
    val expression: String,
) {
    val distance: Int get() = kotlin.math.abs(target - value)
    val exact: Boolean get() = distance == 0
}
