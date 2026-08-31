/*
 * Copyright 2026, OpenRemote Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.openremote.orlib.service.espprovision

import android.net.Uri
import android.util.Log
import io.openremote.orlib.service.ESPProvisionProvider
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface DeviceProvisionAPI {
  suspend fun provision(
    apiURL: URL,
    modelName: String,
    deviceId: String,
    password: String,
    token: String,
  ): ProvisionResult
}

data class ProvisionResult(
  val assetId: String,
  val properties: Map<String, String>,
)

class DeviceProvisionAPIREST() : DeviceProvisionAPI {

  companion object {
    private const val TAG = "DeviceProvisionAPIREST"
  }

  override suspend fun provision(
    apiURL: URL,
    modelName: String,
    deviceId: String,
    password: String,
    token: String,
  ): ProvisionResult =
    withContext(Dispatchers.IO) {
      Log.d(ESPProvisionProvider.TAG, "apiURL $apiURL")
      val uri =
        Uri.parse(apiURL.toString()).buildUpon().appendPath("rest").appendPath("device").build()

      val url = URL(uri.toString())
      Log.d(ESPProvisionProvider.TAG, "Calling URL $url")
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "POST"
      connection.setRequestProperty("Content-Type", "application/json")
      connection.setRequestProperty("Authorization", "Bearer $token")
      connection.doOutput = true

      val requestBody =
        JSONObject().apply {
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
        val responseText =
          BufferedReader(
              InputStreamReader(
                if (responseCode in 200..299) connection.inputStream else connection.errorStream
              )
            )
            .use { it.readText() }

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
        val assetId = json.getString("assetId")
        val properties = mutableMapOf<String, String>()
        if (json.has("properties") && !json.isNull("properties")) {
          val propertiesJson = json.getJSONObject("properties")
          propertiesJson.keys().forEach { key ->
            properties[key] = propertiesJson.getString(key)
          }
        }
        return@withContext ProvisionResult(assetId = assetId, properties = properties)
      } catch (e: DeviceProvisionAPIError) {
        throw e
      } catch (e: Exception) {
        throw DeviceProvisionAPIError.GenericError(e)
      } finally {
        connection.disconnect()
      }
    }
}

sealed class DeviceProvisionAPIError(message: String? = null, cause: Throwable? = null) :
  Exception(message, cause) {
  object Unauthorized : DeviceProvisionAPIError("Unauthorized")

  data class CommunicationError(val reason: String) : DeviceProvisionAPIError(reason)

  object BusinessError : DeviceProvisionAPIError("Business logic error")

  data class GenericError(val error: Throwable) : DeviceProvisionAPIError(error.message, error)

  object UnknownError : DeviceProvisionAPIError("Unknown error")
}
