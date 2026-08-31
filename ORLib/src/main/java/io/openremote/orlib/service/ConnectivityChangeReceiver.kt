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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class ConnectivityChangeReceiver(private val onConnectivityChanged: (Boolean) -> Unit) :
  BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val connectivityManager =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    val result =
      capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        ?: capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        ?: capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ?: false
    onConnectivityChanged(result)
  }

  fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities =
      connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    if (networkCapabilities != null) {
      return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
    return false
  }
}
