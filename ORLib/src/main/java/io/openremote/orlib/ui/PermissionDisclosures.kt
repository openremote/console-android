package io.openremote.orlib.ui

import android.app.Activity
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import io.openremote.orlib.R

/**
 * Shows the in-app prominent disclosure required by the Google Play User Data policy.
 * The disclosure must be shown, and accepted, immediately before any runtime
 * permission request for personal or sensitive data.
 */
object PermissionDisclosures {

    fun show(
        activity: Activity,
        @StringRes title: Int,
        @StringRes message: Int,
        onAccept: () -> Unit,
        onDecline: () -> Unit = {}
    ) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                return@runOnUiThread
            }
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.disclosure_continue) { _, _ -> onAccept() }
                .setNegativeButton(R.string.disclosure_no_thanks) { _, _ -> onDecline() }
                .show()
        }
    }
}
