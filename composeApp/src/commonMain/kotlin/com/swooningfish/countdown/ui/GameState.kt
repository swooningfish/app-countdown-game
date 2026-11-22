package com.swooningfish.countdown.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swooningfish.countdown.game.Conundrum
import com.swooningfish.countdown.game.ConundrumGenerator
import com.swooningfish.countdown.game.ExpressionEvaluator
import com.swooningfish.countdown.game.LetterBag
import com.swooningfish.countdown.game.LetterRules
import com.swooningfish.countdown.game.LettersSolution
import com.swooningfish.countdown.game.NumberRules
import com.swooningfish.countdown.game.NumberSolver
import com.swooningfish.countdown.game.NumbersSolution
import com.swooningfish.countdown.game.Scoring
import com.swooningfish.countdown.game.WordCheck
import com.swooningfish.countdown.game.WordDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class Phase { Picking, Playing, Results }

class Settings {
    var clockEnabled by mutableStateOf(true)
    var enforceLetterRule by mutableStateOf(true)
}

class Session {
    var score by mutableIntStateOf(0)
        private set
    var rounds by mutableIntStateOf(0)
        private set

    fun record(points: Int) {
        score += points
        rounds++
    }

    fun reset() {
        score = 0
        rounds = 0
    }
}

class LettersGame(private val bag: LetterBag = LetterBag()) {
    var letters by mutableStateOf("")
        private set
    var phase by mutableStateOf(Phase.Picking)
        private set
    var guess by mutableStateOf("")
    var clockStart by mutableStateOf<TimeMark?>(null)
        private set
    var check by mutableStateOf<WordCheck?>(null)
        private set
    var solution by mutableStateOf<LettersSolution?>(null)
        private set

    val isFull: Boolean get() = letters.length == LetterRules.TOTAL

    fun pickVowel() {
        if (!isFull) letters += bag.drawVowel()
    }

    fun pickConsonant() {
        if (!isFull) letters += bag.drawConsonant()
    }

    fun removeLast() {
        letters = letters.dropLast(1)
    }

    fun useLetters(value: String) {
        letters = value.uppercase().take(LetterRules.TOTAL)
    }

    fun start(clock: Boolean, startedAt: TimeMark = TimeSource.Monotonic.markNow()) {
        guess = ""
        clockStart = if (clock) startedAt else null
        phase = Phase.Playing
    }

    /** Adds a tapped tile's letter to the guess if that letter is still unused. */
    fun appendFromTile(index: Int) {
        val ch = letters.getOrNull(index) ?: return
        if (guess.count { it == ch } < letters.count { it == ch }) guess += ch
    }

    fun finish(dictionary: WordDictionary, session: Session?) {
        if (phase == Phase.Results) return
        val result = dictionary.check(guess, letters)
        check = result
        solution = dictionary.solve(letters, guess)
        session?.record(result.score)
        clockStart = null
        phase = Phase.Results
    }

    fun newRound() {
        letters = ""
        guess = ""
        check = null
        solution = null
        clockStart = null
        phase = Phase.Picking
    }
}

class NumbersGame {
    var largeCount by mutableIntStateOf(1)
    var numbers by mutableStateOf<List<Int>>(emptyList())
        private set
    var target by mutableStateOf<Int?>(null)
    var phase by mutableStateOf(Phase.Picking)
        private set
    var working by mutableStateOf("")
    var clockStart by mutableStateOf<TimeMark?>(null)
        private set
    var playerResult by mutableStateOf<ExpressionEvaluator.Result?>(null)
        private set
    var playerScore by mutableIntStateOf(0)
        private set
    var solution by mutableStateOf<NumbersSolution?>(null)
        private set

    val isReady: Boolean get() = numbers.size == NumberRules.COUNT && target != null

    fun deal() {
        numbers = NumberRules.deal(largeCount)
        target = NumberRules.randomTarget()
    }

    fun setOwnNumbers(values: List<Int>) {
        numbers = values
    }

    fun newTarget() {
        target = NumberRules.randomTarget()
    }

    fun start(clock: Boolean, startedAt: TimeMark = TimeSource.Monotonic.markNow()) {
        working = ""
        clockStart = if (clock) startedAt else null
        phase = Phase.Playing
    }

    /** Numbers still free to use in the working, by tile index. */
    fun isTileFree(index: Int): Boolean {
        val n = numbers.getOrNull(index) ?: return false
        val usedCount = ExpressionEvaluator.numbersIn(working).count { it == n }
        val earlierSame = numbers.take(index).count { it == n }
        return earlierSame >= usedCount
    }

    fun append(token: String) {
        working = (working.trimEnd() + " " + token).trim()
    }

    fun backspace() {
        val trimmed = working.trimEnd()
        working = if (trimmed.lastOrNull()?.isDigit() == true) {
            trimmed.dropLastWhile { it.isDigit() }.trimEnd()
        } else {
            trimmed.dropLast(1).trimEnd()
        }
    }

    suspend fun finish(session: Session?) {
        if (phase == Phase.Results) return
        val goal = target ?: return
        val result = if (working.isBlank()) null else ExpressionEvaluator.evaluate(working, numbers)
        playerResult = result
        playerScore = (result as? ExpressionEvaluator.Result.Ok)?.let { Scoring.numbers(goal, it.value) } ?: 0
        session?.record(playerScore)
        clockStart = null
        phase = Phase.Results
        val nums = numbers
        solution = withContext(Dispatchers.Default) { NumberSolver.solve(nums, goal) }
    }

    fun newRound() {
        numbers = emptyList()
        target = null
        working = ""
        playerResult = null
        playerScore = 0
        solution = null
        clockStart = null
        phase = Phase.Picking
    }
}

class ConundrumGame {
    var puzzle by mutableStateOf<Conundrum?>(null)
        private set
    var phase by mutableStateOf(Phase.Picking)
        private set
    var guess by mutableStateOf("")
    var clockStart by mutableStateOf<TimeMark?>(null)
        private set
    var solved by mutableStateOf(false)
        private set

    fun start(generator: ConundrumGenerator, clock: Boolean, startedAt: TimeMark = TimeSource.Monotonic.markNow()) {
        puzzle = generator.next() ?: return
        guess = ""
        solved = false
        clockStart = if (clock) startedAt else null
        phase = Phase.Playing
    }

    fun appendFromTile(index: Int) {
        val letters = puzzle?.scrambled ?: return
        val ch = letters.getOrNull(index) ?: return
        if (guess.count { it == ch } < letters.count { it == ch }) guess += ch
    }

    fun finish(session: Session?) {
        if (phase != Phase.Playing) return
        solved = guess.trim().equals(puzzle?.answer, ignoreCase = true)
        session?.record(if (solved) Scoring.CONUNDRUM else 0)
        clockStart = null
        phase = Phase.Results
    }
}
