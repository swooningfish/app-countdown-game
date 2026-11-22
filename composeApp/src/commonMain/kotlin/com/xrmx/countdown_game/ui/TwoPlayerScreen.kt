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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xrmx.countdown_game.game.LetterRules
import com.xrmx.countdown_game.game.MatchAction
import com.xrmx.countdown_game.game.MatchFormat
import com.xrmx.countdown_game.game.MatchSession
import com.xrmx.countdown_game.game.MatchStage
import com.xrmx.countdown_game.game.MatchState
import com.xrmx.countdown_game.game.NumberRules
import com.xrmx.countdown_game.game.NumberSolver
import com.xrmx.countdown_game.game.NumbersSolution
import com.xrmx.countdown_game.game.RoundType
import com.xrmx.countdown_game.game.RunningOrder
import com.xrmx.countdown_game.game.WordDictionary
import com.xrmx.countdown_game.game.Working
import com.xrmx.countdown_game.platform.BluetoothGames
import com.xrmx.countdown_game.platform.FoundHost
import com.xrmx.countdown_game.platform.bluetoothGames
import com.xrmx.countdown_game.platform.rememberBluetoothPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class TwoPlayerStep { Setup, Hosting, Joining, Match }

/** The two-player tab: setting up, finding the other phone, and the game itself. */
class TwoPlayerState(
    /** Null where Bluetooth games aren't available. */
    val games: BluetoothGames? = bluetoothGames(),
) {
    var step by mutableStateOf(TwoPlayerStep.Setup)
    var rounds by mutableStateOf(MatchFormat.Full.rounds)
    var session by mutableStateOf<MatchSession?>(null)
    var hosts by mutableStateOf<List<FoundHost>>(emptyList())
    var connectingTo by mutableStateOf<FoundHost?>(null)
    var problem by mutableStateOf<String?>(null)

    /** True while trying to get back in touch with the other phone after the connection dropped. */
    var reconnecting by mutableStateOf(false)
        private set

    /** Set after trying to reconnect for [RECONNECT_TIMEOUT_MS] without success. */
    var reconnectFailed by mutableStateOf(false)
        private set

    /** How to reach the other phone again: the name this phone hosts under, or the host it joined. */
    private var hostedAs: String? = null
    private var joined: FoundHost? = null
    private var watchJob: Job? = null
    private var reconnectJob: Job? = null

    /** What this player is typing in the current round. */
    var input by mutableStateOf("")
    private var inputRound by mutableIntStateOf(-1)
    private var clockNumber = -1

    /** The clock number whose time has run out on this phone. */
    var timeUpClock by mutableIntStateOf(-1)
    private var clockMark: TimeMark? = null

    private var job: Job? = null
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    /** Clears [input] when a new round's clock starts. */
    fun syncRound(state: MatchState) {
        val key = state.roundIndex * 1000 + state.clock
        if (key != inputRound) {
            inputRound = key
            input = ""
        }
    }

    /** When this phone first saw clock number [clock] start. */
    fun clockStart(clock: Int): TimeMark {
        if (clock != clockNumber || clockMark == null) {
            clockNumber = clock
            clockMark = TimeSource.Monotonic.markNow()
        }
        return clockMark!!
    }

    fun host(games: BluetoothGames, name: String, start: (link: com.xrmx.countdown_game.game.MatchLink) -> MatchSession) {
        cancelJob()
        problem = null
        step = TwoPlayerStep.Hosting
        job = scope.launch {
            try {
                val link = games.host(name)
                val started = start(link)
                session = started
                hostedAs = name
                joined = null
                step = TwoPlayerStep.Match
                watch(games, started)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = e.message ?: "Couldn't host the game"
                step = TwoPlayerStep.Setup
            }
        }
    }

    fun scan(games: BluetoothGames) {
        cancelJob()
        problem = null
        hosts = emptyList()
        connectingTo = null
        step = TwoPlayerStep.Joining
        job = scope.launch {
            try {
                games.scan().collect { hosts = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = e.message ?: "Couldn't look for games"
                step = TwoPlayerStep.Setup
            }
        }
    }

    fun join(games: BluetoothGames, host: FoundHost, name: String) {
        cancelJob()
        connectingTo = host
        job = scope.launch {
            try {
                val link = games.join(host)
                val started = MatchSession.guest(link, name)
                session = started
                joined = host
                hostedAs = null
                step = TwoPlayerStep.Match
                watch(games, started)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = e.message ?: "Couldn't connect to ${host.name}"
                connectingTo = null
                step = TwoPlayerStep.Setup
            }
        }
    }

    /** Reconnects whenever the connection drops, unless the game is over or can't go on. */
    private fun watch(games: BluetoothGames, s: MatchSession) {
        watchJob?.cancel()
        watchJob = scope.launch {
            s.connected.collect { ok ->
                if (!ok && s.error.value == null && s.state.value?.stage != MatchStage.Finished) {
                    reconnect(games, s)
                }
            }
        }
    }

    /**
     * Tries to get back in touch with the other phone: the host advertises the game again, and the
     * guest looks for the same host and joins it. Gives up after [RECONNECT_TIMEOUT_MS].
     */
    private suspend fun reconnect(games: BluetoothGames, s: MatchSession) {
        reconnecting = true
        reconnectFailed = false
        try {
            val back = withTimeoutOrNull(RECONNECT_TIMEOUT_MS) {
                while (true) {
                    val link = try {
                        hostedAs?.let { games.host(it) } ?: joined?.let { rejoin(games, it) } ?: return@withTimeoutOrNull false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Bluetooth may be off for a moment, or the other phone not back yet.
                        delay(RETRY_DELAY_MS)
                        continue
                    }
                    s.attach(link)
                    // The host is only back once the guest says hello; give up on a phone that doesn't.
                    if (withTimeoutOrNull(HELLO_TIMEOUT_MS) { s.connected.first { it } } != null) return@withTimeoutOrNull true
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
            reconnectFailed = back != true
        } finally {
            reconnecting = false
        }
    }

    /** Finds the host this phone joined before, by its Bluetooth id or else its name, and joins it again. */
    private suspend fun rejoin(games: BluetoothGames, host: FoundHost): com.xrmx.countdown_game.game.MatchLink {
        val found = games.scan()
            .mapNotNull { list -> list.firstOrNull { it.id == host.id } ?: list.firstOrNull { it.name == host.name } }
            .first()
        joined = found
        return games.join(found)
    }

    /** Tries to reconnect again after giving up. */
    fun retryReconnect() {
        val s = session ?: return
        val games = games ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch { reconnect(games, s) }
    }

    fun leave() {
        cancelJob()
        watchJob?.cancel()
        watchJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        reconnecting = false
        reconnectFailed = false
        hostedAs = null
        joined = null
        session?.close()
        session = null
        hosts = emptyList()
        connectingTo = null
        input = ""
        step = TwoPlayerStep.Setup
    }

    private fun cancelJob() {
        job?.cancel()
        job = null
    }

    companion object {
        /** How long to keep trying to reconnect before asking the player. */
        const val RECONNECT_TIMEOUT_MS = 60_000L
        private const val RETRY_DELAY_MS = 2_000L
        private const val HELLO_TIMEOUT_MS = 10_000L
    }

    /** Ends any game and stops all background work. Call when the app is closing. */
    fun dispose() {
        leave()
        scope.cancel()
    }
}

@Composable
fun TwoPlayerScreen(
    state: TwoPlayerState,
    settings: Settings,
    dictionary: WordDictionary?,
    modifier: Modifier = Modifier,
) {
    val games = state.games
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askPermission = rememberBluetoothPermission { granted ->
        if (granted) {
            val problem = games?.problem()
            if (problem != null) state.problem = problem else pending?.invoke()
        } else {
            state.problem = "Allow Bluetooth access to play on two phones."
        }
        pending = null
    }
    val withBluetooth: (() -> Unit) -> Unit = { action ->
        pending = action
        askPermission()
    }

    val session = state.session
    when {
        state.step == TwoPlayerStep.Match && session != null -> MatchView(session, state, dictionary, modifier)
        state.step == TwoPlayerStep.Hosting -> WaitingForGuest(state, modifier)
        state.step == TwoPlayerStep.Joining && games != null -> FindGame(state, games, settings, modifier)
        else -> MatchSetup(
            state = state,
            settings = settings,
            bluetoothAvailable = games != null,
            canHost = dictionary != null,
            onHost = {
                if (games != null && dictionary != null) {
                    withBluetooth {
                        val rules = settings.matchRules()
                        val rounds = state.rounds
                        val name = settings.playerName
                        state.host(games, hostName(name)) { link -> MatchSession.host(link, rounds, rules, name, dictionary) }
                    }
                }
            },
            onJoin = { if (games != null) withBluetooth { state.scan(games) } },
            modifier = modifier,
        )
    }
}

private fun hostName(name: String) = name.trim().ifEmpty { "Player 1" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchSetup(
    state: TwoPlayerState,
    settings: Settings,
    bluetoothAvailable: Boolean,
    canHost: Boolean,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier,
) {
    ScreenColumn(modifier) {
        SectionCard(title = "Two players", subtitle = "Play a friend, each on your own phone, over Bluetooth") {
            OutlinedTextField(
                value = settings.playerName,
                onValueChange = { settings.playerName = it.replace("\n", "").take(Settings.MAX_NAME) },
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!bluetoothAvailable) {
                Notice(Icons.Filled.Warning, "Two-player games need the Android or iPhone app.")
            }
            state.problem?.let { Notice(Icons.Filled.Warning, it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onHost, enabled = bluetoothAvailable && canHost, modifier = Modifier.weight(1f), contentPadding = PairedButtonPadding) {
                    Icon(Icons.Filled.Bluetooth, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Host a game")
                }
                FilledTonalButton(onClick = onJoin, enabled = bluetoothAvailable, modifier = Modifier.weight(1f), contentPadding = PairedButtonPadding) {
                    Icon(Icons.Filled.BluetoothSearching, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Join a game")
                }
            }
        }

        val rounds = state.rounds
        SectionCard(
            title = "Running order",
            subtitle = "${rounds.size} rounds, ending with the conundrum. The host's choice and settings are used.",
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MatchFormat.entries.forEach { format ->
                    FilterChip(
                        selected = rounds == format.rounds,
                        onClick = { state.rounds = format.rounds },
                        label = { Text(format.label) },
                    )
                }
            }
            Column {
                rounds.forEachIndexed { index, type ->
                    RoundLine(
                        index = index,
                        type = type,
                        canMoveUp = index > 0 && type != RoundType.Conundrum,
                        canRemove = type != RoundType.Conundrum && rounds.size > 2,
                        onMoveUp = { state.rounds = RunningOrder.moveUp(rounds, index) },
                        onRemove = { state.rounds = RunningOrder.remove(rounds, index) },
                    )
                }
            }
            val full = rounds.size >= RunningOrder.MAX_ROUNDS
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { state.rounds = RunningOrder.add(rounds, RoundType.Letters) }, enabled = !full, modifier = Modifier.weight(1f), contentPadding = PairedButtonPadding) {
                    Icon(Icons.Filled.Add, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(4.dp))
                    Text("Letters")
                }
                OutlinedButton(onClick = { state.rounds = RunningOrder.add(rounds, RoundType.Numbers) }, enabled = !full, modifier = Modifier.weight(1f), contentPadding = PairedButtonPadding) {
                    Icon(Icons.Filled.Add, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(4.dp))
                    Text("Numbers")
                }
            }
        }
        HowToPlayCard(
            "Players take turns choosing the letters and numbers, starting with the host. You both play the same " +
                "selection against the same clock. In the letters, the longer word scores; in the numbers, the closer " +
                "answer scores; level answers both score. In the conundrum, the first right answer wins 10 points, and " +
                "a wrong answer means you can't try again.",
        )
    }
}

@Composable
private fun RoundLine(index: Int, type: RoundType, canMoveUp: Boolean, canRemove: Boolean, onMoveUp: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${index + 1}.",
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(type.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (type != RoundType.Conundrum) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move round ${index + 1} up") }
            IconButton(onClick = onRemove, enabled = canRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove round ${index + 1}") }
        }
    }
}

@Composable
private fun WaitingForGuest(state: TwoPlayerState, modifier: Modifier) {
    ScreenColumn(modifier) {
        SectionCard(title = "Waiting for a player", subtitle = "Ask the other player to open Two players and tap Join a game") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Text("Hosting over Bluetooth…")
            }
            OutlinedButton(onClick = state::leave, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
private fun FindGame(state: TwoPlayerState, games: BluetoothGames, settings: Settings, modifier: Modifier) {
    ScreenColumn(modifier) {
        val connecting = state.connectingTo
        SectionCard(
            title = if (connecting != null) "Joining ${connecting.name}…" else "Games nearby",
            subtitle = if (connecting != null) null else "Make sure the other phone has tapped Host a game",
        ) {
            if (connecting != null || state.hosts.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(if (connecting != null) "Connecting…" else "Looking for games…")
                }
            }
            if (connecting == null) {
                state.hosts.forEach { host ->
                    Surface(
                        onClick = { state.join(games, host, settings.playerName) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        ListItem(
                            headlineContent = { Text(host.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Tap to join") },
                            leadingContent = { Icon(Icons.Filled.PhoneAndroid, null) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    }
                }
            }
            OutlinedButton(onClick = state::leave, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
fun MatchView(session: MatchSession, state: TwoPlayerState, dictionary: WordDictionary?, modifier: Modifier = Modifier) {
    val match by session.state.collectAsState()
    val connected by session.connected.collectAsState()
    val error by session.error.collectAsState()
    val me = session.me
    ScreenColumn(modifier) {
        val s = match
        if (s == null) {
            SectionCard(title = "Starting the game…") {
                if (connected && error == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                error?.let { Notice(Icons.Filled.Warning, it) }
                if (!connected) ConnectionLost(if (me == 0) "the other phone" else "the host", state, canRetry = error == null)
                else LeaveButton(state)
            }
            return@ScreenColumn
        }
        state.syncRound(s)
        Scoreboard(s, me)
        error?.let { Notice(Icons.Filled.Warning, it) }
        if (!connected && s.stage != MatchStage.Finished) {
            ConnectionLost(s.names[s.opponent(me)], state, canRetry = error == null)
        }
        s.notice?.takeIf { s.noticeFor == me || s.noticeFor == -1 }?.let { Notice(Icons.Filled.Warning, it) }
        when (s.stage) {
            MatchStage.Picking -> PickingView(s, me, session, state)
            MatchStage.Playing -> PlayingView(s, me, session, state)
            MatchStage.Revealed -> RevealedView(s, me, session, dictionary)
            MatchStage.Finished -> FinishedView(s, me, state)
        }
    }
}

/** Shown while the other phone can't be reached: reconnecting, or a choice once that has failed. */
@Composable
private fun ConnectionLost(opponent: String, state: TwoPlayerState, canRetry: Boolean) {
    if (state.reconnecting) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                "Lost touch with $opponent. Reconnecting… Keep both phones close with Bluetooth on.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        Notice(Icons.Filled.Warning, "The connection to $opponent was lost.")
        if (canRetry && state.reconnectFailed) {
            Button(onClick = state::retryReconnect, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        }
    }
    LeaveButton(state)
}

@Composable
private fun Scoreboard(s: MatchState, me: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..1).forEach { p ->
                val mine = p == me
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            s.names[p] + if (mine) " (you)" else "",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Text("${s.scores[p]}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        val round = s.round
        if (s.stage != MatchStage.Finished && round != null) {
            Text(
                "Round ${s.roundIndex + 1} of ${s.rounds.size} · ${round.label}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PickingView(s: MatchState, me: Int, session: MatchSession, state: TwoPlayerState) {
    val myPick = s.picker == me
    val pickerName = s.names[s.picker]
    when (s.round) {
        RoundType.Letters -> SectionCard(
            title = if (myPick) "Choose the letters" else "$pickerName is choosing",
            subtitle = "${LetterRules.vowelCount(s.letters)} vowels · ${LetterRules.consonantCount(s.letters)} consonants",
        ) {
            LetterRow(s.letters, slots = LetterRules.TOTAL)
            if (myPick) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { session.act(MatchAction.Vowel) },
                        enabled = LetterRules.canPickVowel(s.letters, s.rules.enforceLetterRule),
                        modifier = Modifier.weight(1f),
                        contentPadding = PairedButtonPadding,
                    ) { Text("Vowel") }
                    FilledTonalButton(
                        onClick = { session.act(MatchAction.Consonant) },
                        enabled = LetterRules.canPickConsonant(s.letters, s.rules.enforceLetterRule),
                        modifier = Modifier.weight(1f),
                        contentPadding = PairedButtonPadding,
                    ) { Text("Consonant") }
                }
            }
        }

        RoundType.Numbers -> SectionCard(
            title = if (myPick) "Choose the numbers" else "$pickerName is choosing",
            subtitle = if (myPick) "How many large ones?" else null,
        ) {
            TargetDisplay(null, Modifier.align(Alignment.CenterHorizontally))
            NumberRow(emptyList(), slots = NumberRules.COUNT)
            if (myPick) SelectionChips { session.act(MatchAction.Numbers(it)) }
        }

        RoundType.Conundrum -> SectionCard(
            title = "Countdown conundrum",
            subtitle = "Either player can start it. If you're watching the show, type in its conundrum.",
        ) {
            LetterRow("", slots = LetterRules.TOTAL)
            Button(onClick = { session.act(MatchAction.SuggestConundrum) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Extension, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Suggest a conundrum")
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = { input -> state.input = input.uppercase().filter { it in 'A'..'Z' }.take(LetterRules.TOTAL) },
                label = { Text("Known conundrum") },
                supportingText = { Text("The nine scrambled letters") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { session.act(MatchAction.KnownConundrum(state.input)) },
                enabled = state.input.length == LetterRules.TOTAL,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Use these letters") }
        }

        null -> Unit
    }
}

@Composable
private fun PlayingView(s: MatchState, me: Int, session: MatchSession, state: TwoPlayerState) {
    val opponent = s.opponent(me)
    val opponentName = s.names[opponent]
    val myAnswer = s.answers[me]
    val conundrum = s.round == RoundType.Conundrum
    // On the show nobody can buzz in once the conundrum clock has stopped.
    val canAnswer = if (conundrum) !s.lockedOut[me] && state.timeUpClock != s.clock else myAnswer == null
    val declare = { if (canAnswer) session.act(MatchAction.Declare(state.input)) }
    val clockStart = s.rules.clockSeconds?.let { state.clockStart(s.clock) }

    SectionCard(title = if (s.round == RoundType.Numbers) "Reach the target" else if (conundrum) "Unscramble the conundrum" else "Find the longest word") {
        when (s.round) {
            RoundType.Numbers -> {
                TargetDisplay(s.target, Modifier.align(Alignment.CenterHorizontally))
                NumberRow(
                    numbers = s.numbers,
                    onTileClick = if (canAnswer) { i -> state.input = Working.append(state.input, s.numbers[i].toString()) } else null,
                    isTileEnabled = { !canAnswer || Working.isTileFree(state.input, s.numbers, it) },
                )
            }
            else -> {
                val letters = if (conundrum) s.conundrum else s.letters
                LetterRow(
                    letters = letters,
                    onTileClick = if (canAnswer) { i ->
                        val ch = letters[i]
                        if (state.input.count { it == ch } < letters.count { it == ch } && state.input.length < LetterRules.TOTAL) state.input += ch
                    } else null,
                    isTileEnabled = { !canAnswer || LetterRules.isTileFree(state.input, letters, it) },
                )
            }
        }
        if (clockStart != null) {
            CountdownClock(
                start = clockStart,
                seconds = s.rules.clockSeconds ?: Settings.DEFAULT_CLOCK_SECONDS,
                // When time is up, declare whatever has been typed. A conundrum needs a buzz, so it just ends.
                onTimeUp = {
                    state.timeUpClock = s.clock
                    if (!conundrum && myAnswer == null) session.act(MatchAction.Declare(state.input))
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    SectionCard(title = if (conundrum) "Your answer" else "Your ${if (s.round == RoundType.Numbers) "working" else "word"}") {
        if (canAnswer) {
            if (s.round == RoundType.Numbers) {
                WorkingField(state.input, s.numbers, onChange = { state.input = it }, onDone = declare)
                OperatorKeys(onKey = { state.input = Working.append(state.input, it) }, onBackspace = { state.input = Working.backspace(state.input) })
            } else {
                WordEntryField(
                    value = state.input,
                    onValueChange = { state.input = it },
                    letters = if (conundrum) s.conundrum else s.letters,
                    onDone = declare,
                    label = if (conundrum) "The nine-letter word" else "Your longest word",
                )
            }
            Button(onClick = declare, modifier = Modifier.fillMaxWidth()) {
                Icon(if (conundrum) Icons.Filled.NotificationsActive else Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(if (conundrum) "Buzz in" else "Declare")
            }
        } else if (conundrum && !s.lockedOut[me]) {
            Text("Time's up. Revealing the answer…", style = MaterialTheme.typography.bodyLarge)
        } else if (conundrum) {
            Notice(Icons.Filled.Cancel, "${myAnswer.orEmpty().ifEmpty { "That" }} isn't right. $opponentName can still answer.")
        } else {
            Text(
                if (myAnswer.isNullOrEmpty()) "You declared no answer." else "You declared $myAnswer.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            when {
                conundrum && s.lockedOut[opponent] -> "$opponentName got it wrong."
                conundrum -> "$opponentName is thinking…"
                s.answers[opponent] != null -> "$opponentName has declared."
                else -> "$opponentName is still thinking…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RevealedView(s: MatchState, me: Int, session: MatchSession, dictionary: WordDictionary?) {
    (listOf(me, s.opponent(me))).forEach { p ->
        val answer = s.answers[p].orEmpty()
        ResultBanner(
            icon = if (s.points[p] > 0) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            title = s.names[p] + ": " + answer.ifEmpty { "–" },
            detail = s.verdicts[p],
            points = s.points[p],
            positive = s.points[p] > 0,
        )
    }

    when (s.round) {
        RoundType.Letters -> {
            val solution = remember(s.letters, dictionary) { dictionary?.solve(s.letters, limit = 12) }
            if (solution != null) {
                SectionCard(
                    title = "Best words",
                    subtitle = if (solution.maxLength > 0) "The longest possible was ${solution.maxLength} letters" else "No words found",
                ) {
                    LetterRow(s.letters)
                    WordGroups(solution.longestWords)
                }
            }
        }
        RoundType.Numbers -> {
            val target = s.target
            if (target != null) {
                val solution by produceState<NumbersSolution?>(null, s.numbers, target) {
                    value = withContext(Dispatchers.Default) { NumberSolver.solve(s.numbers, target) }
                }
                SolutionCard(solution)
            }
        }
        RoundType.Conundrum -> SectionCard(
            title = "The answer",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            s.solutions.forEach {
                LetterRow(it, highlight = true)
                MeaningButton(it)
            }
        }
        null -> Unit
    }

    val last = s.roundIndex == s.rounds.lastIndex
    val waiting = s.ready[me]
    Button(onClick = { session.act(MatchAction.Next) }, enabled = !waiting, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            when {
                waiting -> "Waiting for ${s.names[s.opponent(me)]}…"
                last -> "See the final scores"
                else -> "Next round"
            },
        )
    }
}

@Composable
private fun FinishedView(s: MatchState, me: Int, state: TwoPlayerState) {
    val leader = s.leader
    ResultBanner(
        icon = Icons.Filled.EmojiEvents,
        title = when (leader) {
            null -> "It's a draw!"
            me -> "You win!"
            else -> "${s.names[leader]} wins"
        },
        detail = "${s.names[0]} ${s.scores[0]} – ${s.scores[1]} ${s.names[1]}",
        points = s.scores[me],
        positive = leader == null || leader == me,
    )
    SectionCard(title = "Round by round") {
        s.history.forEachIndexed { i, points ->
            Row(Modifier.fillMaxWidth()) {
                Text("${i + 1}. ${s.rounds.getOrNull(i)?.label.orEmpty()}", modifier = Modifier.weight(1f))
                Text("${points[0]} – ${points[1]}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    LeaveButton(state)
}

@Composable
private fun LeaveButton(state: TwoPlayerState) {
    OutlinedButton(onClick = state::leave, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("Leave game")
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
