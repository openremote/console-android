package io.openremote.orlib.service.espprovision

import android.Manifest
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresPermission
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import io.openremote.orlib.service.ESPProviderErrorCode
import io.openremote.orlib.service.ESPProviderException
import io.openremote.orlib.service.ESPProvisionProvider
import io.openremote.orlib.service.ESPProvisionProviderActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

class DeviceConnection(val deviceRegistry: DeviceRegistry, var callbackChannel: CallbackChannel? = null) {

    companion object {
        private const val SEC_TYPE_0: Int = 0
        private const val SEC_TYPE_1: Int = 1
        private const val SEC_TYPE_2: Int = 2

    }
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
    }

    fun disconnectFromDevice() {
        espDevice?.disconnectDevice()
    }

    fun exitProvisioning() {
        if (!isConnected) {
            throw ESPProviderException(
                errorCode = ESPProviderErrorCode.NOT_CONNECTED,
                errorMessage = "No connection established to device"
            )
        }

        // TODO: Is IO OK ?
        CoroutineScope(Dispatchers.IO).launch {
            try {
                configChannel?.exitProvisioning()
            } catch (e: ORConfigChannelError) {
                throw ESPProviderException(ESPProviderErrorCode.COMMUNICATION_ERROR, e.message ?: e.toString())
            } catch (e: Exception) {
                throw ESPProviderException(ESPProviderErrorCode.GENERIC_ERROR, e.toString())
            }
        }
    }

    fun getDeviceInfo(): DeviceInfo {
        if (!isConnected) {
            throw ESPProviderException(
                errorCode = ESPProviderErrorCode.NOT_CONNECTED,
                errorMessage = "No connection established to device"
            )
        }

        // TODO: should not block
        return runBlocking {
            configChannel!!.getDeviceInfo()
        }
    }

    suspend fun sendOpenRemoteConfig(
        mqttBrokerUrl: String,
        mqttUser: String,
        mqttPassword: String,
        assetId: String
    ) {
        if (!isConnected) {
            throw ESPProviderException(
                errorCode = ESPProviderErrorCode.NOT_CONNECTED,
                errorMessage = "No connection established to device"
            )
        }
        try {
            configChannel?.sendOpenRemoteConfig(
                mqttBrokerUrl = mqttBrokerUrl,
                mqttUser = mqttUser,
                mqttPassword = mqttPassword,
                assetId = assetId
            )
        } catch (e: Exception) {
            throw ESPProviderException(
                errorCode = ESPProviderErrorCode.COMMUNICATION_ERROR,
                errorMessage = e.localizedMessage ?: "Unknown error"
            )
        }
    }

    suspend fun getBackendConnectionStatus(): BackendConnectionStatus {
        if (!isConnected) {
            throw ESPProviderException(
                errorCode = ESPProviderErrorCode.NOT_CONNECTED,
                errorMessage = "No connection established to device"
            )
        }
        return try {
            configChannel?.getBackendConnectionStatus()
                ?: throw ESPProviderException(
                        errorCode = ESPProviderErrorCode.COMMUNICATION_ERROR,
                        errorMessage = "Channel returned null status"
                    )
        } catch (e: Exception) {
            throw ESPProviderException(
                errorCode = ESPProviderErrorCode.COMMUNICATION_ERROR,
                errorMessage = e.localizedMessage ?: "Unknown error"
            )
        } as BackendConnectionStatus
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDeviceConnectionEvent(event: DeviceConnectionEvent) {

        when (event.getEventType()) {
            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                Log.d(ESPProvisionProvider.TAG, "Device Connected Event Received")
                bleStatus = BLEStatus.CONNECTED

                espDevice?.let { device ->
                    setSecurityTypeFromVersionInfo(device)
                    configChannel = ORConfigChannel(device)
                }

                sendConnectToDeviceStatus(ESPProviderConnectToDeviceStatus.CONNECTED.value)
            }

            ESPConstants.EVENT_DEVICE_DISCONNECTED -> {
                bleStatus = BLEStatus.DISCONNECTED
                configChannel = null
                sendConnectToDeviceStatus(ESPProviderConnectToDeviceStatus.DISCONNECTED.value)
            }

            ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> {
                bleStatus = BLEStatus.DISCONNECTED

                // TODO: can I get some error details ?
                sendConnectToDeviceStatus(ESPProviderConnectToDeviceStatus.CONNECTION_ERROR.value)
            }
        }
    }

    private fun sendConnectToDeviceStatus(status: String, error: ESPProviderErrorCode? = null, errorMessage: String? = null) {
        val data = mutableMapOf<String, Any>("id" to (deviceId?.toString() ?: ""), "status" to status)

        error?.let {
            data["errorCode"] = error.code
        }
        errorMessage?.let {
            data["errorMessage"] = it
        }

        callbackChannel?.sendMessage(ESPProvisionProviderActions.CONNECT_TO_DEVICE, data)
    }

    fun setSecurityTypeFromVersionInfo(device: ESPDevice) {
        val protoVerStr: String = device.getVersionInfo()

        try {
            val jsonObject = JSONObject(protoVerStr)
            val provInfo = jsonObject.getJSONObject("prov")

            if (provInfo != null) {
                if (provInfo.has("sec_ver")) {
                    val serVer = provInfo.optInt("sec_ver")
                    Log.d(ESPProvisionProvider.TAG, "Security Version : " + serVer)

                    when (serVer) {
                        SEC_TYPE_0 -> {
                            device.setSecurityType(ESPConstants.SecurityType.SECURITY_0)
                        }

                        SEC_TYPE_1 -> {
                            device.setSecurityType(ESPConstants.SecurityType.SECURITY_1)
                        }

                        SEC_TYPE_2 -> {
                            device.setSecurityType(ESPConstants.SecurityType.SECURITY_2)
                        }

                        else -> {
                            device.setSecurityType(ESPConstants.SecurityType.SECURITY_2)
                        }
                    }
                } else {
                    device.setSecurityType(ESPConstants.SecurityType.SECURITY_1)
                }
            } else {
                Log.e(ESPProvisionProvider.TAG, "proto-ver info is not available.")
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.d(ESPProvisionProvider.TAG, "Capabilities JSON not available.")
        }
    }

    val espDevice: ESPDevice?
        get() = deviceRegistry.provisionManager?.espDevice

    val isConnected: Boolean
        get() = bleStatus == BLEStatus.CONNECTED && espDevice != null && configChannel != null

    enum class ESPProviderConnectToDeviceStatus(val value: String) {
        CONNECTED("connected"),
        DISCONNECTED("disconnected"),
        CONNECTION_ERROR("connectionError");

        override fun toString(): String = value
    }
    enum class BLEStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }
}