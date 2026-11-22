package com.xrmx.countdown_game.game

import kotlin.math.abs

/** Points awarded on the show. */
object Scoring {
    /** One point per letter, doubled to 18 for a nine-letter word. */
    fun letters(length: Int): Int = when {
        length <= 0 -> 0
        length == LetterRules.TOTAL -> 18
        else -> length
    }

    /** 10 for exact, 7 if within 5, 5 if within 10, otherwise nothing. */
    fun numbers(target: Int, value: Int): Int = when (abs(target - value)) {
        0 -> 10
        in 1..5 -> 7
        in 6..10 -> 5
        else -> 0
    }

    const val CONUNDRUM = 10
}
