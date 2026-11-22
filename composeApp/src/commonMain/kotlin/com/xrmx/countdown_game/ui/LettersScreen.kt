package com.xrmx.countdown_game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.xrmx.countdown_game.game.LetterRules
import com.xrmx.countdown_game.game.LettersSolution
import com.xrmx.countdown_game.game.WordCheck
import com.xrmx.countdown_game.game.WordDictionary

@Composable
fun LettersScreen(
    game: LettersGame,
    dictionary: WordDictionary?,
    settings: Settings,
    session: Session,
    modifier: Modifier = Modifier,
) {
    var showTypeDialog by remember { mutableStateOf(false) }

    ScreenColumn(modifier) {
        val vowels = LetterRules.vowelCount(game.letters)
        val consonants = LetterRules.consonantCount(game.letters)
        SectionCard(
            title = "Letters round",
            subtitle = "$vowels vowels · $consonants consonants",
        ) {
            LetterRow(
                letters = game.letters,
                slots = LetterRules.TOTAL,
                onTileClick = if (game.phase == Phase.Playing) game::appendFromTile else null,
            )
            if (game.phase == Phase.Picking) {
                PickLettersControls(game, settings, onType = { showTypeDialog = true })
            }
        }

        when (game.phase) {
            Phase.Picking -> if (game.isFull) {
                ReadyControls(
                    clockSeconds = settings.activeClockSeconds,
                    solveEnabled = dictionary != null,
                    onStart = { game.start(settings.activeClockSeconds) },
                    onSolve = { dictionary?.let { game.finish(it, session = null) } },
                )
            } else {
                HowToPlayCard(
                    "Pick nine letters, then find the longest word you can make from them. " +
                        if (settings.enforceLetterRule) "You need at least 3 vowels and 4 consonants." else "",
                )
            }

            Phase.Playing -> SectionCard(title = "Your word") {
                game.clockStart?.let { start ->
                    CountdownClock(
                        start = start,
                        seconds = game.clockLimit,
                        onTimeUp = { dictionary?.let { game.finish(it, session) } },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                WordEntryField(
                    value = game.guess,
                    onValueChange = { game.guess = it },
                    letters = game.letters,
                    onDone = { dictionary?.let { game.finish(it, session) } },
                )
                Button(
                    onClick = { dictionary?.let { game.finish(it, session) } },
                    enabled = dictionary != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Declare")
                }
            }

            Phase.Results -> {
                LettersResults(game.check, game.solution)
                Button(onClick = game::newRound, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("New letters round")
                }
            }
        }

        if (dictionary == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Loading dictionary…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showTypeDialog) {
        TypeLettersDialog(
            initial = game.letters,
            enforce = settings.enforceLetterRule,
            onDismiss = { showTypeDialog = false },
            onConfirm = {
                game.useLetters(it)
                showTypeDialog = false
            },
        )
    }
}

@Composable
private fun PickLettersControls(game: LettersGame, settings: Settings, onType: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = game::pickVowel,
            enabled = LetterRules.canPickVowel(game.letters, settings.enforceLetterRule),
            modifier = Modifier.weight(1f),
            contentPadding = PairedButtonPadding,
        ) { Text("Vowel") }
        FilledTonalButton(
            onClick = game::pickConsonant,
            enabled = LetterRules.canPickConsonant(game.letters, settings.enforceLetterRule),
            modifier = Modifier.weight(1f),
            contentPadding = PairedButtonPadding,
        ) { Text("Consonant") }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onType) {
            Icon(Icons.Filled.Keyboard, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Type letters")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = game::removeLast, enabled = game.letters.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Remove last letter")
        }
        TextButton(onClick = game::newRound, enabled = game.letters.isNotEmpty()) { Text("Clear") }
    }
}

@Composable
fun ReadyControls(clockSeconds: Int?, solveEnabled: Boolean, onStart: () -> Unit, onSolve: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStart, modifier = Modifier.weight(1f), contentPadding = PairedButtonPadding) {
            Icon(if (clockSeconds != null) Icons.Filled.Timer else Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(if (clockSeconds != null) "Start ${clockSeconds}s clock" else "Play")
        }
        OutlinedButton(onClick = onSolve, enabled = solveEnabled, modifier = Modifier.weight(1f), contentPadding = PairedButtonPadding) {
            Icon(Icons.Filled.Lightbulb, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Solve")
        }
    }
}

@Composable
fun HowToPlayCard(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Text field for a word made from [letters]; only letters A-Z are accepted. */
@Composable
fun WordEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    letters: String,
    onDone: () -> Unit,
    label: String = "Your longest word",
) {
    val canSpell = WordDictionary.canSpell(value, letters)
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.uppercase().filter { it in 'A'..'Z' }.take(LetterRules.TOTAL)) },
        label = { Text(label) },
        singleLine = true,
        isError = value.isNotEmpty() && !canSpell,
        supportingText = {
            Text(
                when {
                    value.isEmpty() -> "Tap the tiles or type"
                    !canSpell -> "Uses letters you don't have"
                    else -> "${value.length} letters"
                },
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear word") }
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LettersResults(check: WordCheck?, solution: LettersSolution?) {
    if (check != null && check != WordCheck.Empty) {
        val valid = check is WordCheck.Valid
        val (title, detail) = when (check) {
            is WordCheck.Valid -> check.word to "${check.word.length} letters"
            is WordCheck.NotInDictionary -> check.word to "Not in the dictionary"
            is WordCheck.NotInLetters -> check.word to "Uses letters that weren't picked"
            WordCheck.Empty -> "" to ""
        }
        ResultBanner(
            icon = if (valid) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            title = title,
            detail = detail,
            points = check.score,
            positive = valid,
            footer = solution?.let {
                when {
                    !valid -> null
                    check.word.length >= it.maxLength -> "That's the longest word possible!"
                    else -> "The best possible was ${it.maxLength} letters."
                }
            },
        )
    } else if (check == WordCheck.Empty) {
        ResultBanner(Icons.Filled.Cancel, "No word declared", "No points this round", 0, positive = false)
    }

    solution ?: return

    if (solution.nineLetterWords.isNotEmpty()) {
        SectionCard(
            title = "Nine-letter word" + if (solution.nineLetterWords.size > 1) "s" else "",
            subtitle = "Worth 18 points",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            solution.nineLetterWords.forEach { LetterRow(it, highlight = true) }
        }
    }

    SectionCard(
        title = "Longest words",
        subtitle = if (solution.longestWords.isEmpty()) "No words found" else "Top ${solution.longestWords.size}, not including your word",
    ) {
        WordGroups(solution.longestWords)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordGroups(words: List<String>) {
    words.groupBy { it.length }.forEach { (length, group) ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "$length letters",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.forEach { word ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            word,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResultBanner(
    icon: ImageVector,
    title: String,
    detail: String,
    points: Int,
    positive: Boolean,
    footer: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (positive) colors.tertiaryContainer else colors.errorContainer,
        contentColor = if (positive) colors.onTertiaryContainer else colors.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(detail, style = MaterialTheme.typography.bodyMedium)
                }
                Box { PointsBadge(points) }
            }
            if (footer != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.EmojiEvents, null, Modifier.size(18.dp))
                    Text(footer, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TypeLettersDialog(initial: String, enforce: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val error = LetterRules.validateSelection(text, enforce)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter your letters") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type the nine letters from the board, in any order.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.uppercase().filter { it in 'A'..'Z' }.take(LetterRules.TOTAL) },
                    singleLine = true,
                    label = { Text("Letters") },
                    isError = text.length == LetterRules.TOTAL && error != null,
                    supportingText = { Text(error ?: "Looks good") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = error == null) { Text("Use letters") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
