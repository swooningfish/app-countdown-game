package com.swooningfish.countdown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swooningfish.countdown.game.ExpressionEvaluator
import com.swooningfish.countdown.game.NumberRules
import com.swooningfish.countdown.game.NumbersSolution
import kotlinx.coroutines.launch

@Composable
fun NumbersScreen(game: NumbersGame, settings: Settings, session: Session, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var showNumbersDialog by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    val finish: (Session?) -> Unit = { s -> scope.launch { game.finish(s) } }

    ScreenColumn(modifier) {
        SectionCard(title = "Numbers round", subtitle = "Use + − × ÷ to reach the target. Each number once.") {
            TargetDisplay(game.target, Modifier.align(Alignment.CenterHorizontally))
            NumberRow(
                numbers = game.numbers,
                slots = NumberRules.COUNT,
                onTileClick = if (game.phase == Phase.Playing) { index -> game.append(game.numbers[index].toString()) } else null,
                isTileEnabled = { game.phase != Phase.Playing || game.isTileFree(it) },
            )
            if (game.phase == Phase.Picking) {
                Text("How many large numbers?", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    for (i in 0..NumberRules.MAX_LARGE) {
                        SegmentedButton(
                            selected = game.largeCount == i,
                            onClick = { game.largeCount = i },
                            shape = SegmentedButtonDefaults.itemShape(i, NumberRules.MAX_LARGE + 1),
                        ) { Text("$i") }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = game::deal, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Casino, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Deal")
                    }
                    OutlinedButton(onClick = { showNumbersDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("My numbers")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = game::newTarget) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Random target")
                    }
                    TextButton(onClick = { showTargetDialog = true }) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Set target")
                    }
                }
            }
        }

        when (game.phase) {
            Phase.Picking -> if (game.isReady) {
                ReadyControls(
                    clockEnabled = settings.clockEnabled,
                    solveEnabled = true,
                    onStart = { game.start(settings.clockEnabled) },
                    onSolve = { finish(null) },
                )
            } else {
                HowToPlayCard(
                    "Choose how many large numbers (25, 50, 75, 100) you want and deal, or enter your own six numbers. " +
                        "The target is a random number from 101 to 999, or set your own.",
                )
            }

            Phase.Playing -> SectionCard(title = "Your working") {
                game.clockStart?.let { start ->
                    CountdownClock(start = start, onTimeUp = { finish(session) }, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                WorkingField(game, onDone = { finish(session) })
                OperatorKeys(game)
                Button(onClick = { finish(session) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Declare")
                }
            }

            Phase.Results -> {
                PlayerNumbersResult(game)
                SolutionCard(game.solution)
                Button(onClick = game::newRound, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("New numbers round")
                }
            }
        }
    }

    if (showNumbersDialog) {
        NumbersDialog(
            initial = game.numbers,
            onDismiss = { showNumbersDialog = false },
            onConfirm = {
                game.setOwnNumbers(it)
                if (game.target == null) game.newTarget()
                showNumbersDialog = false
            },
        )
    }
    if (showTargetDialog) {
        TargetDialog(
            initial = game.target,
            onDismiss = { showTargetDialog = false },
            onConfirm = {
                game.target = it
                showTargetDialog = false
            },
        )
    }
}

@Composable
private fun WorkingField(game: NumbersGame, onDone: () -> Unit) {
    val evaluation = if (game.working.isBlank()) null else ExpressionEvaluator.evaluate(game.working, game.numbers)
    OutlinedTextField(
        value = game.working,
        onValueChange = { game.working = it },
        label = { Text("e.g. (75 + 5) × 8") },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
        isError = evaluation is ExpressionEvaluator.Result.Error,
        supportingText = {
            Text(
                when (evaluation) {
                    null -> "Tap the numbers and operators, or type"
                    is ExpressionEvaluator.Result.Ok -> "= ${evaluation.value}"
                    is ExpressionEvaluator.Result.Error -> evaluation.message
                },
            )
        },
        trailingIcon = {
            if (game.working.isNotEmpty()) {
                IconButton(onClick = { game.working = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear working") }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OperatorKeys(game: NumbersGame) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("+", "−", "×", "÷", "(", ")").forEach { op ->
            FilledTonalIconButton(onClick = { game.append(op) }, shape = CircleShape) {
                Text(op, style = MaterialTheme.typography.titleLarge)
            }
        }
        IconButton(onClick = game::backspace) {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
        }
    }
}

@Composable
private fun PlayerNumbersResult(game: NumbersGame) {
    val target = game.target ?: return
    when (val result = game.playerResult) {
        null -> ResultBanner(Icons.Filled.Cancel, "No answer declared", "Target was $target", 0, positive = false)
        is ExpressionEvaluator.Result.Error -> ResultBanner(Icons.Filled.Cancel, "Working not valid", result.message, 0, positive = false)
        is ExpressionEvaluator.Result.Ok -> {
            val away = kotlin.math.abs(target - result.value)
            ResultBanner(
                icon = if (game.playerScore > 0) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                title = "You reached ${result.value}",
                detail = if (away == 0) "Spot on!" else "$away away from $target",
                points = game.playerScore,
                positive = game.playerScore > 0,
            )
        }
    }
}

@Composable
private fun SolutionCard(solution: NumbersSolution?) {
    if (solution == null) {
        SectionCard(title = "Solving…") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Text("Trying every combination")
            }
        }
        return
    }
    SectionCard(
        title = if (solution.exact) "How to make ${solution.target}" else "Closest possible: ${solution.value}",
        subtitle = when {
            solution.steps.isEmpty() -> "The target is one of the numbers"
            solution.exact -> "Exact solution in ${solution.steps.size} step${if (solution.steps.size == 1) "" else "s"}"
            else -> "No exact solution. ${solution.distance} away from ${solution.target}"
        },
        containerColor = if (solution.exact) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (solution.exact) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        solution.steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "${step.left} ${step.op.symbol} ${step.right} = ${step.result}",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        HorizontalDivider()
        Column {
            Text("As one sum", style = MaterialTheme.typography.labelMedium)
            Text(
                "${solution.expression} = ${solution.value}",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun NumbersDialog(initial: List<Int>, onDismiss: () -> Unit, onConfirm: (List<Int>) -> Unit) {
    var text by remember { mutableStateOf(initial.joinToString(" ")) }
    val parsed = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
    val error = NumberRules.validateNumbers(parsed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter six numbers") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("e.g. 75 50 2 3 8 7") },
                singleLine = true,
                isError = text.isNotBlank() && error != null,
                supportingText = { Text(error ?: "Looks good") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(parsed) }, enabled = error == null) { Text("Use numbers") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TargetDialog(initial: Int?, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }
    val value = text.toIntOrNull()
    val error = NumberRules.validateTarget(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set the target") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(4) },
                label = { Text("Target (101–999 on the show)") },
                singleLine = true,
                isError = text.isNotEmpty() && error != null,
                supportingText = { Text(error ?: " ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onConfirm) }, enabled = error == null) { Text("Set target") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
