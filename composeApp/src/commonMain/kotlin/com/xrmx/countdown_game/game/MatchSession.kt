package com.xrmx.countdown_game.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** A connection to the other phone that carries whole text messages, in order. */
interface MatchLink {
    /** Messages from the other phone. Closed when the connection drops. */
    val incoming: ReceiveChannel<String>

    /** Sends one message. Throws if the connection has dropped. */
    suspend fun send(message: String)

    fun close()
}

/** Two links joined in memory, for tests and screenshots. */
class LoopbackLink private constructor(
    private val inbox: Channel<String>,
    private val outbox: Channel<String>,
) : MatchLink {
    override val incoming: ReceiveChannel<String> get() = inbox

    override suspend fun send(message: String) = outbox.send(message)

    override fun close() {
        inbox.close()
        outbox.close()
    }

    companion object {
        fun pair(): Pair<LoopbackLink, LoopbackLink> {
            val a = Channel<String>(Channel.UNLIMITED)
            val b = Channel<String>(Channel.UNLIMITED)
            return LoopbackLink(a, b) to LoopbackLink(b, a)
        }
    }
}

/**
 * One phone's side of a two-player game. The host runs the [MatchEngine] and sends every new state
 * to the guest; the guest shows what it is sent and passes its actions to the host.
 */
class MatchSession private constructor(
    /** 0 on the host, 1 on the guest. */
    val me: Int,
    private val link: MatchLink,
    parent: CoroutineContext,
    private val compute: CoroutineContext,
) {
    private val scope = CoroutineScope(parent + SupervisorJob(parent[Job]))
    private val _state = MutableStateFlow<MatchState?>(null)
    private val _connected = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    /** The latest game state, or null until the game has started. */
    val state: StateFlow<MatchState?> = _state.asStateFlow()
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    private var engine: MatchEngine? = null
    private val engineLock = Mutex()

    // The host only needs its latest state to reach the guest; the guest's actions must all arrive.
    private val outgoing = Channel<String>(if (me == 0) Channel.CONFLATED else Channel.UNLIMITED)
    private var timer: Job? = null
    private var timedClock = 0

    init {
        scope.launch {
            try {
                for (message in outgoing) link.send(message)
            } catch (e: Exception) {
                disconnected()
            }
        }
    }

    /** Does [action] for this phone's player. */
    fun act(action: MatchAction) {
        if (me == 0) {
            scope.launch { applyOnHost(0, action) }
        } else {
            outgoing.trySend(MatchCodec.encode(MatchMessage.Action(action)))
        }
    }

    fun close() {
        link.close()
        scope.cancel()
        _connected.value = false
    }

    private fun startHost(rounds: List<RoundType>, rules: MatchRules, name: String, dictionary: WordDictionary, random: kotlin.random.Random) {
        scope.launch {
            for (text in link.incoming) {
                when (val message = MatchCodec.decode(text)) {
                    is MatchMessage.Hello -> {
                        if (message.version != MatchCodec.VERSION) {
                            val problem = "The other phone has a different version of the app. Update both phones to the same version."
                            link.send(MatchCodec.encode(MatchMessage.Error(problem)))
                            _error.value = problem
                            break
                        }
                        if (engine == null) {
                            val guestName = message.name.trim().ifEmpty { "Player 2" }
                            engine = MatchEngine(rounds, rules, listOf(name, guestName), dictionary, random)
                        }
                        publish()
                    }
                    is MatchMessage.Action -> applyOnHost(1, message.action)
                    is MatchMessage.Error -> _error.value = message.message
                    else -> Unit
                }
            }
            disconnected()
        }
    }

    private fun startGuest(name: String) {
        outgoing.trySend(MatchCodec.encode(MatchMessage.Hello(MatchCodec.VERSION, name)))
        scope.launch {
            for (text in link.incoming) {
                when (val message = MatchCodec.decode(text)) {
                    is MatchMessage.State -> _state.value = message.state
                    is MatchMessage.Error -> _error.value = message.message
                    else -> Unit
                }
            }
            disconnected()
        }
    }

    private suspend fun applyOnHost(player: Int, action: MatchAction) {
        val changed = engineLock.withLock {
            val e = engine ?: return
            // Choosing a graded target can take a moment, so keep it off the main thread.
            withContext(compute) { e.apply(player, action) }
        }
        if (changed) publish()
    }

    private fun publish() {
        val s = engine?.state ?: return
        _state.value = s
        outgoing.trySend(MatchCodec.encode(MatchMessage.State(s)))
        // A backstop for the clock: if an answer never arrives, end the round a little after time is up.
        val seconds = s.rules.clockSeconds
        if (s.stage == MatchStage.Playing && seconds != null && s.clock != timedClock) {
            timedClock = s.clock
            timer?.cancel()
            val clock = s.clock
            timer = scope.launch {
                delay(seconds * 1000L + CLOCK_GRACE_MS)
                if (engine?.state?.let { it.clock == clock && it.stage == MatchStage.Playing } == true) {
                    applyOnHost(0, MatchAction.TimeUp)
                }
            }
        }
    }

    private fun disconnected() {
        _connected.value = false
    }

    companion object {
        /** How long after the clock runs out the host waits for the guest's answer. */
        const val CLOCK_GRACE_MS = 3_000L

        fun host(
            link: MatchLink,
            rounds: List<RoundType>,
            rules: MatchRules,
            name: String,
            dictionary: WordDictionary,
            context: CoroutineContext = Dispatchers.Main,
            compute: CoroutineContext = Dispatchers.Default,
            random: kotlin.random.Random = kotlin.random.Random.Default,
        ): MatchSession = MatchSession(0, link, context, compute).also {
            it.startHost(rounds, rules, name.trim().ifEmpty { "Player 1" }, dictionary, random)
        }

        fun guest(
            link: MatchLink,
            name: String,
            context: CoroutineContext = Dispatchers.Main,
        ): MatchSession = MatchSession(1, link, context, context).also { it.startGuest(name.trim().ifEmpty { "Player 2" }) }
    }
}
