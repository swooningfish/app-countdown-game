package com.xrmx.countdown_game.ui

import com.xrmx.countdown_game.game.TargetDifficulty
import com.xrmx.countdown_game.game.WordCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SettingsAndSessionTest {
    @Test
    fun settingsRoundTrip() {
        val saved = Settings().apply {
            clockEnabled = false
            clockSeconds = 60
            enforceLetterRule = false
            targetDifficulty = TargetDifficulty.Hard
            nastyLargeNumbers = true
            playerName = "Ann"
        }
        val loaded = Settings().apply { decode(saved.encode()) }
        assertEquals(saved.encode(), loaded.encode())
        assertFalse(loaded.clockEnabled)
        assertEquals(TargetDifficulty.Hard, loaded.targetDifficulty)
    }

    @Test
    fun settingsIgnoreBadValues() {
        val s = Settings().apply { decode("clockSeconds=17\ntargetDifficulty=Silly\nclockEnabled=maybe\nplayerName=" + "x".repeat(50)) }
        assertEquals(Settings.DEFAULT_CLOCK_SECONDS, s.clockSeconds)
        assertEquals(TargetDifficulty.Random, s.targetDifficulty)
        assertEquals(true, s.clockEnabled)
        assertEquals(Settings.MAX_NAME, s.playerName.length)
    }

    @Test
    fun sessionKeepsScoreAndRecords() {
        val session = Session()
        assertNull(session.average)
        session.recordLetters(WordCheck.Valid("STONE"))
        session.recordLetters(WordCheck.Valid("RAT"))
        session.recordLetters(WordCheck.NotInDictionary("XYZZYQ"))
        session.recordNumbers(10)
        session.recordNumbers(7)
        session.recordConundrum(solved = true)
        assertEquals(5 + 3 + 0 + 10 + 7 + 10, session.score)
        assertEquals(6, session.rounds)
        assertEquals("STONE", session.bestWord)
        assertEquals(1, session.numbersSpotOn)
        assertEquals(1, session.conundrumsSolved)

        val loaded = Session().apply { decode(session.encode()) }
        assertEquals(session.encode(), loaded.encode())

        loaded.reset()
        assertEquals(0, loaded.score)
        assertEquals("", loaded.bestWord)
    }
}
