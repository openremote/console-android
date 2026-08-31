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
package io.openremote.orlib.ui

import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.openremote.orlib.R
import io.openremote.orlib.service.ConnectivityChangeReceiver

open class OrOfflineActivity : ComponentActivity() {

  private val connectivityChangeReceiver: ConnectivityChangeReceiver =
    ConnectivityChangeReceiver(onConnectivityChanged = ::onConnectivityChanged)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_offline)
  }

  override fun onResume() {
    super.onResume()
    registerReceiver(
      connectivityChangeReceiver,
      IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
    )
  }

  override fun onPause() {
    super.onPause()
    unregisterReceiver(connectivityChangeReceiver)
  }

  private fun onConnectivityChanged(isConnected: Boolean) {
    if (isConnected) {
      finish()
    }
  }
}
