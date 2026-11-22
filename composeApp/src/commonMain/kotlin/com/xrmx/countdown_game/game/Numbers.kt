package com.xrmx.countdown_game.game

import kotlin.random.Random

/** Rules of the Countdown numbers round. */
object NumberRules {
    const val COUNT = 6
    const val MAX_LARGE = 4
    const val MIN_TARGET = 101
    const val MAX_TARGET = 999
    val LARGE = listOf(25, 50, 75, 100)
    val SMALL = (1..10).flatMap { listOf(it, it) }

    /** The "nasty" large numbers used in a special episode instead of 25, 50, 75 and 100. */
    val NASTY_LARGE = listOf(12, 37, 62, 87)

    /**
     * Picks a target for [numbers]. For anything but [TargetDifficulty.Random] every target from
     * 101 to 999 is graded by the fewest operations it needs, falling back to a neighbouring
     * difficulty when no target fits.
     */
    fun target(numbers: List<Int>, difficulty: TargetDifficulty, random: Random = Random.Default): Int {
        if (difficulty == TargetDifficulty.Random) return randomTarget(random)
        val steps = NumberSolver.fewestSteps(numbers, MAX_TARGET)
        val order = when (difficulty) {
            TargetDifficulty.Easy -> listOf(TargetDifficulty.Easy, TargetDifficulty.Medium, TargetDifficulty.Hard)
            TargetDifficulty.Medium -> listOf(TargetDifficulty.Medium, TargetDifficulty.Easy, TargetDifficulty.Hard)
            else -> listOf(TargetDifficulty.Hard, TargetDifficulty.Medium, TargetDifficulty.Easy)
        }
        for (d in order) {
            val candidates = (MIN_TARGET..MAX_TARGET).filter { d.accepts(steps[it]) }
            if (candidates.isNotEmpty()) return candidates.random(random)
        }
        return randomTarget(random)
    }

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

/** How hard a generated target should be, judged by the fewest operations an exact answer needs. */
enum class TargetDifficulty(val label: String, val description: String) {
    Random("Random", "Any target from 101 to 999, as on the show"),
    Easy("Easy", "Solvable in 1 or 2 operations"),
    Medium("Medium", "Needs 3 or 4 operations"),
    Hard("Hard", "Needs all 5 operations, or can't be solved exactly"),
    ;

    fun accepts(steps: Int): Boolean = when (this) {
        Random -> true
        Easy -> steps <= 2
        Medium -> steps in 3..4
        Hard -> steps >= 5
    }
}

/**
 * Selections contestants ask for on the show. [rows] is how many cards to take from each row of
 * the board, top (large) row first.
 */
enum class NumberSelection(val label: String, val description: String, val rows: List<Int>) {
    SixSmall("6 small", "No large numbers", listOf(0, 2, 2, 2)),
    OneLarge("1 large", "One from the top, five small", listOf(1, 2, 2, 1)),
    TwoLarge("2 large", "Two from the top, four small", listOf(2, 1, 2, 1)),
    ThreeLarge("3 large", "Three from the top, three small", listOf(3, 1, 1, 1)),
    FourLarge("4 large", "All four large, two small", listOf(4, 1, 1, 0)),
    InvertedT("Inverted T", "1 large, then 1, 1 and 3 from the rows below", listOf(1, 1, 1, 3)),
    Whitehall("Whitehall", "1, 2, 1, 2 from top to bottom", listOf(1, 2, 1, 2)),
    Motown("Motown", "4 large and 2 small", listOf(4, 0, 1, 1)),
    ;

    val large: Int get() = rows[0]
}

/**
 * The studio board: four large cards on the top row, then the twenty small cards (two each of
 * 1-10) in rows of 6, 7 and 7, all face down and shuffled.
 */
class NumberBoard(large: List<Int> = NumberRules.LARGE, random: Random = Random.Default) {
    val rows: List<List<Int>>

    init {
        val small = NumberRules.SMALL.shuffled(random)
        rows = listOf(
            large.shuffled(random),
            small.subList(0, 6),
            small.subList(6, 13),
            small.subList(13, 20),
        )
    }

    companion object {
        val ROW_SIZES = listOf(4, 6, 7, 7)
    }
}

data class BoardCard(val row: Int, val column: Int)
