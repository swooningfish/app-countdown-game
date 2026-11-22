package com.xrmx.countdown_game.game

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchTest {
    private val dictionary = WordDictionary(
        listOf("rations", "ration", "aroints", "stone", "notes", "tones", "rat", "senoritas", "orientals", "relations"),
    )

    private fun engine(rounds: String = "LNC", rules: MatchRules = MatchRules()) =
        MatchEngine(RunningOrder.parse(rounds), rules, listOf("Host", "Guest"), dictionary, Random(1))

    private fun MatchEngine.pickLetters(player: Int) {
        repeat(3) { apply(player, MatchAction.Vowel) }
        repeat(6) { apply(player, MatchAction.Consonant) }
    }

    @Test
    fun runningOrderAlwaysEndsWithOneConundrum() {
        assertEquals("LLNC", RunningOrder.encode(RunningOrder.parse("LCLN")))
        assertEquals("LNLC", RunningOrder.encode(RunningOrder.add(RunningOrder.parse("LNC"), RoundType.Letters)))
        assertEquals("NC", RunningOrder.encode(RunningOrder.remove(RunningOrder.parse("LNC"), 0)))
        assertEquals("LNC", RunningOrder.encode(RunningOrder.remove(RunningOrder.parse("LNC"), 2)))
        assertEquals("NLC", RunningOrder.encode(RunningOrder.moveUp(RunningOrder.parse("LNC"), 1)))
        assertEquals("LNC", RunningOrder.encode(RunningOrder.moveUp(RunningOrder.parse("LNC"), 2)))
        assertEquals(15, MatchFormat.Full.rounds.size)
        assertEquals(10, MatchFormat.Full.rounds.count { it == RoundType.Letters })
        assertEquals(4, MatchFormat.Full.rounds.count { it == RoundType.Numbers })
    }

    @Test
    fun onlyThePickerChoosesLettersAndTheClockStartsWhenFull() {
        val e = engine()
        assertEquals(0, e.state.picker)
        assertFalse(e.apply(1, MatchAction.Vowel))
        e.pickLetters(0)
        assertEquals(9, e.state.letters.length)
        assertEquals(MatchStage.Playing, e.state.stage)
        assertEquals(1, e.state.clock)
    }

    @Test
    fun longerWordScoresAndEqualWordsBothScore() {
        val e = engine()
        e.pickLetters(0)
        // Force known letters through a fresh state isn't possible, so judge against what was dealt.
        val letters = e.state.letters
        val word = dictionary.wordsFrom(letters).firstOrNull()
        e.apply(0, MatchAction.Declare(word ?: ""))
        assertEquals(MatchStage.Playing, e.state.stage)
        e.apply(1, MatchAction.Declare(word ?: ""))
        assertEquals(MatchStage.Revealed, e.state.stage)
        assertEquals(e.state.points[0], e.state.points[1])
        assertEquals(e.state.points, e.state.scores)
    }

    @Test
    fun invalidWordScoresNothing() {
        val e = engine()
        e.pickLetters(0)
        e.apply(0, MatchAction.Declare("ZZZZ"))
        e.apply(1, MatchAction.Declare(""))
        assertEquals(listOf(0, 0), e.state.points)
        assertEquals("Uses letters that weren't picked", e.state.verdicts[0])
        assertEquals("No word", e.state.verdicts[1])
    }

    @Test
    fun bothPlayersMustPressNextAndPickingAlternates() {
        val e = engine()
        e.pickLetters(0)
        e.apply(0, MatchAction.TimeUp)
        assertEquals(MatchStage.Revealed, e.state.stage)
        e.apply(0, MatchAction.Next)
        assertEquals(MatchStage.Revealed, e.state.stage)
        e.apply(1, MatchAction.Next)
        assertEquals(1, e.state.roundIndex)
        assertEquals(MatchStage.Picking, e.state.stage)
        assertEquals(RoundType.Numbers, e.state.round)
        assertEquals(1, e.state.picker)
        assertFalse(e.apply(0, MatchAction.Numbers(NumberSelection.TwoLarge)))
    }

    @Test
    fun onlyTheHostCanEndTheClock() {
        val e = engine()
        e.pickLetters(0)
        assertFalse(e.apply(1, MatchAction.TimeUp))
        assertTrue(e.apply(0, MatchAction.TimeUp))
    }

    @Test
    fun closerNumbersAnswerScoresOnly() {
        val e = engine("NC")
        e.apply(0, MatchAction.Numbers(NumberSelection.FourLarge))
        val s = e.state
        assertEquals(6, s.numbers.size)
        assertEquals(4, s.numbers.count { it in NumberRules.LARGE })
        val target = assertNotNull(s.target)
        val solution = NumberSolver.solve(s.numbers, target)
        e.apply(0, MatchAction.Declare(solution.expression))
        e.apply(1, MatchAction.Declare(s.numbers.first().toString()))
        val r = e.state
        assertEquals(MatchStage.Revealed, r.stage)
        assertEquals(Scoring.numbers(target, solution.value), r.points[0])
        if (solution.distance < kotlin.math.abs(target - s.numbers.first())) assertEquals(0, r.points[1])
    }

    @Test
    fun knownConundrumMustBeAnAnagramInTheWordList() {
        val e = engine("C")
        e.apply(1, MatchAction.KnownConundrum("ABCDEFGHI"))
        assertEquals(MatchStage.Picking, e.state.stage)
        assertEquals(1, e.state.noticeFor)
        assertNotNull(e.state.notice)
        e.apply(1, MatchAction.KnownConundrum("toinesras"))
        assertEquals(MatchStage.Playing, e.state.stage)
        assertEquals("TOINESRAS", e.state.conundrum)
        assertEquals(listOf("SENORITAS"), e.state.solutions)
        assertNull(e.state.notice)
    }

    @Test
    fun wrongConundrumAnswerLocksThatPlayerOut() {
        val e = engine("C")
        e.apply(0, MatchAction.KnownConundrum("LATERIONS"))
        assertEquals(listOf("ORIENTALS", "RELATIONS"), e.state.solutions)
        e.apply(0, MatchAction.Declare("RATIONALS"))
        assertTrue(e.state.lockedOut[0])
        assertFalse(e.apply(0, MatchAction.Declare("RELATIONS")))
        e.apply(1, MatchAction.Declare("relations"))
        assertEquals(MatchStage.Revealed, e.state.stage)
        assertEquals(listOf(0, 10), e.state.points)
        e.apply(0, MatchAction.Next)
        e.apply(1, MatchAction.Next)
        assertEquals(MatchStage.Finished, e.state.stage)
        assertEquals(1, e.state.leader)
    }

    @Test
    fun codecRoundTripsStatesAndActions() {
        val e = engine()
        e.pickLetters(0)
        e.apply(0, MatchAction.Declare("RAT"))
        val state = e.state.copy(names = listOf("Ann\nLee", "B=\\o"), notice = "Hi", noticeFor = 1, history = listOf(listOf(3, 0)))
        assertEquals(MatchMessage.State(state), MatchCodec.decode(MatchCodec.encode(MatchMessage.State(state))))
        val actions = listOf(
            MatchAction.Vowel, MatchAction.Consonant, MatchAction.Numbers(NumberSelection.Motown),
            MatchAction.SuggestConundrum, MatchAction.KnownConundrum("ABC"), MatchAction.Declare("(100 + 6) × 3"),
            MatchAction.TimeUp, MatchAction.Next,
        )
        actions.forEach { assertEquals(MatchMessage.Action(it), MatchCodec.decode(MatchCodec.encode(MatchMessage.Action(it)))) }
        assertEquals(MatchMessage.Hello(1, "Zoë"), MatchCodec.decode(MatchCodec.encode(MatchMessage.Hello(1, "Zoë"))))
        assertNull(MatchCodec.decode("NONSENSE\nx=1"))
    }

    @Test
    fun framingSplitsAndJoinsMessages() {
        val message = "STATE\n" + "x".repeat(100) + "é×"
        val packets = PacketFraming.split(message, 20)
        assertTrue(packets.all { it.size <= 20 })
        val joiner = PacketFraming.Joiner()
        val results = packets.map { joiner.add(it) }
        assertEquals(message, results.last())
        assertTrue(results.dropLast(1).all { it == null })
        assertEquals("", joiner.add(PacketFraming.split("", 20).single()))
    }

    @Test
    fun sessionsPlayAGameOverALink() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (hostLink, guestLink) = LoopbackLink.pair()
        val host = MatchSession.host(hostLink, RunningOrder.parse("LC"), MatchRules(clockSeconds = null), "Ann", dictionary, dispatcher, dispatcher, Random(2))
        val guest = MatchSession.guest(guestLink, "Bob", dispatcher)
        advanceUntilIdle()
        assertEquals(listOf("Ann", "Bob"), guest.state.first { it != null }!!.names)

        repeat(4) { host.act(MatchAction.Vowel) }
        repeat(5) { host.act(MatchAction.Consonant) }
        advanceUntilIdle()
        assertEquals(MatchStage.Playing, guest.state.value!!.stage)
        assertEquals(host.state.value!!.letters, guest.state.value!!.letters)

        guest.act(MatchAction.Declare(""))
        host.act(MatchAction.Declare(""))
        advanceUntilIdle()
        assertEquals(MatchStage.Revealed, guest.state.value!!.stage)

        guest.act(MatchAction.Next)
        host.act(MatchAction.Next)
        advanceUntilIdle()
        assertEquals(RoundType.Conundrum, guest.state.value!!.round)

        guest.close()
        advanceUntilIdle()
        assertFalse(host.connected.value)
        host.close()
    }

    @Test
    fun hostEndsTheRoundIfTheGuestNeverAnswers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (hostLink, guestLink) = LoopbackLink.pair()
        val host = MatchSession.host(hostLink, RunningOrder.parse("C"), MatchRules(clockSeconds = 30), "Ann", dictionary, dispatcher, dispatcher)
        val guest = MatchSession.guest(guestLink, "Bob", dispatcher)
        advanceUntilIdle()
        host.act(MatchAction.KnownConundrum("TOINESRAS"))
        advanceUntilIdle()
        assertEquals(MatchStage.Revealed, guest.state.value!!.stage)
        assertEquals(listOf("No answer", "No answer"), guest.state.value!!.verdicts)
        host.close()
        guest.close()
    }

    /** A link whose sends always fail, as when a Bluetooth notification times out. */
    private class FailingLink : MatchLink {
        val inbox = Channel<String>(Channel.UNLIMITED)
        var closed = false
        override val incoming: ReceiveChannel<String> get() = inbox
        override suspend fun send(message: String) = error("Send failed")
        override fun close() {
            closed = true
            inbox.close()
        }
    }

    @Test
    fun guestIsNotSentAnswersWhileTheRoundIsPlayed() {
        val e = engine("C")
        e.apply(0, MatchAction.KnownConundrum("TOINESRAS"))
        e.apply(0, MatchAction.Declare("RATIONS"))
        val playing = e.state.forGuest()
        assertEquals(emptyList(), playing.solutions)
        assertEquals(listOf("", null), playing.answers)
        assertTrue(playing.lockedOut[0])

        e.apply(1, MatchAction.Declare("SENORITAS"))
        val revealed = e.state.forGuest()
        assertEquals(MatchStage.Revealed, revealed.stage)
        assertEquals(listOf("SENORITAS"), revealed.solutions)
        assertEquals(listOf("RATIONS", "SENORITAS"), revealed.answers)
    }

    @Test
    fun conundrumCantBeAnsweredOnceTheClockHasRunOut() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (hostLink, guestLink) = LoopbackLink.pair()
        val host = MatchSession.host(hostLink, RunningOrder.parse("C"), MatchRules(clockSeconds = 30), "Ann", dictionary, dispatcher, dispatcher)
        val guest = MatchSession.guest(guestLink, "Bob", dispatcher)
        advanceUntilIdle()
        host.act(MatchAction.KnownConundrum("TOINESRAS"))
        runCurrent()
        advanceTimeBy(30_000 + MatchSession.CONUNDRUM_GRACE_MS + 1)
        runCurrent()
        assertEquals(MatchStage.Revealed, host.state.value!!.stage)

        guest.act(MatchAction.Declare("SENORITAS"))
        runCurrent()
        assertEquals(listOf(0, 0), host.state.value!!.scores)
        host.close()
        guest.close()
    }

    @Test
    fun lettersAnswersStillArriveDuringTheGracePeriod() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (hostLink, guestLink) = LoopbackLink.pair()
        val host = MatchSession.host(hostLink, RunningOrder.parse("LC"), MatchRules(clockSeconds = 30), "Ann", dictionary, dispatcher, dispatcher)
        val guest = MatchSession.guest(guestLink, "Bob", dispatcher)
        advanceUntilIdle()
        repeat(4) { host.act(MatchAction.Vowel) }
        repeat(5) { host.act(MatchAction.Consonant) }
        runCurrent()
        advanceTimeBy(30_000 + MatchSession.CONUNDRUM_GRACE_MS + 1)
        runCurrent()
        assertEquals(MatchStage.Playing, host.state.value!!.stage)
        guest.act(MatchAction.Declare(""))
        runCurrent()
        assertEquals("", host.state.value!!.answers[1])
        host.close()
        guest.close()
    }

    @Test
    fun failedSendClosesTheLink() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val link = FailingLink()
        val guest = MatchSession.guest(link, "Bob", dispatcher)
        advanceUntilIdle()
        assertFalse(guest.connected.value)
        assertTrue(link.closed)
    }

    @Test
    fun differentAppVersionIsReportedWithoutCrashing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val link = FailingLink()
        val host = MatchSession.host(link, RunningOrder.parse("C"), MatchRules(), "Ann", dictionary, dispatcher, dispatcher)
        link.inbox.send(MatchCodec.encode(MatchMessage.Hello(MatchCodec.VERSION + 1, "Bob")))
        advanceUntilIdle()
        assertNotNull(host.error.value)
        assertFalse(host.connected.value)
        assertTrue(link.closed)
    }

    @Test
    fun longGuestNamesAreShortened() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (hostLink, guestLink) = LoopbackLink.pair()
        val host = MatchSession.host(hostLink, RunningOrder.parse("C"), MatchRules(), "Ann", dictionary, dispatcher, dispatcher)
        guestLink.send(MatchCodec.encode(MatchMessage.Hello(MatchCodec.VERSION, "B".repeat(500))))
        advanceUntilIdle()
        assertEquals(MatchSession.MAX_NAME, host.state.value!!.names[1].length)
        host.close()
    }

    @Test
    fun matchCarriesOnAfterTheConnectionDrops() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (hostLink, guestLink) = LoopbackLink.pair()
        val host = MatchSession.host(hostLink, RunningOrder.parse("LC"), MatchRules(clockSeconds = null), "Ann", dictionary, dispatcher, dispatcher, Random(2))
        val guest = MatchSession.guest(guestLink, "Bob", dispatcher)
        advanceUntilIdle()
        repeat(4) { host.act(MatchAction.Vowel) }
        repeat(5) { host.act(MatchAction.Consonant) }
        advanceUntilIdle()
        val matchId = guest.state.value!!.matchId
        assertTrue(matchId.isNotEmpty())

        // Bluetooth drops: both phones notice, and the guest declares while out of touch.
        hostLink.close()
        advanceUntilIdle()
        assertFalse(host.connected.value)
        assertFalse(guest.connected.value)
        guest.act(MatchAction.Declare("RAT"))
        advanceUntilIdle()

        val (hostLink2, guestLink2) = LoopbackLink.pair()
        host.attach(hostLink2)
        guest.attach(guestLink2)
        advanceUntilIdle()
        assertTrue(host.connected.value)
        assertTrue(guest.connected.value)
        // The declaration made while disconnected arrived, and the game is the same one.
        assertEquals("RAT", host.state.value!!.answers[1])
        assertEquals(matchId, host.state.value!!.matchId)
        assertEquals(host.state.value!!.letters, guest.state.value!!.letters)

        host.act(MatchAction.Declare(""))
        advanceUntilIdle()
        assertEquals(MatchStage.Revealed, guest.state.value!!.stage)
        host.close()
        guest.close()
    }

    @Test
    fun anotherPhoneCantTakeOverAGameInProgress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (hostLink, guestLink) = LoopbackLink.pair()
        val host = MatchSession.host(hostLink, RunningOrder.parse("LC"), MatchRules(clockSeconds = null), "Ann", dictionary, dispatcher, dispatcher)
        val guest = MatchSession.guest(guestLink, "Bob", dispatcher)
        advanceUntilIdle()
        hostLink.close()
        advanceUntilIdle()

        val (hostLink2, intruderLink) = LoopbackLink.pair()
        host.attach(hostLink2)
        val intruder = MatchSession.guest(intruderLink, "Eve", dispatcher)
        advanceUntilIdle()
        assertFalse(host.connected.value)
        assertNull(intruder.state.value)
        assertNotNull(intruder.error.value)
        assertEquals(listOf("Ann", "Bob"), host.state.value!!.names)
        host.close()
        guest.close()
        intruder.close()
    }

    @Test
    fun helloCarriesTheMatchId() {
        val hello = MatchMessage.Hello(MatchCodec.VERSION, "Bob", "abc123")
        assertEquals(hello, MatchCodec.decode(MatchCodec.encode(hello)))
        // A guest joining for the first time, or an older app, sends no match id.
        assertEquals("", (MatchCodec.decode("HELLO\nversion=1\nname=Bob") as MatchMessage.Hello).match)
    }
}
