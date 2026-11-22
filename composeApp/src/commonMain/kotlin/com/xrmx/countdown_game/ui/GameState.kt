package com.xrmx.countdown_game.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xrmx.countdown_game.game.Conundrum
import com.xrmx.countdown_game.game.ConundrumGenerator
import com.xrmx.countdown_game.game.ExpressionEvaluator
import com.xrmx.countdown_game.game.BoardCard
import com.xrmx.countdown_game.game.LetterBag
import com.xrmx.countdown_game.game.NumberBoard
import com.xrmx.countdown_game.game.NumberSelection
import com.xrmx.countdown_game.game.TargetDifficulty
import com.xrmx.countdown_game.game.LetterRules
import com.xrmx.countdown_game.game.LettersSolution
import com.xrmx.countdown_game.game.NumberRules
import com.xrmx.countdown_game.game.NumberSolver
import com.xrmx.countdown_game.game.NumbersSolution
import com.xrmx.countdown_game.game.Scoring
import com.xrmx.countdown_game.game.WordCheck
import com.xrmx.countdown_game.game.WordDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class Phase { Picking, Playing, Results }

class Settings {
    var clockEnabled by mutableStateOf(true)
    var clockSeconds by mutableIntStateOf(DEFAULT_CLOCK_SECONDS)
    var enforceLetterRule by mutableStateOf(true)
    var targetDifficulty by mutableStateOf(TargetDifficulty.Random)
    var nastyLargeNumbers by mutableStateOf(false)

    /** Seconds for the next round's clock, or null when the clock is switched off. */
    val activeClockSeconds: Int? get() = if (clockEnabled) clockSeconds else null

    /** Saved as simple key=value lines. */
    fun encode(): String = listOf(
        "clockEnabled=$clockEnabled",
        "clockSeconds=$clockSeconds",
        "enforceLetterRule=$enforceLetterRule",
        "targetDifficulty=${targetDifficulty.name}",
        "nastyLargeNumbers=$nastyLargeNumbers",
    ).joinToString("\n")

    fun decode(text: String) {
        val values = text.lines().mapNotNull { line ->
            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
        }.toMap()
        values["clockEnabled"]?.toBooleanStrictOrNull()?.let { clockEnabled = it }
        values["clockSeconds"]?.toIntOrNull()?.takeIf { it in CLOCK_OPTIONS }?.let { clockSeconds = it }
        values["enforceLetterRule"]?.toBooleanStrictOrNull()?.let { enforceLetterRule = it }
        values["targetDifficulty"]?.let { name -> TargetDifficulty.entries.firstOrNull { it.name == name } }?.let { targetDifficulty = it }
        values["nastyLargeNumbers"]?.toBooleanStrictOrNull()?.let { nastyLargeNumbers = it }
    }

    companion object {
        const val DEFAULT_CLOCK_SECONDS = 30
        val CLOCK_OPTIONS = listOf(15, 30, 45, 60, 90, 120)
    }
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
    var clockLimit by mutableIntStateOf(Settings.DEFAULT_CLOCK_SECONDS)
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

    fun start(clockSeconds: Int?, startedAt: TimeMark = TimeSource.Monotonic.markNow()) {
        guess = ""
        clockStart = if (clockSeconds != null) startedAt else null
        clockLimit = clockSeconds ?: Settings.DEFAULT_CLOCK_SECONDS
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
    var board by mutableStateOf(NumberBoard())
        private set
    var picked by mutableStateOf<List<BoardCard>>(emptyList())
        private set
    var numbers by mutableStateOf<List<Int>>(emptyList())
        private set
    var target by mutableStateOf<Int?>(null)
    var generatingTarget by mutableStateOf(false)
        private set
    var phase by mutableStateOf(Phase.Picking)
        private set
    var working by mutableStateOf("")
    var clockStart by mutableStateOf<TimeMark?>(null)
        private set
    var clockLimit by mutableIntStateOf(Settings.DEFAULT_CLOCK_SECONDS)
        private set
    var playerResult by mutableStateOf<ExpressionEvaluator.Result?>(null)
        private set
    var playerScore by mutableIntStateOf(0)
        private set
    var solution by mutableStateOf<NumbersSolution?>(null)
        private set

    val isReady: Boolean get() = numbers.size == NumberRules.COUNT && target != null

    val isFull: Boolean get() = numbers.size == NumberRules.COUNT

    /** Shuffles a fresh face-down board, using the nasty large numbers if asked. */
    fun resetBoard(settings: Settings) {
        board = NumberBoard(if (settings.nastyLargeNumbers) NumberRules.NASTY_LARGE else NumberRules.LARGE)
        picked = emptyList()
        numbers = emptyList()
        target = null
    }

    fun isPicked(card: BoardCard): Boolean = card in picked

    /** Turns over one card. Returns true when this completes the six numbers. */
    fun pick(card: BoardCard): Boolean {
        if (isFull || card in picked) return false
        picked = picked + card
        numbers = numbers + board.rows[card.row][card.column]
        return isFull
    }

    /** Picks the remaining cards following a selection from the show, taking from each row in turn. */
    fun pickSelection(selection: NumberSelection, settings: Settings) {
        resetBoard(settings)
        selection.rows.forEachIndexed { row, count ->
            (0 until count).forEach { column -> pick(BoardCard(row, column)) }
        }
    }

    fun setOwnNumbers(values: List<Int>) {
        picked = emptyList()
        numbers = values
    }

    /** Generates a target matching the difficulty setting (graded off the main thread). */
    suspend fun newTarget(difficulty: TargetDifficulty) {
        if (!isFull) return
        generatingTarget = true
        val nums = numbers
        try {
            target = withContext(Dispatchers.Default) { NumberRules.target(nums, difficulty) }
        } finally {
            generatingTarget = false
        }
    }

    fun start(clockSeconds: Int?, startedAt: TimeMark = TimeSource.Monotonic.markNow()) {
        working = ""
        clockStart = if (clockSeconds != null) startedAt else null
        clockLimit = clockSeconds ?: Settings.DEFAULT_CLOCK_SECONDS
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

    fun newRound(settings: Settings) {
        resetBoard(settings)
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
    var clockLimit by mutableIntStateOf(Settings.DEFAULT_CLOCK_SECONDS)
        private set
    var solved by mutableStateOf(false)
        private set

    fun start(generator: ConundrumGenerator, clockSeconds: Int?, startedAt: TimeMark = TimeSource.Monotonic.markNow()) {
        puzzle = generator.next() ?: return
        guess = ""
        solved = false
        clockStart = if (clockSeconds != null) startedAt else null
        clockLimit = clockSeconds ?: Settings.DEFAULT_CLOCK_SECONDS
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
