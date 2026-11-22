package com.xrmx.countdown_game.game

import kotlin.math.abs
import kotlin.random.Random

enum class RoundType(val label: String, val code: Char) {
    Letters("Letters", 'L'),
    Numbers("Numbers", 'N'),
    Conundrum("Conundrum", 'C'),
    ;

    companion object {
        fun fromCode(code: Char): RoundType? = entries.firstOrNull { it.code == code }
    }
}

/** The running order of a two-player game. It always finishes with exactly one conundrum. */
object RunningOrder {
    const val MAX_ROUNDS = 20

    fun parse(code: String): List<RoundType> = normalise(code.mapNotNull { RoundType.fromCode(it) })

    fun encode(rounds: List<RoundType>): String = rounds.joinToString("") { it.code.toString() }

    /** Removes stray conundrums, keeps at most [MAX_ROUNDS] - 1 other rounds and adds the conundrum at the end. */
    fun normalise(rounds: List<RoundType>): List<RoundType> =
        rounds.filter { it != RoundType.Conundrum }.take(MAX_ROUNDS - 1) + RoundType.Conundrum

    /** Adds a letters or numbers round just before the conundrum. */
    fun add(rounds: List<RoundType>, type: RoundType): List<RoundType> =
        normalise(rounds.filter { it != RoundType.Conundrum } + type)

    fun remove(rounds: List<RoundType>, index: Int): List<RoundType> =
        if (rounds.getOrNull(index) == RoundType.Conundrum) rounds else normalise(rounds.filterIndexed { i, _ -> i != index })

    /** Swaps a round with the one before it. The conundrum stays last. */
    fun moveUp(rounds: List<RoundType>, index: Int): List<RoundType> {
        if (index <= 0 || index >= rounds.size || rounds[index] == RoundType.Conundrum) return rounds
        return rounds.toMutableList().also { it[index - 1] = rounds[index]; it[index] = rounds[index - 1] }
    }
}

/** Suggested running orders. Every format ends with the conundrum. */
enum class MatchFormat(val label: String, val description: String, val rounds: List<RoundType>) {
    Full("Full game", "10 letters, 4 numbers and the conundrum, like the show", RunningOrder.parse("LLNLLNLLLNLLLNC")),
    Half("Half game", "5 letters, 2 numbers and the conundrum", RunningOrder.parse("LLNLLNLC")),
    Quick("Quick game", "2 letters, 1 numbers and the conundrum", RunningOrder.parse("LLNC")),
}

/** Settings that apply to both players. The host's settings are used. */
data class MatchRules(
    val clockSeconds: Int? = 30,
    val enforceLetterRule: Boolean = true,
    val targetDifficulty: TargetDifficulty = TargetDifficulty.Random,
    val nastyLargeNumbers: Boolean = false,
)

enum class MatchStage { Picking, Playing, Revealed, Finished }

/**
 * Everything both phones need to show a two-player game. Player 0 is the host, player 1 the guest.
 * Per-player values are lists of two.
 */
data class MatchState(
    val rounds: List<RoundType>,
    val rules: MatchRules,
    val names: List<String>,
    val scores: List<Int> = listOf(0, 0),
    val roundIndex: Int = 0,
    val stage: MatchStage = MatchStage.Picking,
    val letters: String = "",
    val numbers: List<Int> = emptyList(),
    val target: Int? = null,
    /** The conundrum's scrambled letters. */
    val conundrum: String = "",
    /** Every word accepted as the conundrum's answer. */
    val solutions: List<String> = emptyList(),
    /** What each player declared: a word, the numbers working or a conundrum answer. Null until they declare. */
    val answers: List<String?> = listOf(null, null),
    /** How each answer was judged, e.g. "7 letters" or "Not in the dictionary". */
    val verdicts: List<String> = listOf("", ""),
    /** Points scored this round. */
    val points: List<Int> = listOf(0, 0),
    /** Conundrum only: players who gave a wrong answer and can't try again. */
    val lockedOut: List<Boolean> = listOf(false, false),
    /** Players who have pressed Next after the results. */
    val ready: List<Boolean> = listOf(false, false),
    /** Goes up by one every time a round's clock starts. */
    val clock: Int = 0,
    /** Points for each finished round, as [host, guest]. */
    val history: List<List<Int>> = emptyList(),
    val notice: String? = null,
    /** The player the notice is for, or -1 for both. */
    val noticeFor: Int = -1,
) {
    val round: RoundType? get() = rounds.getOrNull(roundIndex)

    /** Who chooses this round's letters or numbers. Players take turns, starting with the host. */
    val picker: Int get() = roundIndex % 2

    fun opponent(player: Int): Int = 1 - player

    /**
     * What the guest's phone may see. While a round is being played, the conundrum's answer and
     * the host's declared answer are hidden, so they can't be read off the Bluetooth link.
     */
    fun forGuest(): MatchState = if (stage != MatchStage.Playing) this else copy(
        solutions = emptyList(),
        answers = listOf(answers[0]?.let { "" }, answers[1]),
    )

    /** 0 or 1 for the player in the lead, or null when level. */
    val leader: Int? get() = when {
        scores[0] > scores[1] -> 0
        scores[1] > scores[0] -> 1
        else -> null
    }
}

sealed interface MatchAction {
    data object Vowel : MatchAction
    data object Consonant : MatchAction
    data class Numbers(val selection: NumberSelection) : MatchAction
    data object SuggestConundrum : MatchAction

    /** A conundrum the players already know, e.g. from an episode they are watching. */
    data class KnownConundrum(val letters: String) : MatchAction

    /** A letters word, numbers working or conundrum answer. Blank means no answer. */
    data class Declare(val answer: String) : MatchAction

    /** Sent by the host when the clock has run out on both phones. */
    data object TimeUp : MatchAction
    data object Next : MatchAction
}

/**
 * The rules of a two-player game. It runs on the host's phone only; the guest is sent each new
 * [state] and sends back its actions.
 */
class MatchEngine(
    rounds: List<RoundType>,
    rules: MatchRules,
    names: List<String>,
    private val dictionary: WordDictionary,
    private val random: Random = Random.Default,
) {
    private val bag = LetterBag(random)
    private val conundrums = ConundrumGenerator(dictionary, random)

    var state = MatchState(RunningOrder.normalise(rounds), rules, names)
        private set

    /** Applies [action] from [player] if it is allowed right now. Returns whether anything changed. */
    fun apply(player: Int, action: MatchAction): Boolean {
        require(player == 0 || player == 1) { "Unknown player $player" }
        val before = state
        val s = state
        when (action) {
            MatchAction.Vowel, MatchAction.Consonant -> {
                if (s.stage != MatchStage.Picking || s.round != RoundType.Letters || player != s.picker) return false
                val vowel = action == MatchAction.Vowel
                val allowed = if (vowel) {
                    LetterRules.canPickVowel(s.letters, s.rules.enforceLetterRule)
                } else {
                    LetterRules.canPickConsonant(s.letters, s.rules.enforceLetterRule)
                }
                if (!allowed) return false
                val letters = s.letters + if (vowel) bag.drawVowel() else bag.drawConsonant()
                state = s.copy(letters = letters, notice = null)
                if (letters.length == LetterRules.TOTAL) startClock()
            }

            is MatchAction.Numbers -> {
                if (s.stage != MatchStage.Picking || s.round != RoundType.Numbers || player != s.picker) return false
                val board = NumberBoard(if (s.rules.nastyLargeNumbers) NumberRules.NASTY_LARGE else NumberRules.LARGE, random)
                val numbers = action.selection.rows.flatMapIndexed { row, count -> board.rows[row].take(count) }
                val target = NumberRules.target(numbers, s.rules.targetDifficulty, random)
                state = s.copy(numbers = numbers, target = target, notice = null)
                startClock()
            }

            MatchAction.SuggestConundrum -> {
                if (s.stage != MatchStage.Picking || s.round != RoundType.Conundrum) return false
                val puzzle = conundrums.next()
                if (puzzle == null) {
                    state = s.copy(notice = "The word list has no conundrums. Type one in instead.", noticeFor = player)
                } else {
                    state = s.copy(conundrum = puzzle.scrambled, solutions = listOf(puzzle.answer), notice = null)
                    startClock()
                }
            }

            is MatchAction.KnownConundrum -> {
                if (s.stage != MatchStage.Picking || s.round != RoundType.Conundrum) return false
                val letters = action.letters.trim().uppercase()
                val problem = conundrumProblem(letters)
                if (problem != null) {
                    state = s.copy(notice = problem, noticeFor = player)
                } else {
                    state = s.copy(conundrum = letters, solutions = conundrumAnswers(letters), notice = null)
                    startClock()
                }
            }

            is MatchAction.Declare -> {
                if (s.stage != MatchStage.Playing) return false
                val answer = action.answer.trim().uppercase().take(MAX_ANSWER)
                if (s.round == RoundType.Conundrum) declareConundrum(player, answer) else declare(player, answer)
            }

            MatchAction.TimeUp -> {
                if (player != 0 || s.stage != MatchStage.Playing) return false
                reveal()
            }

            MatchAction.Next -> {
                if (s.stage != MatchStage.Revealed || s.ready[player]) return false
                val ready = s.ready.set(player, true)
                state = s.copy(ready = ready)
                if (ready.all { it }) nextRound()
            }
        }
        return state != before
    }

    /** Returns why [letters] can't be used as a conundrum, or null if it can. */
    fun conundrumProblem(letters: String): String? = when {
        letters.length != LetterRules.TOTAL || !letters.all { it in 'A'..'Z' } -> "Enter the nine letters of the conundrum"
        conundrumAnswers(letters).isEmpty() -> "No nine-letter word in the word list uses those letters"
        else -> null
    }

    private fun conundrumAnswers(letters: String): List<String> =
        dictionary.wordsOfLength(LetterRules.TOTAL).filter { WordDictionary.canSpell(it, letters) }

    private fun startClock() {
        state = state.copy(stage = MatchStage.Playing, answers = listOf(null, null), clock = state.clock + 1)
    }

    private fun declare(player: Int, answer: String) {
        val s = state
        if (s.answers[player] != null) return
        state = s.copy(answers = s.answers.set(player, answer))
        if (state.answers.all { it != null }) reveal()
    }

    private fun declareConundrum(player: Int, answer: String) {
        val s = state
        if (s.lockedOut[player]) return
        if (answer.isNotEmpty() && answer in s.solutions) {
            state = s.copy(answers = s.answers.set(player, answer), points = s.points.set(player, Scoring.CONUNDRUM))
            reveal()
        } else {
            state = s.copy(answers = s.answers.set(player, answer), lockedOut = s.lockedOut.set(player, true))
            if (state.lockedOut.all { it }) reveal()
        }
    }

    private fun reveal() {
        val s = state
        val answers = s.answers.map { it ?: "" }
        val (verdicts, points) = when (s.round) {
            RoundType.Letters -> judgeLetters(s.letters, answers)
            RoundType.Numbers -> judgeNumbers(s.numbers, s.target ?: 0, answers)
            else -> answers.indices.map { p ->
                when {
                    s.points[p] > 0 -> "Correct!"
                    answers[p].isEmpty() -> "No answer"
                    else -> "Wrong answer"
                }
            } to s.points
        }
        state = s.copy(
            stage = MatchStage.Revealed,
            answers = answers,
            verdicts = verdicts,
            points = points,
            scores = s.scores.zip(points) { a, b -> a + b },
            history = s.history + listOf(points),
            ready = listOf(false, false),
            notice = null,
        )
    }

    /** The longest valid word scores; if both are the same length, both score. */
    private fun judgeLetters(letters: String, words: List<String>): Pair<List<String>, List<Int>> {
        val checks = words.map { dictionary.check(it, letters) }
        val best = checks.maxOf { (it as? WordCheck.Valid)?.word?.length ?: 0 }
        val verdicts = checks.map {
            when (it) {
                WordCheck.Empty -> "No word"
                is WordCheck.NotInLetters -> "Uses letters that weren't picked"
                is WordCheck.NotInDictionary -> "Not in the dictionary"
                is WordCheck.Valid -> "${it.word.length} letters"
            }
        }
        val points = checks.map { if (it is WordCheck.Valid && it.word.length == best) it.score else 0 }
        return verdicts to points
    }

    /** Only the player closer to the target scores (both if equally close), and only within 10. */
    private fun judgeNumbers(numbers: List<Int>, target: Int, workings: List<String>): Pair<List<String>, List<Int>> {
        val results = workings.map { if (it.isBlank()) null else ExpressionEvaluator.evaluate(it, numbers) }
        val distances = results.map { (it as? ExpressionEvaluator.Result.Ok)?.let { ok -> abs(target - ok.value) } }
        val best = distances.filterNotNull().minOrNull()
        val verdicts = results.map {
            when (it) {
                null -> "No answer"
                is ExpressionEvaluator.Result.Error -> it.message
                is ExpressionEvaluator.Result.Ok -> {
                    val away = abs(target - it.value)
                    if (away == 0) "${it.value}, spot on" else "${it.value}, $away away"
                }
            }
        }
        val points = results.mapIndexed { p, r ->
            if (r is ExpressionEvaluator.Result.Ok && distances[p] == best) Scoring.numbers(target, r.value) else 0
        }
        return verdicts to points
    }

    private fun nextRound() {
        val s = state
        val next = s.roundIndex + 1
        state = if (next >= s.rounds.size) {
            s.copy(stage = MatchStage.Finished)
        } else {
            MatchState(
                rounds = s.rounds,
                rules = s.rules,
                names = s.names,
                scores = s.scores,
                roundIndex = next,
                clock = s.clock,
                history = s.history,
            )
        }
    }

    companion object {
        /** Longest answer accepted, enough for any sensible numbers working. */
        const val MAX_ANSWER = 80
    }
}

private fun <T> List<T>.set(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
