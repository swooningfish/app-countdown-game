package com.xrmx.countdown_game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xrmx.countdown_game.AppInfo
import com.xrmx.countdown_game.LoadedWordList
import com.xrmx.countdown_game.WordListRepository
import com.xrmx.countdown_game.game.TargetDifficulty
import com.xrmx.countdown_game.game.WordListInfo
import com.xrmx.countdown_game.game.WordListParser
import com.xrmx.countdown_game.platform.platformAppId
import com.xrmx.countdown_game.platform.platformDescription
import com.xrmx.countdown_game.platform.rememberTextFilePicker
import kotlinx.coroutines.launch

private sealed interface ImportStatus {
    data object Working : ImportStatus
    data class Done(val message: String) : ImportStatus
    data class Failed(val message: String) : ImportStatus
}

@Composable
fun SettingsScreen(
    settings: Settings,
    session: Session,
    wordList: WordListInfo?,
    onWordListChanged: (LoadedWordList) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<ImportStatus?>(null) }
    var showDownload by remember { mutableStateOf(false) }

    fun runImport(action: suspend () -> LoadedWordList, success: (LoadedWordList) -> String) {
        status = ImportStatus.Working
        scope.launch {
            status = try {
                val loaded = action()
                onWordListChanged(loaded)
                ImportStatus.Done(success(loaded))
            } catch (e: Exception) {
                ImportStatus.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    val pickFile = rememberTextFilePicker { result ->
        result?.fold(
            onSuccess = { text -> runImport({ WordListRepository.import(text, "File from this device") }) { "Loaded ${it.info.wordCount} words from your file" } },
            onFailure = { status = ImportStatus.Failed(it.message ?: "Couldn't read that file") },
        )
    }

    ScreenColumn(modifier) {
        SectionCard(title = "Clock") {
            SettingSwitch(
                title = "Countdown clock",
                detail = "Race the clock before the answers are shown",
                checked = settings.clockEnabled,
                onChange = { settings.clockEnabled = it },
            )
            TimeLimitChips(settings)
        }

        SectionCard(title = "Letters") {
            SettingSwitch(
                title = "Vowel and consonant rule",
                detail = "At least 3 vowels and 4 consonants, as on the show",
                checked = settings.enforceLetterRule,
                onChange = { settings.enforceLetterRule = it },
            )
        }

        SectionCard(title = "Numbers") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Target difficulty", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TargetDifficulty.entries.forEachIndexed { index, difficulty ->
                        SegmentedButton(
                            selected = settings.targetDifficulty == difficulty,
                            onClick = { settings.targetDifficulty = difficulty },
                            shape = SegmentedButtonDefaults.itemShape(index, TargetDifficulty.entries.size),
                            icon = {},
                        ) { Text(difficulty.label, maxLines = 1) }
                    }
                }
                Text(
                    settings.targetDifficulty.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingSwitch(
                title = "Nasty large numbers",
                detail = "Use 12, 37, 62 and 87 instead of 25, 50, 75 and 100, as in a special episode",
                checked = settings.nastyLargeNumbers,
                onChange = { settings.nastyLargeNumbers = it },
            )
        }

        SectionCard(title = "Word list", subtitle = "Used to check words, find the longest ones and make conundrums") {
            if (wordList != null) {
                Column {
                    Text(
                        if (wordList.builtIn) wordList.source else "Custom list",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        buildString {
                            append("${wordList.wordCount} words")
                            wordList.updated?.let { append(" · updated $it") }
                            if (!wordList.builtIn) append("\n${wordList.source}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val working = status == ImportStatus.Working
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { showDownload = true }, enabled = !working, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CloudDownload, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Download")
                }
                OutlinedButton(onClick = pickFile, enabled = !working, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileOpen, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Upload file")
                }
            }
            if (wordList != null && !wordList.builtIn) {
                TextButton(
                    onClick = { runImport({ WordListRepository.resetToBuiltIn() }) { "Back to the built-in word list" } },
                    enabled = !working,
                ) {
                    Icon(Icons.Filled.Restore, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Use built-in list")
                }
            }
            when (val s = status) {
                ImportStatus.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading word list…", style = MaterialTheme.typography.bodyMedium)
                }
                is ImportStatus.Done -> StatusLine(Icons.Filled.TaskAlt, s.message, MaterialTheme.colorScheme.tertiary)
                is ImportStatus.Failed -> StatusLine(Icons.Filled.ErrorOutline, s.message, MaterialTheme.colorScheme.error)
                null -> Text(
                    "Plain text, one word per line (SCOWL downloads work as-is). Capitalised words are treated as " +
                        "proper nouns and skipped. Needs at least ${WordListParser.MIN_WORDS} words.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Score") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${session.score} points", style = MaterialTheme.typography.titleMedium)
                    Text("${session.rounds} rounds played", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = session::reset) { Text("Reset") }
            }
        }

        SectionCard(title = "About") {
            DetailRow("App", AppInfo.NAME)
            DetailRow("Version", "${AppInfo.VERSION_NAME} (build ${AppInfo.VERSION_CODE})")
            DetailRow("App ID", platformAppId())
            DetailRow("Running on", platformDescription())
            DetailRow("Word list", wordList?.let { "${it.wordCount} words" } ?: "Loading…")
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Built-in word list: SCOWL © Kevin Atkinson. Countdown is a Channel 4 programme; this is an " +
                        "unofficial practice app, not affiliated with or endorsed by Channel 4. Words are checked against " +
                        "an open word list, so results can differ from the show's dictionary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDownload) {
        DownloadDialog(
            onDismiss = { showDownload = false },
            onDownload = { url ->
                showDownload = false
                runImport({ WordListRepository.download(url) }) { "Downloaded ${it.info.wordCount} words" }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeLimitChips(settings: Settings) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Time limit",
            style = MaterialTheme.typography.labelLarge,
            color = if (settings.clockEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Settings.CLOCK_OPTIONS.forEach { seconds ->
                FilterChip(
                    selected = settings.clockSeconds == seconds,
                    onClick = { settings.clockSeconds = seconds },
                    enabled = settings.clockEnabled,
                    label = { Text(if (seconds == Settings.DEFAULT_CLOCK_SECONDS) "${seconds}s (show)" else "${seconds}s") },
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun DownloadDialog(onDismiss: () -> Unit, onDownload: (String) -> Unit) {
    var url by remember { mutableStateOf(WordListInfo.SCOWL_URL) }
    val valid = url.trim().startsWith("https://", ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download a word list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the https:// link to a plain-text word list. The default is the latest SCOWL British English list.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Link") },
                    isError = !valid,
                    supportingText = { if (!valid) Text("Must start with https://") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { url = WordListInfo.SCOWL_URL }) { Text("Use SCOWL link") }
            }
        },
        confirmButton = { TextButton(onClick = { onDownload(url) }, enabled = valid) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
