package io.openremote.orlib.service.espprovision

import android.net.Uri
import android.util.Log
import io.openremote.orlib.service.ESPProvisionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

interface DeviceProvisionAPI {
    suspend fun provision(modelName: String, deviceId: String, password: String, token: String): String
}

class DeviceProvisionAPIREST(private val apiURL: URL) : DeviceProvisionAPI {

    companion object {
        private const val TAG = "DeviceProvisionAPIREST"
    }

    override suspend fun provision(modelName: String, deviceId: String, password: String, token: String): String = withContext(Dispatchers.IO) {
        Log.d(ESPProvisionProvider.TAG, "apiURL $apiURL")
        val uri = Uri.parse(apiURL.toString()).buildUpon()
            .appendPath("rest")
            .appendPath("device")
            .build()

        val url = URL(uri.toString())
        Log.d(ESPProvisionProvider.TAG, "Calling URL $url")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.doOutput = true

        val requestBody = JSONObject().apply {
            put("modelName", modelName)
            put("deviceId", deviceId)
            put("password", password)
        }

        try {
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseText = BufferedReader(InputStreamReader(
                if (responseCode in 200..299) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            if (responseCode !in 200..299) {
                Log.d(ESPProvisionProvider.TAG, "Response code $responseCode")
                Log.d(ESPProvisionProvider.TAG, "Response text $responseText")
                when (responseCode) {
                    401 -> throw DeviceProvisionAPIError.Unauthorized
                    409 -> throw DeviceProvisionAPIError.BusinessError
                    else -> throw DeviceProvisionAPIError.UnknownError
                }
            }

            val json = JSONObject(responseText)
            return@withContext json.getString("assetId")
        } catch (e: DeviceProvisionAPIError) {
            throw e
        } catch (e: Exception) {
            throw DeviceProvisionAPIError.GenericError(e)
        } finally {
            connection.disconnect()
        }
    }
}

sealed class DeviceProvisionAPIError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    object Unauthorized : DeviceProvisionAPIError("Unauthorized")
    data class CommunicationError(val reason: String) : DeviceProvisionAPIError(reason)
    object BusinessError : DeviceProvisionAPIError("Business logic error")
    data class GenericError(val error: Throwable) : DeviceProvisionAPIError(error.message, error)
    object UnknownError : DeviceProvisionAPIError("Unknown error")
}