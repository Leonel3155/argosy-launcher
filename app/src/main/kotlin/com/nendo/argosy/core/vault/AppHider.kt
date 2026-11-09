package com.nendo.argosy.core.vault

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.io.File

/**
 * Hides chosen apps from the launcher and from Settings > Apps while the vault is closed.
 *
 * DevicePolicyManager.setApplicationHidden only works for the device owner, which is granted once
 * over ADB (`dpm set-device-owner`). A hidden app keeps its data; Android treats it as not
 * installed until it is unhidden. Without device owner every call here is a no-op.
 */
object AppHider {

    private const val TAG = "AppHider"
    private const val LIST_FILE = "hidden_apps"

    class AdminReceiver : DeviceAdminReceiver()

    fun admin(context: Context) = ComponentName(context, AdminReceiver::class.java)

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context).isDeviceOwnerApp(context.packageName)

    fun adbCommand(context: Context): String =
        "adb shell dpm set-device-owner ${context.packageName}/${AdminReceiver::class.java.name}"

    private fun listFile() = File(VaultProfile.sharedDir(), LIST_FILE)

    fun managedPackages(): Set<String> =
        runCatching { listFile().readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
            .getOrDefault(emptySet())

    fun setManaged(context: Context, packageName: String, managed: Boolean) {
        val updated = if (managed) managedPackages() + packageName else managedPackages() - packageName
        listFile().writeText(updated.joinToString("\n"))
        // A package dropped from the list must not stay hidden with nothing left to unhide it.
        if (!managed) setHidden(context, packageName, hidden = false)
    }

    /**
     * Shows the managed apps while the vault is open and hides them otherwise.
     */
    fun applyForProfile(context: Context, vaultOpen: Boolean) {
        if (!isDeviceOwner(context)) return
        managedPackages().forEach { setHidden(context, it, hidden = !vaultOpen) }
    }

    private fun setHidden(context: Context, packageName: String, hidden: Boolean) {
        runCatching { dpm(context).setApplicationHidden(admin(context), packageName, hidden) }
            .onFailure { Log.w(TAG, "setApplicationHidden($packageName, $hidden) failed", it) }
    }

    /**
     * Unhides everything and gives up device owner, so the app can be uninstalled normally.
     */
    fun release(context: Context) {
        if (!isDeviceOwner(context)) return
        managedPackages().forEach { setHidden(context, it, hidden = false) }
        listFile().delete()
        runCatching { dpm(context).clearDeviceOwnerApp(context.packageName) }
            .onFailure { Log.w(TAG, "clearDeviceOwnerApp failed", it) }
    }
}
