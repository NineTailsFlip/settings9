package io.github.ninetailsflip.settings

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── The notifications ───────────────────────────────────────────────────────────────────────

/** One notification as the shade lists it. */
data class ShadeNotification(
    val key: String,
    val packageName: String,
    val app: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val clearable: Boolean,
    val autoCancel: Boolean,
    val open: PendingIntent?,
    val actions: List<Notification.Action>,
)

/** The facts about a posted notification the shade decides on, apart from Android's classes. */
data class Posted(
    val key: String,
    val group: String?,
    val groupSummary: Boolean,
    val media: Boolean,
    val postTime: Long,
)

object ShadeRules {
    /** Which posted notifications the shade lists, newest first: a media one is the music row
     *  instead, and a group's summary only stands in for a group with nothing else posted. */
    fun listed(posted: List<Posted>): List<String> {
        val groupsWithChildren = posted.filter { !it.groupSummary && it.group != null }.mapNotNull { it.group }.toSet()
        return posted
            .filter { !it.media }
            .filter { !it.groupSummary || it.group == null || it.group !in groupsWithChildren }
            .sortedByDescending { it.postTime }
            .map { it.key }
    }

    /** [Posted] for a notification. Its group is the one Android files it under: an app's own,
     *  or the one the system bundles an app's loose notifications into (`overrideGroupKey`), whose
     *  summary has no text of its own. */
    fun of(sbn: StatusBarNotification): Posted {
        val n = sbn.notification
        return Posted(
            sbn.key,
            sbn.groupKey.takeIf { sbn.isGroup },
            n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            n.category == Notification.CATEGORY_TRANSPORT || n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
            sbn.postTime,
        )
    }

    /** "now", "5 min", "3 h", else the time or the date: how long ago a notification came. */
    fun ago(now: Long, then: Long, locale: Locale = Locale.getDefault()): String {
        val minutes = (now - then) / 60_000
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "$minutes min"
            minutes < 12 * 60 -> "${minutes / 60} h"
            minutes < 24 * 60 -> SimpleDateFormat("HH:mm", locale).format(Date(then))
            else -> SimpleDateFormat("d MMM", locale).format(Date(then))
        }
    }
}

/**
 * The notifications and the music the shade shows, kept by [ShadeListener] (a notification
 * listener Settings turns on for itself, [grantAccess]) and read by [ShadeScreen], which redraws
 * when [onChange] fires.
 */
object ShadeFeed {
    @Volatile var notifications: List<ShadeNotification> = emptyList()
        private set
    @Volatile var media: MediaController? = null
        private set
    @Volatile var connected = false
        private set
    var onChange: (() -> Unit)? = null

    private var listener: ShadeListener? = null
    private val main = Handler(Looper.getMainLooper())

    internal fun attach(service: ShadeListener) {
        listener = service
        connected = true
        reload()
        watchMedia(service)
    }

    internal fun detach(service: ShadeListener) {
        if (listener !== service) return
        listener = null
        connected = false
        notifications = emptyList()
        media = null
        changed()
    }

    internal fun reload() {
        val service = listener ?: return
        val active = runCatching { service.activeNotifications }.getOrNull().orEmpty()
        val byKey = active.associateBy { it.key }
        val context = service.applicationContext
        notifications = ShadeRules.listed(active.map(ShadeRules::of)).mapNotNull { byKey[it] }.map { item(context, it) }
        changed()
    }

    private fun item(context: Context, sbn: StatusBarNotification): ShadeNotification {
        val pm = context.packageManager
        val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: app
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        return ShadeNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            app = app,
            title = title,
            text = text.replace('\n', ' '),
            postTime = sbn.postTime,
            clearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            open = n.contentIntent,
            actions = n.actions.orEmpty().filter { it.actionIntent != null },
        )
    }

    fun dismiss(key: String) {
        runCatching { listener?.cancelNotification(key) }
    }

    fun clearAll() {
        runCatching { listener?.cancelAllNotifications() }
    }

    // ── Media ───────────────────────────────────────────────────────────────────────────────

    private fun watchMedia(service: ShadeListener) {
        val sessions = service.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(service, ShadeListener::class.java)
        fun pick(controllers: List<MediaController>?) {
            media = controllers.orEmpty().firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.orEmpty().firstOrNull { it.metadata != null }
            changed()
        }
        runCatching {
            pick(sessions.getActiveSessions(component))
            sessions.addOnActiveSessionsChangedListener({ pick(it) }, component)
        }.onFailure { Log.w(TAG, "No media sessions", it) }
    }

    /** "▶ Title — Artist", or null with nothing playing or paused. */
    fun mediaLine(): String? {
        val controller = media ?: return null
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        return (if (playing) "▶ " else "⏸ ") + title + (artist?.let { " — $it" } ?: "")
    }

    fun mediaApp(context: Context): String = media?.packageName?.let { pkg ->
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }.orEmpty()

    fun playPause() {
        val controller = media ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
        else controller.transportControls.play()
    }

    fun skip(direction: Int) {
        val controls = media?.transportControls ?: return
        if (direction > 0) controls.skipToNext() else controls.skipToPrevious()
    }

    private fun changed() {
        main.post { onChange?.invoke() }
    }

    /** Settings reads the notifications to show them in the shade; a system app may grant itself
     *  the access (NotificationManager.setNotificationListenerAccessGranted, hidden). */
    fun grantAccess(context: Context) {
        val component = ComponentName(context, ShadeListener::class.java)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (runCatching { nm.isNotificationListenerAccessGranted(component) }.getOrDefault(false)) return
        if (Hidden.call(nm, "setNotificationListenerAccessGranted", ComponentName::class.java to component, Hidden.BOOLEAN to true) == null) {
            Log.w(TAG, "No notification access: the shade lists none")
        }
    }

    private const val TAG = "Settings"
}

/** Settings' notification listener: keeps [ShadeFeed] current. */
class ShadeListener : NotificationListenerService() {
    override fun onListenerConnected() = ShadeFeed.attach(this)
    override fun onListenerDisconnected() = ShadeFeed.detach(this)
    override fun onNotificationPosted(sbn: StatusBarNotification?) = ShadeFeed.reload()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = ShadeFeed.reload()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = ShadeFeed.reload()
}

// ── Quick settings ──────────────────────────────────────────────────────────────────────────

/** The flashlight: CameraManager's torch, and whether it's on (its callback). */
object Torch {
    @Volatile var on = false
        private set
    private var watching = false

    private fun camera(context: Context) = context.getSystemService(CameraManager::class.java)

    private fun id(cm: CameraManager): String? = runCatching {
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    fun available(context: Context) = camera(context)?.let { id(it) } != null

    fun watch(context: Context) {
        if (watching) return
        val cm = camera(context) ?: return
        val torchId = id(cm) ?: return
        watching = true
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) on = enabled
            }
            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == torchId) on = false
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun set(context: Context, on: Boolean): Boolean {
        val cm = camera(context) ?: return false
        val torchId = id(cm) ?: return false
        return runCatching { cm.setTorchMode(torchId, on) }.isSuccess
    }
}

// ── The screen ──────────────────────────────────────────────────────────────────────────────

/**
 * The notification shade and quick settings, for the keypad: what the stock shade shows, as a
 * Nine list. The notifications first (the music on top), then the switches. Opened by the system
 * in place of SystemUI's panel (android_vendor_thor2's patch 0010): long press Home, or an app
 * expanding the status bar. [panel] "settings" opens on the switches.
 */
class ShadeScreen(var panel: String?) : Screen("") {

    private sealed class Entry {
        object None : Entry()
        object Media : Entry()
        data class Item(val notification: ShadeNotification) : Entry()
        data class Tile(val name: String, val page: String?) : Entry()
    }

    private var entries: List<Entry> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override val title: String
        get() = SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date())

    override fun subtitle(): String {
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val operator = runCatching { Network.telephony(context)?.networkOperatorName }.getOrNull().orEmpty()
        return listOfNotNull(
            SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date()),
            level.takeIf { it >= 0 }?.let { "$it %" + if (battery?.isCharging == true) " charging" else "" },
            operator.ifEmpty { null },
        ).joinToString(" · ")
    }

    override fun onShow() {
        ShadeFeed.grantAccess(context)
        Torch.watch(context)
        ShadeFeed.onChange = { refresh() }
        ShadeFeed.reload()
        handler.post(ticker)
    }

    override fun onHide() {
        handler.removeCallbacks(ticker)
        ShadeFeed.onChange = null
    }

    override fun rows(): List<Row> {
        val rows = mutableListOf<Row>()
        val kinds = mutableListOf<Entry>()
        fun add(row: Row, entry: Entry = Entry.None) {
            rows += row
            kinds += entry
        }

        add(header("Notifications"))
        ShadeFeed.mediaLine()?.let { line ->
            add(Row(line, "${ShadeFeed.mediaApp(context)} · ok play/pause · ←→ track", adjust = { ShadeFeed.skip(it) }, ok = { ShadeFeed.playPause() }), Entry.Media)
        }
        val now = System.currentTimeMillis()
        val items = ShadeFeed.notifications
        items.forEach { n ->
            val meta = listOf(n.app, ShadeRules.ago(now, n.postTime), n.text).filter { it.isNotEmpty() }.joinToString(" · ")
            add(Row(n.title, meta, adjust = { if (it < 0) dismiss(n) }, ok = { openNotification(n) }), Entry.Item(n))
        }
        when {
            !ShadeFeed.connected -> add(Row("No notifications yet", "waiting for Android to hand them over"))
            items.isEmpty() && ShadeFeed.mediaLine() == null -> add(Row("No notifications"))
        }
        if (items.any { it.clearable }) add(Row("Clear all", "dismisses every one that can be") { ShadeFeed.clearAll() })

        add(header("Quick settings"))
        tiles().forEach { (row, entry) -> add(row, entry) }

        entries = kinds
        return rows
    }

    private fun tiles(): List<Pair<Row, Entry>> = buildList {
        val wifi = Network.wifi(context)
        val wifiOn = wifi?.isWifiEnabled == true
        add(
            Row("Wi-Fi", if (wifiOn) Network.connectedSsid(context) ?: "on, not connected" else "off", marked = wifiOn, ok = {
                @Suppress("DEPRECATION")
                if (wifi?.setWifiEnabled(!wifiOn) != true) say("Android didn't switch Wi-Fi")
            }) to Entry.Tile("Wi-Fi", Screens.WIFI),
        )
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter != null) {
            val btOn = adapter.isEnabled
            val connected = if (btOn) runCatching {
                adapter.bondedDevices.orEmpty().filter { Hidden.call(it, "isConnected") as? Boolean == true }.map { it.name ?: it.address }
            }.getOrDefault(emptyList()) else emptyList()
            add(
                Row("Bluetooth", if (!btOn) "off" else connected.joinToString(", ").ifEmpty { "on" }, marked = btOn, ok = {
                    @Suppress("DEPRECATION")
                    val done = if (btOn) adapter.disable() else adapter.enable()
                    if (!done) say("Android didn't switch Bluetooth")
                }) to Entry.Tile("Bluetooth", Screens.BLUETOOTH),
            )
        }
        if (Network.telephony(context)?.simState == android.telephony.TelephonyManager.SIM_STATE_READY) {
            val data = Network.dataOn(context)
            add(
                Row("Mobile data", onOff(data), marked = data, ok = {
                    if (!Network.setData(context, !data)) say("Android didn't change mobile data")
                }) to Entry.Tile("Mobile data", Screens.MOBILE),
            )
        }
        val airplane = Network.airplane(context)
        add(
            Row("Airplane mode", onOff(airplane), marked = airplane, ok = {
                if (!Network.setAirplane(context, !airplane)) say("Android didn't change airplane mode")
            }) to Entry.Tile("Airplane mode", Screens.NETWORK),
        )
        if (Torch.available(context)) {
            val torch = Torch.on
            add(
                Row("Flashlight", onOff(torch), marked = torch, ok = {
                    if (!Torch.set(context, !torch)) say("the flashlight is busy")
                }) to Entry.Tile("Flashlight", null),
            )
        }
        val dnd = Notifications.dndOn(context)
        add(
            Row("Do not disturb", onOff(dnd), marked = dnd, ok = {
                if (!Notifications.setDnd(context, !dnd)) say("Android didn't change it")
            }) to Entry.Tile("Do not disturb", Screens.NOTIFICATIONS),
        )
        Sound.audio(context)?.let { audio ->
            val modes = listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT)
            val step = { direction: Int ->
                val next = modes[(modes.indexOf(audio.ringerMode).coerceAtLeast(0) + direction + modes.size) % modes.size]
                runCatching { audio.ringerMode = next }.onFailure { say("Android didn't change the mode") }
                Unit
            }
            add(Row("Sound", "${Sound.mode(context)} · ←→ or ok change", adjust = step, ok = { step(1) }) to Entry.Tile("Sound", Screens.SOUND))
        }
        val brightness = context.systemInt(Settings.System.SCREEN_BRIGHTNESS, 128)
        add(
            Row("Brightness", bar((brightness * 20 + 127) / 255, 20), adjust = { direction ->
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (brightness + direction * 13).coerceIn(1, 255))
            }) to Entry.Tile("Brightness", Screens.DISPLAY),
        )
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm != null) {
            val location = lm.isLocationEnabled
            add(
                Row("Location", onOff(location), marked = location, ok = {
                    if (Hidden.call(lm, "setLocationEnabledForUser", Hidden.BOOLEAN to !location, UserHandle::class.java to Process.myUserHandle()) == null) {
                        say("Android didn't change it")
                    }
                }) to Entry.Tile("Location", Screens.LOCATION),
            )
        }
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null) {
            val saver = power.isPowerSaveMode
            add(
                Row("Battery saver", onOff(saver), marked = saver, ok = {
                    if (Hidden.call(power, "setPowerSaveModeEnabled", Hidden.BOOLEAN to !saver) as? Boolean != true) say("Android didn't change it")
                }) to Entry.Tile("Battery saver", Screens.BATTERY),
            )
        }
        OuterScreen.read(context)?.let { s ->
            val outer = OuterScreen.flag(s, "enabled")
            add(
                Row("Outer screen", onOff(outer), marked = outer, ok = {
                    if (!OuterScreen.write(context, "enabled", !outer)) say("the outer screen didn't take it")
                }) to Entry.Tile("Outer screen", Screens.OUTER),
            )
        }
        add(Row("All settings", "") { open(Screens.MAIN) } to Entry.Tile("Settings", null))
    }

    private fun entry(): Entry = entries.getOrNull(host.selected) ?: Entry.None

    override fun initialRow(): Int {
        rows()
        val firstTile = entries.indexOfFirst { it is Entry.Tile }
        val firstNews = entries.indexOfFirst { it is Entry.Item || it is Entry.Media }
        return when {
            panel == "settings" -> firstTile
            firstNews >= 0 -> firstNews
            else -> firstTile
        }.coerceAtLeast(0)
    }

    /** Back to where [panel] opens: the system asked again, with the shade already open. */
    fun reopen(panel: String?) {
        this.panel = panel
        host.select(initialRow())
        refresh()
    }

    override fun hint(): String = when (val e = entry()) {
        is Entry.Item -> if (e.notification.clearable) "ok open · ← dismiss · * actions · ‹ close" else "ok open · * actions · ‹ close"
        is Entry.Media -> "ok play/pause · ←→ track · ‹ close"
        is Entry.Tile -> when (e.name) {
            "Brightness" -> "←→ change · * more · ‹ close"
            "Settings" -> "ok open · ‹ close"
            else -> "ok switch · * more · ‹ close"
        }
        Entry.None -> "‹ close · * menu"
    }

    override fun menu(): List<Pair<String, () -> Unit>> = when (val e = entry()) {
        is Entry.Item -> buildList {
            val n = e.notification
            n.actions.forEach { action ->
                val label = action.title?.toString().orEmpty().ifEmpty { "Action" }
                add(label to { runAction(n, action) })
            }
            if (n.clearable) add("Dismiss" to { dismiss(n) })
            add("Clear all" to { ShadeFeed.clearAll() })
            add("${n.app}'s notifications" to { open(Screens.APP, n.packageName) })
        }
        is Entry.Tile -> listOfNotNull(e.page?.let { page -> "${e.name} settings" to { open(page) } })
        else -> emptyList()
    }

    private fun dismiss(n: ShadeNotification) {
        if (n.clearable) ShadeFeed.dismiss(n.key)
    }

    /** Opens a notification as tapping it does: its intent, sent as the shade (which is in front,
     *  so may start the app's screen), and gone if the app asked for that. */
    private fun openNotification(n: ShadeNotification) {
        val sent = n.open?.let { send(it, null) } ?: false
        if (n.autoCancel) ShadeFeed.dismiss(n.key)
        if (sent) host.close() else if (n.open == null) say("nothing opens from it")
    }

    private fun runAction(n: ShadeNotification, action: Notification.Action) {
        val inputs = action.remoteInputs?.filter { it.allowFreeFormInput }.orEmpty()
        if (inputs.isEmpty()) {
            if (send(action.actionIntent, null)) host.close()
            return
        }
        host.ask(FieldRequest("${action.title ?: "reply"} to ${n.title}", FieldKind.TEXT) { text ->
            if (text.isBlank()) return@FieldRequest
            val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
            val fill = Intent()
            RemoteInput.addResultsToIntent(inputs.toTypedArray(), fill, results)
            if (send(action.actionIntent, fill)) say("sent")
        })
    }

    private fun send(intent: PendingIntent, fill: Intent?): Boolean = try {
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        intent.send(context, 0, fill, null, null, null, options.toBundle())
        true
    } catch (e: PendingIntent.CanceledException) {
        say("the app took it back")
        false
    }
}
