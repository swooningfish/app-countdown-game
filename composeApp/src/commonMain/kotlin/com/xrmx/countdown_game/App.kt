package com.xrmx.countdown_game

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xrmx.countdown_game.game.ConundrumGenerator
import com.xrmx.countdown_game.game.WordDictionary
import com.xrmx.countdown_game.game.WordListInfo
import com.xrmx.countdown_game.platform.BluetoothGames
import com.xrmx.countdown_game.platform.PlatformBackHandler
import com.xrmx.countdown_game.platform.bluetoothGames
import com.xrmx.countdown_game.platform.readStoredText
import com.xrmx.countdown_game.platform.writeStoredText
import com.xrmx.countdown_game.ui.ConundrumGame
import com.xrmx.countdown_game.ui.ConundrumScreen
import com.xrmx.countdown_game.ui.CountdownTheme
import com.xrmx.countdown_game.ui.LettersGame
import com.xrmx.countdown_game.ui.LettersScreen
import com.xrmx.countdown_game.ui.NumbersGame
import com.xrmx.countdown_game.ui.NumbersScreen
import com.xrmx.countdown_game.ui.Session
import com.xrmx.countdown_game.ui.Settings
import com.xrmx.countdown_game.ui.SettingsScreen
import com.xrmx.countdown_game.ui.TwoPlayerScreen
import com.xrmx.countdown_game.ui.TwoPlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val icon: ImageVector) {
    Letters("Letters", Icons.Filled.Abc),
    Numbers("Numbers", Icons.Filled.Calculate),
    Conundrum("Conundrum", Icons.Filled.Extension),
    TwoPlayer("2 Player", Icons.Filled.Groups),
}

/** Everything the app remembers while it runs. */
class AppState(bluetooth: BluetoothGames? = bluetoothGames()) {
    val settings = Settings()
    val session = Session()
    val letters = LettersGame()
    val numbers = NumbersGame()
    val conundrum = ConundrumGame()
    val twoPlayer = TwoPlayerState(bluetooth)
    var tab by mutableStateOf(Tab.Letters)
    var showSettings by mutableStateOf(false)
    var dictionary by mutableStateOf<WordDictionary?>(null)
    var wordList by mutableStateOf<WordListInfo?>(null)
}

private const val SETTINGS_FILE = "settings.properties"

/**
 * @param preloadedDictionary skips loading saved settings and word lists (used for screenshots).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    state: AppState = remember { AppState() },
    darkTheme: Boolean = isSystemInDarkTheme(),
    preloadedDictionary: WordDictionary? = null,
) {
    val persist = preloadedDictionary == null
    LaunchedEffect(Unit) {
        if (!persist) {
            state.dictionary = preloadedDictionary
            state.wordList = WordListInfo(true, WordListInfo.BUILT_IN_SOURCE, preloadedDictionary!!.size, null)
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) { runCatching { readStoredText(SETTINGS_FILE) }.getOrNull() }
            ?.let { state.settings.decode(it) }
        val loaded = WordListRepository.load()
        state.dictionary = loaded.dictionary
        state.wordList = loaded.info
    }
    if (persist) {
        LaunchedEffect(Unit) {
            snapshotFlow { state.settings.encode() }.drop(1).collect { text ->
                withContext(Dispatchers.Default) { runCatching { writeStoredText(SETTINGS_FILE, text) } }
            }
        }
    }

    CountdownTheme(darkTheme) {
        val settings = state.settings
        val session = state.session
        val dictionary = state.dictionary
        val generator = remember(dictionary) { dictionary?.let { ConundrumGenerator(it) } }
        val tab = state.tab

        PlatformBackHandler(enabled = state.showSettings) { state.showSettings = false }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (state.showSettings) "Settings" else AppInfo.NAME, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (state.showSettings) {
                            IconButton(onClick = { state.showSettings = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            AssistChip(
                                onClick = { state.showSettings = true },
                                label = { Text("${session.score}") },
                                leadingIcon = { Icon(Icons.Filled.EmojiEvents, contentDescription = "Score") },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    },
                    actions = {
                        if (!state.showSettings) {
                            IconButton(onClick = { state.showSettings = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!state.showSettings) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = t == tab,
                                onClick = { state.tab = t },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val screenModifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding()
            if (state.showSettings) {
                SettingsScreen(
                    settings = settings,
                    session = session,
                    wordList = state.wordList,
                    onWordListChanged = { loaded ->
                        state.dictionary = loaded.dictionary
                        state.wordList = loaded.info
                    },
                    modifier = screenModifier,
                )
            } else {
                when (tab) {
                    Tab.Letters -> LettersScreen(state.letters, dictionary, settings, session, screenModifier)
                    Tab.Numbers -> NumbersScreen(state.numbers, settings, session, screenModifier)
                    Tab.Conundrum -> ConundrumScreen(state.conundrum, generator, settings, session, screenModifier)
                    Tab.TwoPlayer -> TwoPlayerScreen(state.twoPlayer, settings, dictionary, screenModifier)
                }
            }
        }
    }
}
