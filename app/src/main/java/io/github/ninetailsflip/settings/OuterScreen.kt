package io.github.ninetailsflip.settings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import java.text.Collator

/**
 * The outer screen's and the extra keys' settings, kept by ThorSubScreen (the thor2 image's
 * system app that drives the outer screen and handles the keys apps don't). It serves them from
 * its provider `org.thor2.subscreen.prefs` (rows of key, value, type; update writes them; call
 * "show_now" lights the outer screen), which only holders of WRITE_SECURE_SETTINGS may use.
 * A setting it hasn't stored yet has the default below, the same as ThorSubScreen's Prefs.
 */
object OuterScreen {
    private const val TAG = "Settings"
    private val URI: Uri = Uri.parse("content://org.thor2.subscreen.prefs")

    private val DEFAULTS: Map<String, Any> = mapOf(
        "enabled" to true, "timeout" to "8", "brightness" to 60,
        "night_mode" to false, "night_start" to "23", "night_end" to "7",
        "wake_lid" to true, "wake_sos" to true, "wake_volume" to false, "wake_notifications" to true,
        "wake_charging" to true, "charging_anim" to true, "torch_double_sos" to true,
        "clock_style" to "digital", "clock_format" to "system", "show_seconds" to false, "show_date" to true,
        "signature" to "", "battery_style" to "icon_percent", "notif_icons" to true, "show_missed" to true,
        "show_steps" to true, "page_notifications" to true, "notif_text" to false, "page_music" to true,
        "page_pet" to true, "page_eightball" to true, "lid_answer" to false, "lid_end" to false,
    )

    /** The stored settings (key to value as text), or null without ThorSubScreen. */
    fun read(context: Context): Map<String, String>? = try {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "No outer screen settings: ${e.message}")
        null
    }

    fun write(context: Context, key: String, value: Any?): Boolean = try {
        val values = ContentValues().apply {
            when (value) {
                null -> putNull(key)
                is Boolean -> put(key, value)
                is Int -> put(key, value)
                else -> put(key, value.toString())
            }
        }
        context.contentResolver.update(URI, values, null, null) > 0
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't change $key", e)
        false
    }

    fun showNow(context: Context): Boolean = try {
        context.contentResolver.call(URI, "show_now", null, null)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't light the outer screen", e)
        false
    }

    fun flag(stored: Map<String, String>, key: String): Boolean =
        stored[key]?.toBooleanStrictOrNull() ?: (DEFAULTS[key] as? Boolean ?: true)

    fun text(stored: Map<String, String>, key: String): String = stored[key] ?: DEFAULTS[key]?.toString().orEmpty()

    fun number(stored: Map<String, String>, key: String): Int = stored[key]?.toIntOrNull() ?: (DEFAULTS[key] as? Int ?: 0)

    // ── The keys ────────────────────────────────────────────────────────────────────────────

    /** The keys ThorSubScreen gives actions to (KeyActions.DEFAULTS): key code, name, the short
     *  and long press defaults. */
    data class Key(val code: Int, val name: String, val short: String, val long: String)

    val KEYS = listOf(
        Key(131, "Contacts key", "contacts", "none"),
        Key(132, "Messages key", "messages", "none"),
        Key(133, "F3 key", "notifications", "cursor"),
        Key(27, "Camera key", "camera", "torch"),
        Key(5, "Green key", "call_log", "dialer"),
    )

    /** ThorSubScreen's own actions, in the order its picker lists them. */
    val ACTIONS = linkedMapOf(
        "none" to "Nothing",
        "contacts" to "Contacts",
        "messages" to "Messages",
        "dialer" to "Phone: dial pad",
        "call_log" to "Phone: call log",
        "camera" to "Camera",
        "torch" to "Flashlight",
        "notifications" to "Notifications",
        "quick_settings" to "Quick settings",
        "cursor" to "Cursor mode",
    )

    fun prefKey(key: Key, long: Boolean) = "key_${key.code}_${if (long) "long" else "short"}"

    fun action(stored: Map<String, String>, key: Key, long: Boolean): String =
        stored[prefKey(key, long)] ?: if (long) key.long else key.short

    /** "Contacts", or an app's name for "app:package/activity". */
    fun actionLabel(context: Context, action: String): String = ACTIONS[action] ?: action.removePrefix("app:").let { component ->
        val pkg = component.substringBefore('/')
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
    }
}

/** Display → Outer screen: everything about the screen on the lid. */
class OuterScreenScreen : Screen("Outer screen") {
    private val timeouts = listOf("5", "8", "15", "30", "60")
    private val clocks = listOf("digital", "words")
    private val formats = listOf("system", "24", "12")

    override fun subtitle() = "the screen on the lid · SOS flips its pages"

    override fun hint() = "←→ or ok change · ‹ back"

    private fun cycle(list: List<String>, now: String, direction: Int): String =
        list[(list.indexOf(now).coerceAtLeast(0) + direction + list.size) % list.size]

    private fun set(key: String, value: Any?) {
        if (!OuterScreen.write(context, key, value)) say("the outer screen didn't take it")
    }

    private fun toggle(stored: Map<String, String>, title: String, key: String, meta: String = ""): Row {
        val on = OuterScreen.flag(stored, key)
        return Row(title, listOf(onOff(on), meta).filter { it.isNotEmpty() }.joinToString(" · "), adjust = { set(key, !on) })
    }

    override fun rows(): List<Row> {
        val s = OuterScreen.read(context) ?: return listOf(Row("No outer screen on this phone", "ThorSubScreen isn't installed"))
        val enabled = OuterScreen.flag(s, "enabled")
        return buildList {
            add(Row("Outer screen", onOff(enabled), adjust = { set("enabled", !enabled) }))
            if (!enabled) return@buildList
            add(Row("Show it now", "lights it up on the clock") {
                if (OuterScreen.showNow(context)) say("look at the outer screen") else say("the outer screen didn't answer")
            })

            add(header("Clock"))
            val clock = OuterScreen.text(s, "clock_style").let { if (it in clocks) it else "digital" }
            add(Row("Clock", if (clock == "words") "the time in words" else "digits", adjust = { set("clock_style", cycle(clocks, clock, it)) }))
            val format = OuterScreen.text(s, "clock_format")
            val formatName = mapOf("system" to "as the phone", "24" to "24-hour", "12" to "12-hour")
            add(Row("Time format", formatName[format] ?: format, adjust = { set("clock_format", cycle(formats, format, it)) }))
            add(toggle(s, "Seconds", "show_seconds"))
            add(toggle(s, "Date", "show_date"))
            val signature = OuterScreen.text(s, "signature")
            add(Row("Signature", signature.ifEmpty { "none: a line of your own under the clock" }) {
                host.ask(FieldRequest("a line under the clock", FieldKind.TEXT, signature) { set("signature", it.trim()) })
            })

            add(header("Status line"))
            val battery = OuterScreen.text(s, "battery_style") != "hidden"
            add(Row("Battery", onOff(battery), adjust = { set("battery_style", if (battery) "hidden" else "percent") }))
            add(toggle(s, "New notifications", "notif_icons", "how many"))
            add(toggle(s, "Missed calls", "show_missed"))
            if (context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) {
                add(toggle(s, "Steps today", "show_steps"))
            }

            add(header("Pages"))
            add(toggle(s, "Notifications", "page_notifications"))
            if (OuterScreen.flag(s, "page_notifications")) add(toggle(s, "Their text", "notif_text", "anyone can see the lid"))
            add(toggle(s, "Music", "page_music"))
            add(toggle(s, "Kitty", "page_pet"))
            add(toggle(s, "Magic 8-ball", "page_eightball"))

            add(header("Lights up when"))
            add(toggle(s, "The lid closes", "wake_lid"))
            add(toggle(s, "SOS is pressed", "wake_sos"))
            if (OuterScreen.flag(s, "wake_sos")) add(toggle(s, "Two SOS presses", "torch_double_sos", "the flashlight"))
            add(toggle(s, "A volume key is pressed", "wake_volume"))
            add(toggle(s, "A notification comes", "wake_notifications"))
            add(toggle(s, "The charger goes in or out", "wake_charging"))
            if (OuterScreen.flag(s, "wake_charging")) add(toggle(s, "Charging animation", "charging_anim"))

            add(header("Screen"))
            val timeout = OuterScreen.text(s, "timeout")
            add(Row("Turns off after", Display.timeoutText((timeout.toIntOrNull() ?: 8) * 1000), adjust = { set("timeout", cycle(timeouts, timeout, it)) }))
            val brightness = OuterScreen.number(s, "brightness")
            add(Row("Brightness", bar(brightness / 5, 20), adjust = { set("brightness", (brightness + it * 5).coerceIn(5, 100)) }))
            val night = OuterScreen.flag(s, "night_mode")
            add(Row("Night mode", if (night) "on: dimmer, and notifications don't light it up" else "off", adjust = { set("night_mode", !night) }))
            if (night) {
                for ((key, title) in listOf("night_start" to "Night from", "night_end" to "Night until")) {
                    val hour = OuterScreen.text(s, key).toIntOrNull() ?: 0
                    add(Row(title, "%02d:00".format(hour), adjust = { set(key, ((hour + it + 24) % 24).toString()) }))
                }
            }
        }
    }
}

/** System → Buttons and lid: what the extra keys do, the red key, long press Home, the lid. */
class KeysScreen : Screen("Buttons and lid") {
    private val endButton = listOf(1 to "goes home", 2 to "turns the screen off", 3 to "goes home, then turns the screen off")

    override fun subtitle() = "the keys apps don't use, and the lid"

    override fun rows(): List<Row> = buildList {
        val s = OuterScreen.read(context)
        if (s != null) {
            add(header("Keys"))
            OuterScreen.KEYS.forEach { key ->
                for (long in listOf(false, true)) {
                    val now = OuterScreen.action(s, key, long)
                    val title = if (long) "${key.name}, long press" else key.name
                    add(Row(title, OuterScreen.actionLabel(context, now), adjust = { direction ->
                        val list = OuterScreen.ACTIONS.keys.toList()
                        val next = list[(list.indexOf(now).let { if (it < 0) 0 else it } + direction + list.size) % list.size]
                        if (!OuterScreen.write(context, OuterScreen.prefKey(key, long), next)) say("didn't take it")
                    }, ok = { open(Screens.KEY_ACTION, OuterScreen.prefKey(key, long)) }))
                }
            }
        }

        add(header("System keys"))
        val end = Settings.System.getInt(context.contentResolver, Settings.System.END_BUTTON_BEHAVIOR, 2)
        add(Row("Red key", endButton.firstOrNull { it.first == end }?.second ?: "as Android has it", adjust = { direction ->
            val at = endButton.indexOfFirst { it.first == end }.coerceAtLeast(0)
            val next = endButton[(at + direction + endButton.size) % endButton.size].first
            Settings.System.putInt(context.contentResolver, Settings.System.END_BUTTON_BEHAVIOR, next)
        }))
        // thor2's PhoneWindowManager (android_vendor_thor2, patches/frameworks_base 0002).
        val shade = context.globalInt("thor2_home_long_press_shade", 1) == 1
        add(Row("Long press Home", if (shade) "opens the notification shade" else "does nothing", adjust = {
            Settings.Global.putInt(context.contentResolver, "thor2_home_long_press_shade", if (shade) 0 else 1)
        }))
        add(Row("Left soft key, long press", "recent apps"))

        if (s != null) {
            add(header("Lid"))
            val answer = OuterScreen.flag(s, "lid_answer")
            add(Row("Opening it answers a call", onOff(answer), adjust = { OuterScreen.write(context, "lid_answer", !answer) }))
            val hangUp = OuterScreen.flag(s, "lid_end")
            add(Row("Closing it ends a call", onOff(hangUp), adjust = { OuterScreen.write(context, "lid_end", !hangUp) }))
        }
    }

    override fun hint() = "ok choose · ←→ change · ‹ back"
}

/** What one key does: ThorSubScreen's own actions, then any app. [prefKey] is the setting. */
class KeyActionScreen(private val prefKey: String) : Screen("Key action") {
    private val key = OuterScreen.KEYS.firstOrNull { prefKey.startsWith("key_${it.code}_") }
    private val long = prefKey.endsWith("_long")
    private val apps: List<Pair<String, String>> by lazy {
        val pm = context.packageManager
        val collator = Collator.getInstance()
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { ri -> "app:${ri.activityInfo.packageName}/${ri.activityInfo.name}" to ri.loadLabel(pm).toString() }
            .sortedWith { a, b -> collator.compare(a.second, b.second) }
    }

    override val title: String get() = key?.let { if (long) "${it.name}, long press" else it.name } ?: "Key action"

    override fun subtitle() = "what the key does"

    override fun hint() = "ok choose · ←→ next letter · ‹ back"

    private fun choices(): List<Pair<String?, String>> =
        OuterScreen.ACTIONS.map { it.key to it.value } + listOf<Pair<String?, String>>(null to "Apps") + apps

    override fun rows(): List<Row> {
        val k = key ?: return listOf(Row("Not a key here"))
        val now = OuterScreen.read(context)?.let { OuterScreen.action(it, k, long) }
        return choices().map { (value, label) ->
            if (value == null) header(label)
            else Row(label, "", marked = value == now) {
                if (OuterScreen.write(context, prefKey, value)) host.close() else say("didn't take it")
            }
        }
    }

    override fun initialRow(): Int {
        val now = key?.let { k -> OuterScreen.read(context)?.let { OuterScreen.action(it, k, long) } }
        return choices().indexOfFirst { it.first != null && it.first == now }.coerceAtLeast(0)
    }

    override fun jump(from: Int, direction: Int) =
        jumpByKey(choices().mapIndexed { i, (value, label) -> if (value == null || i < OuterScreen.ACTIONS.size) null else label.first().uppercaseChar() }, from, direction)
}
