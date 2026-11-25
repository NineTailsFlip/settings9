package io.github.ninetailsflip.settings

import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import java.net.Inet4Address

object About {
    fun model(): String = listOf(Build.MANUFACTURER, Build.MODEL).distinct().joinToString(" ")
}

/** What the phone is: model, Android and LineageOS versions, its numbers and addresses. OK on
 *  the build number seven times turns on the developer options, as on any Android. */
// The phone's own number and IMEI are exactly what this page is for.
@android.annotation.SuppressLint("HardwareIds")
class AboutScreen : Screen("About phone") {
    private var buildPresses = 0

    override fun subtitle() = About.model()

    override fun rows(): List<Row> {
        val resolver = context.contentResolver
        val name = Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
        val tm = context.getSystemService(TelephonyManager::class.java)
        val imei = runCatching { tm?.imei }.getOrNull()
        val number = runCatching { tm?.line1Number }.getOrNull()
        val ip = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm?.getLinkProperties(cm.activeNetwork)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        }.getOrNull()
        val uptime = SystemClock.elapsedRealtime() / 60_000
        val developer = context.globalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        return buildList {
            add(Row("Device name", name) {
                host.ask(FieldRequest("The phone's name", FieldKind.TEXT, name) { typed ->
                    if (typed.isNotBlank()) Settings.Global.putString(resolver, Settings.Global.DEVICE_NAME, typed.trim())
                })
            })
            add(Row("Model", About.model()))
            add(Row("Android", "${Build.VERSION.RELEASE} · security patch ${Build.VERSION.SECURITY_PATCH}"))
            Hidden.property("ro.lineage.version").takeIf { it.isNotEmpty() }?.let { add(Row("LineageOS", it)) }
            add(Row("Build number", Build.DISPLAY + when {
                developer -> " · developer options are on"
                buildPresses > 0 -> " · ${7 - buildPresses} more"
                else -> ""
            }) { pressBuild(developer) })
            add(Row("Kernel", System.getProperty("os.version").orEmpty()))
            add(header("Numbers"))
            add(Row("Phone number", number?.ifEmpty { null } ?: "unknown"))
            add(Row("IMEI", imei ?: "unknown"))
            add(Row("IP address", ip ?: "not connected"))
            add(Row("Up time", "${uptime / 60} h ${uptime % 60} min"))
        }
    }

    private fun pressBuild(developer: Boolean) {
        if (developer) return say("developer options are on: System → Developer options")
        buildPresses++
        if (buildPresses >= 7) {
            Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
            buildPresses = 0
            say("you're a developer now")
        }
    }
}
