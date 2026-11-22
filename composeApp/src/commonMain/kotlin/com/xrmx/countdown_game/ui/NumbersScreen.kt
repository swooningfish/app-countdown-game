package com.xrmx.countdown_game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import com.xrmx.countdown_game.game.BoardCard
import com.xrmx.countdown_game.game.NumberBoard
import com.xrmx.countdown_game.game.NumberSelection
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xrmx.countdown_game.game.ExpressionEvaluator
import com.xrmx.countdown_game.game.NumberRules
import com.xrmx.countdown_game.game.NumbersSolution
import kotlinx.coroutines.launch

@Composable
fun NumbersScreen(game: NumbersGame, settings: Settings, session: Session, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var showNumbersDialog by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    val finish: (Session?) -> Unit = { s -> scope.launch { game.finish(s) } }
    val generateTarget: () -> Unit = { scope.launch { game.newTarget(settings.targetDifficulty) } }

    // An untouched board follows the large-numbers setting.
    LaunchedEffect(settings.nastyLargeNumbers) {
        if (game.phase == Phase.Picking && game.numbers.isEmpty()) game.resetBoard(settings)
    }

    ScreenColumn(modifier) {
        SectionCard(title = "Numbers round", subtitle = "Use + − × ÷ to reach the target. Each number once.") {
            TargetDisplay(game.target, Modifier.align(Alignment.CenterHorizontally))
            if (game.phase == Phase.Picking) {
                Text(
                    if (game.generatingTarget) {
                        "Choosing a ${settings.targetDifficulty.label.lowercase()} target…"
                    } else {
                        "Target difficulty: ${settings.targetDifficulty.label}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            NumberRow(
                numbers = game.numbers,
                slots = NumberRules.COUNT,
                onTileClick = if (game.phase == Phase.Playing) { index -> game.append(game.numbers[index].toString()) } else null,
                isTileEnabled = { game.phase != Phase.Playing || game.isTileFree(it) },
            )
            if (game.phase == Phase.Picking) {
                if (!game.isFull) {
                    Text("Pick six cards from the board", style = MaterialTheme.typography.labelLarge)
                    NumberBoardView(
                        board = game.board,
                        isPicked = game::isPicked,
                        onPick = { card -> if (game.pick(card) && game.target == null) generateTarget() },
                    )
                    Text("…or ask for a selection", style = MaterialTheme.typography.labelLarge)
                    SelectionChips { selection ->
                        game.pickSelection(selection, settings)
                        generateTarget()
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    TextButton(onClick = generateTarget, enabled = game.isFull && !game.generatingTarget) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(4.dp))
                        Text("New target")
                    }
                    TextButton(onClick = { showTargetDialog = true }) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(4.dp))
                        Text("Set target")
                    }
                    TextButton(onClick = { showNumbersDialog = true }) { Text("My numbers") }
                }
                if (game.numbers.isNotEmpty() || game.target != null) {
                    OutlinedButton(onClick = { game.resetBoard(settings) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Filled.Casino, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Shuffle a new board")
                    }
                }
            }
        }

        when (game.phase) {
            Phase.Picking -> if (game.isReady && !game.generatingTarget) {
                ReadyControls(
                    clockSeconds = settings.activeClockSeconds,
                    solveEnabled = true,
                    onStart = { game.start(settings.activeClockSeconds) },
                    onSolve = { finish(null) },
                )
            } else {
                HowToPlayCard(
                    "Turn over six cards. The top row holds the large numbers " +
                        (if (settings.nastyLargeNumbers) "(the nasty 12, 37, 62 and 87)" else "(25, 50, 75 and 100)") +
                        " and the rows below hold two each of 1 to 10. Or ask for a selection used on the show, or enter your own.",
                )
            }

            Phase.Playing -> SectionCard(title = "Your working") {
                game.clockStart?.let { start ->
                    CountdownClock(start = start, seconds = game.clockLimit, onTimeUp = { finish(session) }, modifier = Modifier.align(Alignment.CenterHorizontally))
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
                Button(onClick = { game.newRound(settings) }, modifier = Modifier.fillMaxWidth()) {
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
                if (game.target == null) generateTarget()
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

/** The face-down studio board: large numbers on top, small numbers in the three rows below. */
@Composable
private fun NumberBoardView(board: NumberBoard, isPicked: (BoardCard) -> Boolean, onPick: (BoardCard) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        board.rows.forEachIndexed { r, row ->
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val gap = 5.dp
                val width = ((maxWidth - gap * 6) / 7).coerceAtMost(46.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { c, value ->
                        val card = BoardCard(r, c)
                        val picked = isPicked(card)
                        val colors = MaterialTheme.colorScheme
                        Surface(
                            onClick = { onPick(card) },
                            enabled = !picked,
                            modifier = Modifier.size(width = width, height = width * 1.2f),
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                picked -> colors.surfaceVariant
                                r == 0 -> colors.secondary
                                else -> colors.primary
                            },
                            shadowElevation = if (picked) 0.dp else 2.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (picked) value.toString() else "?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (width.value * 0.36f).sp,
                                    color = when {
                                        picked -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                                        r == 0 -> colors.onSecondary
                                        else -> colors.onPrimary
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionChips(onSelect: (NumberSelection) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberSelection.entries.forEach { selection ->
            AssistChip(onClick = { onSelect(selection) }, label = { Text(selection.label) })
        }
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
