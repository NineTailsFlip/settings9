package io.github.ninetailsflip.settings

import android.app.AlarmManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.LocaleList
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SystemScreen : Screen("System") {
    override fun rows(): List<Row> {
        val locale = Languages.current()
        val developer = context.globalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        return buildList {
            add(Row("Languages", locale.getDisplayName(locale)) { open(Screens.LANGUAGES) })
            add(Row("Keyboard", Keyboard.summary(context)) { open(Screens.KEYBOARD) })
            add(Row("Date and time", DateTime.summary(context)) { open(Screens.DATE_TIME) })
            // thor2's PhoneWindowManager (android_vendor_thor2, patches/frameworks_base 0002).
            val shade = context.globalInt("thor2_home_long_press_shade", 1) == 1
            add(Row("Long press Home", if (shade) "opens the notification shade" else "does nothing", adjust = {
                Settings.Global.putInt(context.contentResolver, "thor2_home_long_press_shade", if (shade) 0 else 1)
            }))
            if (developer) add(Row("Developer options", "for app developers") { open(Screens.DEVELOPER) })
            add(Row("Reset", "erase everything on the phone") { open(Screens.RESET) })
        }
    }
}

// ── Languages ───────────────────────────────────────────────────────────────────────────────

/** The system language, through LocalePicker (hidden), the way Android's own Settings sets it. */
object Languages {
    private fun picker() = Class.forName("com.android.internal.app.LocalePicker")

    fun current(): Locale = Resources.getSystem().configuration.locales[0]

    fun supported(context: Context): List<Locale> {
        @Suppress("UNCHECKED_CAST")
        val tags = runCatching { picker().getMethod("getSupportedLocales", Context::class.java).invoke(null, context) as Array<String> }
            .getOrNull() ?: Resources.getSystem().assets.locales
        return tags.asSequence()
            .map { Locale.forLanguageTag(it.replace('_', '-')) }
            .filter { it.language.isNotEmpty() && it.country.isNotEmpty() }
            .distinct()
            .sortedBy { it.getDisplayName(it).lowercase(it) }
            .toList()
    }

    fun set(locale: Locale): Boolean = runCatching {
        picker().getMethod("updateLocales", LocaleList::class.java).invoke(null, LocaleList(locale))
    }.isSuccess

    fun name(locale: Locale) = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

class LanguagesScreen : Screen("Languages") {
    private val locales by lazy { Languages.supported(context) }

    override fun subtitle() = "the phone's language · the Nine apps stay in English"
    override fun hint() = "ok choose · ←→ next letter · ‹ back"

    override fun rows() = locales.map { locale ->
        val current = locale.toLanguageTag() == Languages.current().toLanguageTag()
        Row(Languages.name(locale), locale.getDisplayName(Locale.ENGLISH), marked = current) {
            if (!current && !Languages.set(locale)) say("Android didn't change the language")
        }
    }

    override fun initialRow() = locales.indexOfFirst { it.toLanguageTag() == Languages.current().toLanguageTag() }.coerceAtLeast(0)

    override fun jump(from: Int, direction: Int) = jumpByKey(locales.map { Languages.name(it).first().uppercaseChar() }, from, direction)
}

// ── Keyboard ────────────────────────────────────────────────────────────────────────────────

/** One of K9's layouts, from K9's LanguagesProvider. */
data class KeyboardLayout(val id: Int, val name: String, val localeTag: String, val enabled: Boolean)

object Keyboard {
    const val K9_PACKAGE = "io.github.ninetailsflip.k9"
    private val LAYOUTS: Uri = Uri.parse("content://io.github.ninetailsflip.k9.languages/languages")

    fun current(context: Context): String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)

    fun summary(context: Context): String {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return ""
        val id = current(context)
        val label = imm.inputMethodList.firstOrNull { it.id == id }?.loadLabel(context.packageManager)?.toString() ?: "none"
        val layouts = layouts(context).filter { it.enabled }.joinToString(", ") { it.name }
        return if (layouts.isEmpty()) label else "$label · $layouts"
    }

    fun layouts(context: Context): List<KeyboardLayout> = runCatching {
        context.contentResolver.query(LAYOUTS, null, null, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val name = c.getColumnIndexOrThrow("name")
            val locale = c.getColumnIndexOrThrow("locale")
            val enabled = c.getColumnIndexOrThrow("enabled")
            buildList {
                while (c.moveToNext()) add(KeyboardLayout(c.getInt(id), c.getString(name), c.getString(locale), c.getInt(enabled) == 1))
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    fun setLayouts(context: Context, ids: List<Int>): Boolean = runCatching {
        context.contentResolver.update(LAYOUTS, ContentValues().apply { put("enabled", ids.joinToString(",")) }, null, null) > 0
    }.getOrDefault(false)

    /** Turns on the keyboard [id] and makes it the one in use. */
    fun use(context: Context, id: String) {
        val resolver = context.contentResolver
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_INPUT_METHODS).orEmpty()
            .split(':').filter { it.isNotEmpty() }
        if (enabled.none { it.substringBefore(';') == id }) {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_INPUT_METHODS, (enabled + id).joinToString(":"))
        }
        Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD, id)
    }
}

/** Which keyboard types, its layouts and its own settings. */
class KeyboardScreen : Screen("Keyboard") {
    override fun rows(): List<Row> {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        val methods = imm.inputMethodList
        val current = Keyboard.current(context)
        val inUse = methods.firstOrNull { it.id == current }
        return buildList {
            if (inUse?.packageName == Keyboard.K9_PACKAGE) {
                val layouts = Keyboard.layouts(context).filter { it.enabled }
                add(Row("Layouts", layouts.joinToString(", ") { it.name }.ifEmpty { "K9 isn't answering" }) { open(Screens.LAYOUTS) })
            }
            if (inUse?.settingsActivity != null) {
                add(Row("${inUse.loadLabel(context.packageManager)} settings", "how it types") {
                    start(
                        Intent(Intent.ACTION_MAIN).setClassName(inUse.packageName, inUse.settingsActivity)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                })
            }
            add(header("Keyboard in use"))
            methods.forEach { method ->
                val label = method.loadLabel(context.packageManager).toString()
                add(Row(label, method.packageName, marked = method.id == current) {
                    if (method.id != current) Keyboard.use(context, method.id)
                })
            }
        }
    }
}

/** K9's layouts: tick any number, at least one stays. Saved as they change. */
class LayoutsScreen : Screen("Layouts") {
    private var layouts: List<KeyboardLayout> = emptyList()

    override fun subtitle() = "the layouts K9 types in · # switches between them while typing"
    override fun hint() = "ok tick · ←→ next letter · ‹ back"

    override fun onShow() {
        layouts = Keyboard.layouts(context)
    }

    override fun rows(): List<Row> {
        if (layouts.isEmpty()) return listOf(Row("K9 isn't installed or isn't answering"))
        val on = layouts.filter { it.enabled }.map { it.id }.toSet()
        return layouts.map { layout ->
            Row(layout.name, Locale.forLanguageTag(layout.localeTag).getDisplayName(Locale.ENGLISH), marked = layout.id in on) {
                val next = if (layout.id in on) on - layout.id else on + layout.id
                when {
                    next.isEmpty() -> say("at least one layout stays on")
                    !Keyboard.setLayouts(context, layouts.map { it.id }.filter { it in next }) -> say("K9 didn't take it")
                    else -> layouts = Keyboard.layouts(context)
                }
            }
        }
    }

    override fun initialRow() = layouts.indexOfFirst { it.enabled }.coerceAtLeast(0)

    override fun jump(from: Int, direction: Int) = jumpByKey(layouts.map { it.name.first().uppercaseChar() }, from, direction)
}

// ── Date and time ───────────────────────────────────────────────────────────────────────────

class TimeZoneEntry(val id: String, val offsetMs: Int) {
    val city: String = id.substringAfterLast('/').replace('_', ' ')
    val region: String = id.substringBefore('/')
    val offsetLabel: String
        get() {
            val minutes = offsetMs / 60_000
            val sign = if (minutes < 0) "−" else "+"
            return "UTC$sign%02d:%02d".format(kotlin.math.abs(minutes) / 60, kotlin.math.abs(minutes) % 60)
        }
}

object DateTime {
    private val REGIONS = setOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")

    /** Every city time zone, west to east, then by city. */
    fun zones(now: Long): List<TimeZoneEntry> = TimeZone.getAvailableIDs()
        .filter { it.substringBefore('/') in REGIONS && '/' in it }
        .map { TimeZoneEntry(it, TimeZone.getTimeZone(it).getOffset(now)) }
        .sortedWith(compareBy({ it.offsetMs }, { it.city }))

    fun format(pattern: String, millis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(millis))

    fun zoneLabel(id: String): String {
        val zone = TimeZoneEntry(id, TimeZone.getTimeZone(id).getOffset(System.currentTimeMillis()))
        return "${zone.offsetLabel} · ${zone.city}"
    }

    fun summary(context: Context) = format("EEE d MMM, HH:mm") + if (context.globalInt(Settings.Global.AUTO_TIME, 1) == 1) " · automatic" else ""

    fun setZone(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java)?.setTimeZone(id)
        TimeZone.setDefault(TimeZone.getTimeZone(id))
    }

    fun setTime(context: Context, millis: Long) {
        context.getSystemService(AlarmManager::class.java)?.setTime(millis)
    }
}

class DateTimeScreen : Screen("Date and time") {
    override fun subtitle() = DateTime.format("EEEE d MMMM yyyy, HH:mm")

    override fun rows(): List<Row> = buildList {
        val resolver = context.contentResolver
        val auto = context.globalInt(Settings.Global.AUTO_TIME, 1) == 1
        val autoZone = context.globalInt(Settings.Global.AUTO_TIME_ZONE, 1) == 1
        val clock24 = Settings.System.getString(resolver, Settings.System.TIME_12_24) != "12"
        add(Row("Automatic date and time", onOff(auto), adjust = { Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME, if (auto) 0 else 1) }))
        add(Row("Automatic time zone", onOff(autoZone), adjust = { Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME_ZONE, if (autoZone) 0 else 1) }))
        add(Row("Time zone", DateTime.zoneLabel(TimeZone.getDefault().id)) { open(Screens.ZONES) })
        add(Row("24-hour clock", onOff(clock24), adjust = { Settings.System.putString(resolver, Settings.System.TIME_12_24, if (clock24) "12" else "24") }))
        if (!auto) {
            add(header("By hand: ←→ change"))
            add(Row("Date", DateTime.format("EEE d MMM yyyy"), adjust = { shift(it * DAY) }))
            add(Row("Hour", DateTime.format("HH"), adjust = { shift(it * HOUR) }))
            add(Row("Minute", DateTime.format("mm"), adjust = { shift(it * MINUTE) }))
        }
    }

    private fun shift(by: Long) = DateTime.setTime(context, System.currentTimeMillis() + by)

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}

class ZonesScreen : Screen("Time zone") {
    private val zones by lazy { DateTime.zones(System.currentTimeMillis()) }

    override fun subtitle() = "west to east"
    override fun hint() = "ok choose · ←→ next offset · ‹ back"

    override fun rows() = zones.map { zone ->
        Row("${zone.offsetLabel} · ${zone.city}", zone.region, marked = zone.id == TimeZone.getDefault().id) {
            Settings.Global.putInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0)
            DateTime.setZone(context, zone.id)
            host.close()
        }
    }

    override fun initialRow() = zones.indexOfFirst { it.id == TimeZone.getDefault().id }.coerceAtLeast(0)

    override fun jump(from: Int, direction: Int) = jumpByKey(zones.map { it.offsetMs }, from, direction)
}

// ── Developer options ───────────────────────────────────────────────────────────────────────

class DeveloperScreen : Screen("Developer options") {
    override fun rows(): List<Row> {
        val resolver = context.contentResolver
        val on = context.globalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val adb = context.globalInt(Settings.Global.ADB_ENABLED, 0) == 1
        val awake = context.globalInt(Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0
        return listOf(
            Row("Developer options", onOff(on), adjust = {
                Settings.Global.putInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, if (on) 0 else 1)
                if (on) Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, 0)
            }),
            Row("USB debugging", onOff(adb), adjust = { Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, if (adb) 0 else 1) }),
            Row("Stay awake while charging", onOff(awake), adjust = { Settings.Global.putInt(resolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, if (awake) 0 else 7) }),
            Row("All developer options", "Android's own page") {
                start(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).setPackage(StockSettings.PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
    }
}

// ── Reset ───────────────────────────────────────────────────────────────────────────────────

/** Erasing everything: OK three times (it can't be undone), then Android restarts into
 *  recovery and wipes the phone. */
class ResetScreen : Screen("Reset") {
    private var presses = 0

    override fun subtitle() = "erases every app, account, message and photo on the phone"
    override fun hint() = if (presses > 0) "ok ${3 - presses} more to erase · ‹ cancels" else super.hint()

    override fun rows() = listOf(
        Row(
            "Erase everything",
            when (presses) {
                0 -> "OK three times; the phone restarts and is like new"
                1 -> "OK twice more"
                else -> "OK once more: this can't be undone"
            },
        ) {
            presses++
            if (presses >= 3) {
                presses = 0
                val wipe = Intent("android.intent.action.FACTORY_RESET")
                    .setPackage("android")
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra("android.intent.extra.REASON", "Settings")
                context.sendBroadcast(wipe)
                say("erasing…")
            }
        },
    )

    override fun back(): Boolean {
        if (presses == 0) return false
        presses = 0
        return true
    }
}
