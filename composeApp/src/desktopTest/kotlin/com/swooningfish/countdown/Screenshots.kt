package com.swooningfish.countdown

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.swooningfish.countdown.game.WordDictionary
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Renders the README screenshots off-screen with the real UI and solvers.
 * Run with: ./gradlew :composeApp:generateScreenshots
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "docs/screenshots").apply { mkdirs() }
    val dictionary = WordDictionary.parse(File("src/commonMain/composeResources/files/words.txt").readText())
    // Pretend the clock started a while ago so it shows part of its sweep.
    fun startedSecondsAgo(s: Int) = TimeSource.Monotonic.markNow() - s.seconds

    fun shot(name: String, dark: Boolean = false, setup: AppState.() -> Unit) {
        val state = AppState().apply(setup)
        ImageComposeScene(width = 1080, height = 2160, density = Density(2.7f)) {
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
        repeat(3) { letters.pickVowel() }
        repeat(3) { letters.pickConsonant() }
        letters.useLetters("STARIE")
    }
    shot("letters-playing") {
        letters.useLetters("RSTLNEAIO")
        letters.start(clock = true, startedAt = startedSecondsAgo(11))
        letters.guess = "RATIONS"
    }
    shot("letters-results") {
        letters.useLetters("RSTLNEAIO")
        letters.start(clock = false)
        letters.guess = "RATIONS"
        letters.finish(dictionary, session)
    }
    shot("numbers-playing") {
        tab = Tab.Numbers
        numbers.setOwnNumbers(listOf(100, 75, 50, 25, 6, 3))
        numbers.target = 952
        numbers.start(clock = true, startedAt = startedSecondsAgo(19))
        numbers.working = "(100 + 6) × 3"
    }
    shot("numbers-results") {
        tab = Tab.Numbers
        numbers.setOwnNumbers(listOf(100, 75, 50, 25, 6, 3))
        numbers.target = 952
        numbers.start(clock = false)
        numbers.working = "(100 + 6) × 75 ÷ 25 × 3"
        runBlocking { numbers.finish(session) }
    }
    shot("conundrum-dark", dark = true) {
        tab = Tab.Conundrum
        val generator = com.swooningfish.countdown.game.ConundrumGenerator(dictionary, kotlin.random.Random(7))
        conundrum.start(generator, clock = true, startedAt = startedSecondsAgo(8))
    }
}
