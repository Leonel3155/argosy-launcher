package com.nendo.argosy.ui.screens.home.delegates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpotlightRotationTest {

    private val interval = 6_000L
    private var turns = 0

    private fun TestScope.rotation() = SpotlightRotation(backgroundScope, interval) { turns++ }

    @Test
    fun `turns once per interval while running`() = runTest {
        val rotation = rotation()

        rotation.restart()
        advanceTimeBy(interval - 1)
        runCurrent()
        assertEquals(0, turns)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, turns)

        advanceTimeBy(interval * 2)
        runCurrent()
        assertEquals(3, turns)
    }

    @Test
    fun `stop halts the turning`() = runTest {
        val rotation = rotation()

        rotation.restart()
        advanceTimeBy(interval)
        runCurrent()
        rotation.stop()
        advanceTimeBy(interval * 3)
        runCurrent()

        assertEquals(1, turns)
    }

    @Test
    fun `a turn by hand happens at once and gets a full interval on screen`() = runTest {
        val rotation = rotation()

        rotation.restart()
        advanceTimeBy(interval - 1_000)
        runCurrent()
        rotation.advance()
        assertEquals(1, turns)

        advanceTimeBy(interval - 1)
        runCurrent()
        assertEquals(1, turns)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, turns)
    }

    @Test
    fun `a turn by hand while stopped does not start the countdown`() = runTest {
        val rotation = rotation()

        rotation.advance()
        advanceTimeBy(interval * 3)
        runCurrent()

        assertEquals(1, turns)
    }
}
