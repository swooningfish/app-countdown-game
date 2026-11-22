package com.xrmx.countdown_game.game

/** Messages sent between the two phones in a two-player game. */
sealed interface MatchMessage {
    /**
     * The guest's first message after connecting. [match] is the id of the game it is coming back
     * to after a dropped connection, or empty when joining for the first time.
     */
    data class Hello(val version: Int, val name: String, val match: String = "") : MatchMessage

    /** The host sends the whole game state after every change. */
    data class State(val state: MatchState) : MatchMessage

    /** Something the guest did. */
    data class Action(val action: MatchAction) : MatchMessage

    /** Sent before closing the connection when the game can't go on. */
    data class Error(val message: String) : MatchMessage
}

/**
 * Plain-text encoding of [MatchMessage]: a type line followed by key=value lines. Small enough to
 * send over Bluetooth Low Energy in a few packets.
 */
object MatchCodec {
    const val VERSION = 1

    fun encode(message: MatchMessage): String = when (message) {
        is MatchMessage.Hello -> lines("HELLO", "version" to message.version.toString(), "name" to message.name, "match" to message.match)
        is MatchMessage.Error -> lines("ERROR", "message" to message.message)
        is MatchMessage.Action -> lines("ACTION", *encodeAction(message.action).toTypedArray())
        is MatchMessage.State -> lines("STATE", *encodeState(message.state).toTypedArray())
    }

    /** Returns null for anything it doesn't understand. */
    fun decode(text: String): MatchMessage? = runCatching {
        val all = text.split('\n')
        val values = all.drop(1).mapNotNull { line ->
            val eq = line.indexOf('=')
            if (eq < 0) null else line.substring(0, eq) to unescape(line.substring(eq + 1))
        }.toMap()
        when (all.first()) {
            "HELLO" -> MatchMessage.Hello(values.getValue("version").toInt(), values["name"].orEmpty(), values["match"].orEmpty())
            "ERROR" -> MatchMessage.Error(values["message"].orEmpty())
            "ACTION" -> decodeAction(values)?.let { MatchMessage.Action(it) }
            "STATE" -> MatchMessage.State(decodeState(values))
            else -> null
        }
    }.getOrNull()

    private fun encodeAction(action: MatchAction): List<Pair<String, String>> = when (action) {
        MatchAction.Vowel -> listOf("type" to "vowel")
        MatchAction.Consonant -> listOf("type" to "consonant")
        is MatchAction.Numbers -> listOf("type" to "numbers", "selection" to action.selection.name)
        MatchAction.SuggestConundrum -> listOf("type" to "suggest")
        is MatchAction.KnownConundrum -> listOf("type" to "known", "letters" to action.letters)
        is MatchAction.Declare -> listOf("type" to "declare", "answer" to action.answer)
        MatchAction.TimeUp -> listOf("type" to "timeup")
        MatchAction.Next -> listOf("type" to "next")
    }

    private fun decodeAction(v: Map<String, String>): MatchAction? = when (v["type"]) {
        "vowel" -> MatchAction.Vowel
        "consonant" -> MatchAction.Consonant
        "numbers" -> NumberSelection.entries.firstOrNull { it.name == v["selection"] }?.let { MatchAction.Numbers(it) }
        "suggest" -> MatchAction.SuggestConundrum
        "known" -> MatchAction.KnownConundrum(v["letters"].orEmpty())
        "declare" -> MatchAction.Declare(v["answer"].orEmpty())
        "timeup" -> MatchAction.TimeUp
        "next" -> MatchAction.Next
        else -> null
    }

    private fun encodeState(s: MatchState): List<Pair<String, String>> = buildList {
        add("match" to s.matchId)
        add("rounds" to RunningOrder.encode(s.rounds))
        add("clockSeconds" to (s.rules.clockSeconds?.toString() ?: "off"))
        add("letterRule" to s.rules.enforceLetterRule.toString())
        add("difficulty" to s.rules.targetDifficulty.name)
        add("nasty" to s.rules.nastyLargeNumbers.toString())
        add("name0" to s.names[0])
        add("name1" to s.names[1])
        add("scores" to s.scores.joinToString(","))
        add("round" to s.roundIndex.toString())
        add("stage" to s.stage.name)
        add("letters" to s.letters)
        add("numbers" to s.numbers.joinToString(","))
        s.target?.let { add("target" to it.toString()) }
        add("conundrum" to s.conundrum)
        add("solutions" to s.solutions.joinToString(","))
        s.answers.forEachIndexed { i, a -> if (a != null) add("answer$i" to a) }
        s.verdicts.forEachIndexed { i, v -> add("verdict$i" to v) }
        add("points" to s.points.joinToString(","))
        add("locked" to s.lockedOut.joinToString(","))
        add("ready" to s.ready.joinToString(","))
        add("clock" to s.clock.toString())
        add("history" to s.history.joinToString(";") { it.joinToString(",") })
        s.notice?.let { add("notice" to it) }
        add("noticeFor" to s.noticeFor.toString())
    }

    private fun decodeState(v: Map<String, String>): MatchState {
        fun ints(key: String) = v[key].orEmpty().split(',').filter { it.isNotEmpty() }.map { it.toInt() }
        fun pair(key: String) = ints(key).also { require(it.size == 2) { "$key needs two values" } }
        fun bools(key: String) = v[key].orEmpty().split(',').map { it.toBooleanStrict() }.also { require(it.size == 2) }
        return MatchState(
            rounds = RunningOrder.parse(v.getValue("rounds")),
            rules = MatchRules(
                clockSeconds = v["clockSeconds"]?.toIntOrNull(),
                enforceLetterRule = v["letterRule"]?.toBooleanStrictOrNull() ?: true,
                targetDifficulty = TargetDifficulty.entries.firstOrNull { it.name == v["difficulty"] } ?: TargetDifficulty.Random,
                nastyLargeNumbers = v["nasty"]?.toBooleanStrictOrNull() ?: false,
            ),
            names = listOf(v["name0"].orEmpty(), v["name1"].orEmpty()),
            scores = pair("scores"),
            roundIndex = v.getValue("round").toInt(),
            stage = MatchStage.valueOf(v.getValue("stage")),
            letters = v["letters"].orEmpty(),
            numbers = ints("numbers"),
            target = v["target"]?.toInt(),
            conundrum = v["conundrum"].orEmpty(),
            solutions = v["solutions"].orEmpty().split(',').filter { it.isNotEmpty() },
            answers = listOf(v["answer0"], v["answer1"]),
            verdicts = listOf(v["verdict0"].orEmpty(), v["verdict1"].orEmpty()),
            points = pair("points"),
            lockedOut = bools("locked"),
            ready = bools("ready"),
            clock = v["clock"]?.toInt() ?: 0,
            history = v["history"].orEmpty().split(';').filter { it.isNotEmpty() }.map { round ->
                round.split(',').map { it.toInt() }.also { require(it.size == 2) }
            },
            notice = v["notice"],
            noticeFor = v["noticeFor"]?.toInt() ?: -1,
            matchId = v["match"].orEmpty(),
        )
    }

    private fun lines(type: String, vararg values: Pair<String, String>): String =
        (listOf(type) + values.map { (k, v) -> "$k=${escape(v)}" }).joinToString("\n")

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "")

    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                sb.append(if (value[i + 1] == 'n') '\n' else value[i + 1])
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

/**
 * Splits messages into packets small enough for one Bluetooth LE write or notification, and joins
 * them back together. Each packet starts with a flag byte: 1 if more packets follow, 0 for the last.
 */
object PacketFraming {
    const val MORE: Byte = 1
    const val LAST: Byte = 0

    /** [packetSize] is the largest packet the link can carry, including the flag byte. */
    fun split(message: String, packetSize: Int): List<ByteArray> {
        require(packetSize >= 2) { "Packets must hold at least one byte of data" }
        val bytes = message.encodeToByteArray()
        val chunk = packetSize - 1
        if (bytes.isEmpty()) return listOf(byteArrayOf(LAST))
        return (bytes.indices step chunk).map { start ->
            val end = minOf(start + chunk, bytes.size)
            byteArrayOf(if (end < bytes.size) MORE else LAST) + bytes.copyOfRange(start, end)
        }
    }

    /** Collects packets for one direction of a link. */
    class Joiner {
        private var buffer = ByteArray(0)
        private var overflowed = false

        /**
         * Adds a packet and returns the message if it was the last one. A message longer than
         * [MAX_MESSAGE_BYTES] is dropped, so the other phone can't make this one run out of memory.
         */
        fun add(packet: ByteArray): String? {
            if (packet.isEmpty()) return null
            if (!overflowed) {
                buffer += packet.copyOfRange(1, packet.size)
                if (buffer.size > MAX_MESSAGE_BYTES) {
                    buffer = ByteArray(0)
                    overflowed = true
                }
            }
            if (packet[0] == MORE) return null
            if (overflowed) {
                overflowed = false
                return null
            }
            val message = buffer.decodeToString()
            buffer = ByteArray(0)
            return message
        }
    }

    /** Far more than any real message, which is a few hundred bytes. */
    const val MAX_MESSAGE_BYTES = 16_384
}
