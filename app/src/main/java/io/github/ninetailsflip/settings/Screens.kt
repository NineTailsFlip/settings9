package io.github.ninetailsflip.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Every page of Settings by name, and which page an `android.settings.*` intent opens. */
object Screens {
    const val MAIN = "main"
    const val NETWORK = "network"
    const val WIFI = "wifi"
    const val MOBILE = "mobile"
    const val BLUETOOTH = "bluetooth"
    const val APPS = "apps"
    const val APP = "app"
    const val DEFAULT_APPS = "default_apps"
    const val NOTIFICATIONS = "notifications"
    const val SOUND = "sound"
    const val DISPLAY = "display"
    const val BATTERY = "battery"
    const val STORAGE = "storage"
    const val SECURITY = "security"
    const val LOCATION = "location"
    const val ACCOUNTS = "accounts"
    const val ACCESSIBILITY = "accessibility"
    const val SYSTEM = "system"
    const val LANGUAGES = "languages"
    const val KEYBOARD = "keyboard"
    const val LAYOUTS = "layouts"
    const val DATE_TIME = "date_time"
    const val ZONES = "zones"
    const val DEVELOPER = "developer"
    const val RESET = "reset"
    const val ABOUT = "about"
    const val SHADE = "shade"
    const val OUTER = "outer"
    const val KEYS = "keys"
    const val KEY_ACTION = "key_action"

    fun create(name: String, arg: String?): Screen = when (name) {
        NETWORK -> NetworkScreen()
        WIFI -> WifiScreen()
        MOBILE -> MobileScreen()
        BLUETOOTH -> BluetoothScreen()
        APPS -> AppsScreen()
        APP -> AppScreen(arg.orEmpty())
        DEFAULT_APPS -> DefaultAppsScreen()
        NOTIFICATIONS -> NotificationsScreen()
        SOUND -> SoundScreen()
        DISPLAY -> DisplayScreen()
        BATTERY -> BatteryScreen()
        STORAGE -> StorageScreen()
        SECURITY -> SecurityScreen()
        LOCATION -> LocationScreen()
        ACCOUNTS -> AccountsScreen()
        ACCESSIBILITY -> AccessibilityScreen()
        SYSTEM -> SystemScreen()
        LANGUAGES -> LanguagesScreen()
        KEYBOARD -> KeyboardScreen()
        LAYOUTS -> LayoutsScreen()
        DATE_TIME -> DateTimeScreen()
        ZONES -> ZonesScreen()
        DEVELOPER -> DeveloperScreen()
        RESET -> ResetScreen()
        ABOUT -> AboutScreen()
        SHADE -> ShadeScreen(arg)
        OUTER -> OuterScreenScreen()
        KEYS -> KeysScreen()
        KEY_ACTION -> KeyActionScreen(arg.orEmpty())
        else -> MainScreen()
    }

    /** The `android.settings.*` actions these Settings answer (the manifest lists the same), and
     *  the page each opens. One not here opens the main page. */
    val ACTIONS: Map<String, String> = mapOf(
        Settings.ACTION_SETTINGS to MAIN,
        Settings.ACTION_WIRELESS_SETTINGS to NETWORK,
        Settings.ACTION_AIRPLANE_MODE_SETTINGS to NETWORK,
        Settings.ACTION_WIFI_SETTINGS to WIFI,
        Settings.ACTION_WIFI_IP_SETTINGS to WIFI,
        Settings.ACTION_DATA_ROAMING_SETTINGS to MOBILE,
        Settings.ACTION_NETWORK_OPERATOR_SETTINGS to MOBILE,
        Settings.ACTION_BLUETOOTH_SETTINGS to BLUETOOTH,
        Settings.ACTION_APPLICATION_SETTINGS to APPS,
        Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS to APPS,
        Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS to APPS,
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS to APP,
        Settings.ACTION_APP_NOTIFICATION_SETTINGS to APP,
        Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS to DEFAULT_APPS,
        "android.settings.ALL_APPS_NOTIFICATION_SETTINGS" to NOTIFICATIONS,
        "android.settings.ZEN_MODE_SETTINGS" to NOTIFICATIONS,
        Settings.ACTION_SOUND_SETTINGS to SOUND,
        Settings.ACTION_DISPLAY_SETTINGS to DISPLAY,
        "android.settings.DARK_THEME_SETTINGS" to DISPLAY,
        Settings.ACTION_BATTERY_SAVER_SETTINGS to BATTERY,
        Intent.ACTION_POWER_USAGE_SUMMARY to BATTERY,
        Settings.ACTION_INTERNAL_STORAGE_SETTINGS to STORAGE,
        Settings.ACTION_MEMORY_CARD_SETTINGS to STORAGE,
        Settings.ACTION_SECURITY_SETTINGS to SECURITY,
        "android.settings.LOCK_SCREEN_SETTINGS" to SECURITY,
        Settings.ACTION_PRIVACY_SETTINGS to SECURITY,
        Settings.ACTION_LOCATION_SOURCE_SETTINGS to LOCATION,
        Settings.ACTION_SYNC_SETTINGS to ACCOUNTS,
        Settings.ACTION_ACCESSIBILITY_SETTINGS to ACCESSIBILITY,
        Settings.ACTION_LOCALE_SETTINGS to LANGUAGES,
        "android.settings.LANGUAGE_SETTINGS" to LANGUAGES,
        Settings.ACTION_INPUT_METHOD_SETTINGS to KEYBOARD,
        Settings.ACTION_HARD_KEYBOARD_SETTINGS to KEYBOARD,
        Settings.ACTION_DATE_SETTINGS to DATE_TIME,
        Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS to DEVELOPER,
        Settings.ACTION_DEVICE_INFO_SETTINGS to ABOUT,
    )

    /** The page (and its argument: the app an app page is about) an intent asks for. */
    fun forIntent(intent: Intent): Pair<String, String?> {
        val name = ACTIONS[intent.action] ?: MAIN
        val arg = when (name) {
            APP -> intent.data?.takeIf { it.scheme == "package" }?.schemeSpecificPart
                ?: intent.getStringExtra(Settings.EXTRA_APP_PACKAGE)
            else -> null
        }
        return if (name == APP && arg == null) APPS to null else name to arg
    }

    /** An intent for another app's settings page, the way apps ask for one. */
    fun appDetails(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
}
