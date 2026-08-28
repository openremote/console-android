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

import android.app.IntentService
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import io.openremote.orlib.ORConstants
import io.openremote.orlib.R
import io.openremote.orlib.ui.OrMainActivity

class ORMessagingActionService : IntentService("org.openremote.android.ORMessagingActionService") {
  private var notificationResource: NotificationResource? = null

  override fun onCreate() {
    super.onCreate()
    notificationResource = NotificationResource(applicationContext)
  }

  override fun onHandleIntent(intent: Intent?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      val it = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
      this.sendBroadcast(it)
    }
    val notificationId = intent!!.getLongExtra("notificationId", 0L)
    val acknowledgement = intent.getStringExtra("acknowledgement")
    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    manager.cancel(java.lang.Long.hashCode(notificationId))
    val consoleId =
      getSharedPreferences(
          applicationContext.getString(R.string.app_name),
          MODE_PRIVATE,
        )
        .getString(ORConstants.CONSOLE_ID_KEY, "")
    if (!TextUtils.isEmpty(consoleId)) {
      notificationResource!!.notificationAcknowledged(
        notificationId,
        consoleId,
        acknowledgement,
      )
    }
    val appUrl = intent.getStringExtra("appUrl")
    if (!appUrl.isNullOrBlank()) {
      val openInBrowser = intent.getBooleanExtra("openInBrowser", false)
      val silent = intent.getBooleanExtra("silent", false)
      val data = intent.getStringExtra("data")
      var httpMethod = intent.getStringExtra("httpMethod")
      httpMethod = if (TextUtils.isEmpty(httpMethod)) "GET" else httpMethod

      when {
        openInBrowser -> {
          // Don't load the app just send straight to browser
          val i = Intent(Intent.ACTION_VIEW)
          i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          i.data = Uri.parse(appUrl)
          startActivity(i)
        }
        silent -> {
          // Do silent HTTP request
          notificationResource!!.executeRequest(httpMethod!!, appUrl, data)
        }
        else -> {
          val pm: PackageManager = packageManager
          val launchIntent: Intent =
            pm.getLaunchIntentForPackage(applicationContext.packageName)
              ?: Intent(applicationContext, OrMainActivity::class.java)
          launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
          launchIntent.putExtra("appUrl", appUrl)
          startActivity(launchIntent)
        }
      }
    }
  }
}
