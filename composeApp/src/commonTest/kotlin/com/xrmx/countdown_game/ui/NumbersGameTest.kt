package com.xrmx.countdown_game.ui

import com.xrmx.countdown_game.game.NumberSelection
import com.xrmx.countdown_game.game.TargetDifficulty
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NumbersGameTest {
    @Test
    fun targetForOldNumbersIsDroppedWhenTheBoardIsReshuffled() = runTest {
        val settings = Settings()
        val game = NumbersGame()
        game.pickSelection(NumberSelection.TwoLarge, settings)
        val job = launch { game.newTarget(TargetDifficulty.Hard) }
        runCurrent()
        game.resetBoard(settings)
        job.join()
        assertNull(game.target)
        assertFalse(game.generatingTarget)
    }

    @Test
    fun typedTargetWinsOverOneStillBeingChosen() = runTest {
        val settings = Settings()
        val game = NumbersGame()
        game.pickSelection(NumberSelection.TwoLarge, settings)
        val job = launch { game.newTarget(TargetDifficulty.Hard) }
        runCurrent()
        game.setOwnTarget(123)
        job.join()
        assertEquals(123, game.target)
    }

    @Test
    fun targetIsChosenWhenNothingChanges() = runTest {
        val game = NumbersGame()
        game.pickSelection(NumberSelection.TwoLarge, Settings())
        game.newTarget(TargetDifficulty.Random)
        assertNotNull(game.target)
        assertFalse(game.generatingTarget)
    }
}
