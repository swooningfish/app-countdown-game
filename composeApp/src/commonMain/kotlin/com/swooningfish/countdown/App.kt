package com.swooningfish.countdown

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swooningfish.countdown.game.ConundrumGenerator
import com.swooningfish.countdown.game.WordDictionary
import com.swooningfish.countdown.resources.Res
import com.swooningfish.countdown.ui.ConundrumGame
import com.swooningfish.countdown.ui.ConundrumScreen
import com.swooningfish.countdown.ui.CountdownTheme
import com.swooningfish.countdown.ui.LettersGame
import com.swooningfish.countdown.ui.LettersScreen
import com.swooningfish.countdown.ui.NumbersGame
import com.swooningfish.countdown.ui.NumbersScreen
import com.swooningfish.countdown.ui.Session
import com.swooningfish.countdown.ui.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

enum class Tab(val label: String, val icon: ImageVector) {
    Letters("Letters", Icons.Filled.Abc),
    Numbers("Numbers", Icons.Filled.Calculate),
    Conundrum("Conundrum", Icons.Filled.Extension),
}

/** Everything the app remembers while it runs. */
class AppState {
    val settings = Settings()
    val session = Session()
    val letters = LettersGame()
    val numbers = NumbersGame()
    val conundrum = ConundrumGame()
    var tab by mutableStateOf(Tab.Letters)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun App(
    state: AppState = remember { AppState() },
    darkTheme: Boolean = isSystemInDarkTheme(),
    preloadedDictionary: WordDictionary? = null,
) {
    CountdownTheme(darkTheme) {
        val settings = state.settings
        val session = state.session
        val dictionary by produceState(preloadedDictionary) {
            if (value == null) {
                value = withContext(Dispatchers.Default) {
                    WordDictionary.parse(Res.readBytes("files/words.txt").decodeToString())
                }
            }
        }
        val generator = remember(dictionary) { dictionary?.let { ConundrumGenerator(it) } }
        var showSettings by remember { mutableStateOf(false) }
        val tab = state.tab

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Countdown", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        AssistChip(
                            onClick = { showSettings = true },
                            label = { Text("${session.score}") },
                            leadingIcon = { Icon(Icons.Filled.EmojiEvents, contentDescription = "Score") },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
            bottomBar = {
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
            },
        ) { padding ->
            val screenModifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding()
            when (tab) {
                Tab.Letters -> LettersScreen(state.letters, dictionary, settings, session, screenModifier)
                Tab.Numbers -> NumbersScreen(state.numbers, settings, session, screenModifier)
                Tab.Conundrum -> ConundrumScreen(state.conundrum, generator, settings, session, screenModifier)
            }
        }

        if (showSettings) {
            SettingsDialog(settings, session, dictionary?.size, onDismiss = { showSettings = false })
        }
    }
}

@Composable
private fun SettingsDialog(settings: Settings, session: Session, wordCount: Int?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingSwitch(
                    title = "30-second clock",
                    detail = "Race the clock before the answers are shown",
                    checked = settings.clockEnabled,
                    onChange = { settings.clockEnabled = it },
                )
                SettingSwitch(
                    title = "Vowel and consonant rule",
                    detail = "At least 3 vowels and 4 consonants",
                    checked = settings.enforceLetterRule,
                    onChange = { settings.enforceLetterRule = it },
                )
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Score: ${session.score}", style = MaterialTheme.typography.titleMedium)
                        Text("${session.rounds} rounds played", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = session::reset) { Text("Reset") }
                }
                HorizontalDivider()
                Text(
                    "Word list: SCOWL (British, size 70)" + (wordCount?.let { ", $it words" } ?: "") +
                        ". Copyright Kevin Atkinson. Results may differ from the dictionary used on TV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
