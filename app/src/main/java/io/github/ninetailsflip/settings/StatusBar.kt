package io.github.ninetailsflip.settings

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import life.furrer.nine.core.prefs.NinePrefs
import life.furrer.nine.core.ui.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── What the bar says ───────────────────────────────────────────────────────────────────────

enum class Link { OFF, ON, CONNECTED }
enum class Ringer { NORMAL, VIBRATE, SILENT }
enum class Tone { BRIGHT, DIM, ALERT }

/** What the status bar shows, apart from Android's classes. [signal] is 0..4, -1 with no
 *  service, null without a SIM; [battery] is a percentage, null when unknown. */
data class BarState(
    val time: String = "",
    val unread: Int = 0,
    val alarm: Boolean = false,
    val dnd: Boolean = false,
    val ringer: Ringer = Ringer.NORMAL,
    val bluetooth: Link = Link.OFF,
    val wifi: Link = Link.OFF,
    val airplane: Boolean = false,
    val signal: Int? = null,
    val battery: Int? = null,
    val charging: Boolean = false,
)

/** One mark on the right of the bar. With too little room the lowest [priority] goes first. */
data class Mark(val text: String, val tone: Tone, val priority: Int)

object BarLine {
    const val ALARM = "◷"
    private const val BARS = "▂▄▆█"

    /** The marks, left to right: what's new, then the modes, the radios, the battery. */
    fun marks(s: BarState): List<Mark> = buildList {
        if (s.unread > 0) add(Mark("✉${s.unread}", Tone.BRIGHT, 6))
        if (s.alarm) add(Mark(ALARM, Tone.DIM, 1))
        if (s.dnd) add(Mark("DND", Tone.BRIGHT, 4))
        when (s.ringer) {
            Ringer.VIBRATE -> add(Mark("vib", Tone.DIM, 2))
            Ringer.SILENT -> add(Mark("mute", Tone.DIM, 3))
            Ringer.NORMAL -> {}
        }
        if (s.bluetooth != Link.OFF) add(Mark("BT", if (s.bluetooth == Link.CONNECTED) Tone.BRIGHT else Tone.DIM, 5))
        if (s.wifi != Link.OFF) add(Mark("Wi-Fi", if (s.wifi == Link.CONNECTED) Tone.BRIGHT else Tone.DIM, 7))
        if (s.airplane) {
            add(Mark("✈", Tone.BRIGHT, 8))
        } else if (s.signal != null) {
            add(Mark(signal(s.signal), if (s.signal > 0) Tone.BRIGHT else Tone.DIM, 8))
        }
        s.battery?.let {
            val tone = if (!s.charging && it <= 15) Tone.ALERT else Tone.BRIGHT
            add(Mark("$it %" + if (s.charging) "+" else "", tone, 9))
        }
    }

    /** "▂▄▆" for three bars of four, "▁" for none, "×" with no service. */
    fun signal(level: Int): String = when {
        level < 0 -> "×"
        level == 0 -> "▁"
        else -> BARS.take(level.coerceAtMost(4))
    }

    /** The marks that fit: drops the least important until [width] of them fits [room]. */
    fun fit(marks: List<Mark>, room: Float, width: (List<Mark>) -> Float): List<Mark> {
        var shown = marks
        while (shown.isNotEmpty() && width(shown) > room) {
            val least = shown.minBy { it.priority }
            shown = shown - least
        }
        return shown
    }
}

/** A new notification as the bar shows it for a few seconds, in place of SystemUI's pop-up. */
data class HeadsUp(val key: String, val line: String, val open: PendingIntent?, val sticky: Boolean)

object HeadsUpRules {
    /** The facts the decision takes, apart from Android's classes. */
    data class Posted(
        val importance: Int,
        val ongoing: Boolean,
        val groupSummary: Boolean,
        val media: Boolean,
        val alertOnce: Boolean,
        val seen: Boolean,
        val own: Boolean,
    )

    /** Whether a posted notification shows in the bar: the ones SystemUI would pop up, so
     *  urgent (importance high), not ongoing, not a group's summary, not the music, not an
     *  update that asked to alert only once, not Settings' own. */
    fun shows(p: Posted): Boolean =
        p.importance >= NotificationManager.IMPORTANCE_HIGH && !p.ongoing && !p.groupSummary &&
            !p.media && !(p.alertOnce && p.seen) && !p.own

    /** "Mom: are you coming?"; a notification with no text says its app: "Clock: Alarm". */
    fun line(app: String, title: String?, text: String?): String {
        val t = title?.trim().orEmpty()
        val x = text?.trim().orEmpty().replace('\n', ' ')
        return when {
            t.isNotEmpty() && x.isNotEmpty() -> "$t: $x"
            t.isNotEmpty() -> "$app: $t"
            x.isNotEmpty() -> "$app: $x"
            else -> app
        }
    }
}

// ── The service ─────────────────────────────────────────────────────────────────────────────

/**
 * The status bar: one line over SystemUI's, in the Nine look. The time on the left, the marks
 * on the right ([BarLine]); a new urgent notification takes the line for a few seconds
 * ([HeadsUp]) in place of SystemUI's pop-up, which it turns off. A tap opens the shade (or the
 * notification on the line).
 *
 * The system keeps it running like SystemUI: it binds it from boot (android_vendor_thor2's patch
 * 0011, ro.config.thor2_statusbar), so it's direct-boot aware, and the patch keeps the status bar
 * always shown and SystemUI's panel shut. While the bar is up, SystemUI draws nothing of its own
 * in its place (StatusBarManager.disable); without it, SystemUI's own icons come back.
 */
class StatusBarService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var view: BarView? = null
    private var state = BarState()
    private var headsUp: HeadsUp? = null
    private val disableToken = Binder()
    private var telephonyCallback: TelephonyCallback? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var signal = -1
    private var inService = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        instance = this
        show()
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction("android.intent.action.SIM_STATE_CHANGED")
        }, RECEIVER_EXPORTED)
        watchNetwork()
        watchTelephony()
        ShadeFeed.onBarChange = { main.post { refresh() } }
        refresh()
    }

    override fun onDestroy() {
        instance = null
        ShadeFeed.onBarChange = null
        runCatching { unregisterReceiver(receiver) }
        telephonyCallback?.let { cb -> runCatching { getSystemService(TelephonyManager::class.java)?.unregisterTelephonyCallback(cb) } }
        networkCallback?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
        main.removeCallbacksAndMessages(null)
        view?.let { v ->
            runCatching { getSystemService(WindowManager::class.java).removeView(v) }
            systemUiDraws(true)
            runCatching { Settings.Global.putInt(contentResolver, HEADS_UP_SETTING, 1) }
        }
        view = null
        super.onDestroy()
    }

    // ── The window ──

    private fun height(): Int {
        val wm = getSystemService(WindowManager::class.java)
        val fromInsets = runCatching {
            wm.maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        }.getOrDefault(0)
        return if (fromInsets > 0) fromInsets else (24 * resources.displayMetrics.density).toInt()
    }

    @Suppress("DEPRECATION")
    private fun show() {
        val wm = getSystemService(WindowManager::class.java)
        val bar = BarView(this) { tapped() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height(),
            TYPE_STATUS_BAR_ADDITIONAL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP
            title = "NineStatusBar"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        try {
            wm.addView(bar, params)
            view = bar
            systemUiDraws(false)
            // SystemUI's own pop-ups: the bar shows new notifications instead. Sound and
            // vibration stay (they're NotificationManager's, not SystemUI's).
            runCatching { Settings.Global.putInt(contentResolver, HEADS_UP_SETTING, 0) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "No status bar window", e)
        }
    }

    /** SystemUI's clock, icons and notification icons: off while the bar covers them. */
    @SuppressLint("WrongConstant") // "statusbar": StatusBarManager, hidden
    private fun systemUiDraws(on: Boolean) {
        val sbm = getSystemService("statusbar") ?: return
        val what = if (on) 0 else DISABLE_CLOCK or DISABLE_NOTIFICATION_ICONS or DISABLE_SYSTEM_INFO
        Hidden.call(sbm, "disable", Hidden.INT to what)
        Hidden.call(sbm, "disable2", Hidden.INT to if (on) 0 else DISABLE2_SYSTEM_ICONS)
    }

    private fun tapped() {
        val shown = headsUp
        if (shown != null) {
            clearHeadsUp()
            if (shown.open != null && !locked()) {
                runCatching {
                    shown.open.send(this, 0, null, null, null, null,
                        ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle())
                }
                return
            }
        }
        if (locked()) return
        runCatching {
            startActivity(Intent(this, ShadeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun locked() = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    // ── What it shows ──

    private fun refresh() {
        state = read()
        view?.show(state, headsUp)
    }

    private fun read(): BarState {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) l * 100 / s else null
        }
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val audio = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        return BarState(
            time = time(),
            unread = ShadeFeed.notifications.count { it.clearable },
            alarm = getSystemService(AlarmManager::class.java)?.nextAlarmClock != null,
            dnd = (nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL) > NotificationManager.INTERRUPTION_FILTER_ALL,
            ringer = when (audio?.ringerMode) {
                AudioManager.RINGER_MODE_VIBRATE -> Ringer.VIBRATE
                AudioManager.RINGER_MODE_SILENT -> Ringer.SILENT
                else -> Ringer.NORMAL
            },
            bluetooth = bluetooth(),
            wifi = wifi(),
            airplane = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            signal = if (!simReady()) null else if (!inService) -1 else signal.coerceIn(0, 4),
            battery = level,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    private fun time(): String {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    @SuppressLint("MissingPermission")
    private fun bluetooth(): Link {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return Link.OFF
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return Link.OFF
        val profiles = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)
        val connected = runCatching {
            profiles.any { adapter.getProfileConnectionState(it) == BluetoothAdapter.STATE_CONNECTED }
        }.getOrDefault(false)
        return if (connected) Link.CONNECTED else Link.ON
    }

    private fun wifi(): Link {
        val wifi = getSystemService(WifiManager::class.java) ?: return Link.OFF
        if (!wifi.isWifiEnabled) return Link.OFF
        val cm = getSystemService(ConnectivityManager::class.java)
        val connected = cm?.allNetworks.orEmpty().any {
            cm?.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        return if (connected) Link.CONNECTED else Link.ON
    }

    private fun simReady(): Boolean =
        getSystemService(TelephonyManager::class.java)?.simState == TelephonyManager.SIM_STATE_READY

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { refresh() } }
            override fun onLost(network: Network) { main.post { refresh() } }
        }
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
            networkCallback = callback
        }
    }

    private fun watchTelephony() {
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener, TelephonyCallback.ServiceStateListener {
            override fun onSignalStrengthsChanged(strength: SignalStrength) {
                signal = strength.level
                refresh()
            }
            override fun onServiceStateChanged(serviceState: ServiceState) {
                inService = serviceState.state == ServiceState.STATE_IN_SERVICE
                refresh()
            }
        }
        runCatching {
            tm.createForSubscriptionId(SubscriptionManager.getDefaultSubscriptionId()).registerTelephonyCallback(mainExecutor, callback)
            telephonyCallback = callback
        }.onFailure { Log.w(TAG, "No signal strength", it) }
    }

    // ── New notifications ──

    internal fun posted(sbn: StatusBarNotification, ranking: Ranking?, seen: Boolean) {
        val n = sbn.notification
        val rules = HeadsUpRules.Posted(
            importance = ranking?.importance ?: NotificationManager.IMPORTANCE_DEFAULT,
            ongoing = n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            media = n.category == Notification.CATEGORY_TRANSPORT || n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
            alertOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            seen = seen,
            own = sbn.packageName == packageName,
        )
        if (!HeadsUpRules.shows(rules)) return
        if (getSystemService(PowerManager::class.java)?.isInteractive != true) return
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }
            .getOrDefault(sbn.packageName)
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        // An alarm or the like (a full-screen intent) stays until it's gone, and opens its screen.
        val sticky = n.fullScreenIntent != null
        headsUp = HeadsUp(sbn.key, HeadsUpRules.line(app, title, text), n.fullScreenIntent ?: n.contentIntent, sticky)
        main.removeCallbacks(clearRunnable)
        if (!sticky) main.postDelayed(clearRunnable, HEADS_UP_MS)
        refresh()
    }

    internal fun removed(key: String) {
        if (headsUp?.key == key) clearHeadsUp()
    }

    private val clearRunnable = Runnable { clearHeadsUp() }

    private fun clearHeadsUp() {
        main.removeCallbacks(clearRunnable)
        if (headsUp != null) {
            headsUp = null
            refresh()
        }
    }

    companion object {
        private const val TAG = "Settings"
        private const val HEADS_UP_MS = 5_000L
        private const val HEADS_UP_SETTING = "heads_up_notifications_enabled"
        // StatusBarManager's flags (hidden).
        private const val DISABLE_NOTIFICATION_ICONS = 0x00020000
        private const val DISABLE_SYSTEM_INFO = 0x00100000
        private const val DISABLE_CLOCK = 0x00800000
        private const val DISABLE2_SYSTEM_ICONS = 1 shl 1
        // WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL (hidden): above SystemUI's bar.
        private const val TYPE_STATUS_BAR_ADDITIONAL = WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW + 41

        @Volatile internal var instance: StatusBarService? = null
            private set
    }
}

// ── The view ────────────────────────────────────────────────────────────────────────────────

/** The bar's one line, drawn: the time and the marks, or a notification's line. */
@SuppressLint("ViewConstructor")
class BarView(context: Context, private val onTap: () -> Unit) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TEXT_SP * resources.displayMetrics.scaledDensity
        typeface = if (runCatching { NinePrefs.useMonospaceFont(context) }.getOrDefault(true)) Typeface.MONOSPACE else Typeface.DEFAULT
    }
    private val side = SIDE_DP * resources.displayMetrics.density
    private val gap get() = paint.measureText("  ")
    private var state = BarState()
    private var headsUp: HeadsUp? = null
    private var downY = 0f

    init {
        setBackgroundColor(Palette.BLACK)
        contentDescription = "Status bar"
    }

    fun show(state: BarState, headsUp: HeadsUp?) {
        this.state = state
        this.headsUp = headsUp
        invalidate()
    }

    private fun color(tone: Tone) = when (tone) {
        Tone.BRIGHT -> Palette.WHITE
        Tone.DIM -> Palette.GRAY_MUTED
        Tone.ALERT -> Palette.RED
    }

    private fun width(marks: List<Mark>): Float =
        marks.sumOf { paint.measureText(it.text).toDouble() }.toFloat() + gap * (marks.size - 1).coerceAtLeast(0)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val baseline = (height - paint.descent() - paint.ascent()) / 2f
        val room = width - 2 * side
        val line = headsUp
        if (line != null) {
            paint.color = Palette.WHITE
            canvas.drawText(ellipsize(line.line, room), side, baseline, paint)
            return
        }
        paint.color = Palette.WHITE
        canvas.drawText(state.time, side, baseline, paint)
        val left = paint.measureText(state.time) + gap
        val marks = BarLine.fit(BarLine.marks(state), room - left, ::width)
        var x = width - side - width(marks)
        for (mark in marks) {
            paint.color = color(mark.tone)
            canvas.drawText(mark.text, x, baseline, paint)
            x += paint.measureText(mark.text) + gap
        }
    }

    private fun ellipsize(text: String, room: Float): String {
        if (paint.measureText(text) <= room) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > room) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> downY = event.y
            MotionEvent.ACTION_UP -> {
                // A tap, or a swipe down from the bar: the stock gesture for the shade.
                if (event.y - downY >= 0) onTap()
            }
        }
        return true
    }

    companion object {
        private const val TEXT_SP = 13f
        private const val SIDE_DP = 8
    }
}
