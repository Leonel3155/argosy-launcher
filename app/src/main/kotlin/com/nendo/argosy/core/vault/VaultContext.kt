package com.nendo.argosy.core.vault

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.view.Display
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Points every private storage location of [base] at the vault tree, so the vault profile runs on
 * its own database, preferences, files and caches while the code above it stays unaware.
 *
 * Internal storage lives under no_backup/vault, which Android never backs up. Shared preferences
 * cannot be moved out of shared_prefs, so they are renamed with a prefix instead. External storage
 * moves to Android/data/<package>/v with a .nomedia marker so nothing reaches the gallery.
 *
 * Contexts derived through the create* calls are wrapped again; a derived context that escaped the
 * wrapper would read and write the normal profile.
 */
internal class VaultContext(base: Context, private val root: File) : ContextWrapper(base) {

    private fun dir(name: String): File = File(root, name).apply { mkdirs() }

    override fun getFilesDir(): File = dir("files")

    override fun getCacheDir(): File = dir("cache")

    override fun getNoBackupFilesDir(): File = dir("no_backup")

    override fun getDir(name: String, mode: Int): File = dir("app_$name")

    override fun getDatabasePath(name: String): File {
        if (name.startsWith(File.separator)) return File(name)
        return File(dir("databases"), name)
    }

    override fun databaseList(): Array<String> = dir("databases").list() ?: emptyArray()

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?
    ): SQLiteDatabase = super.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory)

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?
    ): SQLiteDatabase =
        super.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory, errorHandler)

    override fun deleteDatabase(name: String): Boolean =
        super.deleteDatabase(getDatabasePath(name).absolutePath)

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences(PREFS_PREFIX + name, mode)

    override fun deleteSharedPreferences(name: String): Boolean =
        super.deleteSharedPreferences(PREFS_PREFIX + name)

    override fun getFileStreamPath(name: String): File = File(filesDir, name)

    override fun fileList(): Array<String> = filesDir.list() ?: emptyArray()

    override fun deleteFile(name: String): Boolean = File(filesDir, name).delete()

    override fun openFileInput(name: String): FileInputStream {
        val file = File(filesDir, name)
        if (!file.exists()) throw FileNotFoundException(file.absolutePath)
        return FileInputStream(file)
    }

    override fun openFileOutput(name: String, mode: Int): FileOutputStream =
        FileOutputStream(File(filesDir, name), (mode and MODE_APPEND) != 0)

    override fun getExternalFilesDir(type: String?): File? =
        externalRoot(super.getExternalFilesDir(null))?.let { vaultRoot ->
            val files = File(vaultRoot, "files")
            (if (type == null) files else File(files, type)).apply { mkdirs() }
        }

    override fun getExternalCacheDir(): File? =
        externalRoot(super.getExternalFilesDir(null))?.let { File(it, "cache").apply { mkdirs() } }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
        VaultContext(super.createConfigurationContext(overrideConfiguration), root)

    override fun createDisplayContext(display: Display): Context =
        VaultContext(super.createDisplayContext(display), root)

    override fun createWindowContext(type: Int, options: android.os.Bundle?): Context =
        VaultContext(super.createWindowContext(type, options), root)

    override fun createAttributionContext(attributionTag: String?): Context =
        VaultContext(super.createAttributionContext(attributionTag), root)

    /**
     * Android/data/<package>/v for the volume that owns [appFilesDir], with .nomedia written once.
     */
    private fun externalRoot(appFilesDir: File?): File? {
        val packageDir = appFilesDir?.parentFile ?: return null
        val vaultRoot = File(packageDir, "v")
        if (!vaultRoot.exists()) vaultRoot.mkdirs()
        val noMedia = File(vaultRoot, ".nomedia")
        if (!noMedia.exists()) runCatching { noMedia.createNewFile() }
        return vaultRoot
    }

    companion object {
        const val PREFS_PREFIX = "vault_"
    }
}
