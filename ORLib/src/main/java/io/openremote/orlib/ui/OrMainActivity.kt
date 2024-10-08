package io.openremote.orlib.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.webkit.ConsoleMessage.MessageLevel
import android.webkit.WebView.WebViewTransport
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.firebase.messaging.FirebaseMessaging
import io.openremote.orlib.ORConstants
import io.openremote.orlib.ORConstants.BASE_URL_KEY
import io.openremote.orlib.ORConstants.CLEAR_URL
import io.openremote.orlib.R
import io.openremote.orlib.databinding.ActivityOrMainBinding
import io.openremote.orlib.service.BleProvider
import io.openremote.orlib.service.ConnectivityChangeReceiver
import io.openremote.orlib.service.GeofenceProvider
import io.openremote.orlib.service.QrScannerProvider
import io.openremote.orlib.service.SecureStorageProvider
import io.openremote.orlib.shared.SharedData.offlineActivity
import org.json.JSONException
import org.json.JSONObject
import java.util.logging.Level
import java.util.logging.Logger


open class OrMainActivity : Activity() {

    private val LOG = Logger.getLogger(
        OrMainActivity::class.java.name
    )

    private val locationResponseCode = 555
    private var locationCallback: GeolocationPermissions.Callback? = null;
    private var locationOrigin: String? = null;

    private val pushResponseCode = 556

    private lateinit var binding: ActivityOrMainBinding
    private lateinit var sharedPreferences: SharedPreferences


    private var mapper = jacksonObjectMapper()
    private val connectivityChangeReceiver: ConnectivityChangeReceiver =
        ConnectivityChangeReceiver(onConnectivityChanged = ::onConnectivityChanged)
    private var timeOutHandler: Handler? = null
    private var timeOutRunnable: Runnable? = null
    private var progressBar: ProgressBar? = null
    private var webViewIsLoading = false
    private var webViewLoaded = false
    private var lastConnectivity = false
    private var geofenceProvider: GeofenceProvider? = null
    private var qrScannerProvider: QrScannerProvider? = null
    private var bleProvider: BleProvider? = null
    private var secureStorageProvider: SecureStorageProvider? = null
    private var consoleId: String? = null
    private var connectFailCount: Int = 0
    private var connectFailResetHandler: Handler? = null
    private var connectFailResetRunnable: Runnable? = null
    protected var baseUrl: String? = null
    private var onDownloadCompleteReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context, intent: Intent) {
            val action = intent.action
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
                Toast.makeText(applicationContext, R.string.download_completed, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        if (!connectivityChangeReceiver.isInternetAvailable(this)) {
            val intent = Intent(this, offlineActivity)
            startActivity(intent)
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Enable remote debugging of WebView from Chrome Debugger tools
        if (0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            LOG.info("Enabling remote debugging")
            WebView.setWebContentsDebuggingEnabled(true)
        }

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            initializeWebView()
        }

        progressBar = findViewById(R.id.webProgressBar)
        progressBar?.max = 100
        progressBar?.progress = 1

        if (intent.hasExtra(BASE_URL_KEY)) {
            baseUrl = intent.getStringExtra(BASE_URL_KEY)
        }

        openIntentUrl(intent)
    }


    override fun onNewIntent(intent: Intent) {
        openIntentUrl(intent)
    }

    private fun openIntentUrl(intent: Intent) {
        when {
            intent.hasExtra("appUrl") -> {
                val url = intent.getStringExtra("appUrl")!!
                LOG.fine("Loading web view: $url")
                loadUrl(url)
            }

            else -> {
                var url = baseUrl
                val intentUrl = intent.getStringExtra("appUrl")
                if (intentUrl != null) {
                    url = if (intentUrl.startsWith("http") || intentUrl.startsWith("https")) {
                        intentUrl
                    } else {
                        url + intentUrl
                    }
                }
                LOG.fine("Loading web view: $url")
                if (url != null) {
                    loadUrl(url)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            connectivityChangeReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            onDownloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (!webViewLoaded && !webViewIsLoading) {
            reloadWebView()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(connectivityChangeReceiver)
        unregisterReceiver(onDownloadCompleteReceiver)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (binding.webView.canGoBack()) {
                        binding.webView.goBack()
                    } else {
                        finish()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.webView.saveState(outState)
    }

    private fun reloadWebView() {
        var url = binding.webView.url
        if ("about:blank" == url || url == null) {
            url = baseUrl
        }
        LOG.fine("Reloading web view: $url")
        loadUrl(url!!)
    }


    @SuppressLint("SetJavaScriptEnabled")
    fun initializeWebView() {
        LOG.fine("Initializing web view")
        val webAppInterface = WebAppInterface(this)
        binding.webView.apply {
            addJavascriptInterface(webAppInterface, "MobileInterface")
            settings.javaScriptEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setGeolocationEnabled(true)
            settings.setSupportMultipleWindows(true)
            webViewClient = object : WebViewClient() {
                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    // When initialising Keycloak with an invalid offline refresh token (e.g. wrong nonce because
                    // server was reinstalled), we detect the failure and then don't show an error view.
                    // The JS code will handle the error and show the login screen.
                    if (request.url.lastPathSegment != null && request.url.lastPathSegment == "token" && request.method == "POST" && errorResponse.statusCode == 400) {
                        return
                    }

                    handleError(
                        errorResponse.statusCode,
                        errorResponse.reasonPhrase,
                        request.url.toString()
                    )
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    // Remote debugging session from Chrome wants to load about:blank and then fails with "ERROR_UNSUPPORTED_SCHEME", ignore
                    if ("net::ERR_CACHE_MISS".contentEquals(error.description)) {
                        return
                    }
                    if (request.url.toString() == "about:blank" && error.errorCode == ERROR_UNSUPPORTED_SCHEME) {
                        return
                    }
                    handleError(
                        error.errorCode,
                        error.description.toString(),
                        request.url.toString()
                    )
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    progressBar!!.visibility = View.VISIBLE
                    timeOutRunnable = Runnable {
                        if (!webViewLoaded) {
                            handleError(ERROR_TIMEOUT, "Connection timed out", url)
                        }
                    }
                    timeOutHandler = Looper.myLooper()?.let { Handler(it) }
                    timeOutHandler!!.postDelayed(timeOutRunnable!!, 5000)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    webViewLoaded = true
                    webViewIsLoading = false
                    progressBar!!.visibility = View.GONE
                    timeOutRunnable?.let { timeOutHandler!!.removeCallbacks(it) }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    if (request.url.scheme.equals("webbrowser", ignoreCase = true)) {
                        val newUrl = request.url.buildUpon().scheme("https").build().toString()
                        val i = Intent(Intent.ACTION_VIEW)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        i.data = Uri.parse(newUrl)
                        startActivity(i)
                        return true
                    }
                    if (!request.url.isAbsolute && baseUrl?.isNotEmpty()!!) {
                        view.loadUrl("${baseUrl}/${request.url}")
                        return true
                    }

                    return super.shouldOverrideUrlLoading(view, request)
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {

                    if (view == binding.webView && detail?.didCrash() == true) {
                        onCreate(null)
                        return true
                    }

                    return super.onRenderProcessGone(view, detail)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val msg =
                        "WebApp console (" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + "): " + consoleMessage.message()
                    when (consoleMessage.messageLevel()) {
                        MessageLevel.DEBUG, MessageLevel.TIP -> LOG.fine(msg)
                        MessageLevel.LOG -> LOG.info(msg)
                        else -> LOG.severe(msg)
                    }
                    return true
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        callback?.invoke(origin, true, false)
                    } else {
                        locationCallback = callback
                        locationOrigin = origin
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ),
                            locationResponseCode
                        )
                    }
                }

                override fun onProgressChanged(view: WebView, progress: Int) {
                    progressBar!!.progress = progress
                }

                override fun onCreateWindow(
                    view: WebView,
                    dialog: Boolean,
                    userGesture: Boolean,
                    resultMsg: Message
                ): Boolean {
                    val newWebView = WebView(this@OrMainActivity)
                    view.addView(newWebView)
                    val transport = resultMsg.obj as WebViewTransport
                    transport.webView = newWebView
                    resultMsg.sendToTarget()
                    newWebView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val browserIntent = Intent(Intent.ACTION_VIEW)
                            browserIntent.data = request.url
                            startActivity(browserIntent)
                            return true
                        }
                    }
                    return true
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(
                        this@OrMainActivity,
                        writePermission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Write permission has not been granted yet, request it.
                    requestPermissions(
                        arrayOf(writePermission),
                        ORConstants.WRITE_PERMISSION_FOR_DOWNLOAD
                    )
                } else {
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
            }
        }
    }

    private fun handleError(
        errorCode: Int,
        description: String,
        failingUrl: String?
    ) {
        LOG.warning("Error requesting '$failingUrl': $errorCode($description)")
        //TODO should we always ignore image errors and locale json files?
        if (failingUrl != null && (failingUrl.endsWith("png")
                    || failingUrl.endsWith("jpg")
                    || failingUrl.endsWith("ico")
                    || failingUrl.contains("locales")
                    || failingUrl.contains("consoleappconfig"))
        ) {
            LOG.info("Ignoring error loading image resource")
            return
        }
        // This will be the URL loaded into the webview itself (false for images etc. of the main page)
        // Check page load error URL
        if (errorCode >= 500) {
            if (baseUrl != null && baseUrl != failingUrl && connectFailCount < 10) {
                loadUrl(baseUrl!!)
                Toast.makeText(this, description, Toast.LENGTH_SHORT).show()
                connectFailCount++
                connectFailResetRunnable?.let { connectFailResetHandler!!.removeCallbacks(it) }
                connectFailResetRunnable = Runnable {
                    connectFailCount = 0
                }
                connectFailResetHandler = Looper.myLooper()?.let { Handler(it) }
                connectFailResetHandler!!.postDelayed(connectFailResetRunnable!!, 5000)
            } else {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.putExtra(CLEAR_URL, baseUrl)
                    launchIntent.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(launchIntent)
                    finish()
                }
                Toast.makeText(
                    applicationContext,
                    "The main page couldn't be opened",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun loadUrl(url: String) {
        if (connectivityChangeReceiver.isInternetAvailable(this)) {
            webViewIsLoading = true
            webViewLoaded = false
            val encodedUrl = url.replace(" ", "%20")
            binding.webView.loadUrl(encodedUrl)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == ORConstants.WRITE_PERMISSION_FOR_DOWNLOAD) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(applicationContext, R.string.downloading_file, Toast.LENGTH_LONG)
                    .show()
            }
        } else if (requestCode == GeofenceProvider.locationResponseCode) {
            geofenceProvider?.onRequestPermissionsResult(this)
        } else if (requestCode == locationResponseCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationCallback?.invoke(locationOrigin, true, false)
            }
        } else if (requestCode == BleProvider.BLUETOOTH_PERMISSION_REQUEST_CODE || requestCode == BleProvider.ENABLE_BLUETOOTH_REQUEST_CODE) {
            bleProvider?.onRequestPermissionsResult(
                this,
                requestCode,
                object : BleProvider.BleCallback {
                    override fun accept(responseData: Map<String, Any>) {
                        notifyClient(responseData)
                    }
                })
        } else if (requestCode == pushResponseCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notifyClient(
                    hashMapOf(
                        "action" to "PROVIDER_ENABLE",
                        "provider" to "push",
                        "hasPermission" to true,
                        "success" to true
                    )
                )
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == QrScannerProvider.REQUEST_SCAN_QR) {
            val response = when (resultCode) {
                RESULT_OK -> {
                    val qrResult = data?.getStringExtra("result")

                    hashMapOf(
                        "action" to "SCAN_RESULT",
                        "provider" to "qr",
                        "data" to hashMapOf("result" to qrResult)
                    )
                }

                else -> hashMapOf(
                    "action" to "SCAN_RESULT",
                    "provider" to "qr",
                    "data" to hashMapOf("result" to "FAILED")
                )
            }
            notifyClient(response)
        }
    }

    private inner class WebAppInterface(
        private val activity: Activity
    ) {
        @JavascriptInterface
        @Throws(JSONException::class)
        public fun postMessage(jsonMessage: String) {
            val reader = JSONObject(jsonMessage)
            val messageType = reader.getString("type")
            val data = reader.optJSONObject("data")
            LOG.info("Received WebApp message: $reader")

            when (messageType) {
                "error" -> {
                    LOG.fine("Received WebApp message, error: " + data?.getString("error"))
                    Toast.makeText(
                        this@OrMainActivity,
                        "Error occurred ${data?.getString("error")}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                "provider" -> {
                    data?.let {
                        val action = it.getString("action")
                        if (!action.isNullOrEmpty()) {
                            val provider = it.getString("provider")
                            when {
                                provider.equals("geofence", ignoreCase = true) -> {
                                    handleGeofenceProviderMessage(it)
                                }

                                provider.equals("push", ignoreCase = true) -> {
                                    handlePushProviderMessage(it)
                                }

                                provider.equals("storage", ignoreCase = true) -> {
                                    handleStorageProviderMessage(it)
                                }

                                provider.equals("qr", ignoreCase = true) -> {
                                    handleQrScannerProviderMessage(it)
                                }

                                provider.equals("ble", ignoreCase = true) -> {
                                    handleBleProviderMessage(it)
                                }
                            }
                        }
                    }
                }

                "CLEAR_WEB_HISTORY" -> {
                    handleClearHistoryMessage()
                }
            }
        }

        private fun handleClearHistoryMessage() {
            binding.webView.post {
                binding.webView.clearHistory()
            }
        }

        @Throws(JSONException::class)
        private fun handleGeofenceProviderMessage(data: JSONObject) {
            val action = data.getString("action")
            if (geofenceProvider == null) {
                geofenceProvider = GeofenceProvider(activity)
            }
            when {
                action.equals("PROVIDER_INIT", ignoreCase = true) -> {
                    val initData: Map<String, Any> = geofenceProvider!!.initialize()
                    notifyClient(initData)
                }

                action.equals("PROVIDER_ENABLE", ignoreCase = true) -> {
                    val consoleId = data.getString("consoleId")
                    (activity as OrMainActivity).consoleId = consoleId
                    geofenceProvider?.enable(this@OrMainActivity, baseUrl ?: "",
                        consoleId, object : GeofenceProvider.GeofenceCallback {
                            override fun accept(responseData: Map<String, Any>) {
                                notifyClient(responseData)
                            }
                        })
                }

                action.equals("PROVIDER_DISABLE", ignoreCase = true) -> {
                    geofenceProvider?.disable()
                    val response: MutableMap<String, Any> = HashMap()
                    response["action"] = "PROVIDER_DISABLE"
                    response["provider"] = "geofence"
                    notifyClient(response)
                }

                action.equals("GEOFENCE_REFRESH", ignoreCase = true) -> {
                    geofenceProvider?.refreshGeofences()
                }

                action.equals("GET_LOCATION", ignoreCase = true) -> {
                    geofenceProvider?.getLocation(
                        this@OrMainActivity,
                        object : GeofenceProvider.GeofenceCallback {
                            override fun accept(responseData: Map<String, Any>) {
                                notifyClient(responseData)
                            }
                        })
                }
            }
        }

        @Throws(JSONException::class)
        private fun handlePushProviderMessage(data: JSONObject) {
            val action = data.getString("action")
            when {
                action.equals("PROVIDER_INIT", ignoreCase = true) -> {
                    // Push permission is covered by the INTERNET permission and is not a runtime permission
                    val response: MutableMap<String, Any> = HashMap()
                    response["action"] = "PROVIDER_INIT"
                    response["provider"] = "push"
                    response["version"] = "fcm"
                    response["enabled"] = false
                    response["disabled"] =
                        sharedPreferences.contains(ORConstants.PUSH_PROVIDER_DISABLED_KEY)
                    response["requiresPermission"] = false
                    response["hasPermission"] = true
                    response["success"] = true
                    notifyClient(response)
                }

                action.equals("PROVIDER_ENABLE", ignoreCase = true) -> {
                    val consoleId = data.getString("consoleId")
                    sharedPreferences.edit()
                        .putString(ORConstants.CONSOLE_ID_KEY, consoleId)
                        .remove(ORConstants.PUSH_PROVIDER_DISABLED_KEY)
                        .apply()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@OrMainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            if (ActivityCompat.shouldShowRequestPermissionRationale(
                                    this@OrMainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            ) {
                                runOnUiThread {
                                    AlertDialog.Builder(this@OrMainActivity)
                                        .setTitle(R.string.push_notification_alert_title)
                                        .setMessage(R.string.push_notification_alert_message)
                                        .setIcon(R.drawable.ic_notification)
                                        .setCancelable(false)
                                        .setPositiveButton(
                                            R.string.yes
                                        ) { dialog, which ->
                                            requestPermissions(
                                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                                pushResponseCode
                                            )
                                        }
                                        .setNegativeButton(
                                            R.string.no
                                        ) { dialog, which ->
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
                                        }.show()
                                    }
                            } else {
                                requestPermissions(
                                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                    pushResponseCode
                                )
                            }
                        } else {
                            val mgr =
                                this@OrMainActivity.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            if (!mgr.areNotificationsEnabled()) {
                                AlertDialog.Builder(this@OrMainActivity)
                                    .setTitle(R.string.push_notification_alert_title)
                                    .setMessage(R.string.push_notification_alert_message)
                                    .setIcon(R.drawable.ic_notification)
                                    .setCancelable(false)
                                    .setPositiveButton(
                                        R.string.yes
                                    ) { dialog, which ->
                                        val intent = Intent()
                                        intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                        intent.putExtra(
                                            Settings.EXTRA_APP_PACKAGE,
                                            this@OrMainActivity.packageName
                                        )
                                        startActivity(intent)
                                    }
                                    .setNegativeButton(
                                        R.string.no
                                    ) { dialog, which ->
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
                                    }.show()
                            }
                        }
                    }
                    // TODO: Implement topic support
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        val response: MutableMap<String, Any?> =
                            HashMap()
                        response["action"] = "PROVIDER_ENABLE"
                        response["provider"] = "push"
                        response["hasPermission"] = true
                        response["success"] = true
                        val responseData: MutableMap<String, Any> =
                            HashMap()
                        if (task.isSuccessful) {
                            responseData["token"] = task.result
                        }
                        response["data"] = responseData
                        notifyClient(response)
                    }
                }

                action.equals("PROVIDER_DISABLE", ignoreCase = true) -> {
                    // Cannot disable push notifications
                    val response: MutableMap<String, Any?> = HashMap()
                    response["action"] = "PROVIDER_DISABLE"
                    response["provider"] = "push"
                    sharedPreferences.edit()
                        .putBoolean(ORConstants.PUSH_PROVIDER_DISABLED_KEY, true).apply()
                    notifyClient(response)
                }
            }
        }

        @Throws(JSONException::class)
        private fun handleStorageProviderMessage(data: JSONObject) {
            val action = data.getString("action")
            if (secureStorageProvider == null) {
                secureStorageProvider = SecureStorageProvider(activity)
            }
            when {
                action.equals("PROVIDER_INIT", ignoreCase = true) -> {
                    val response = secureStorageProvider!!.initialize()
                    notifyClient(response)
                }

                action.equals("PROVIDER_ENABLE", ignoreCase = true) -> {
                    // Doesn't require enabling but just in case it gets called lets return a valid response
                    val response = secureStorageProvider!!.enable()
                    notifyClient(response)
                }

                action.equals("STORE", ignoreCase = true) -> {
                    val key = data.getString("key")
                    val valueJson = data.getString("value")
                    secureStorageProvider!!.storeData(key, valueJson)
                }

                action.equals("RETRIEVE", ignoreCase = true) -> {
                    val key = data.getString("key")
                    try {
                        val response = secureStorageProvider!!.retrieveData(key)
                        notifyClient(response)
                    } catch (e: Exception) {
                        LOG.log(Level.SEVERE, "Failed to retrieve data", e)
                        notifyClient(
                            hashMapOf(
                                "action" to "RETRIEVE",
                                "provider" to "storage",
                                "key" to key,
                                "value" to null
                            )
                        )
                    }
                }
            }
        }

        @Throws(JSONException::class)
        private fun handleQrScannerProviderMessage(data: JSONObject) {
            val action = data.getString("action")
            if (qrScannerProvider == null) {
                qrScannerProvider = QrScannerProvider(activity)
            }
            when {
                action.equals("PROVIDER_INIT", ignoreCase = true) -> {
                    val initData: Map<String, Any> = qrScannerProvider!!.initialize()
                    notifyClient(initData)
                }

                action.equals("PROVIDER_ENABLE", ignoreCase = true) -> {

                    qrScannerProvider?.enable(object : QrScannerProvider.ScannerCallback {
                        override fun accept(responseData: Map<String, Any>) {
                            notifyClient(responseData)
                        }
                    })
                }

                action.equals("PROVIDER_DISABLE", ignoreCase = true) -> {
                    val response = qrScannerProvider?.disable()
                    notifyClient(response)
                }

                action.equals("SCAN_QR", ignoreCase = true) -> {
                    qrScannerProvider?.startScanner(this@OrMainActivity, object :
                        QrScannerProvider.ScannerCallback {
                        override fun accept(responseData: Map<String, Any>) {
                            notifyClient(responseData)
                        }
                    })
                }
            }
        }

        @Throws(JSONException::class)
        private fun handleBleProviderMessage(data: JSONObject) {
            val action = data.getString("action")
            if (bleProvider == null) {
                bleProvider = BleProvider(activity)
            }
            when {
                action.equals("PROVIDER_INIT", ignoreCase = true) -> {
                    val initData: Map<String, Any> = bleProvider!!.initialize()
                    notifyClient(initData)
                }

                action.equals("PROVIDER_ENABLE", ignoreCase = true) -> {
                    bleProvider?.enable(object : BleProvider.BleCallback {
                        override fun accept(responseData: Map<String, Any>) {
                            notifyClient(responseData)
                        }
                    })
                }

                action.equals("PROVIDER_DISABLE", ignoreCase = true) -> {
                    val response = bleProvider?.disable()
                    notifyClient(response)
                }

                action.equals("SCAN_BLE_DEVICES", ignoreCase = true) -> {
                    bleProvider?.startBLEScan(
                        this@OrMainActivity,
                        object : BleProvider.BleCallback {
                            override fun accept(responseData: Map<String, Any>) {
                                notifyClient(responseData)
                            }
                        })
                }

                action.equals("CONNECT_TO_DEVICE", ignoreCase = true) -> {
                    val address = data.getString("address")
                    bleProvider?.connectToDevice(address, object : BleProvider.BleCallback {
                        override fun accept(responseData: Map<String, Any>) {
                            notifyClient(responseData)
                        }
                    })
                }

                action.equals("DISCONNECT_FROM_DEVICE", ignoreCase = true) -> {
                    bleProvider?.disconnectFromDevice(object : BleProvider.BleCallback {
                        override fun accept(responseData: Map<String, Any>) {
                            notifyClient(responseData)
                        }
                    })
                }

                action.equals("SEND_TO_DEVICE", ignoreCase = true) -> {
                    val attributeId = data.getString("attributeId")
                    val value = data.get("value")
                    bleProvider?.sendToDevice(attributeId, value, object : BleProvider.BleCallback {
                        override fun accept(responseData: Map<String, Any>) {
                            notifyClient(responseData)
                        }
                    })
                }
            }
        }
    }

    private fun notifyClient(data: Map<String, Any?>?) {
        try {
            var jsonString = mapper.writeValueAsString(data)
            LOG.info("Sending response to client: $jsonString")

            // Double escape quotes (this is needed for browsers to be able to parse the response)
            jsonString = jsonString.replace("\\\"", "\\\\\"")

            runOnUiThread {
                binding.webView.evaluateJavascript(
                    String.format(
                        "OpenRemoteConsole._handleProviderResponse('%s')",
                        jsonString
                    ), null
                )
            }
        } catch (e: JsonProcessingException) {
            e.printStackTrace()
        }
    }

    private fun onConnectivityChanged(connectivity: Boolean) {
        LOG.info("Connectivity changed: $connectivity")
        if (connectivity && !webViewIsLoading && !lastConnectivity) {
            reloadWebView()
        }
        lastConnectivity = connectivity
    }
}
