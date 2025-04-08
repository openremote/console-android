package io.openremote.orlib.service.espprovision

import android.util.Log
import data.entity.Response
import io.openremote.orlib.service.ESPProviderErrorCode
import io.openremote.orlib.service.ESPProvisionProvider
import io.openremote.orlib.service.ESPProvisionProviderActions
import utils.PasswordType
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BatteryProvision(var deviceConnection: DeviceConnection?, var callbackChannel: CallbackChannel?, var apiURL: URL) {
    var batteryProvisionAPI: BatteryProvisionAPI

    var backendConnectionTimeoutMillis = 60_000

    init {
        batteryProvisionAPI = BatteryProvisionAPIREST(apiURL)
    }

    suspend fun provision(userToken: String) {
        if (deviceConnection == null || !deviceConnection!!.isConnected) {
            sendProvisionDeviceStatus(false, ESPProviderErrorCode.NOT_CONNECTED, "No connection established to device")
        }

        try {
            val deviceInfo = deviceConnection!!.getDeviceInfo()
            Log.d(ESPProvisionProvider.TAG, "Device id is ${deviceInfo.deviceId}")

            val password = generatePassword()

            val assetId = batteryProvisionAPI.provision(deviceInfo.deviceId, password, userToken)
            val userName = deviceInfo.deviceId.lowercase(Locale("en"))

            deviceConnection?.sendOpenRemoteConfig(
                mqttBrokerUrl = mqttURL,
                mqttUser = userName,
                mqttPassword = password,
                assetId = assetId
            )

            var status = BackendConnectionStatus.CONNECTING
            val startTime = System.currentTimeMillis()

            while (status != BackendConnectionStatus.CONNECTED) {
                if (System.currentTimeMillis() - startTime > backendConnectionTimeoutMillis) {
                    sendProvisionDeviceStatus(
                        connected = false,
                        error = ESPProviderErrorCode.TIMEOUT_ERROR,
                        errorMessage = "Timeout waiting for backend to get connected"
                    )
                    return
                }

                status = deviceConnection?.getBackendConnectionStatus()
                    ?: BackendConnectionStatus.DISCONNECTED
            }
            sendProvisionDeviceStatus(true)
        } catch (e: BatteryProvisionAPIError) {
            val (errorCode, errorMessage) = mapBatteryProvisionAPIError(e)
            sendProvisionDeviceStatus(false, errorCode, errorMessage)
        }
    }

    private fun sendProvisionDeviceStatus(connected: Boolean, error: ESPProviderErrorCode? = null, errorMessage: String? = null) {
        val data = mutableMapOf<String, Any>("connected" to connected)

        error?.let {
            data["errorCode"] = it.code
        }
        errorMessage?.let {
            data["errorMessage"] = it
        }

        callbackChannel?.sendMessage(ESPProvisionProviderActions.PROVISION_DEVICE, data)
    }

    private fun mapBatteryProvisionAPIError(error: BatteryProvisionAPIError): Pair<ESPProviderErrorCode, String?> {
        return when (error) {
            is BatteryProvisionAPIError.BusinessError,
            is BatteryProvisionAPIError.UnknownError -> ESPProviderErrorCode.GENERIC_ERROR to null

            is BatteryProvisionAPIError.GenericError -> ESPProviderErrorCode.GENERIC_ERROR to error.error.localizedMessage

            is BatteryProvisionAPIError.Unauthorized -> ESPProviderErrorCode.SECURITY_ERROR to null

            is BatteryProvisionAPIError.CommunicationError -> ESPProviderErrorCode.COMMUNICATION_ERROR to error.message
        }
    }

    // Using https://github.com/iammohdzaki/Password-Generator
    private suspend fun generatePassword(): String = suspendCoroutine { continuation ->
        PasswordGenerator.Builder(PasswordType.RANDOM)
            .showLogs(false)
            .includeUpperCaseChars(true)
            .includeNumbers(true)
            .includeLowerCaseChars(true)
            .includeSpecialSymbols(false)
            .passwordLength(16)
            .callback(object : PasswordGenerator.Callback {
                override fun onPasswordGenerated(response: Response) {
                    continuation.resume(response.password)
                }
            })
            .build()
            .generate()
    }

    private val mqttURL: String
        get() {
            // TODO: is this OK or do we want to get the mqtt url from the server?
            return "mqtts://${apiURL.host ?: "localhost"}:8883"
        }
}