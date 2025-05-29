package io.openremote.orlib.service.espprovision

import com.espressif.provisioning.ESPDevice
import com.espressif.provisioning.listeners.ResponseListener
import io.openremote.orlib.service.espprovision.ORConfigChannelProtocol.Request
import io.openremote.orlib.service.espprovision.ORConfigChannelProtocol.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

data class DeviceInfo(
    val deviceId: String,
    val modelName: String
)

enum class BackendConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, FAILED
}
sealed class ORConfigChannelError(message: String) : Exception(message) {
    class InvalidRequest(message: String) : ORConfigChannelError(message)
    object MessageOutOfOrder : ORConfigChannelError("Message out of order")
    class InvalidResponse(message: String) : ORConfigChannelError(message)
    object OperationFailure : ORConfigChannelError("Operation failed")
    object GenericError : ORConfigChannelError("Generic error")
}

class ORConfigChannel(private val device: ESPDevice) {

    private var messageId = 0

    suspend fun getDeviceInfo(): DeviceInfo {
        val request = Request.newBuilder()
            .setDeviceInfo(Request.DeviceInfo.getDefaultInstance())
            .setId(messageId++.toString())
            .build()

        val response = sendRequest(request)
        if (response.hasDeviceInfo()) {
            val info = response.deviceInfo
            return DeviceInfo(deviceId = info.deviceId, modelName = info.modelName)
        } else {
            throw ORConfigChannelError.InvalidResponse("Invalid response type")
        }
    }

    suspend fun sendOpenRemoteConfig(
        mqttBrokerUrl: String,
        mqttUser: String,
        mqttPassword: String,
        realm: String = "master",
        assetId: String
    ) {
        val config = Request.OpenRemoteConfig.newBuilder()
            .setMqttBrokerUrl(mqttBrokerUrl)
            .setUser(mqttUser)
            .setMqttPassword(mqttPassword)
            .setAssetId(assetId)
            .setRealm(realm)
            .build()

        val request = Request.newBuilder()
            .setOpenRemoteConfig(config)
            .setId(messageId++.toString())
            .build()

        val response = sendRequest(request)
        if (!response.hasOpenRemoteConfig() || response.openRemoteConfig.status != Response.OpenRemoteConfig.Status.SUCCESS) {
            throw ORConfigChannelError.OperationFailure
        }
    }

    suspend fun getBackendConnectionStatus(): BackendConnectionStatus {
        val request = Request.newBuilder()
            .setBackendConnectionStatus(Request.BackendConnectionStatus.getDefaultInstance())
            .setId(messageId++.toString())
            .build()

        val response = sendRequest(request)
        if (response.hasBackendConnectionStatus()) {
            return when (response.backendConnectionStatus.status) {
                Response.BackendConnectionStatus.Status.DISCONNECTED -> BackendConnectionStatus.DISCONNECTED
                Response.BackendConnectionStatus.Status.CONNECTING -> BackendConnectionStatus.CONNECTING
                Response.BackendConnectionStatus.Status.CONNECTED -> BackendConnectionStatus.CONNECTED
                Response.BackendConnectionStatus.Status.FAILED -> BackendConnectionStatus.FAILED
                else -> throw ORConfigChannelError.InvalidResponse("Unrecognized status")
            }
        } else {
            throw ORConfigChannelError.InvalidResponse("Invalid response type")
        }
    }

    suspend fun exitProvisioning() {
        val request = Request.newBuilder()
            .setExitProvisioning(Request.ExitProvisioning.getDefaultInstance())
            .setId(messageId++.toString())
            .build()
        sendRequest(request)
    }

    private suspend fun sendRequest(request: Request): Response = suspendCancellableCoroutine { cont ->
        val data = request.toByteArray()
        device.sendDataToCustomEndPoint("or-cfg", data, object: ResponseListener {
            override fun onSuccess(returnData: ByteArray?) {
                val response = Response.parseFrom(returnData)
                if (response.id != request.id) {
                    cont.resumeWithException(ORConfigChannelError.MessageOutOfOrder)
                } else if (!response.hasResult() || response.result.result != Response.ResponseResult.Result.SUCCESS) {
                    cont.resumeWithException(
                        ORConfigChannelError.InvalidResponse("Response result was not success")
                    )
                } else {
                    // TODO: why the onCancellation ?
                    cont.resume(response, onCancellation = null)
                }
            }

            override fun onFailure(e: java.lang.Exception?) {
                // TODO: pass some details ?
                cont.resumeWithException(ORConfigChannelError.GenericError)
            }

        })
    }
}