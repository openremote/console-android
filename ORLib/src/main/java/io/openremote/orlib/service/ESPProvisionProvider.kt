package io.openremote.orlib.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import io.openremote.orlib.R
import io.openremote.orlib.service.BleProvider.BleCallback
import io.openremote.orlib.service.BleProvider.Companion.BLUETOOTH_PERMISSION_REQUEST_CODE
import io.openremote.orlib.service.BleProvider.Companion.ENABLE_BLUETOOTH_REQUEST_CODE
import io.openremote.orlib.service.espprovision.BatteryProvision
import io.openremote.orlib.service.espprovision.CallbackChannel
import io.openremote.orlib.service.espprovision.DeviceConnection
import io.openremote.orlib.service.espprovision.DeviceRegistry
import io.openremote.orlib.service.espprovision.WifiProvisioner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

object ESPProvisionProviderActions {
    const val PROVIDER_INIT = "PROVIDER_INIT"
    const val PROVIDER_ENABLE = "PROVIDER_ENABLE"
    const val PROVIDER_DISABLE = "PROVIDER_DISABLE"
    const val START_BLE_SCAN = "START_BLE_SCAN"
    const val STOP_BLE_SCAN = "STOP_BLE_SCAN"
    const val CONNECT_TO_DEVICE = "CONNECT_TO_DEVICE"
    const val DISCONNECT_FROM_DEVICE = "DISCONNECT_FROM_DEVICE"
    const val START_WIFI_SCAN = "START_WIFI_SCAN"
    const val STOP_WIFI_SCAN = "STOP_WIFI_SCAN"
    const val SEND_WIFI_CONFIGURATION = "SEND_WIFI_CONFIGURATION"
    const val PROVISION_DEVICE = "PROVISION_DEVICE"
    const val EXIT_PROVISIONING = "EXIT_PROVISIONING"
}

class ESPProvisionProvider(val context: Context, val apiURL: URL = URL("http://localhost:8080/api/master")) {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val deviceRegistry: DeviceRegistry
    var deviceConnection: DeviceConnection? = null

    private var searchDeviceTimeout: Long = 120
    private var searchDeviceMaxIterations = 25

    var wifiProvisioner: WifiProvisioner? = null
    private var searchWifiTimeout: Long = 120
    private var searchWifiMaxIterations = 25

    init {
        deviceRegistry = DeviceRegistry(context, searchDeviceTimeout, searchDeviceMaxIterations)
    }

    interface ESPProvisionCallback {
        fun accept(responseData: Map<String, Any>)
    }

    companion object {
        private const val espProvisionDisabledKey = "espProvisionDisabled"
        private const val version = "beta"

        const val TAG = "ESPProvisionProvider"

        const val ENABLE_BLUETOOTH_ESPPROVISION_REQUEST_CODE = 655
        const val BLUETOOTH_PERMISSION_ESPPROVISION_REQUEST_CODE = 656
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    fun initialize(): Map<String, Any> {
        val sharedPreferences =
            context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE)

        return hashMapOf(
            "action" to ESPProvisionProviderActions.PROVIDER_INIT,
            "provider" to "espprovision",
            "version" to version,
            "requiresPermission" to true,
            "hasPermission" to hasPermission(),
            "success" to true,
            "enabled" to false,
            "disabled" to sharedPreferences.contains(espProvisionDisabledKey)
        )
    }

    @SuppressLint("MissingPermission")
    fun enable(callback: ESPProvisionCallback?, activity: Activity) {
        if (!bluetoothAdapter.isEnabled) {
            Log.d("ESP", "BLE not enabled")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent,
                ESPProvisionProvider.Companion.ENABLE_BLUETOOTH_ESPPROVISION_REQUEST_CODE
            )
        } else if (!hasPermission()) {
            Log.d("ESP", "Does not have permissions")
            requestPermissions(activity)
        }

        deviceRegistry.enable()

        val sharedPreferences =
            context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE)

        sharedPreferences.edit()
            .remove(espProvisionDisabledKey)
            .apply()

        callback?.accept(
            hashMapOf(
                "action" to ESPProvisionProviderActions.PROVIDER_ENABLE,
                "provider" to "espprovision",
                "hasPermission" to hasPermission(),
                "success" to true,
                "enabled" to true,
                "disabled" to sharedPreferences.contains(espProvisionDisabledKey)
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun disable(): Map<String, Any> {
        deviceRegistry.disable()

//        disconnectFromDevice()

        val sharedPreferences =
            context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean(espProvisionDisabledKey, true)
            .apply()

        return hashMapOf(
            "action" to ESPProvisionProviderActions.PROVIDER_DISABLE,
            "provider" to "espprovision"
        )
    }

    @SuppressLint("MissingPermission")
    fun onRequestPermissionsResult(
        activity: Activity,
        requestCode: Int,
        prefix: String?
    ) {
        if (requestCode == BLUETOOTH_PERMISSION_ESPPROVISION_REQUEST_CODE) {
            val hasPermission = hasPermission()
            if (hasPermission) {
                if (!bluetoothAdapter.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    activity.startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_ESPPROVISION_REQUEST_CODE)
                } else {
                    deviceRegistry.startDevicesScan(prefix)
                }
            }
        } else if (requestCode == ENABLE_BLUETOOTH_ESPPROVISION_REQUEST_CODE) {
            if (bluetoothAdapter.isEnabled) {
                deviceRegistry.startDevicesScan(prefix)
            }
        }
    }

    // Device scan

    @SuppressLint("MissingPermission")
    fun startDevicesScan(prefix: String?, activity: Activity, callback: ESPProvisionCallback) {
        // TODO: check in BLE provider what activity is used for
        deviceRegistry.callbackChannel = CallbackChannel(callback, "espprovision")
        if (!bluetoothAdapter.isEnabled) {
            Log.d("ESP", "BLE not enabled")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent,
                ESPProvisionProvider.Companion.ENABLE_BLUETOOTH_ESPPROVISION_REQUEST_CODE
            )
        } else if (!hasPermission()) {
            Log.d("ESP", "Does not have permissions")
            requestPermissions(activity)
        } else {
            deviceRegistry.startDevicesScan(prefix)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDevicesScan() {
        deviceRegistry.stopDevicesScan()
    }

    // MARK: Device connect/disconnect

    @SuppressLint("MissingPermission")
    fun connectTo(deviceId: String, pop: String? = null, username: String? = null) {
        if (deviceConnection == null) {
            deviceConnection = DeviceConnection(deviceRegistry, deviceRegistry.callbackChannel)
        }
        deviceConnection?.connectTo(deviceId, pop, username)
    }

    fun disconnectFromDevice() {
        wifiProvisioner?.stopWifiScan()
        deviceConnection?.disconnectFromDevice()
    }

    fun exitProvisioning() {
        if (deviceConnection == null) {
            // TODO
            return
        }
        if (!deviceConnection!!.isConnected) {
            // TODO
            return
        }
        deviceConnection!!.exitProvisioning()
        // TODO
    }

    // Wifi scan

    fun startWifiScan() {
        if (wifiProvisioner == null) {
            wifiProvisioner = WifiProvisioner(deviceConnection, deviceRegistry.callbackChannel, searchWifiTimeout, searchWifiMaxIterations)
        }
        wifiProvisioner!!.startWifiScan()
    }

    fun stopWifiScan() {
        wifiProvisioner?.stopWifiScan()
    }

    fun sendWifiConfiguration(ssid: String, password: String) {
        if (wifiProvisioner == null) {
            wifiProvisioner = WifiProvisioner(deviceConnection, deviceRegistry.callbackChannel, searchWifiTimeout, searchWifiMaxIterations)
        }
        wifiProvisioner!!.sendWifiConfiguration(ssid, password)
    }

    // OR Configuration

    fun provisionDevice(userToken: String) {
        // TODO

        val batteryProvision = BatteryProvision(deviceConnection, deviceRegistry.callbackChannel, apiURL)
        CoroutineScope(Dispatchers.Main).launch {  // TODO: what's the appropriate dispatcher ? and should this be here or further down the call chain ?
            batteryProvision.provision(userToken)
        }
    }

    private fun requestPermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                ESPProvisionProvider.Companion.BLUETOOTH_PERMISSION_ESPPROVISION_REQUEST_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                ESPProvisionProvider.Companion.BLUETOOTH_PERMISSION_ESPPROVISION_REQUEST_CODE
            )
        }
    }

    private fun hasPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

}

enum class ESPProviderErrorCode(val code: Int) {
    UNKNOWN_DEVICE(100),

    BLE_COMMUNICATION_ERROR(200),

    NOT_CONNECTED(300),
    COMMUNICATION_ERROR(301),

    SECURITY_ERROR(400),

    WIFI_CONFIGURATION_ERROR(500),
    WIFI_COMMUNICATION_ERROR(501),
    WIFI_AUTHENTICATION_ERROR(502),
    WIFI_NETWORK_NOT_FOUND(503),

    TIMEOUT_ERROR(600),

    GENERIC_ERROR(10000);
}