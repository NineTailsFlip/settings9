package io.github.ninetailsflip.settings

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * The Nine look for what's left of stock Android (SystemUI's volume dialog, permission prompts,
 * the share sheet, the file picker, system dialogs): Material You's colors in its monochrome
 * style - grays, no accent from the wallpaper - on Lineage's pure black. The font is already
 * monospace (android_vendor_thor2's framework overlay). Applied once per [VERSION], the first
 * time Settings starts (at boot: the status bar runs from boot); the user may change it after.
 */
object SystemLook {
    const val VERSION = 1
    private const val PREFS = "system_look"
    private const val KEY_APPLIED = "applied"
    private const val THEME = "theme_customization_overlay_packages"
    const val STYLE_KEY = "android.theme.customization.theme_style"
    const val STYLE = "MONOCHROMATIC"
    private val LINEAGE_SETTINGS: Uri = Uri.parse("content://lineagesettings")

    fun applyOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_APPLIED, 0) >= VERSION) return
        if (apply(context)) prefs.edit().putInt(KEY_APPLIED, VERSION).apply()
    }

    /** Whether both took. */
    fun apply(context: Context): Boolean = monochrome(context) and black(context)

    /** SystemUI's theme setting with the monochrome style; its other keys (the color source)
     *  stay. SystemUI rebuilds the system colors when it changes. */
    private fun monochrome(context: Context): Boolean {
        val resolver = context.contentResolver
        val json = runCatching { JSONObject(Settings.Secure.getString(resolver, THEME) ?: "{}") }
            .getOrElse { JSONObject() }
        if (json.optString(STYLE_KEY) == STYLE) return true
        json.put(STYLE_KEY, STYLE)
        return runCatching { Settings.Secure.putString(resolver, THEME, json.toString()) }
            .onFailure { Log.w(TAG, "No monochrome style", it) }
            .getOrDefault(false)
    }

    /** Lineage's "pure black" (LineageSettings.Secure.BERRY_BLACK_THEME): with the dark theme,
     *  SystemUI puts black under everything instead of Material You's dark grays. */
    private fun black(context: Context): Boolean = runCatching {
        context.contentResolver.call(LINEAGE_SETTINGS, "PUT_secure", "berry_black_theme",
            Bundle().apply { putString(Settings.NameValueTable.VALUE, "1") })
        true
    }.onFailure { Log.w(TAG, "No pure black", it) }.getOrDefault(false)

    private const val TAG = "Settings"
}
