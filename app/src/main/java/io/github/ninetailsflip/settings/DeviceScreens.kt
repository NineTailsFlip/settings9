package io.github.ninetailsflip.settings

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter

// ── Sound ───────────────────────────────────────────────────────────────────────────────────

object Sound {
    fun audio(context: Context): AudioManager? = context.getSystemService(AudioManager::class.java)

    fun mode(context: Context): String = when (audio(context)?.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> "silent"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "ring"
    }

    fun summary(context: Context): String {
        val audio = audio(context) ?: return ""
        val ring = audio.getStreamVolume(AudioManager.STREAM_RING)
        return "${mode(context)} · ring volume $ring of ${audio.getStreamMaxVolume(AudioManager.STREAM_RING)}"
    }
}

/** Volumes (Left/Right change them), ring or vibrate or silent, and the sounds themselves. */
class SoundScreen : Screen("Sound & vibration") {
    private val streams = listOf(
        AudioManager.STREAM_RING to "Ring volume",
        AudioManager.STREAM_NOTIFICATION to "Notification volume",
        AudioManager.STREAM_MUSIC to "Media volume",
        AudioManager.STREAM_ALARM to "Alarm volume",
        AudioManager.STREAM_VOICE_CALL to "Call volume",
    )
    private val modes = listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT)

    override fun hint() = "←→ change · ok open · ‹ back"

    override fun rows(): List<Row> {
        val audio = Sound.audio(context) ?: return listOf(Row("No sound on this phone"))
        return buildList {
            add(Row("Mode", Sound.mode(context), adjust = { direction ->
                val next = modes[(modes.indexOf(audio.ringerMode).coerceAtLeast(0) + direction + modes.size) % modes.size]
                runCatching { audio.ringerMode = next }.onFailure { say("Android didn't change the mode") }
            }))
            add(header("Volumes"))
            streams.forEach { (stream, label) ->
                val max = audio.getStreamMaxVolume(stream)
                val now = audio.getStreamVolume(stream)
                add(Row(label, bar(now, max), adjust = { direction ->
                    runCatching { audio.setStreamVolume(stream, (now + direction).coerceIn(0, max), AudioManager.FLAG_PLAY_SOUND) }
                        .onFailure { say("Android didn't change it") }
                }))
            }
            add(header("Sounds"))
            add(soundRow("Ringtone", RingtoneManager.TYPE_RINGTONE))
            add(soundRow("Notification sound", RingtoneManager.TYPE_NOTIFICATION))
            add(soundRow("Alarm sound", RingtoneManager.TYPE_ALARM))
            val vibrateOnRing = context.systemInt("vibrate_when_ringing", 0) == 1
            add(Row("Vibrate when ringing", onOff(vibrateOnRing), adjust = {
                Settings.System.putInt(context.contentResolver, "vibrate_when_ringing", if (vibrateOnRing) 0 else 1)
            }))
            val keyTones = context.systemInt(Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
            add(Row("Keypad tones", onOff(keyTones), adjust = {
                Settings.System.putInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, if (keyTones) 0 else 1)
            }))
        }
    }

    private fun soundRow(label: String, type: Int): Row {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, type)
        val name = uri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) } ?: "none"
        return Row(label, name) {
            val pick = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            host.startForResult(pick) { result, data ->
                if (result == android.app.Activity.RESULT_OK) {
                    @Suppress("DEPRECATION")
                    val chosen: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    RingtoneManager.setActualDefaultRingtoneUri(context, type, chosen)
                }
            }
        }
    }
}

/** "▮▮▮▯▯ 3 of 5" - a value on a scale, the way volume and brightness rows show it. */
fun bar(value: Int, max: Int, width: Int = 10): String {
    if (max <= 0) return "$value"
    val filled = (value * width + max / 2) / max
    return "▮".repeat(filled.coerceIn(0, width)) + "▯".repeat((width - filled).coerceIn(0, width)) + "  $value of $max"
}

// ── Display ─────────────────────────────────────────────────────────────────────────────────

object Display {
    val TIMEOUTS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)

    fun timeoutText(ms: Int): String = if (ms < 60_000) "${ms / 1000} seconds" else "${ms / 60_000} min"

    fun summary(context: Context): String {
        val level = context.systemInt(Settings.System.SCREEN_BRIGHTNESS, 128)
        return "brightness ${level * 100 / 255} % · off after ${timeoutText(context.systemInt(Settings.System.SCREEN_OFF_TIMEOUT, 30_000))}"
    }
}

class DisplayScreen : Screen("Display") {
    private val scales = listOf(0.85f, 1.0f, 1.15f, 1.3f)

    override fun hint() = "←→ change · ok open · ‹ back"

    override fun rows(): List<Row> {
        val resolver = context.contentResolver
        val brightness = context.systemInt(Settings.System.SCREEN_BRIGHTNESS, 128)
        val auto = context.systemInt(Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        val timeout = context.systemInt(Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
        val uiMode = context.getSystemService(UiModeManager::class.java)
        val dark = uiMode?.nightMode == UiModeManager.MODE_NIGHT_YES
        val scale = Settings.System.getFloat(resolver, Settings.System.FONT_SCALE, 1.0f)
        val stayOn = context.globalInt(Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0
        return listOf(
            Row("Brightness", bar((brightness * 20 + 127) / 255, 20), adjust = { direction ->
                val next = (brightness + direction * 13).coerceIn(1, 255)
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, next)
            }),
            Row("Adaptive brightness", onOff(auto), adjust = {
                Settings.System.putInt(
                    resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                )
            }),
            Row("Screen off after", Display.timeoutText(timeout), adjust = { direction ->
                val list = Display.TIMEOUTS
                val at = list.indexOfFirst { it >= timeout }.let { if (it < 0) list.lastIndex else it }
                Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, list[(at + direction).coerceIn(0, list.lastIndex)])
            }),
            Row("Dark theme", onOff(dark), adjust = {
                // UiModeManager.setNightMode needs MODIFY_DAY_NIGHT_MODE, which Settings holds.
                runCatching { uiMode?.nightMode = if (dark) UiModeManager.MODE_NIGHT_NO else UiModeManager.MODE_NIGHT_YES }
                    .onFailure { say("Android didn't change the theme") }
            }),
            Row("Text size", "${(scale * 100).toInt()} %", adjust = { direction ->
                val at = scales.indexOfFirst { it >= scale - 0.01f }.coerceAtLeast(0)
                Settings.System.putFloat(resolver, Settings.System.FONT_SCALE, scales[(at + direction).coerceIn(0, scales.lastIndex)])
            }),
            Row("Stay on while charging", onOff(stayOn), adjust = {
                // 7: AC, USB and wireless.
                Settings.Global.putInt(resolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, if (stayOn) 0 else 7)
            }),
        ) + listOfNotNull(
            OuterScreen.read(context)?.let { s ->
                Row("Outer screen", if (OuterScreen.flag(s, "enabled")) "on · clock, calls, notifications, music" else "off") { open(Screens.OUTER) }
            },
        )
    }
}

// ── Battery ─────────────────────────────────────────────────────────────────────────────────

class BatteryScreen : Screen("Battery") {
    override fun rows(): List<Row> {
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = battery?.isCharging == true
        val power = context.getSystemService(PowerManager::class.java)
        val saver = power?.isPowerSaveMode == true
        val percent = context.systemInt("status_bar_show_battery_percent", 0) == 1
        return listOf(
            Row("Charge", if (level >= 0) "${bar(level, 100, 10).substringBefore("  ")}  $level %" + if (charging) " · charging" else "" else "unknown"),
            Row("Battery saver", onOff(saver), adjust = {
                if (Hidden.call(power!!, "setPowerSaveModeEnabled", Hidden.BOOLEAN to !saver) as? Boolean != true) say("Android didn't change it")
            }),
            Row("Percentage in the status bar", onOff(percent), adjust = {
                Settings.System.putInt(context.contentResolver, "status_bar_show_battery_percent", if (percent) 0 else 1)
            }),
        )
    }
}

// ── Storage ─────────────────────────────────────────────────────────────────────────────────

object Storage {
    fun stat(): StatFs? = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()

    fun summary(): String {
        val stat = stat() ?: return ""
        val used = stat.totalBytes - stat.availableBytes
        return "${used * 100 / stat.totalBytes.coerceAtLeast(1)} % used"
    }
}

class StorageScreen : Screen("Storage") {
    override fun rows(): List<Row> {
        val stat = Storage.stat() ?: return listOf(Row("Unknown"))
        val total = stat.totalBytes
        val free = stat.availableBytes
        return listOf(
            Row("Used", "${Formatter.formatFileSize(context, total - free)} of ${Formatter.formatFileSize(context, total)}"),
            Row("Free", Formatter.formatFileSize(context, free)),
            Row("Apps", "which apps take the space") { open(Screens.APPS) },
            Row("Files", "photos, downloads, documents") {
                start(
                    Intent(Intent.ACTION_VIEW)
                        .setType("vnd.android.document/root")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}
