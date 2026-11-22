package com.xrmx.countdown_game

import android.os.Bundle
import com.xrmx.countdown_game.platform.AndroidPlatform
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Holds the app's state outside the activity, so turning the phone, switching dark mode or
 * changing the text size doesn't lose the current round or drop a two-player match.
 */
class AppStateHolder : ViewModel() {
    val state = AppState()

    override fun onCleared() {
        state.twoPlayer.leave()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidPlatform.init(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val holder = ViewModelProvider(this)[AppStateHolder::class.java]
        setContent { App(state = holder.state) }
    }
}
