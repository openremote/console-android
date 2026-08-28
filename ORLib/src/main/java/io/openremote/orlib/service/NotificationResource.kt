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
package io.openremote.orlib.service

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.openremote.orlib.ORConstants
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.logging.Logger

class NotificationResource(context: Context) {
  private val sharedPreferences: SharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(context)

  fun executeRequest(httpMethod: String, appUrl: String, data: String?) {
    Executors.newCachedThreadPool().execute {
      URL(appUrl)
        .openConnection()
        .let {
          it as HttpURLConnection
        }
        .apply {
          requestMethod = httpMethod
          setRequestProperty("Accept", "application/json")

          if (!data.isNullOrBlank()) {
            setRequestProperty("Content-Type", "application/json")
            doOutput = true

            val outputWriter = outputStream.bufferedWriter()
            outputWriter.write(data)
            outputWriter.flush()
          }
        }
    }
  }

  fun notificationDelivered(notificationId: Long, fcmToken: String?) {
    LOG.info("Notification status update 'delivered': $notificationId")

    val host = sharedPreferences.getString(ORConstants.HOST_KEY, null)
    val realm = sharedPreferences.getString(ORConstants.REALM_KEY, null)

    if (!host.isNullOrBlank() && !realm.isNullOrBlank()) {
      val url = host.plus("/api/${realm}")
      Executors.newCachedThreadPool().execute {
        URL("${url}/notification/${notificationId}/delivered?targetId=$fcmToken")
          .openConnection()
          .let {
            it as HttpURLConnection
          }
          .apply {
            requestMethod = "PUT"
            setRequestProperty("Accept", "application/json")

            setRequestProperty("Content-Type", "application/json")
            doOutput = true
          }
      }
    }
  }

  fun notificationAcknowledged(
    notificationId: Long,
    fcmToken: String?,
    acknowledgement: String?,
  ) {
    val host = sharedPreferences.getString(ORConstants.HOST_KEY, null)
    val realm = sharedPreferences.getString(ORConstants.REALM_KEY, null)

    if (!host.isNullOrBlank() && !realm.isNullOrBlank()) {
      val url = host.plus("/api/${realm}")
      Executors.newCachedThreadPool().execute {
        URL("${url}/notification/${notificationId}/acknowledged?targetId=$fcmToken")
          .openConnection()
          .let {
            it as HttpURLConnection
          }
          .apply {
            requestMethod = "PUT"
            setRequestProperty("Accept", "application/json")

            setRequestProperty("Content-Type", "application/json")
            doOutput = true

            val outputWriter = outputStream.bufferedWriter()
            outputWriter.write("{\"acknowledgement\": \"${acknowledgement ?: ""}\"}")
            outputWriter.flush()
          }
      }
    }
  }

  companion object {
    private val LOG = Logger.getLogger(NotificationResource::class.java.name)
  }
}
