# Prominent Disclosure Compliance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show an in-app prominent disclosure dialog immediately before every runtime permission request, satisfying Google Play's Prominent Disclosure and Consent Requirement (User Data policy) that got the Generic App rejected.

**Architecture:** One shared helper (`PermissionDisclosures`) in ORLib shows an AlertDialog (disclosure text + Continue/No thanks) and only fires the system permission request after the user taps Continue. Every call site that currently calls `requestPermissions(...)` directly (or gates its explanatory dialog behind `shouldShowRequestPermissionRationale`, i.e. shows it only AFTER a denial) is rewritten to route through this helper BEFORE the first system dialog.

**Tech Stack:** Kotlin, Android (ORLib library module consumed by GenericApp), androidx.appcompat AlertDialog (already used throughout ORLib), Gradle (AGP 9.1, Kotlin 2.4).

## Global Constraints

- **No test infrastructure exists** in this repo (no test source sets, no Robolectric). Verification per task = successful compile (`./gradlew :ORLib:assembleDebug`) + code review. Final task also builds the app: `./gradlew :GenericApp:app:assembleDebug`. This is a deliberate, approved deviation from TDD — do NOT add test frameworks.
- Gradle module names: `:ORLib`, `:GenericApp:app`. Run gradle from repo root `/Users/michaelrademaker/Developer/OR/console-android`.
- All work on branch `fix/prominent-disclosure`.
- Commit messages: normal English, imperative mood. **Never add any Co-Authored-By trailer or AI/Claude/"Generated with" attribution.**
- Use `androidx.appcompat.app.AlertDialog` (matches existing imports in these files), never `android.app.AlertDialog`.
- Google policy wording requirement: disclosures for data collected in background must contain the phrase "even when the app is closed or not in use". Do not reword the strings given in Task 1.
- Keep existing behavior on decline paths identical to what a system-dialog denial produces today (documented per task).
- Do not touch unrelated code, do not reformat files, do not "fix" unrelated warnings.

---

### Task 1: PermissionDisclosures helper + disclosure strings

**Files:**
- Create: `ORLib/src/main/java/io/openremote/orlib/ui/PermissionDisclosures.kt`
- Modify: `ORLib/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `object PermissionDisclosures` with function
  `show(activity: Activity, title: Int, message: Int, onAccept: () -> Unit, onDecline: () -> Unit = {})`
  — `title`/`message` are `@StringRes` ints. Later tasks call exactly this signature.
- Produces string resources (exact names): `location_disclosure_title`, `location_disclosure_body`, `webview_location_disclosure_body`, `background_location_disclosure_title`, `background_location_disclosure_body`, `push_notification_disclosure_title`, `push_notification_disclosure_body`, `camera_disclosure_title`, `camera_disclosure_body`, `bluetooth_disclosure_title`, `bluetooth_disclosure_body`, `bluetooth_location_disclosure_body`, `storage_disclosure_title`, `storage_disclosure_body`, `disclosure_continue`, `disclosure_no_thanks`.

- [ ] **Step 1: Create the helper**

Create `ORLib/src/main/java/io/openremote/orlib/ui/PermissionDisclosures.kt` with exactly:

```kotlin
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
```

Rationale for `runOnUiThread`: several callers run on the WebView JavaScript bridge thread (`@JavascriptInterface`), not the main thread.

- [ ] **Step 2: Add strings**

In `ORLib/src/main/res/values/strings.xml`, add before `</resources>`:

```xml
    <string name="location_disclosure_title">Location access</string>
    <string name="location_disclosure_body">This app collects location data to enable geofencing and to update your location on the platform, even when the app is closed or not in use.\nNo location history is stored, only the latest location is saved.</string>
    <string name="webview_location_disclosure_body">This app uses your location to show location-based information in the console while you are using the app.</string>
    <string name="background_location_disclosure_title">Background location access</string>
    <string name="background_location_disclosure_body">This app collects location data to enable geofencing, even when the app is closed or not in use.\nNo location history is stored, only the latest location is saved.\nTo enable this, allow location access all the time in the next step.</string>
    <string name="push_notification_disclosure_title">Notifications</string>
    <string name="push_notification_disclosure_body">This app sends push notifications to alert you about events and messages from the platform.\nTo receive them, allow notifications in the next step.</string>
    <string name="camera_disclosure_title">Camera access</string>
    <string name="camera_disclosure_body">This app uses the camera to scan QR codes.\nImages are processed on your device and are not stored or shared.</string>
    <string name="bluetooth_disclosure_title">Bluetooth access</string>
    <string name="bluetooth_disclosure_body">This app uses Bluetooth to find and connect to nearby devices, so they can be set up and controlled.</string>
    <string name="bluetooth_location_disclosure_body">This app uses Bluetooth to find and connect to nearby devices, so they can be set up and controlled.\nOn this version of Android, location permission is required to scan for Bluetooth devices.</string>
    <string name="storage_disclosure_title">Storage access</string>
    <string name="storage_disclosure_body">To save the file to your Downloads folder, allow storage access in the next step.</string>
    <string name="disclosure_continue">Continue</string>
    <string name="disclosure_no_thanks">No thanks</string>
```

- [ ] **Step 3: Fix pre-existing typos in the two existing alert strings**

Same file. In `background_location_alert_body` and `push_notification_alert_message`, change "Do you want to enabled it in the settings?" to "Do you want to enable it in the settings?" (both occurrences). Change nothing else in those strings.

- [ ] **Step 4: Verify compile**

Run from repo root: `./gradlew :ORLib:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add ORLib/src/main/java/io/openremote/orlib/ui/PermissionDisclosures.kt ORLib/src/main/res/values/strings.xml
git commit -m "Add prominent disclosure dialog helper and disclosure strings"
```

---

### Task 2: GeofenceProvider location flow

**Files:**
- Modify: `ORLib/src/main/java/io/openremote/orlib/service/GeofenceProvider.kt` (functions `registerPermissions` ~line 372 and `onRequestPermissionsResult` ~line 405; add one instance field)

**Interfaces:**
- Consumes: `PermissionDisclosures.show(activity, titleRes, messageRes, onAccept, onDecline)` from Task 1; strings `location_disclosure_title`, `location_disclosure_body`, `background_location_disclosure_title`, `background_location_disclosure_body`.
- Produces: unchanged public API (`enable`, `onRequestPermissionsResult(activity)` signatures untouched — `OrMainActivity` already calls both).

**Context for the implementer:** This is the flow Google flagged. Today `registerPermissions()` fires the system fine-location dialog with no disclosure, and the "Background location needed" dialog appears only AFTER the system prompt (and only when `shouldShowRequestPermissionRationale` is true). Required order: disclosure → system fine dialog → background disclosure → system background dialog. Decline anywhere → report state to the web app via `onEnable(callback)`, same as a denial today. Note `enable()` (~line 232) already gates this whole flow to once-per-install via `locationPermissionAskedKey`.

- [ ] **Step 1: Add imports and field**

Add import (keep existing imports; remove `androidx.appcompat.app.AlertDialog` only if nothing else in the file uses it after this task — check with grep):

```kotlin
import io.openremote.orlib.ui.PermissionDisclosures
```

Add an instance field next to the existing `enableCallback` declaration:

```kotlin
private var backgroundDisclosureAsked = false
```

- [ ] **Step 2: Replace `registerPermissions`**

Replace the entire existing `registerPermissions(activity: Activity)` function with:

```kotlin
    private fun registerPermissions(activity: Activity) {
        LOG.info("Requesting geofence permissions")
        if (context.checkSelfPermission(ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                context.checkSelfPermission(ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBackgroundLocation(activity)
            } else {
                enableCallback?.let {
                    onEnable(it)
                    enableCallback = null
                }
            }
        } else {
            PermissionDisclosures.show(
                activity,
                R.string.location_disclosure_title,
                R.string.location_disclosure_body,
                onAccept = {
                    activity.requestPermissions(
                        arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
                        locationResponseCode
                    )
                },
                onDecline = {
                    enableCallback?.let {
                        onEnable(it)
                        enableCallback = null
                    }
                }
            )
        }
    }

    private fun requestBackgroundLocation(activity: Activity) {
        backgroundDisclosureAsked = true
        PermissionDisclosures.show(
            activity,
            R.string.background_location_disclosure_title,
            R.string.background_location_disclosure_body,
            onAccept = {
                activity.requestPermissions(
                    arrayOf(ACCESS_BACKGROUND_LOCATION),
                    locationResponseCode
                )
            },
            onDecline = {
                enableCallback?.let {
                    onEnable(it)
                    enableCallback = null
                }
            }
        )
    }
```

- [ ] **Step 3: Replace `onRequestPermissionsResult`**

Replace the entire existing `onRequestPermissionsResult(activity: Activity)` function with:

```kotlin
    fun onRequestPermissionsResult(activity: Activity) {
        if (enableCallback != null) {
            onEnable(enableCallback!!)
            enableCallback = null
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            context.checkSelfPermission(ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            !backgroundDisclosureAsked
        ) {
            requestBackgroundLocation(activity)
        }
    }
```

Behavior notes (why this shape):
- `onEnable` first preserves today's behavior of answering the web app right after the fine-location round; the background round is fire-and-forget.
- `backgroundDisclosureAsked` prevents an infinite loop: background denial re-enters this function with background still missing.
- When entering via `registerPermissions` with fine already granted, `requestBackgroundLocation`'s decline still answers the web app because `enableCallback` is set in that path.

- [ ] **Step 4: Verify compile**

Run: `./gradlew :ORLib:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add ORLib/src/main/java/io/openremote/orlib/service/GeofenceProvider.kt
git commit -m "Show prominent disclosure before geofence location permission requests"
```

---

### Task 3: OrMainActivity — WebView geolocation, push notifications, download storage

**Files:**
- Modify: `ORLib/src/main/java/io/openremote/orlib/ui/OrMainActivity.kt` (three sites: `onGeolocationPermissionsShowPrompt` ~line 340, `setDownloadListener` ~line 389, push `PROVIDER_ENABLE` handler ~line 692; plus `onRequestPermissionsResult` ~line 489)

**Interfaces:**
- Consumes: `PermissionDisclosures.show(...)` from Task 1; strings `location_disclosure_title`, `webview_location_disclosure_body`, `storage_disclosure_title`, `storage_disclosure_body`, `push_notification_disclosure_title`, `push_notification_disclosure_body`.
- Produces: private helper `startDownload(url: String, userAgent: String, contentDisposition: String, mimetype: String)` and private field `pendingDownload: (() -> Unit)?` — used only within this file.

`OrMainActivity` is in package `io.openremote.orlib.ui` — same package as the helper, so **no import is needed** for `PermissionDisclosures`.

- [ ] **Step 1: WebView geolocation prompt (site A)**

Replace the body of `onGeolocationPermissionsShowPrompt` (currently: granted → invoke callback; else → direct `requestPermissions`) with:

```kotlin
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        callback?.invoke(origin, true, false)
                    } else {
                        locationCallback = callback
                        locationOrigin = origin
                        PermissionDisclosures.show(
                            this@OrMainActivity,
                            R.string.location_disclosure_title,
                            R.string.webview_location_disclosure_body,
                            onAccept = {
                                requestPermissions(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ),
                                    locationResponseCode
                                )
                            },
                            onDecline = {
                                locationCallback?.invoke(locationOrigin, false, false)
                                locationCallback = null
                            }
                        )
                    }
                }
```

- [ ] **Step 2: Answer the WebView on system-dialog denial too**

In `onRequestPermissionsResult`, the `requestCode == locationResponseCode` branch currently only handles grant. Replace that branch body with:

```kotlin
        } else if (requestCode == locationResponseCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationCallback?.invoke(locationOrigin, true, false)
            } else {
                locationCallback?.invoke(locationOrigin, false, false)
            }
            locationCallback = null
        }
```

- [ ] **Step 3: Download listener (site B)**

Replace the entire `setDownloadListener { ... }` block with the version below, and add the extracted `startDownload` as a new private method plus the `pendingDownload` field. On Android 10+ (API 29) `WRITE_EXTERNAL_STORAGE` is not needed for `DownloadManager` writes to the public Downloads dir, so skip the permission (and the disclosure) entirely there.

```kotlin
            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        this@OrMainActivity,
                        writePermission
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startDownload(url, userAgent, contentDisposition, mimetype)
                } else {
                    pendingDownload = { startDownload(url, userAgent, contentDisposition, mimetype) }
                    PermissionDisclosures.show(
                        this@OrMainActivity,
                        R.string.storage_disclosure_title,
                        R.string.storage_disclosure_body,
                        onAccept = {
                            requestPermissions(
                                arrayOf(writePermission),
                                ORConstants.WRITE_PERMISSION_FOR_DOWNLOAD
                            )
                        },
                        onDecline = {
                            pendingDownload = null
                        }
                    )
                }
            }
```

New field (next to `locationCallback` declarations at the top of the class):

```kotlin
    private var pendingDownload: (() -> Unit)? = null
```

New private method (place directly after the function containing `setDownloadListener`, i.e. after `initializeWebView`'s closing brace or adjacent to `handleError`); the body is the code that currently lives in the `else` branch of the download listener, verbatim:

```kotlin
    private fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setMimeType(mimetype)
        //------------------------COOKIE!!------------------------
        val cookies = CookieManager.getInstance().getCookie(url)
        request.addRequestHeader("cookie", cookies)
        //------------------------COOKIE!!------------------------
        request.addRequestHeader("User-Agent", userAgent)
        request.setDescription("Downloading file...")
        request.setTitle(
            URLUtil.guessFileName(
                url,
                contentDisposition,
                mimetype
            )
        )
        request.allowScanningByMediaScanner()
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            URLUtil.guessFileName(url, contentDisposition, mimetype)
        )
        val dm =
            getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        Toast.makeText(applicationContext, R.string.downloading_file, Toast.LENGTH_LONG)
            .show()
        dm.enqueue(request)
    }
```

- [ ] **Step 4: Run the pending download after grant**

In `onRequestPermissionsResult`, replace the `requestCode == ORConstants.WRITE_PERMISSION_FOR_DOWNLOAD` branch body with:

```kotlin
        if (requestCode == ORConstants.WRITE_PERMISSION_FOR_DOWNLOAD) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingDownload?.invoke()
            }
            pendingDownload = null
        }
```

(Drop the old Toast there — `startDownload` already shows it.)

- [ ] **Step 5: Push notifications (site C)**

In the push `PROVIDER_ENABLE` handler (~line 698), the current structure inside `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { if (permission not granted) { ... } else { areNotificationsEnabled check } }` has two branches for the not-granted case: a rationale-gated AlertDialog, and a direct `requestPermissions` call. Replace BOTH branches (the whole `if (ActivityCompat.shouldShowRequestPermissionRationale(...)) { ... } else { requestPermissions(...) }` block) with a single unconditional disclosure:

```kotlin
                            PermissionDisclosures.show(
                                this@OrMainActivity,
                                R.string.push_notification_disclosure_title,
                                R.string.push_notification_disclosure_body,
                                onAccept = {
                                    requestPermissions(
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                        pushResponseCode
                                    )
                                },
                                onDecline = {
                                    sharedPreferences.edit()
                                        .putBoolean(
                                            ORConstants.PUSH_PROVIDER_DISABLED_KEY,
                                            true
                                        )
                                        .apply()
                                    notifyClient(
                                        hashMapOf(
                                            "action" to "PROVIDER_ENABLE",
                                            "provider" to "push",
                                            "hasPermission" to false,
                                            "success" to true
                                        )
                                    )
                                }
                            )
```

Keep everything else in the handler untouched: the `else` branch with the `areNotificationsEnabled()` settings dialog, and the unconditional `FirebaseMessaging.getInstance().token` block after it. The old dialog's negative-button logic is exactly what `onDecline` above reproduces. The `runOnUiThread` wrapper that existed around the old dialog is no longer needed (the helper does it internally) — remove it.

- [ ] **Step 6: Notify web app when the system push dialog is denied**

In `onRequestPermissionsResult`, the `requestCode == pushResponseCode` branch currently only notifies on grant. Replace the branch body with:

```kotlin
        } else if (requestCode == pushResponseCode) {
            val granted =
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            notifyClient(
                hashMapOf(
                    "action" to "PROVIDER_ENABLE",
                    "provider" to "push",
                    "hasPermission" to granted,
                    "success" to true
                )
            )
        }
```

- [ ] **Step 7: Verify compile**

Run: `./gradlew :ORLib:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add ORLib/src/main/java/io/openremote/orlib/ui/OrMainActivity.kt
git commit -m "Show prominent disclosure before location, notification and storage permission requests"
```

---

### Task 4: QrScannerActivity camera flow

**Files:**
- Modify: `ORLib/src/main/java/io/openremote/orlib/ui/QrScannerActivity.kt` (`surfaceCreated` ~line 63, `onRequestPermissionsResult` ~line 112; add one field)

**Interfaces:**
- Consumes: `PermissionDisclosures.show(...)` (same package `io.openremote.orlib.ui` — no import needed); strings `camera_disclosure_title`, `camera_disclosure_body`.

- [ ] **Step 1: Add field**

At the top of the class next to the other fields:

```kotlin
    private var cameraDisclosureShown = false
```

- [ ] **Step 2: Show disclosure in `surfaceCreated`**

Replace the `surfaceCreated` override body with:

```kotlin
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    if (ActivityCompat.checkSelfPermission(
                            this@QrScannerActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraSource!!.start(surfaceView!!.holder)
                    } else if (!cameraDisclosureShown) {
                        cameraDisclosureShown = true
                        PermissionDisclosures.show(
                            this@QrScannerActivity,
                            R.string.camera_disclosure_title,
                            R.string.camera_disclosure_body,
                            onAccept = {
                                ActivityCompat.requestPermissions(
                                    this@QrScannerActivity,
                                    arrayOf(Manifest.permission.CAMERA),
                                    REQUEST_CAMERA_PERMISSION
                                )
                            },
                            onDecline = {
                                setResult(RESULT_CANCELED)
                                finish()
                            }
                        )
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
```

The `cameraDisclosureShown` guard stops a second dialog when the surface is recreated after returning from the permission dialog.

- [ ] **Step 3: Denial retry dialog exits on "No"**

In `onRequestPermissionsResult`, the denial path shows the `camera_needed_alert_*` retry dialog with `.setNegativeButton(R.string.no, null)` — user is left on a black preview. Change that line to:

```kotlin
                .setNegativeButton(R.string.no) { _, _ ->
                    setResult(RESULT_CANCELED)
                    finish()
                }
```

Keep the rest of the dialog as is (the retry dialog itself is compliant: it precedes the re-request).

- [ ] **Step 4: Verify compile**

Run: `./gradlew :ORLib:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add ORLib/src/main/java/io/openremote/orlib/ui/QrScannerActivity.kt
git commit -m "Show prominent disclosure before camera permission request in QR scanner"
```

---

### Task 5: BleProvider + ESPProvisionProvider Bluetooth flows, final build

**Files:**
- Modify: `ORLib/src/main/java/io/openremote/orlib/service/BleProvider.kt` (`requestPermissions` ~line 467)
- Modify: `ORLib/src/main/java/io/openremote/orlib/service/ESPProvisionProvider.kt` (`requestPermissions` ~line 279)

**Interfaces:**
- Consumes: `PermissionDisclosures.show(...)` from Task 1 (import `io.openremote.orlib.ui.PermissionDisclosures` in both files); strings `bluetooth_disclosure_title`, `bluetooth_disclosure_body`, `bluetooth_location_disclosure_body`.

**Context:** Both providers request BLUETOOTH_SCAN/BLUETOOTH_CONNECT (API 31+) or ACCESS_FINE_LOCATION (below 31, needed for BLE scanning) directly. Decline behavior today on system-dialog denial is "nothing happens" (the web app gets no callback), so `onDecline` stays empty — do not invent new callback messages.

- [ ] **Step 1: BleProvider**

Add import `io.openremote.orlib.ui.PermissionDisclosures`. Replace the entire `requestPermissions(activity: Activity)` function with:

```kotlin
    private fun requestPermissions(activity: Activity) {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            R.string.bluetooth_disclosure_body
        } else {
            R.string.bluetooth_location_disclosure_body
        }
        PermissionDisclosures.show(
            activity,
            R.string.bluetooth_disclosure_title,
            message,
            onAccept = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ),
                        BLUETOOTH_PERMISSION_REQUEST_CODE
                    )
                } else {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        BLUETOOTH_PERMISSION_REQUEST_CODE
                    )
                }
            }
        )
    }
```

- [ ] **Step 2: ESPProvisionProvider**

Add import `io.openremote.orlib.ui.PermissionDisclosures`. Replace the entire `requestPermissions(activity: Activity)` function with the same shape, using this provider's request code:

```kotlin
    private fun requestPermissions(activity: Activity) {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            R.string.bluetooth_disclosure_body
        } else {
            R.string.bluetooth_location_disclosure_body
        }
        PermissionDisclosures.show(
            activity,
            R.string.bluetooth_disclosure_title,
            message,
            onAccept = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ),
                        ESPProvisionProvider.Companion.BLUETOOTH_PERMISSION_ESPPROVISION_REQUEST_CODE
                    )
                } else {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        ESPProvisionProvider.Companion.BLUETOOTH_PERMISSION_ESPPROVISION_REQUEST_CODE
                    )
                }
            }
        )
    }
```

- [ ] **Step 3: Verify compile of library AND app**

Run: `./gradlew :ORLib:assembleDebug :GenericApp:app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add ORLib/src/main/java/io/openremote/orlib/service/BleProvider.kt ORLib/src/main/java/io/openremote/orlib/service/ESPProvisionProvider.kt
git commit -m "Show prominent disclosure before Bluetooth permission requests"
```

---

## Manual QA checklist (post-implementation, human or emulator)

- Fresh install → enable geofence from web app → disclosure dialog appears BEFORE any system location dialog; Continue → system dialog; then background disclosure → system "allow all the time" screen.
- Decline first disclosure → no system dialog ever shown; web app receives geofence response.
- QR scan → camera disclosure before system camera dialog; "No thanks" closes scanner.
- Push enable on Android 13+ → notification disclosure before system dialog.
- BLE / ESP provisioning → Bluetooth disclosure before system dialog.
- File download on Android 9 device/emulator → storage disclosure; on Android 10+ no dialog, download starts.
