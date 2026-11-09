package com.nendo.argosy.core.vault

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import java.io.File

/**
 * Combo-based lock for the vault profile, active only on AYN handhelds (Thor, Odin).
 *
 * The lock is entered with a button combo instead of a text PIN so it can be used on a controller
 * without a keyboard. While [armed] (the About settings screen is on screen), holding the
 * modifier button (L3) starts a capture: each button pressed while L3 is held is recorded, and
 * releasing L3 submits the recorded combo. A plain L3 press with nothing recorded is passed back
 * to the app as an ordinary click via [onModifierTap].
 *
 * The combo is matched against [DoorSecret], which stores only a salted hash. First run with no
 * secret stored sets one when the same combo is entered twice. Raw button codes are used, so the
 * combo is independent of the Nintendo/Xbox face-button layout.
 */
object VaultGuard {

    private const val MODIFIER = KeyEvent.KEYCODE_BUTTON_THUMBL

    enum class Result { IGNORED, CAPTURED, OPEN, CLOSE, SET }

    val isSupportedDevice: Boolean by lazy {
        listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE).any { field ->
            val v = field?.lowercase().orEmpty()
            v.contains("ayn") || v.contains("odin") || v.contains("thor")
        }
    }

    @Volatile
    var armed: Boolean = false

    private var secret: DoorSecret? = null
    private var capturing = false
    private val recorded = mutableListOf<Int>()
    private var pendingFirstEntry: List<Int>? = null

    private fun secret(): DoorSecret =
        secret ?: DoorSecret(File(VaultProfile.sharedDir(), "door")).also { secret = it }

    /**
     * Feeds one key event to the lock. Returns what happened; the caller acts on OPEN/CLOSE/SET
     * and swallows anything that is not IGNORED. [onModifierTap] replays a bare modifier tap.
     */
    fun onKey(event: KeyEvent, vaultOpen: Boolean, onModifierTap: () -> Unit): Result {
        if (!isSupportedDevice || !armed) return Result.IGNORED
        val down = event.action == KeyEvent.ACTION_DOWN

        if (event.keyCode == MODIFIER) {
            if (down && event.repeatCount == 0) {
                capturing = true
                recorded.clear()
                return Result.CAPTURED
            }
            if (!down && capturing) {
                capturing = false
                val combo = recorded.toList()
                recorded.clear()
                if (combo.isEmpty()) {
                    onModifierTap()
                    return Result.CAPTURED
                }
                return submit(combo, vaultOpen)
            }
            return Result.IGNORED
        }

        if (capturing) {
            if (down && event.repeatCount == 0) recorded.add(event.keyCode)
            return Result.CAPTURED
        }
        return Result.IGNORED
    }

    private fun submit(combo: List<Int>, vaultOpen: Boolean): Result {
        val store = secret()
        if (!store.exists()) {
            if (!store.isValidLength(combo)) return Result.CAPTURED
            val first = pendingFirstEntry
            return if (first == null) {
                pendingFirstEntry = combo
                Result.CAPTURED
            } else if (first == combo) {
                pendingFirstEntry = null
                store.save(combo)
                Result.SET
            } else {
                pendingFirstEntry = combo
                Result.CAPTURED
            }
        }
        if (store.matches(combo)) {
            pendingFirstEntry = null
            return if (vaultOpen) Result.CLOSE else Result.OPEN
        }
        // Inside the vault, a new combo entered twice in a row replaces the stored one; outside,
        // a wrong combo does nothing.
        if (vaultOpen && store.isValidLength(combo)) {
            val first = pendingFirstEntry
            return if (first == combo) {
                pendingFirstEntry = null
                store.save(combo)
                Result.SET
            } else {
                pendingFirstEntry = combo
                Result.CAPTURED
            }
        }
        return Result.CAPTURED
    }
}
