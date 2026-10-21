package com.nendo.argosy.core.vault

import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores the unlock combo as a salted PBKDF2 hash, so the raw combo is never written to disk and
 * cannot be recovered from a backup of the app. The combo is a list of button codes; [matches]
 * recomputes the hash of a candidate and compares in constant time.
 *
 * The file lives in the shared no_backup dir (readable in both profiles, never backed up). A
 * length of 6 to 16 entries is required so a combo is neither trivial nor impossible to enter.
 */
internal class DoorSecret(private val file: File) {

    data class Stored(val salt: ByteArray, val hash: ByteArray)

    fun exists(): Boolean = file.exists()

    fun isValidLength(combo: List<Int>): Boolean = combo.size in 6..16

    fun save(combo: List<Int>) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(combo, salt)
        file.writeBytes(salt + hash)
    }

    fun matches(combo: List<Int>): Boolean {
        val stored = read() ?: return false
        val candidate = hash(combo, stored.salt)
        return constantTimeEquals(candidate, stored.hash)
    }

    fun clear() {
        file.delete()
    }

    private fun read(): Stored? {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.size <= 16) return null
        return Stored(bytes.copyOfRange(0, 16), bytes.copyOfRange(16, bytes.size))
    }

    private fun hash(combo: List<Int>, salt: ByteArray): ByteArray {
        val chars = combo.joinToString(",") { it.toString() }.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    companion object {
        private const val ITERATIONS = 120_000
    }
}
