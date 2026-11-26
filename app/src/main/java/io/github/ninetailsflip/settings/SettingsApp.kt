package io.github.ninetailsflip.settings

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.io.File
import android.util.Log
import io.github.ninetailsflip.update.SetupChoices
import life.furrer.nine.core.KeyHelp
import life.furrer.nine.core.Nine
import life.furrer.nine.core.NineApp
import life.furrer.nine.core.NineSurface

class SettingsApp : Application() {

    // Direct Boot: the status bar (StatusBarService) runs before the first unlock, when only
    // device-protected storage is readable, so Settings keeps everything there, like Lock.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.createDeviceProtectedStorageContext())
    }

    override fun onCreate() {
        super.onCreate()
        if (getSystemService(UserManager::class.java)?.isUserUnlocked == true) DeviceStorage.moveFrom(this)
        Nine.init(
            NineApp(
                id = "settings",
                name = "Settings",
                tagline = "The phone's settings",
                surfaces = listOf(
                    NineSurface(
                        id = "",
                        name = "Settings",
                        introText = "The phone's settings, as Nine lists: network, Bluetooth, apps, sound, display, security, languages, keyboard and the rest.",
                        keyMap = listOf(
                            KeyHelp("Up / Down", "Move"),
                            KeyHelp("OK", "Open, choose, or flip a value"),
                            KeyHelp("Left / Right", "Change a value; jump by letter in long lists"),
                            KeyHelp("Back", "The previous page"),
                            KeyHelp("*", "Menu"),
                        ),
                    ),
                ),
                websiteUrl = "https://github.com/NineTailsFlip",
                sourceUrl = "https://github.com/NineTailsFlip/settings9",
                trackerUrl = "https://github.com/NineTailsFlip/settings9/issues",
                settingsActivity = LookActivity::class.java,
            ),
        )
        // The look chosen in the phone's first-time setup, like every Nine app.
        SetupChoices.adopt(this)
        // The shade's notifications: the listener is on from the first start, so they're there
        // the first time the shade opens.
        ShadeFeed.grantAccess(this)
        OwnPermissions.grant(this)
        SystemLook.applyOnce(this)
    }
}

/**
 * Settings' preferences moved to device-protected storage (0.4.0): the ones an older version
 * kept in the credential-protected one come over once, the first time the phone is unlocked.
 */
object DeviceStorage {
    fun moveFrom(context: Context) {
        val device = context.createDeviceProtectedStorageContext()
        // Context.createCredentialProtectedStorageContext is hidden.
        val credential = Hidden.call(context, "createCredentialProtectedStorageContext") as? Context ?: return
        val old = File(credential.dataDir, "shared_prefs").listFiles().orEmpty()
        for (file in old) {
            val name = file.name.removeSuffix(".xml")
            if (file.name == name || File(device.dataDir, "shared_prefs/${file.name}").exists()) continue
            if (!device.moveSharedPreferencesFrom(credential, name)) Log.w("Settings", "Couldn't move $name")
        }
    }
}

/**
 * Settings is the phone's own settings app, so it grants itself the runtime permissions it asks
 * for (Bluetooth, phone state, accounts) instead of asking. Android does that by itself only for
 * persistent platform apps, which Settings isn't: without BLUETOOTH_CONNECT the shade couldn't
 * switch Bluetooth. Takes GRANT_RUNTIME_PERMISSIONS, which Settings holds by the platform signature.
 */
object OwnPermissions {
    fun grant(context: Context) {
        val pm = context.packageManager
        val requested = runCatching {
            pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        }.getOrNull().orEmpty()
        for (permission in requested) {
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) continue
            val info = runCatching { pm.getPermissionInfo(permission, 0) }.getOrNull() ?: continue
            if (info.protection != PermissionInfo.PROTECTION_DANGEROUS) continue
            Hidden.call(pm, "grantRuntimePermission", String::class.java to context.packageName,
                String::class.java to permission, UserHandle::class.java to Process.myUserHandle())
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w("Settings", "Couldn't grant itself $permission")
            }
        }
    }
}
