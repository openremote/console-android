package io.openremote.orlib.ui

import android.app.Activity
import android.util.TypedValue
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
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
            // The AppCompat AlertDialog requires a theme that resolves alertDialogTheme;
            // consuming apps may run this activity with a non-AppCompat theme.
            val themedContext = if (activity.theme.resolveAttribute(
                    androidx.appcompat.R.attr.alertDialogTheme, TypedValue(), true
                )
            ) {
                activity
            } else {
                ContextThemeWrapper(
                    activity,
                    androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog_Alert
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
