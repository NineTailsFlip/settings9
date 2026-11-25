package io.github.ninetailsflip.settings

import android.app.KeyguardManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.os.BatteryManager
import android.provider.Settings

/** The first page: every section of the phone's settings, each with what it's set to now. */
class MainScreen : Screen("Settings") {
    override fun subtitle() = "the phone's settings"

    override fun rows(): List<Row> {
        val wifi = Network.wifiSummary(context)
        val bluetooth = runCatching {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) "not on this phone" else onOff(adapter.isEnabled)
        }.getOrDefault("")
        val battery = context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val locked = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        val location = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
        return listOf(
            Row("Network & internet", wifi) { open(Screens.NETWORK) },
            Row("Bluetooth", bluetooth) { open(Screens.BLUETOOTH) },
            Row("Apps", "info, permissions, defaults") { open(Screens.APPS) },
            Row("Notifications", "Do not disturb: ${onOff(Notifications.dndOn(context))}") { open(Screens.NOTIFICATIONS) },
            Row("Sound & vibration", Sound.summary(context)) { open(Screens.SOUND) },
            Row("Display", Display.summary(context)) { open(Screens.DISPLAY) },
            Row("Battery", battery?.let { "$it %" }.orEmpty()) { open(Screens.BATTERY) },
            Row("Storage", Storage.summary()) { open(Screens.STORAGE) },
            Row("Security", if (locked) "screen lock: PIN" else "screen lock: none") { open(Screens.SECURITY) },
            Row("Location", onOff(location)) { open(Screens.LOCATION) },
            Row("Accounts", Accounts.summary(context)) { open(Screens.ACCOUNTS) },
            Row("Accessibility", "text size, colors, animations") { open(Screens.ACCESSIBILITY) },
            Row("System", "languages, keyboard, date and time, reset") { open(Screens.SYSTEM) },
            Row("About phone", About.model()) { open(Screens.ABOUT) },
        )
    }

    override fun menu() = listOf("Android's own settings" to { start(StockSettings.intent()) })
}

/** Small readers the main page and the section pages share. */
internal fun Context.secureInt(name: String, default: Int) = Settings.Secure.getInt(contentResolver, name, default)
internal fun Context.globalInt(name: String, default: Int) = Settings.Global.getInt(contentResolver, name, default)
internal fun Context.systemInt(name: String, default: Int) = Settings.System.getInt(contentResolver, name, default)
