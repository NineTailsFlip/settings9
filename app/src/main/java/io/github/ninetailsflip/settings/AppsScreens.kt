package io.github.ninetailsflip.settings

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.text.format.Formatter
import java.io.File

/** An installed app as the Apps page lists it. */
data class AppItem(val packageName: String, val label: String, val system: Boolean, val enabled: Boolean)

object Apps {
    fun list(context: Context, includeSystem: Boolean): List<AppItem> {
        val pm = context.packageManager
        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.activityInfo.packageName }.toSet()
        return pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            .filter { includeSystem || it.packageName in launchable || !isSystem(it) }
            .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString(), isSystem(it), it.enabled) }
            .sortedBy { it.label.lowercase() }
    }

    private fun isSystem(info: ApplicationInfo) = info.flags and ApplicationInfo.FLAG_SYSTEM != 0

    fun notificationsOn(context: Context, packageName: String, uid: Int): Boolean {
        val service = notificationService() ?: return true
        return Hidden.call(service, "areNotificationsEnabledForPackage", String::class.java to packageName, Hidden.INT to uid) as? Boolean ?: true
    }

    fun setNotifications(packageName: String, uid: Int, on: Boolean): Boolean {
        val service = notificationService() ?: return false
        return Hidden.call(service, "setNotificationsEnabledForPackage", String::class.java to packageName, Hidden.INT to uid, Hidden.BOOLEAN to on) != null
    }

    private fun notificationService(): Any? = Hidden.callStatic("android.app.NotificationManager", "getService")

    fun forceStop(context: Context, packageName: String): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        return Hidden.call(am, "forceStopPackage", String::class.java to packageName) != null
    }

    fun uninstall(context: Context, packageName: String) {
        val intent = Intent(context, ScreenActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        context.packageManager.packageInstaller.uninstall(packageName, pending.intentSender)
    }

    /** ActivityManager.clearApplicationUserData(package, observer): hidden; null observer. */
    fun clearData(context: Context, packageName: String): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val observer = runCatching { Class.forName("android.content.pm.IPackageDataObserver") }.getOrNull() ?: return false
        return Hidden.call(am, "clearApplicationUserData", String::class.java to packageName, observer to null) as? Boolean == true
    }

    fun size(info: ApplicationInfo): Long = runCatching { File(info.sourceDir).length() }.getOrDefault(0L)
}

/** Every app: OK opens one. The `*` menu shows or hides the system's own apps and leads to the
 *  default apps. */
class AppsScreen : Screen("Apps") {
    private var showSystem = false
    private var apps: List<AppItem> = emptyList()

    override fun subtitle() = "${apps.size} apps" + if (showSystem) ", system apps included" else ""
    override fun hint() = "ok open · ←→ next letter · * menu"

    override fun onShow() {
        apps = Apps.list(context, showSystem)
    }

    override fun rows(): List<Row> = buildList {
        add(Row("Default apps", "phone, messages, home, browser…") { open(Screens.DEFAULT_APPS) })
        add(Row("Permissions", "which apps may use the camera, location, contacts…") {
            start(Intent("android.intent.action.MANAGE_PERMISSIONS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        add(header("All apps"))
        apps.forEach { app ->
            add(Row(app.label, listOfNotNull(app.packageName, if (!app.enabled) "turned off" else null).joinToString(" · ")) {
                open(Screens.APP, app.packageName)
            })
        }
    }

    override fun jump(from: Int, direction: Int) =
        jumpByKey(listOf<Char?>(null, null, null) + apps.map { it.label.firstOrNull()?.uppercaseChar() }, from, direction)

    override fun menu() = listOf(
        (if (showSystem) "Hide system apps" else "Show system apps") to {
            showSystem = !showSystem
            apps = Apps.list(context, showSystem)
        },
    )
}

/** One app: what it is, and what can be done with it. */
class AppScreen(private val packageName: String) : Screen("App") {
    private var armed: String? = null

    private fun info(): ApplicationInfo? = runCatching {
        context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
    }.getOrNull()

    override fun subtitle() = packageName

    override fun rows(): List<Row> {
        val pm = context.packageManager
        val info = info() ?: return listOf(Row("Not installed", packageName))
        val label = pm.getApplicationLabel(info).toString()
        val version = runCatching { pm.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val notifications = Apps.notificationsOn(context, packageName, info.uid)
        val launch = pm.getLaunchIntentForPackage(packageName)
        return buildList {
            add(Row(label, listOfNotNull(version.ifEmpty { null }, if (system) "system app" else null, Formatter.formatShortFileSize(context, Apps.size(info))).joinToString(" · ")))
            if (launch != null) add(Row("Open") { start(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            add(Row("Notifications", onOff(notifications), adjust = {
                if (!Apps.setNotifications(packageName, info.uid, !notifications)) say("Android didn't change it")
            }))
            add(Row("Permissions", "what it may use") {
                start(
                    Intent("android.intent.action.MANAGE_APP_PERMISSIONS")
                        .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            })
            add(Row("Force stop", if (armed == STOP) "OK again to stop it" else "if it misbehaves") { confirm(STOP) { if (Apps.forceStop(context, packageName)) say("stopped") else say("Android didn't stop it") } })
            if (system) {
                val enabled = info.enabled
                add(Row(if (enabled) "Turn off" else "Turn on", if (armed == TOGGLE) "OK again to confirm" else "a system app can't be uninstalled") {
                    confirm(TOGGLE) {
                        pm.setApplicationEnabledSetting(
                            packageName,
                            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                            0,
                        )
                    }
                })
            } else {
                add(Row("Uninstall", if (armed == UNINSTALL) "OK again to uninstall $label" else "") {
                    confirm(UNINSTALL) {
                        Apps.uninstall(context, packageName)
                        say("uninstalling…")
                    }
                })
            }
            add(Row("Clear data", if (armed == CLEAR) "OK again: its settings, accounts and files go" else "start the app over, as if just installed") {
                confirm(CLEAR) { if (Apps.clearData(context, packageName)) say("cleared") else say("Android didn't clear it") }
            })
        }
    }

    /** A step that can't be undone needs OK twice on the same row; any other key disarms it. */
    private fun confirm(what: String, action: () -> Unit) {
        if (armed == what) {
            armed = null
            action()
        } else {
            armed = what
        }
    }

    override fun hint() = if (armed != null) "ok again to confirm · any other key cancels" else super.hint()

    override fun back(): Boolean {
        if (armed == null) return false
        armed = null
        return true
    }

    private companion object {
        const val STOP = "stop"
        const val TOGGLE = "toggle"
        const val UNINSTALL = "uninstall"
        const val CLEAR = "clear"
    }
}

/** Default apps, each a role Android hands to one app: the phone, messages, home screen,
 *  browser. Choosing opens Android's own list of the apps that can take the role
 *  (PermissionController's MANAGE_DEFAULT_APP page). */
class DefaultAppsScreen : Screen("Default apps") {
    private val roles = listOf(
        RoleManager.ROLE_DIALER to "Phone",
        RoleManager.ROLE_SMS to "Messages",
        RoleManager.ROLE_HOME to "Home screen",
        RoleManager.ROLE_BROWSER to "Browser",
    )

    override fun rows(): List<Row> {
        val rm = context.getSystemService(RoleManager::class.java)
        return roles.filter { (role, _) -> rm?.isRoleAvailable(role) != false }.map { (role, label) ->
            Row(label, holder(role)?.let(::labelOf) ?: "none") {
                start(
                    Intent("android.intent.action.MANAGE_DEFAULT_APP")
                        .putExtra("android.intent.extra.ROLE_NAME", role)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** Who holds [role], through each role's own public API (RoleManager.getRoleHolders needs a
     *  permission only the system's permission controller holds). */
    private fun holder(role: String): String? = runCatching {
        val pm = context.packageManager
        when (role) {
            RoleManager.ROLE_DIALER -> context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
            RoleManager.ROLE_SMS -> Telephony.Sms.getDefaultSmsPackage(context)
            RoleManager.ROLE_HOME -> pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.takeIf { it != "android" }
            RoleManager.ROLE_BROWSER -> pm.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.takeIf { it != "android" }
            else -> null
        }
    }.getOrNull()

    private fun labelOf(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}

/** Do not disturb, what the lock screen shows, and each app's notifications (on its page). */
object Notifications {
    fun dndOn(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter.let {
            it != null && it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }

    /** NotificationManager.setZenMode(mode, conditionId, reason): hidden. 0 off, 1 priority only. */
    fun setDnd(context: Context, on: Boolean): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return Hidden.call(nm, "setZenMode", Hidden.INT to (if (on) 1 else 0), android.net.Uri::class.java to null, String::class.java to "Settings") != null
    }
}

class NotificationsScreen : Screen("Notifications") {
    override fun rows(): List<Row> {
        val dnd = Notifications.dndOn(context)
        val onLock = context.secureInt("lock_screen_show_notifications", 1) == 1
        val private = context.secureInt("lock_screen_allow_private_notifications", 1) == 1
        return listOf(
            Row("Do not disturb", if (dnd) "on: only calls and messages from starred contacts" else "off", adjust = {
                if (!Notifications.setDnd(context, !dnd)) say("Android didn't change it")
            }),
            Row("On the lock screen", onOff(onLock), adjust = {
                Settings.Secure.putInt(context.contentResolver, "lock_screen_show_notifications", if (onLock) 0 else 1)
            }),
            Row("Their contents when locked", if (private) "shown" else "hidden", adjust = {
                Settings.Secure.putInt(context.contentResolver, "lock_screen_allow_private_notifications", if (private) 0 else 1)
            }),
            Row("Per app", "Apps → an app → Notifications") { open(Screens.APPS) },
        )
    }
}

