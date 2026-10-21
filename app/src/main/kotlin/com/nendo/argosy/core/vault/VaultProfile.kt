package com.nendo.argosy.core.vault

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Which storage profile this process runs on: the normal one, or the vault.
 *
 * The choice is made once, in [resolveAtProcessStart], from a marker that [enter] writes just
 * before it restarts the process. The marker is consumed on read, so any later process start
 * (a crash, Android reclaiming memory, a background job) comes up in the normal profile. Leaving
 * the vault is therefore just a restart without the marker.
 */
object VaultProfile {

    private const val MARKER = "vault_launch"
    private const val MARKER_MAX_AGE_MS = 60_000L
    private const val AUTO_LOCK_AFTER_SCREEN_OFF_MS = 60_000L

    @Volatile
    var isActive: Boolean = false
        private set

    private var realBase: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoLock = Runnable { realBase?.let { exit(it) } }

    /**
     * Reads and consumes the launch marker. Call from Application.attachBaseContext with the
     * unwrapped base, before anything opens storage.
     */
    fun resolveAtProcessStart(base: Context) {
        realBase = base
        val marker = File(base.noBackupFilesDir, MARKER)
        val age = System.currentTimeMillis() - marker.lastModified()
        isActive = marker.exists() && age in 0..MARKER_MAX_AGE_MS
        if (marker.exists()) marker.delete()
    }

    /**
     * The unwrapped no_backup directory, shared by both profiles. Holds the door sequence and the
     * hidden-app list, which have to be readable before the vault is open.
     */
    fun sharedDir(): File = requireNotNull(realBase) { "VaultProfile not resolved" }.noBackupFilesDir

    fun wrap(base: Context): Context {
        if (!isActive) return base
        val root = File(realBase?.noBackupFilesDir ?: base.noBackupFilesDir, "vault")
        return VaultContext(base, root)
    }

    /**
     * Process-wide guards: secure windows and auto-lock while the vault is open, and the hidden
     * apps put back into the right state for this profile.
     */
    fun install(app: Application) {
        AppHider.applyForProfile(app, vaultOpen = isActive)
        if (!isActive) return
        app.registerActivityLifecycleCallbacks(SecureWindows)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(app, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun enter(context: Context) {
        File(sharedDir(), MARKER).writeText(System.currentTimeMillis().toString())
        restart(context)
    }

    /**
     * Leaves the vault. The hidden apps are hidden again here as well as at the next start, so
     * they are gone even if the relaunch is slow.
     */
    fun exit(context: Context) {
        AppHider.applyForProfile(context, vaultOpen = false)
        restart(context)
    }

    private fun restart(context: Context) {
        val appContext = context.applicationContext
        appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            appContext.startActivity(intent)
        }
        Process.killProcess(Process.myPid())
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> mainHandler.postDelayed(autoLock, AUTO_LOCK_AFTER_SCREEN_OFF_MS)
                Intent.ACTION_SCREEN_ON -> mainHandler.removeCallbacks(autoLock)
            }
        }
    }

    /**
     * FLAG_SECURE keeps vault screens out of screenshots, screen recordings and the recent-apps
     * thumbnails.
     */
    private object SecureWindows : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
