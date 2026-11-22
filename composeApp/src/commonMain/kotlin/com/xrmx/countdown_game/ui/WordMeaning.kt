package com.xrmx.countdown_game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xrmx.countdown_game.game.WordDefinitions
import com.xrmx.countdown_game.game.WordSense
import com.xrmx.countdown_game.platform.downloadText

/** What a lookup found: senses, an empty list when the word isn't known, or a failure. */
private sealed interface Lookup {
    data object Loading : Lookup
    data class Found(val senses: List<WordSense>) : Lookup
    data object Failed : Lookup
}

/** Meanings already fetched this session, so reopening a word is instant and works offline. */
private val cache = mutableStateMapOf<String, List<WordSense>>()

/** A small "Meaning" button that opens the word's definition. */
@Composable
fun MeaningButton(word: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("What does ${word.uppercase()} mean?")
    }
    if (open) WordMeaningDialog(word, onDismiss = { open = false })
}

/** Looks up [word] online and shows its meanings. */
@Composable
fun WordMeaningDialog(word: String, onDismiss: () -> Unit) {
    val key = word.trim().lowercase()
    var lookup by remember(key) { mutableStateOf(cache[key]?.let { Lookup.Found(it) } ?: Lookup.Loading) }
    var attempt by remember(key) { mutableStateOf(0) }
    LaunchedEffect(key, attempt) {
        if (lookup !is Lookup.Loading) return@LaunchedEffect
        lookup = try {
            val senses = WordDefinitions.parse(downloadText(WordDefinitions.url(key)))
            cache[key] = senses
            Lookup.Found(senses)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The API answers "not found" with an error status, which arrives here too.
            Lookup.Failed
        }
    }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word.uppercase(), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (val l = lookup) {
                    Lookup.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Looking it up…")
                    }
                    is Lookup.Found -> if (l.senses.isEmpty()) {
                        Text("No definition found. The word list can include words the online dictionary doesn't have.")
                    } else {
                        l.senses.forEach { sense ->
                            Column {
                                if (sense.partOfSpeech.isNotEmpty()) {
                                    Text(
                                        sense.partOfSpeech,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(sense.definition, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Lookup.Failed -> {
                        Text("Couldn't find a definition. Check your connection, or look it up on Wiktionary.")
                        TextButton(onClick = {
                            lookup = Lookup.Loading
                            attempt++
                        }) { Text("Try again") }
                    }
                }
                Text(
                    "Definitions from dictionaryapi.dev. The game checks words against its own word list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { runCatching { uriHandler.openUri(WordDefinitions.moreUrl(key)) } }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Wiktionary")
            }
        },
    )
}
