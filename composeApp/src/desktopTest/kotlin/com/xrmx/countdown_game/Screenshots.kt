package com.xrmx.countdown_game

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.xrmx.countdown_game.game.BoardCard
import com.xrmx.countdown_game.game.ConundrumGenerator
import com.xrmx.countdown_game.game.LoopbackLink
import com.xrmx.countdown_game.game.MatchAction
import com.xrmx.countdown_game.game.MatchFormat
import com.xrmx.countdown_game.game.MatchLink
import com.xrmx.countdown_game.game.MatchRules
import com.xrmx.countdown_game.game.MatchSession
import com.xrmx.countdown_game.game.WordDictionary
import com.xrmx.countdown_game.platform.BluetoothGames
import com.xrmx.countdown_game.platform.FoundHost
import com.xrmx.countdown_game.ui.TwoPlayerStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Renders the README screenshots off-screen with the real UI and solvers.
 * Run with: ./gradlew :composeApp:generateScreenshots
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "docs/screenshots").apply { mkdirs() }
    // An optional second argument scales the text, to check layouts with the phone's larger text sizes.
    val fontScale = args.getOrNull(1)?.toFloat() ?: 1f
    val dictionary = WordDictionary.parse(File("src/commonMain/composeResources/files/words.txt").readText())
    // Pretend the clock started a while ago so it shows part of its sweep.
    fun startedSecondsAgo(s: Int) = TimeSource.Monotonic.markNow() - s.seconds

    fun shot(name: String, dark: Boolean = false, height: Int = 2160, setup: AppState.() -> Unit) {
        val state = AppState(bluetooth = FakeBluetooth).apply(setup)
        // Larger text needs a taller image to show the whole screen.
        ImageComposeScene(width = 1080, height = (height * fontScale * fontScale).toInt(), density = Density(2.7f, fontScale)) {
            App(state, darkTheme = dark, preloadedDictionary = dictionary)
        }.use { scene ->
            var image = scene.render(0)
            repeat(5) { image = scene.render((it + 1) * 100_000_000L) }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed")
            File(out, "$name.png").writeBytes(png.bytes)
            println("Wrote ${File(out, "$name.png")}")
        }
    }

    shot("letters-picking") {
        letters.useLetters("STARIE")
    }
    shot("letters-playing") {
        letters.useLetters("RSTLNEAIO")
        letters.start(clockSeconds = 30, startedAt = startedSecondsAgo(11))
        letters.guess = "RATIONS"
    }
    shot("letters-results") {
        letters.useLetters("RSTLNEAIO")
        letters.start(clockSeconds = null)
        letters.guess = "RATIONS"
        letters.finish(dictionary, session)
    }
    shot("numbers-board") {
        tab = Tab.Numbers
        numbers.pick(BoardCard(0, 2))
        numbers.pick(BoardCard(1, 4))
        numbers.pick(BoardCard(3, 1))
    }
    shot("numbers-playing") {
        tab = Tab.Numbers
        numbers.setOwnNumbers(listOf(100, 75, 50, 25, 6, 3))
        numbers.setOwnTarget(952)
        numbers.start(clockSeconds = 30, startedAt = startedSecondsAgo(19))
        numbers.working = "(100 + 6) × 3"
    }
    shot("numbers-results") {
        tab = Tab.Numbers
        numbers.setOwnNumbers(listOf(100, 75, 50, 25, 6, 3))
        numbers.setOwnTarget(952)
        numbers.start(clockSeconds = null)
        numbers.working = "(100 + 6) × 75 ÷ 25 × 3"
        runBlocking { numbers.finish(session) }
    }
    shot("conundrum-dark", dark = true) {
        tab = Tab.Conundrum
        conundrum.start(ConundrumGenerator(dictionary, Random(7)), clockSeconds = 30, startedAt = startedSecondsAgo(8))
    }
    // Two phones joined in memory: screenshots show the guest's phone.
    fun AppState.twoPlayerGame(play: (host: MatchSession, guest: MatchSession) -> Unit) {
        val (hostLink, guestLink) = LoopbackLink.pair()
        val host = MatchSession.host(
            hostLink, MatchFormat.Full.rounds, MatchRules(), "Alex", dictionary,
            Dispatchers.Unconfined, Dispatchers.Unconfined, Random(3),
        )
        val guest = MatchSession.guest(guestLink, "Sam", Dispatchers.Unconfined)
        play(host, guest)
        tab = Tab.TwoPlayer
        twoPlayer.session = guest
        twoPlayer.step = TwoPlayerStep.Match
    }
    fun MatchSession.pickLetters(vowels: Int) {
        repeat(vowels) { act(MatchAction.Vowel) }
        repeat(9 - vowels) { act(MatchAction.Consonant) }
    }

    shot("two-player-setup") {
        tab = Tab.TwoPlayer
        settings.playerName = "Sam"
        twoPlayer.rounds = MatchFormat.Half.rounds
    }
    shot("two-player-playing") {
        twoPlayerGame { host, _ -> host.pickLetters(4) }
        val match = twoPlayer.session!!.state.value!!
        twoPlayer.syncRound(match)
        twoPlayer.clockStart(match.clock)
        twoPlayer.input = dictionary.wordsFrom(match.letters).getOrNull(3).orEmpty()
    }
    shot("two-player-results") {
        twoPlayerGame { host, guest ->
            host.pickLetters(4)
            val words = dictionary.wordsFrom(host.state.value!!.letters)
            host.act(MatchAction.Declare(words.getOrNull(2).orEmpty()))
            guest.act(MatchAction.Declare(words.getOrNull(5).orEmpty()))
        }
    }
    shot("settings", height = 4400) {
        showSettings = true
    }
}

/** Stands in for Bluetooth so the two-player screens render as they do on a phone. */
private object FakeBluetooth : BluetoothGames {
    override fun problem(): String? = null
    override suspend fun host(name: String): MatchLink = error("Not available in screenshots")
    override fun scan(): Flow<List<FoundHost>> = emptyFlow()
    override suspend fun join(host: FoundHost): MatchLink = error("Not available in screenshots")
}
