package com.xrmx.countdown_game.ui

import com.xrmx.countdown_game.game.TargetDifficulty
import com.xrmx.countdown_game.game.WordCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            alertSeconds = 10
            alertVibrate = false
            alertBeep = true
        }
        val loaded = Settings().apply { decode(saved.encode()) }
        assertEquals(saved.encode(), loaded.encode())
        assertFalse(loaded.clockEnabled)
        assertEquals(TargetDifficulty.Hard, loaded.targetDifficulty)
        assertEquals(ClockAlerts(seconds = 10, vibrate = false, beep = true), loaded.clockAlerts)
    }

    @Test
    fun clockAlertsDefaultToVibratingForFiveSeconds() {
        val alerts = Settings().clockAlerts
        assertEquals(5, alerts.seconds)
        assertTrue(alerts.vibrate)
        assertFalse(alerts.beep)
        assertFalse(ClockAlerts(seconds = 0).enabled)
        assertFalse(ClockAlerts(vibrate = false, beep = false).enabled)
    }

    @Test
    fun settingsIgnoreBadValues() {
        val s = Settings().apply { decode("alertSeconds=7\nclockSeconds=17\ntargetDifficulty=Silly\nclockEnabled=maybe\nplayerName=" + "x".repeat(50)) }
        assertEquals(Settings.DEFAULT_CLOCK_SECONDS, s.clockSeconds)
        assertEquals(TargetDifficulty.Random, s.targetDifficulty)
        assertEquals(true, s.clockEnabled)
        assertEquals(Settings.MAX_NAME, s.playerName.length)
        assertEquals(Settings.DEFAULT_ALERT_SECONDS, s.alertSeconds)
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
