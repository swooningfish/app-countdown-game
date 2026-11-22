package com.xrmx.countdown_game

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Desktop build, handy for trying the app on a PC: ./gradlew :composeApp:run */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Countdown",
        state = rememberWindowState(width = 440.dp, height = 900.dp),
    ) {
        App()
    }
}
