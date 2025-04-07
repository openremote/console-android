package io.openremote.orlib.service.espprovision

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.listeners.BleScanListener
import io.openremote.orlib.service.ESPProviderErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID

/*
// TODO: do we really need to for Android ?
interface ORESPProvisionManager {
    suspend fun searchESPDevices(devicePrefix: String, transport: ESPConstants.TransportType, security: ESPConstants.SecurityType): List<
            DeviceRegistry.DiscoveredDevice>
    fun stopESPDevicesSearch()
}
 */

class EspressifProvisionManager(private val provisionManager: ESPProvisionManager) {
    init {
        provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_1)
    }

    @androidx.annotation.RequiresPermission(allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.BLUETOOTH_ADMIN, android.Manifest.permission.BLUETOOTH])
    suspend fun searchESPDevices(devicePrefix: String): List<DeviceRegistry.DiscoveredDevice> {
        return withContext(Dispatchers.Main) {

            // Was Dispatchers.IO


            suspendCancellableCoroutine { continuation ->
                var alreadyResumed = false // TODO: does it behave same as iOS ? will we receive 2 times the call ?
                var devices: MutableList<DeviceRegistry.DiscoveredDevice> = mutableListOf()

                provisionManager.searchBleEspDevices(devicePrefix, object: BleScanListener {
                    override fun scanStartFailed() {
                        // Don't care about that information
                    }

                    override fun onPeripheralFound(device: BluetoothDevice, scanResult: ScanResult) {
//                        Log.d("espprovision", "Found peripheral")
                        if (!scanResult.scanRecord?.deviceName.isNullOrEmpty()) {
//                            Log.d("espprovision", "Device name ${scanResult.scanRecord?.deviceName}")

                            var serviceUuid = ""
                            scanResult.scanRecord?.serviceUuids?.firstOrNull()?.toString()?.let { uuid ->
                                serviceUuid = uuid
                            }
//                            Log.d("espprovision", "Service UUID $serviceUuid")
                            scanResult.scanRecord!!.deviceName?.let { deviceName ->
                                // TODO: should only add device if it doesn't exist yet
                                if (devices.find { it.name == deviceName } == null) {
                                    devices.add(DeviceRegistry.DiscoveredDevice(deviceName, serviceUuid, device))
                                    Log.d("espprovision", "Added device, list is now $devices")
                                }
                            }
                        }
                    }

                    override fun scanCompleted() {
                        Log.d("espprovision", "Scan completed")
                        // TODO: I don't want that second param
                        continuation.resume(devices, onCancellation = null)
                    }

                    override fun onFailure(e: Exception) {
                        continuation.cancel(e)
                    }

                })
                /*
                provisionManager.searchESPDevices(devicePrefix, transport, security) { deviceList, error ->
                    if (error != null) {
                        if (!alreadyResumed) {
                            alreadyResumed = true
                            continuation.resumeWith(Result.failure(error))
                        }
                    } else {
                        alreadyResumed = true
                        continuation.resumeWith(Result.success(deviceList ?: emptyList()))
                    }
                }*/
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH])
    fun stopESPDevicesSearch()
    {
        provisionManager.stopBleScan()
    }

    val espDevice: ESPDevice
        get() = provisionManager.espDevice
}

class DeviceRegistry(private val context: Context, searchDeviceTimeout: Long, searchDeviceMaxIterations: Int, var callbackChannel: CallbackChannel? = null) {
    private var loopDetector = LoopDetector(searchDeviceTimeout, searchDeviceMaxIterations)
    var provisionManager: EspressifProvisionManager? = null

    //    var callbackChannel: CallbackChannel? = null
//    private var devices = mutableListOf<DiscoveredDevice>()
//    private val devicesIndex = mutableMapOf<UUID, DiscoveredDevice>()
    var bleScanning = false

    private var devices: MutableList<DiscoveredDevice> = mutableListOf()
    private var devicesIndex: MutableMap<UUID, DiscoveredDevice> = mutableMapOf()

    fun enable() {
        provisionManager = EspressifProvisionManager(ESPProvisionManager.getInstance(context))
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH])
    fun disable() {
        if (bleScanning) stopDevicesScan()
        provisionManager = null
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH])
    fun startDevicesScan(prefix: String? = "") {
        bleScanning = true
        resetDevicesList()
        loopDetector.reset()
        devicesScan(prefix ?: "")
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH])
    fun stopDevicesScan(sendMessage: Boolean = true) {
        bleScanning = false
        provisionManager?.stopESPDevicesSearch()
        if (sendMessage) {
            callbackChannel?.sendMessage("STOP_BLE_SCAN", null)
        }
    }

    @androidx.annotation.RequiresPermission(allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.BLUETOOTH_ADMIN, android.Manifest.permission.BLUETOOTH])
    private fun devicesScan(prefix: String) {
        provisionManager?.let { manager ->
            if (loopDetector.detectLoop()) {
                stopDevicesScan(sendMessage = false)
                sendDeviceScanError(ESPProviderErrorCode.TIMEOUT_ERROR)
                return
            }

            CoroutineScope(Dispatchers.IO).launch {

//                Log.d("DeviceRegistry", "Starting BLE scan")

                try {
                    val deviceList = manager.searchESPDevices(prefix)
                    Log.d("espprovision", "I got a list of devices $deviceList")
                    if (bleScanning) {
                        var devicesChanged = false
                        for (device in deviceList) {
                            if (getDeviceNamed(device.name) == null) {
                                devicesChanged = true
                                registerDevice(device)
                            }
                        }
                        Log.d("espprovision", "devicesChanges $devicesChanged")
                        Log.d("espprovision", "devices $devices")
                        if (devices.isNotEmpty() && devicesChanged) {
                            Log.d("espprovision", "callbackChannel $callbackChannel")
                            callbackChannel?.sendMessage(
                                "START_BLE_SCAN",
                                mapOf("devices" to devices.map { it.info })
                            )
                        }
                        devicesScan(prefix)
                    }
                } catch (e: Exception) {
                    Log.w("DeviceRegistry", "Error during device scan: ${e.localizedMessage}")
                    sendDeviceScanError(ESPProviderErrorCode.GENERIC_ERROR)
                }
            }
        }
    }

    private fun sendDeviceScanError(error: ESPProviderErrorCode, errorMessage: String? = null) {
        val data = mutableMapOf<String, Any>("errorCode" to error.code)

        errorMessage?.let {
            data["errorMessage"] = it
        }

        callbackChannel?.sendMessage(action = "STOP_BLE_SCAN", data = data)
    }

    private fun resetDevicesList() {
        devices = mutableListOf()
        devicesIndex = mutableMapOf()
    }

    private fun getDeviceNamed(name: String): DiscoveredDevice? {
        return devices.firstOrNull { it.name == name }
    }

    fun getDeviceWithId(id: UUID): DiscoveredDevice? {
        return devicesIndex[id]
    }

    private fun registerDevice(device: DiscoveredDevice) {
        devices.add(device)
        devicesIndex[device.id] = device
    }

    data class DiscoveredDevice(val name: String, val serviceUuid: String, val device: BluetoothDevice, val id: UUID = UUID.randomUUID()) {
        val info: Map<String, Any>
            get() = mapOf("id" to id.toString(), "name" to name)
    }
}