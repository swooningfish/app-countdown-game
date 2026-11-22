package com.xrmx.countdown_game.game

import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.first
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
 *
 * If the connection drops the game carries on here: [connected] turns false, and the app can
 * [attach] a new connection to the same phone to pick up where it left off.
 */
class MatchSession private constructor(
    /** 0 on the host, 1 on the guest. */
    val me: Int,
    private var link: MatchLink,
    private val name: String,
    parent: CoroutineContext,
    private val compute: CoroutineContext,
) {
    private val scope = CoroutineScope(parent + SupervisorJob(parent[Job]))
    private val _state = MutableStateFlow<MatchState?>(null)
    private val _connected = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    /** The latest game state, or null until the game has started. */
    val state: StateFlow<MatchState?> = _state.asStateFlow()

    /** Whether the other phone can be reached right now. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    private var engine: MatchEngine? = null
    private val engineLock = Mutex()
    private var closed = false

    // The host only needs its latest state to reach the guest; the guest's actions must all arrive.
    private val outgoing = Channel<String>(if (me == 0) Channel.CONFLATED else Channel.UNLIMITED)
    private var timer: Job? = null
    private var timedClock = 0

    init {
        scope.launch { sendLoop() }
    }

    /**
     * Sends queued messages whenever connected. A guest's message that couldn't be sent is kept and
     * sent again after reconnecting; the host just sends its latest state when the guest is back.
     */
    private suspend fun sendLoop() {
        var pending: String? = null
        while (true) {
            val message = pending ?: outgoing.receiveCatching().getOrNull() ?: return
            pending = message
            _connected.first { it }
            val target = link
            try {
                target.send(message)
                pending = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (me == 0) pending = null
                linkLost(target)
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

    /**
     * Carries on the game over [newLink], a fresh connection to the other phone after the last one
     * dropped. The host only counts as connected once the guest has shown it is the same player.
     */
    fun attach(newLink: MatchLink) {
        if (closed) {
            newLink.close()
            return
        }
        link.close()
        link = newLink
        startReading(newLink)
        if (me == 1) greetHost(newLink, onSent = { _connected.value = true })
    }

    fun close() {
        closed = true
        link.close()
        scope.cancel()
        _connected.value = false
    }

    private fun startReading(l: MatchLink) {
        scope.launch {
            for (text in l.incoming) {
                val message = MatchCodec.decode(text) ?: continue
                if (me == 0) onHostMessage(l, message) else onGuestMessage(message)
            }
            linkLost(l)
        }
    }

    private suspend fun onHostMessage(l: MatchLink, message: MatchMessage) {
        when (message) {
            is MatchMessage.Hello -> {
                if (message.version != MatchCodec.VERSION) {
                    val problem = "The other phone has a different version of the app. Update both phones to the same version."
                    _error.value = problem
                    // The guest may already have gone, so a failed send mustn't end the app.
                    runCatching { l.send(MatchCodec.encode(MatchMessage.Error(problem))) }
                    linkLost(l)
                    return
                }
                val existing = engine
                if (existing != null && !isSameGuest(existing.state, message)) {
                    // Another phone found this game while waiting for the guest to come back.
                    runCatching { l.send(MatchCodec.encode(MatchMessage.Error("This game is already being played."))) }
                    linkLost(l)
                    return
                }
                if (existing == null) {
                    val guestName = message.name.trim().take(MAX_NAME).ifEmpty { "Player 2" }
                    engine = MatchEngine(rounds, rules, listOf(name, guestName), dictionary!!, random, newMatchId(random))
                }
                _connected.value = true
                publish()
            }
            is MatchMessage.Action -> applyOnHost(1, message.action)
            is MatchMessage.Error -> _error.value = message.message
            else -> Unit
        }
    }

    /** The returning phone knows this game's id, or is the same player before it was told the id. */
    private fun isSameGuest(s: MatchState, hello: MatchMessage.Hello): Boolean =
        if (hello.match.isNotEmpty()) hello.match == s.matchId else hello.name.trim().take(MAX_NAME) == s.names[1]

    private fun onGuestMessage(message: MatchMessage) {
        when (message) {
            is MatchMessage.State -> _state.value = message.state
            is MatchMessage.Error -> _error.value = message.message
            else -> Unit
        }
    }

    private fun greetHost(l: MatchLink, onSent: () -> Unit = {}) {
        val hello = MatchCodec.encode(MatchMessage.Hello(MatchCodec.VERSION, name, _state.value?.matchId.orEmpty()))
        scope.launch {
            try {
                l.send(hello)
                if (l === link) onSent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                linkLost(l)
            }
        }
    }

    // Host-only settings for the engine, set by [host].
    private var rounds: List<RoundType> = emptyList()
    private var rules: MatchRules = MatchRules()
    private var dictionary: WordDictionary? = null
    private var random: kotlin.random.Random = kotlin.random.Random.Default

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
        outgoing.trySend(MatchCodec.encode(MatchMessage.State(s.forGuest())))
        // A backstop for the clock: if an answer never arrives, end the round a little after time is up.
        val seconds = s.rules.clockSeconds
        if (s.stage == MatchStage.Playing && seconds != null && s.clock != timedClock) {
            timedClock = s.clock
            timer?.cancel()
            val clock = s.clock
            timer = scope.launch {
                val grace = if (s.round == RoundType.Conundrum) CONUNDRUM_GRACE_MS else CLOCK_GRACE_MS
                delay(seconds * 1000L + grace)
                if (engine?.state?.let { it.clock == clock && it.stage == MatchStage.Playing } == true) {
                    applyOnHost(0, MatchAction.TimeUp)
                }
            }
        }
    }

    /**
     * Called when [l] stops working. Closes it, so a half-dead connection doesn't leave the other
     * phone waiting. Ignored for a connection that has already been replaced.
     */
    private fun linkLost(l: MatchLink) {
        l.close()
        if (l === link) _connected.value = false
    }

    companion object {
        /** How long after the clock runs out the host waits for the guest's answer. */
        const val CLOCK_GRACE_MS = 3_000L

        /**
         * How long after the clock runs out a conundrum buzz may still arrive. Only enough to cover
         * the Bluetooth delay, so nobody can answer once their clock shows time is up.
         */
        const val CONUNDRUM_GRACE_MS = 750L

        /** Longest player name accepted from the other phone. */
        const val MAX_NAME = 20

        private const val ID_CHARS = "abcdefghijkmnpqrstuvwxyz23456789"

        fun newMatchId(random: kotlin.random.Random): String = String(CharArray(10) { ID_CHARS[random.nextInt(ID_CHARS.length)] })

        fun host(
            link: MatchLink,
            rounds: List<RoundType>,
            rules: MatchRules,
            name: String,
            dictionary: WordDictionary,
            context: CoroutineContext = Dispatchers.Main,
            compute: CoroutineContext = Dispatchers.Default,
            random: kotlin.random.Random = kotlin.random.Random.Default,
        ): MatchSession = MatchSession(0, link, name.trim().ifEmpty { "Player 1" }, context, compute).also {
            it.rounds = rounds
            it.rules = rules
            it.dictionary = dictionary
            it.random = random
            it.startReading(link)
        }

        fun guest(
            link: MatchLink,
            name: String,
            context: CoroutineContext = Dispatchers.Main,
        ): MatchSession = MatchSession(1, link, name.trim().ifEmpty { "Player 2" }, context, context).also {
            it.startReading(link)
            it.greetHost(link)
        }
    }
}
