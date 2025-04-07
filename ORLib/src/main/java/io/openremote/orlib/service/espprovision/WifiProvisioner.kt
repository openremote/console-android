package io.openremote.orlib.service.espprovision

import android.util.Log
import android.widget.Toast
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPConstants.ProvisionFailureReason
import com.espressif.provisioning.WiFiAccessPoint
import com.espressif.provisioning.listeners.ProvisionListener
import com.espressif.provisioning.listeners.WiFiScanListener
import io.openremote.orlib.service.ESPProviderErrorCode
import io.openremote.orlib.service.ESPProvisionProvider
import io.openremote.orlib.service.ESPProvisionProviderActions

class WifiProvisioner(private var deviceConnection: DeviceConnection? = null, var callbackChannel: CallbackChannel? = null, searchWifiTimeout: Long, searchWifiMaxIterations: Int) {
    private var loopDetector = LoopDetector(searchWifiTimeout, searchWifiMaxIterations)

    var wifiScanning = false
        private set

    private val wifiNetworks = mutableListOf<WiFiAccessPoint>()

    fun startWifiScan() {
        if (deviceConnection?.isConnected != true) {
            sendWifiScanError(ESPProviderErrorCode.NOT_CONNECTED)
            return
        }
        wifiScanning = true
        loopDetector.reset()
        scanWifi()
    }

    fun stopWifiScan(sendMessage: Boolean = true) {
        wifiScanning = false
        if (sendMessage) {
            callbackChannel?.sendMessage(ESPProvisionProviderActions.STOP_WIFI_SCAN, null)
        }
    }

    private fun scanWifi() {
        if (loopDetector.detectLoop()) {
            stopWifiScan(false)
            sendWifiScanError(ESPProviderErrorCode.TIMEOUT_ERROR)
            return
        }


        deviceConnection?.espDevice?.scanNetworks(object : WiFiScanListener {
            override fun onWifiListReceived(wifiList: ArrayList<WiFiAccessPoint?>?) {
                wifiList?.let { wifiNetworks.addAll(it.mapNotNull { network -> network }) }
                callbackChannel?.sendMessage(ESPProvisionProviderActions.START_WIFI_SCAN, hashMapOf(
                    "networks" to wifiNetworks.map { network ->
                        hashMapOf(
                            "ssid" to network.wifiName,
                            "signalStrength" to network.rssi)
                    }
                ))
            }

            override fun onWiFiScanFailed(e: Exception) {
                // TODO
/*
                Log.e(com.espressif.ui.activities.WiFiScanActivity.TAG, "onWiFiScanFailed")
                e.printStackTrace()
                runOnUiThread(object : Runnable {
                    override fun run() {
                        updateProgressAndScanBtn(false)
                        Toast.makeText(
                            this@WiFiScanActivity,
                            "Failed to get Wi-Fi scan list",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
            */
            }
        })
    }

    private fun sendWifiScanError(error: ESPProviderErrorCode? = null, errorMessage: String? = null) {
        val data = mutableMapOf<String, Any>(
            "id" to (deviceConnection?.deviceId?.toString() ?: "N/A")
        )
        error?.let { data["errorCode"] = it.code }
        errorMessage?.let { data["errorMessage"] = it }
        callbackChannel?.sendMessage(ESPProvisionProviderActions.STOP_WIFI_SCAN, data)
    }

    fun sendWifiConfiguration(ssid: String, password: String) {
        if (deviceConnection?.isConnected != true) {
            sendWifiConfigurationStatus(false, ESPProviderErrorCode.NOT_CONNECTED)
            return
        }
        stopWifiScan()

        deviceConnection?.espDevice?.provision(ssid, password, object: ProvisionListener {
            override fun createSessionFailed(e: java.lang.Exception?) {
                sendWifiConfigurationStatus(false, ESPProviderErrorCode.GENERIC_ERROR, e.toString())
            }

            override fun wifiConfigSent() {
                /* ignore */
            }

            override fun wifiConfigFailed(e: java.lang.Exception?) {
                sendWifiConfigurationStatus(false, ESPProviderErrorCode.WIFI_CONFIGURATION_ERROR, e.toString())
            }

            override fun wifiConfigApplied() {
                /* ignore */
            }

            override fun wifiConfigApplyFailed(e: java.lang.Exception?) {
                sendWifiConfigurationStatus(false, ESPProviderErrorCode.GENERIC_ERROR, e.toString())
            }

            override fun provisioningFailedFromDevice(failureReason: ESPConstants.ProvisionFailureReason?) {
                sendWifiConfigurationStatus(false, mapProvisionFailureReason(failureReason ?: ProvisionFailureReason.UNKNOWN))
            }

            override fun deviceProvisioningSuccess() {
                sendWifiConfigurationStatus(true)
            }

            override fun onProvisioningFailed(e: java.lang.Exception?) {
                sendWifiConfigurationStatus(false, ESPProviderErrorCode.GENERIC_ERROR, e.toString())
            }

        })
    }

    private fun sendWifiConfigurationStatus(connected: Boolean, error: ESPProviderErrorCode? = null, errorMessage: String? = null) {
        val data = mutableMapOf<String, Any>("connected" to connected)
        error?.let { data["errorCode"] = it.code }
        errorMessage?.let { data["errorMessage"] = it }
        callbackChannel?.sendMessage(ESPProvisionProviderActions.SEND_WIFI_CONFIGURATION, data)
    }

    private fun mapProvisionFailureReason(reason: ProvisionFailureReason): ESPProviderErrorCode {
        return when (reason) {
            ProvisionFailureReason.AUTH_FAILED -> ESPProviderErrorCode.WIFI_AUTHENTICATION_ERROR
            ProvisionFailureReason.NETWORK_NOT_FOUND -> ESPProviderErrorCode.WIFI_NETWORK_NOT_FOUND
            ProvisionFailureReason.DEVICE_DISCONNECTED -> ESPProviderErrorCode.NOT_CONNECTED
            ProvisionFailureReason.UNKNOWN -> ESPProviderErrorCode.GENERIC_ERROR
        }
    }
}