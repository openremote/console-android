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

import android.app.Activity
import android.util.TypedValue
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import io.openremote.orlib.R

/**
 * Shows the in-app prominent disclosure required by the Google Play User Data policy. The
 * disclosure must be shown, and accepted, immediately before any runtime permission request for
 * personal or sensitive data.
 */
object PermissionDisclosures {

  fun show(
    activity: Activity,
    @StringRes title: Int,
    @StringRes message: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit = {},
  ) {
    activity.runOnUiThread {
      if (activity.isFinishing || activity.isDestroyed) {
        return@runOnUiThread
      }
      // The AppCompat AlertDialog requires a theme that resolves alertDialogTheme;
      // consuming apps may run this activity with a non-AppCompat theme.
      val themedContext =
        if (
          activity.theme.resolveAttribute(
            androidx.appcompat.R.attr.alertDialogTheme,
            TypedValue(),
            true,
          )
        ) {
          activity
        } else {
          ContextThemeWrapper(
            activity,
            androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog_Alert,
          )
        }
      AlertDialog.Builder(themedContext)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(false)
        .setPositiveButton(R.string.disclosure_continue) { _, _ -> onAccept() }
        .setNegativeButton(R.string.disclosure_no_thanks) { _, _ -> onDecline() }
        .show()
    }
  }
}
