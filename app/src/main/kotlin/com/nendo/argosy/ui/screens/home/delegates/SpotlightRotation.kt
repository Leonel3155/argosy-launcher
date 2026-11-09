package com.nendo.argosy.ui.screens.home.delegates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The spotlight's countdown: [onTurn] runs every [intervalMs] between [restart] and [stop].
 * [advance] runs it at once and, while the countdown is on, starts the countdown over, so a card
 * turned by hand stays up for a full interval.
 */
class SpotlightRotation(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val onTurn: () -> Unit
) {
    private var countdown: Job? = null

    fun restart() {
        countdown?.cancel()
        countdown = scope.launch {
            while (true) {
                delay(intervalMs)
                onTurn()
            }
        }
    }

    fun stop() {
        countdown?.cancel()
        countdown = null
    }

    fun advance() {
        onTurn()
        if (countdown != null) restart()
    }
}
