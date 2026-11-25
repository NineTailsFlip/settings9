package io.github.ninetailsflip.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * The system calls Settings needs that the SDK it's built against doesn't show (@SystemApi and
 * hidden ones: airplane mode, removing a Bluetooth bond, the PIN). Settings is a platform-signed
 * system app, so Android lets it make them; here they're made by reflection. A call that fails
 * - a different Android, a method renamed - logs and comes back as null rather than throwing,
 * and the row that needed it says it couldn't.
 */
object Hidden {
    private const val TAG = "Settings"

    /** [target].[name](args), each arg given as its parameter type to its value. */
    fun call(target: Any, name: String, vararg args: Pair<Class<*>, Any?>): Any? = try {
        target.javaClass.getMethod(name, *args.map { it.first }.toTypedArray())
            .invoke(target, *args.map { it.second }.toTypedArray())
    } catch (e: Exception) {
        Log.w(TAG, "$name failed", e)
        null
    }

    /** [className].[name](args), a static method. */
    fun callStatic(className: String, name: String, vararg args: Pair<Class<*>, Any?>): Any? = try {
        Class.forName(className).getMethod(name, *args.map { it.first }.toTypedArray())
            .invoke(null, *args.map { it.second }.toTypedArray())
    } catch (e: Exception) {
        Log.w(TAG, "$className.$name failed", e)
        null
    }

    /** A system property (ro.lineage.version, ...), or [default]. */
    fun property(key: String, default: String = ""): String =
        (callStatic("android.os.SystemProperties", "get", String::class.java to key, String::class.java to default) as? String) ?: default

    val INT: Class<*> = Int::class.javaPrimitiveType!!
    val BOOLEAN: Class<*> = Boolean::class.javaPrimitiveType!!
    val LONG: Class<*> = Long::class.javaPrimitiveType!!
}

/**
 * The stock Settings app stays in the image for the system's own dialogs (Bluetooth pairing,
 * the home screen before the first unlock, confirming a PIN for another app) until these Settings
 * take them over too; its icon is hidden, so the home screen lists one Settings.
 */
object StockSettings {
    const val PACKAGE = "com.android.settings"
    private val LAUNCHER = ComponentName(PACKAGE, "com.android.settings.Settings")
    private val HOME = ComponentName(PACKAGE, "com.android.settings.homepage.SettingsHomepageActivity")

    fun hideLauncherEntry(context: Context) {
        try {
            val pm = context.packageManager
            if (pm.getComponentEnabledSetting(LAUNCHER) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(LAUNCHER, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        } catch (e: Exception) {
            // Not there (another phone), or not allowed (not the system's Settings): nothing to hide.
            Log.i("Settings", "stock Settings' icon stays: ${e.message}")
        }
    }

    /** The stock app's own home page, for what isn't here yet. */
    fun intent(): Intent = Intent(Intent.ACTION_MAIN).setComponent(HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
