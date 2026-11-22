package com.xrmx.countdown_game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.time.TimeMark

/** Scrollable, centred column that stays readable on tablets. */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                }
            }
            content()
        }
    }
}

/** A row of square letter tiles; empty slots are drawn as outlines. */
@Composable
fun LetterRow(
    letters: String,
    modifier: Modifier = Modifier,
    slots: Int = letters.length,
    highlight: Boolean = false,
    onTileClick: ((Int) -> Unit)? = null,
    isTileEnabled: (Int) -> Boolean = { true },
) {
    TileRow(slots, maxTile = 56.dp, modifier = modifier) { index, size ->
        val ch = letters.getOrNull(index)
        Tile(
            text = ch?.toString(),
            size = size,
            height = size,
            highlight = highlight,
            enabled = ch != null && isTileEnabled(index),
            onClick = if (ch != null && onTileClick != null) ({ onTileClick(index) }) else null,
            fontSizeFraction = 0.55f,
        )
    }
}

/** A row of number cards, as dealt on the board. */
@Composable
fun NumberRow(
    numbers: List<Int>,
    modifier: Modifier = Modifier,
    slots: Int = numbers.size,
    onTileClick: ((Int) -> Unit)? = null,
    isTileEnabled: (Int) -> Boolean = { true },
) {
    TileRow(slots, maxTile = 88.dp, modifier = modifier) { index, size ->
        val n = numbers.getOrNull(index)
        Tile(
            text = n?.toString(),
            size = size,
            height = size * 0.8f,
            highlight = n != null && n >= 25,
            enabled = n != null && isTileEnabled(index),
            onClick = if (n != null && onTileClick != null) ({ onTileClick(index) }) else null,
            fontSizeFraction = 0.4f,
        )
    }
}

/** Tighter padding for two buttons sharing a row, so their labels stay on one line with larger text sizes. */
val PairedButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Composable
private fun TileRow(
    slots: Int,
    maxTile: Dp,
    modifier: Modifier = Modifier,
    tile: @Composable (index: Int, size: Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val gap = 6.dp
        val size = ((maxWidth - gap * (slots - 1)) / slots).coerceAtMost(maxTile)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(slots) { tile(it, size) }
        }
    }
}

@Composable
private fun Tile(
    text: String?,
    size: Dp,
    height: Dp,
    highlight: Boolean,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    fontSizeFraction: Float,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val modifier = Modifier.size(width = size, height = height)
    if (text == null) {
        Surface(modifier, shape = shape, color = colors.surfaceContainerHighest, border = BorderStroke(1.dp, colors.outlineVariant)) {}
        return
    }
    val container = when {
        !enabled -> colors.surfaceVariant
        highlight -> colors.secondaryContainer
        else -> colors.primary
    }
    val content = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.5f)
        highlight -> colors.onSecondaryContainer
        else -> colors.onPrimary
    }
    val inner: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                fontSize = (size.value * fontSizeFraction).sp,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
            )
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, modifier = modifier, shape = shape, color = container, shadowElevation = 2.dp, content = inner)
    } else {
        Surface(modifier, shape = shape, color = container, shadowElevation = 2.dp, content = inner)
    }
}

/** The target number, shown on a dark panel like the studio display. */
@Composable
fun TargetDisplay(target: Int?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
    ) {
        Text(
            text = target?.toString()?.padStart(3, ' ') ?: "– – –",
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            color = Color(0xFFFFC233),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The 30-second clock: a hand sweeps from 12 to 6 o'clock while the elapsed time fills in behind it.
 */
@Composable
fun CountdownClock(
    start: TimeMark,
    onTimeUp: () -> Unit,
    modifier: Modifier = Modifier,
    seconds: Int = 30,
    diameter: Dp = 132.dp,
) {
    val totalMs = seconds * 1000L
    val latestOnTimeUp by rememberUpdatedState(onTimeUp)
    var elapsedMs by remember(start) { mutableLongStateOf(start.elapsedNow().inWholeMilliseconds.coerceAtMost(totalMs)) }
    LaunchedEffect(start) {
        while (true) {
            elapsedMs = start.elapsedNow().inWholeMilliseconds.coerceAtMost(totalMs)
            if (elapsedMs >= totalMs) {
                latestOnTimeUp()
                break
            }
            withFrameMillis { }
        }
    }
    val fraction = elapsedMs.toFloat() / totalMs
    val face = MaterialTheme.colorScheme.surfaceContainerHighest
    val rim = MaterialTheme.colorScheme.primary
    val sweep = Color(0xFFFFC233).copy(alpha = 0.75f)
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val hand = MaterialTheme.colorScheme.onSurface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(diameter)) {
            val radius = min(size.width, size.height) / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(face, radius, centre)
            // The show's clock only uses the right-hand half: 30 seconds from 12 to 6.
            drawArc(
                color = sweep,
                startAngle = -90f,
                sweepAngle = 180f * fraction,
                useCenter = true,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = Size(radius * 2, radius * 2),
            )
            for (i in 0 until 12) {
                val angle = (i * 30 - 90) * PI / 180
                val outer = Offset(centre.x + cos(angle).toFloat() * radius * 0.92f, centre.y + sin(angle).toFloat() * radius * 0.92f)
                val inner = Offset(centre.x + cos(angle).toFloat() * radius * 0.78f, centre.y + sin(angle).toFloat() * radius * 0.78f)
                drawLine(tick, inner, outer, strokeWidth = if (i % 3 == 0) 4f else 2f, cap = StrokeCap.Round)
            }
            val handAngle = (-90 + 180 * fraction) * PI / 180
            drawLine(
                hand,
                centre,
                Offset(centre.x + cos(handAngle).toFloat() * radius * 0.85f, centre.y + sin(handAngle).toFloat() * radius * 0.85f),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
            drawCircle(hand, radius * 0.06f, centre)
            drawCircle(rim, radius, centre, style = Stroke(width = radius * 0.08f))
        }
        Spacer(Modifier.height(4.dp))
        val remaining = ((totalMs - elapsedMs + 999) / 1000).toInt()
        Text(
            if (remaining > 0) "$remaining s" else "Time's up!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun PointsBadge(points: Int, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondary) {
        Text(
            "+$points",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondary,
            fontWeight = FontWeight.Bold,
        )
    }
}
