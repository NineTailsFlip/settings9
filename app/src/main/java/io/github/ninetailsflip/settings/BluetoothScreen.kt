package io.github.ninetailsflip.settings

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/** Bluetooth on or off, the phone's name, its paired devices and the ones around to pair with.
 *  OK on a paired device connects it (Android reconnects paired devices itself; this asks it
 *  now), `*` forgets one; OK on a new device pairs - a PIN, if the device wants one, comes as
 *  Android's own pairing prompt. */
class BluetoothScreen : Screen("Bluetooth") {
    private val found = linkedMapOf<String, BluetoothDevice>()
    private var listening = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                @Suppress("DEPRECATION")
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null && device.bondState == BluetoothDevice.BOND_NONE) found[device.address] = device
            }
            refresh()
        }
    }

    private fun adapter(): BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun subtitle(): String {
        val adapter = adapter() ?: return "this phone has no Bluetooth"
        return if (adapter.isEnabled) "visible to others as ${adapter.name}" else "off"
    }

    override fun hint() = "ok connect or pair · * menu · ‹ back"

    override fun onShow() {
        if (!listening) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            listening = true
        }
    }

    override fun onHide() {
        if (listening) {
            runCatching { context.unregisterReceiver(receiver) }
            listening = false
        }
        runCatching { adapter()?.cancelDiscovery() }
    }

    override fun rows(): List<Row> = buildList {
        val adapter = adapter()
        if (adapter == null) {
            add(Row("No Bluetooth on this phone"))
            return@buildList
        }
        val on = adapter.isEnabled
        add(Row("Bluetooth", onOff(on), adjust = { switch(adapter, !on) }))
        if (!on) return@buildList
        add(Row("Phone name", adapter.name.orEmpty()) { rename(adapter) })
        val paired = adapter.bondedDevices.orEmpty().sortedBy { it.name.orEmpty() }
        add(header("Paired devices"))
        if (paired.isEmpty()) add(Row("None yet"))
        paired.forEach { device ->
            val connected = isConnected(device)
            add(Row(device.name ?: device.address, if (connected) "connected" else "paired", marked = connected) { connect(device) })
        }
        add(header("Pair a new device"))
        add(Row(if (adapter.isDiscovering) "Looking for devices…" else "Look for devices", "devices around must be in pairing mode") {
            found.clear()
            if (!adapter.startDiscovery()) say("Android didn't start looking")
        })
        found.values.forEach { device ->
            add(Row(device.name ?: device.address, if (device.bondState == BluetoothDevice.BOND_BONDING) "pairing…" else "OK pairs") {
                adapter.cancelDiscovery()
                if (!device.createBond()) say("Android didn't start pairing")
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun switch(adapter: BluetoothAdapter, on: Boolean) {
        val done = if (on) adapter.enable() else adapter.disable()
        if (!done) say("Android didn't switch Bluetooth")
    }

    private fun rename(adapter: BluetoothAdapter) {
        host.ask(FieldRequest("The name others see", FieldKind.TEXT, adapter.name.orEmpty()) { name ->
            if (name.isBlank() || !adapter.setName(name.trim())) say("Android didn't take the name")
        })
    }

    private fun isConnected(device: BluetoothDevice) = Hidden.call(device, "isConnected") as? Boolean == true

    private fun connect(device: BluetoothDevice) {
        // BluetoothAdapter.connectAllEnabledProfiles(device): @SystemApi.
        val adapter = adapter() ?: return
        if (Hidden.call(adapter, "connectAllEnabledProfiles", BluetoothDevice::class.java to device) == null) {
            say("Android didn't connect ${device.name ?: device.address}")
        } else {
            say("connecting…")
        }
    }

    override fun menu(): List<Pair<String, () -> Unit>> {
        val adapter = adapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.orEmpty().map { device ->
            "Forget ${device.name ?: device.address}" to {
                if (Hidden.call(device, "removeBond") as? Boolean == true) say("forgot it") else say("Android didn't forget it")
            }
        }
    }
}
