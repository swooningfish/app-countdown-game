package com.swooningfish.countdown.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.swooningfish.countdown.game.ConundrumGenerator
import com.swooningfish.countdown.game.LetterRules
import com.swooningfish.countdown.game.Scoring

@Composable
fun ConundrumScreen(
    game: ConundrumGame,
    generator: ConundrumGenerator?,
    settings: Settings,
    session: Session,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        SectionCard(title = "Countdown conundrum", subtitle = "Unscramble the nine letters into one word") {
            LetterRow(
                letters = game.puzzle?.scrambled.takeIf { game.phase != Phase.Picking } ?: "",
                slots = LetterRules.TOTAL,
                onTileClick = if (game.phase == Phase.Playing) game::appendFromTile else null,
            )
        }

        when (game.phase) {
            Phase.Picking -> {
                HowToPlayCard("Solve the nine-letter anagram before the clock runs out. A correct answer scores ${Scoring.CONUNDRUM} points.")
                NewConundrumButton(enabled = generator != null) { generator?.let { game.start(it, settings.clockEnabled) } }
            }

            Phase.Playing -> SectionCard(title = "Your answer") {
                game.clockStart?.let { start ->
                    CountdownClock(start = start, onTimeUp = { game.finish(session) }, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                WordEntryField(
                    value = game.guess,
                    onValueChange = { game.guess = it },
                    letters = game.puzzle?.scrambled ?: "",
                    onDone = { game.finish(session) },
                    label = "The nine-letter word",
                )
                Button(onClick = { game.finish(session) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(if (game.guess.isEmpty()) "Reveal" else "Declare")
                }
            }

            Phase.Results -> {
                val answer = game.puzzle?.answer ?: ""
                if (game.solved) {
                    ResultBanner(Icons.Filled.CheckCircle, "Correct!", "You unscrambled it", Scoring.CONUNDRUM, positive = true)
                } else {
                    ResultBanner(
                        Icons.Filled.Cancel,
                        if (game.guess.isEmpty()) "No answer" else game.guess,
                        "Not this time",
                        0,
                        positive = false,
                    )
                }
                SectionCard(
                    title = "The answer",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    LetterRow(answer, highlight = true)
                }
                NewConundrumButton(enabled = generator != null) { generator?.let { game.start(it, settings.clockEnabled) } }
            }
        }
    }
}

@Composable
private fun NewConundrumButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Extension, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(if (enabled) "New conundrum" else "Loading dictionary…")
    }
}
