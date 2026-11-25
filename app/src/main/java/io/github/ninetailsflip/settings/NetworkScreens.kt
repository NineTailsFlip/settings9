package io.github.ninetailsflip.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.ScanResult
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.TelephonyManager

/** Wi-Fi, mobile data and airplane mode, through the system's own (partly @SystemApi) calls. */
object Network {
    fun wifi(context: Context): WifiManager? = context.applicationContext.getSystemService(WifiManager::class.java)

    @Suppress("DEPRECATION")
    fun connectedSsid(context: Context): String? {
        val info = wifi(context)?.connectionInfo ?: return null
        if (info.supplicantState != SupplicantState.COMPLETED) return null
        return info.ssid?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID }
    }

    fun wifiSummary(context: Context): String = when {
        airplane(context) -> "airplane mode"
        wifi(context)?.isWifiEnabled != true -> "Wi-Fi off"
        else -> connectedSsid(context)?.let { "Wi-Fi: $it" } ?: "Wi-Fi on, not connected"
    }

    fun airplane(context: Context) = context.globalInt(Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun setAirplane(context: Context, on: Boolean): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return Hidden.call(cm, "setAirplaneMode", Hidden.BOOLEAN to on) != null || airplane(context) == on
    }

    fun telephony(context: Context): TelephonyManager? = context.getSystemService(TelephonyManager::class.java)

    fun dataOn(context: Context) = runCatching { telephony(context)?.isDataEnabled == true }.getOrDefault(false)

    @Suppress("DEPRECATION")
    fun setData(context: Context, on: Boolean) = runCatching { telephony(context)?.setDataEnabled(on) }.isSuccess

    fun roamingOn(context: Context) = context.globalInt(Settings.Global.DATA_ROAMING, 0) == 1

    fun setRoaming(context: Context, on: Boolean): Boolean {
        val tm = telephony(context) ?: return false
        return Hidden.call(tm, "setDataRoamingEnabled", Hidden.BOOLEAN to on) != null
    }
}

data class WifiNetwork(val ssid: String, val security: WifiSecurity, val level: Int, val saved: Int?)

enum class WifiSecurity { OPEN, PSK, SAE, OTHER }

class NetworkScreen : Screen("Network & internet") {
    override fun rows(): List<Row> {
        val tm = Network.telephony(context)
        val operator = runCatching { tm?.networkOperatorName }.getOrNull().orEmpty()
        return listOf(
            Row("Wi-Fi", Network.wifiSummary(context)) { open(Screens.WIFI) },
            Row("Mobile network", listOfNotNull(operator.ifEmpty { null }, "data ${onOff(Network.dataOn(context))}").joinToString(" · ")) { open(Screens.MOBILE) },
            Row("Airplane mode", onOff(Network.airplane(context)), adjust = { toggleAirplane() }),
        )
    }

    private fun toggleAirplane() {
        if (!Network.setAirplane(context, !Network.airplane(context))) say("Android didn't change airplane mode")
    }
}

class MobileScreen : Screen("Mobile network") {
    override fun subtitle() = runCatching { Network.telephony(context)?.networkOperatorName }.getOrNull().orEmpty()
        .ifEmpty { "no SIM or no network" }

    override fun rows(): List<Row> = listOf(
        Row("Mobile data", onOff(Network.dataOn(context)), adjust = {
            if (!Network.setData(context, !Network.dataOn(context))) say("Android didn't change mobile data")
        }),
        Row("Roaming", onOff(Network.roamingOn(context)), adjust = {
            if (!Network.setRoaming(context, !Network.roamingOn(context))) say("Android didn't change roaming")
        }),
        Row("Access point names", "the operator's data settings") {
            start(Intent(Settings.ACTION_APN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    )
}

/** Wi-Fi on or off, the networks around, and joining one - the password typed under the list.
 *  A saved network can be forgotten from the `*` menu. */
class WifiScreen : Screen("Wi-Fi") {
    private var networks: List<WifiNetwork> = emptyList()
    private var connecting: String? = null
    private var listening = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reload()
            refresh()
        }
    }

    override fun subtitle() = Network.wifiSummary(context)
    override fun hint() = "ok join · * menu: forget · ‹ back"

    override fun onShow() {
        if (!listening) {
            val filter = IntentFilter().apply {
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            listening = true
        }
        @Suppress("DEPRECATION")
        if (Network.wifi(context)?.isWifiEnabled == true) runCatching { Network.wifi(context)?.startScan() }
        reload()
    }

    override fun onHide() {
        if (listening) {
            runCatching { context.unregisterReceiver(receiver) }
            listening = false
        }
    }

    @Suppress("DEPRECATION")
    private fun reload() {
        val wifi = Network.wifi(context) ?: return
        if (!wifi.isWifiEnabled) {
            networks = emptyList()
            return
        }
        val saved = runCatching { wifi.configuredNetworks }.getOrNull().orEmpty()
            .associate { it.SSID.orEmpty().removeSurrounding("\"") to it.networkId }
        networks = runCatching { wifi.scanResults }.getOrDefault(emptyList())
            .filter { !it.SSID.isNullOrEmpty() }
            .groupBy { it.SSID }
            .map { (ssid, results) ->
                val best = results.maxBy { it.level }
                WifiNetwork(ssid, securityOf(best), WifiManager.calculateSignalLevel(best.level, 4), saved[ssid])
            }
            .sortedByDescending { it.level }
        if (Network.connectedSsid(context) == connecting) connecting = null
    }

    private fun securityOf(result: ScanResult): WifiSecurity {
        val caps = result.capabilities.orEmpty()
        return when {
            "EAP" in caps -> WifiSecurity.OTHER
            "SAE" in caps && "PSK" !in caps -> WifiSecurity.SAE
            "PSK" in caps -> WifiSecurity.PSK
            "WEP" in caps -> WifiSecurity.OTHER
            else -> WifiSecurity.OPEN
        }
    }

    override fun rows(): List<Row> = buildList {
        val wifi = Network.wifi(context)
        val on = wifi?.isWifiEnabled == true
        add(Row("Wi-Fi", onOff(on), adjust = {
            @Suppress("DEPRECATION")
            if (wifi?.setWifiEnabled(!on) != true) say("Android didn't switch Wi-Fi")
        }))
        if (!on) return@buildList
        add(header("Networks"))
        if (networks.isEmpty()) add(Row("Looking for networks…"))
        val connected = Network.connectedSsid(context)
        networks.forEach { network ->
            add(Row(network.ssid, describe(network, connected), marked = network.ssid == connected) { choose(network, connected) })
        }
    }

    private fun describe(network: WifiNetwork, connected: String?): String = listOfNotNull(
        when (network.ssid) {
            connected -> "connected"
            connecting -> "connecting…"
            else -> if (network.saved != null) "saved" else null
        },
        when (network.security) {
            WifiSecurity.OPEN -> "open"
            WifiSecurity.PSK, WifiSecurity.SAE -> "password"
            WifiSecurity.OTHER -> "enterprise"
        },
        "▂▄▆█".take(network.level.coerceAtLeast(1)),
    ).joinToString(" · ")

    private fun choose(network: WifiNetwork, connected: String?) {
        if (network.ssid == connected) return say("already connected")
        val saved = network.saved
        if (saved != null) {
            @Suppress("DEPRECATION")
            val joined = Network.wifi(context)?.enableNetwork(saved, true) == true
            if (joined) connecting = network.ssid else say("Android didn't join ${network.ssid}")
            return
        }
        when (network.security) {
            WifiSecurity.OPEN -> join(network, null)
            WifiSecurity.OTHER -> say("this network needs a certificate: not supported here yet")
            WifiSecurity.PSK, WifiSecurity.SAE -> host.ask(
                FieldRequest("Password for ${network.ssid}", FieldKind.PASSWORD) { password ->
                    if (password.length < 8) say("a Wi-Fi password has at least 8 characters") else join(network, password)
                },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun join(network: WifiNetwork, password: String?) {
        val config = WifiConfiguration().apply {
            SSID = "\"${network.ssid}\""
            when {
                password == null -> setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN)
                network.security == WifiSecurity.SAE -> {
                    setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                    preSharedKey = "\"$password\""
                }
                else -> {
                    setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK)
                    preSharedKey = "\"$password\""
                }
            }
        }
        val wifi = Network.wifi(context) ?: return
        val id = wifi.addNetwork(config)
        if (id == -1 || !wifi.enableNetwork(id, true)) {
            say("Android didn't take ${network.ssid}")
        } else {
            wifi.saveConfiguration()
            connecting = network.ssid
            say("connecting to ${network.ssid}…")
        }
    }

    override fun menu(): List<Pair<String, () -> Unit>> {
        val saved = networks.filter { it.saved != null }
        return saved.map { network ->
            "Forget ${network.ssid}" to {
                @Suppress("DEPRECATION")
                if (Network.wifi(context)?.removeNetwork(network.saved!!) == true) say("forgot ${network.ssid}") else say("Android didn't forget it")
                reload()
            }
        } + ("Scan again" to {
            @Suppress("DEPRECATION")
            runCatching { Network.wifi(context)?.startScan() }
            say("looking for networks…")
        })
    }
}
