package com.leandro.btbridge

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BtDeviceUi(
    val name: String,
    val address: String,
    val bonded: Boolean
)

/**
 * Maneja el escaneo y el emparejado de dispositivos Bluetooth.
 * El emparejado real se hace con createBond(); el audio (A2DP) lo conecta el sistema
 * una vez emparejado. Esta app NO reemplaza el stack Bluetooth: solo facilita el flujo.
 */
class BtDevicesViewModel(app: Application) : AndroidViewModel(app) {

    private val adapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _devices = MutableStateFlow<List<BtDeviceUi>>(emptyList())
    val devices: StateFlow<List<BtDeviceUi>> = _devices

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val found = LinkedHashMap<String, BtDeviceUi>()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    d?.let { addDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun loadBonded() {
        adapter ?: return
        found.clear()
        adapter.bondedDevices?.forEach { addDevice(it) }
        emit()
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val a = adapter ?: return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        getApplication<Application>().registerReceiver(receiver, filter)
        loadBonded()
        if (a.isDiscovering) a.cancelDiscovery()
        _scanning.value = true
        a.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        adapter?.cancelDiscovery()
        _scanning.value = false
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
    }

    /** Dispara el emparejado del sistema con el dispositivo elegido. */
    @SuppressLint("MissingPermission")
    fun pair(address: String) {
        val device = adapter?.getRemoteDevice(address) ?: return
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            device.createBond()
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(d: BluetoothDevice) {
        val name = d.name ?: "(sin nombre)"
        found[d.address] = BtDeviceUi(
            name = name,
            address = d.address,
            bonded = d.bondState == BluetoothDevice.BOND_BONDED
        )
        emit()
    }

    private fun emit() {
        _devices.value = found.values.sortedByDescending { it.bonded }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
