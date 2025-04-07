package io.openremote.orlib.service.espprovision

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import io.openremote.orlib.service.ESPProvisionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.UUID

class DeviceConnection(val deviceRegistry: DeviceRegistry, var callbackChannel: CallbackChannel? = null) {

    init {
        EventBus.getDefault().register(this)
    }
// TODO: must un-register -> need a clean-up routine

    private var bleStatus: BLEStatus = BLEStatus.DISCONNECTED

    var deviceId: UUID? = null
        private set

    private var configChannel: ORConfigChannel? = null

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH])
    fun connectTo(deviceId: String, pop: String? = null, username: String? = null) {

        if (deviceRegistry.bleScanning) {
            deviceRegistry.stopDevicesScan()
        }

        val devId = UUID.fromString(deviceId)
        val dev = deviceRegistry.getDeviceWithId(devId)

        if (dev != null) {
//            device = dev.device // TODO: should I set this ? to what ?
            this.deviceId = devId

            espDevice?.proofOfPossession = pop ?: "abcd1234"
            espDevice?.userName = username ?: "UNUSED"
            espDevice?.connectBLEDevice(dev.device, dev.serviceUuid)

        }
// TODO send status message back
    }

    fun exitProvisioning() {
        if (!isConnected) {
            // TODO
        }
        // Is IO OK ?
        CoroutineScope(Dispatchers.IO).launch {
            try {
//                configChannel?.exitProvisioning()
            } catch (e: Exception) {
                // Handle error (log or show UI feedback)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDeviceConnectionEvent(event: DeviceConnectionEvent) {

        when (event.getEventType()) {
            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                Log.d(ESPProvisionProvider.TAG, "Device Connected Event Received")
//                setSecurityTypeFromVersionInfo() // TODO: do we need this ?


                bleStatus = BLEStatus.CONNECTED

                configChannel = ORConfigChannel() // TODO: should implement / pass device


                callbackChannel?.sendMessage("CONNECT_TO_DEVICE", mapOf(
                    "id" to (deviceId?.toString() ?: ""),
                    "status" to "connected"))
                // TODO: proper enum for status
            }
/*
            ESPConstants.EVENT_DEVICE_DISCONNECTED -> if (espDevice != null && espDevice!!.getTransportType() == ESPConstants.TransportType.TRANSPORT_BLE) {
                Toast.makeText(this@AddDeviceActivity, "Device disconnected", Toast.LENGTH_LONG)
                    .show()
                finish()
            } else {
                if (!isFinishing()) {
                    askForManualDeviceConnection()
                }
            }

            ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> if (espDevice != null && espDevice!!.getTransportType() == ESPConstants.TransportType.TRANSPORT_BLE) {
                alertForDeviceNotSupported("Failed to connect with device")
            } else {
                if (!isFinishing()) {
                    askForManualDeviceConnection()
                }
            }*/
        }
    }


    val espDevice: ESPDevice?
        get() = deviceRegistry.provisionManager?.espDevice

    val isConnected: Boolean
        get() = bleStatus == BLEStatus.CONNECTED && espDevice != null && configChannel != null

    enum class BLEStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }
}