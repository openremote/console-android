package io.openremote.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.DownloadManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.view.*
import android.webkit.*
import android.webkit.ConsoleMessage.MessageLevel
import android.webkit.WebView.WebViewTransport
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.iid.FirebaseInstanceId
import io.openremote.app.R
import io.openremote.app.models.ORAppConfig
import io.openremote.app.network.ApiManager
import io.openremote.app.service.GeofenceProvider
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class MainActivity : Activity() {
    private val connectivityChangeReceiver: ConnectivityChangeReceiver =
        ConnectivityChangeReceiver()
    private var timeOutHandler: Handler? = null
    private var timeOutRunnable: Runnable? = null
    private var progressBar: ProgressBar? = null
    private var webViewTimeout = WEBVIEW_LOAD_TIMEOUT_DEFAULT
    private var webViewLoaded = false
    private var sharedPreferences: SharedPreferences? = null
    private var geofenceProvider: GeofenceProvider? = null
    private var consoleId: String? = null
    private var appConfig: ORAppConfig? = null
    private var onDownloadCompleteReciever: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context, intent: Intent) {
            val action = intent.action
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
                Toast.makeText(applicationContext, R.string.download_completed, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private val clientUrl: String?
        get() {
            var returnValue: String? = null

            sharedPreferences?.let { pref ->
                pref.getString("project", null)?.let { projectName ->
                    pref.getString("realm", null)?.let { realmName ->
                        returnValue = "https://${projectName}.openremote.io/api/$realmName"
                    }
                }
            }

            return returnValue
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        try {
            val timeoutStr = getString(R.string.OR_CONSOLE_LOAD_TIMEOUT)
            webViewTimeout = timeoutStr.toInt()
        } catch (nfe: NumberFormatException) {
            println("Could not parse console load timeout value: $nfe")
        }

        // Enable remote debugging of WebView from Chrome Debugger tools
        if (0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            LOG.info("Enabling remote debugging")
            WebView.setWebContentsDebuggingEnabled(true)
        }
        setContentView(R.layout.activity_main)
        if (savedInstanceState != null) {
            webView?.restoreState(savedInstanceState)
        } else {
            initializeWebView()
        }

        progressBar = findViewById(R.id.webProgressBar)
        progressBar?.max = 100
        progressBar?.progress = 1

        if (intent.hasExtra(APP_CONFIG_KEY)) {
            appConfig = jacksonObjectMapper().readValue(
                intent.getStringExtra(APP_CONFIG_KEY),
                ORAppConfig::class.java
            )
        }

        if (appConfig == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

            val project = sharedPreferences!!.getString("project", null)
            val realm = sharedPreferences!!.getString("realm", null)

            if (!project.isNullOrBlank() && !realm.isNullOrBlank()) {
                val apiManager = ApiManager("https://${project}.openremote.io/api/$realm")
                apiManager.getAppConfig { statusCode, appConfig, error ->
                    if (statusCode in 200..299) {
                        this.appConfig = appConfig
                        processAppConfig()
                    }
                    openIntentUrl(intent)
                }
            }
        } else {
            processAppConfig()
            openIntentUrl(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        openIntentUrl(intent)
    }

    private fun processAppConfig() {
        if (appConfig != null) {

            if (!appConfig!!.primaryColor.isNullOrBlank()) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = Color.parseColor(appConfig!!.primaryColor!!)

                progressBar?.progressTintList =
                    ColorStateList.valueOf(Color.parseColor(appConfig!!.primaryColor!!))
            }

            if (appConfig!!.menuEnabled && !appConfig!!.links.isNullOrEmpty()) {
                val floatingActionButton = FloatingActionButton(this)

                val layoutParams = RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                val margin = 36
                when (appConfig!!.menuPosition) {
                    "BOTTOM_RIGHT" -> {
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END)
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                        layoutParams.marginEnd = margin
                        layoutParams.bottomMargin = margin
                    }
                    "TOP_RIGHT" -> {
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END)
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                        layoutParams.marginEnd = margin
                        layoutParams.topMargin = margin
                    }
                    "TOP_LEFT" -> {
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START)
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                        layoutParams.marginStart = margin
                        layoutParams.topMargin = margin
                    }
                    else -> {
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START)
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                        layoutParams.marginStart = margin
                        layoutParams.bottomMargin = margin
                    }
                }
                floatingActionButton.layoutParams = layoutParams
                floatingActionButton.setImageResource(R.drawable.ic_menu)
                if (!appConfig!!.primaryColor.isNullOrBlank()) {
                    floatingActionButton.backgroundTintList =
                        ColorStateList.valueOf(Color.parseColor(appConfig!!.primaryColor!!))
                }
                if (!appConfig!!.secondaryColor.isNullOrBlank()) {
                    floatingActionButton.imageTintList =
                        ColorStateList.valueOf(Color.parseColor(appConfig!!.secondaryColor!!))
                } else {
                    floatingActionButton.imageTintList = ColorStateList.valueOf(Color.DKGRAY)
                }
                floatingActionButton.setOnClickListener {
                    // We are showing only toast message. However, you can do anything you need.
                    val popupMenu = PopupMenu(this@MainActivity, it)
                    for ((index, link) in appConfig!!.links!!.withIndex()) {
                        popupMenu.menu.add(index, index, index, link.displayText)
                    }
                    popupMenu.setOnMenuItemClickListener { item ->
                        loadUrl(appConfig!!.links!![item.itemId].pageLink)
                        true
                    }
                    popupMenu.show()
                }

                activity_web.addView(floatingActionButton)
            }
        }
    }

    private fun openIntentUrl(intent: Intent) {
        when {
            intent.hasExtra("appUrl") -> {
                val url = intent.getStringExtra("appUrl")
                LOG.fine("Loading web view: $url")
                loadUrl(url)
            }
            appConfig != null -> {
                LOG.fine("Loading web view: ${appConfig!!.initialUrl}")
                loadUrl(appConfig!!.initialUrl)
            }
            else -> {
                var url = clientUrl
                val intentUrl = intent.getStringExtra("appUrl")
                if (intentUrl != null) {
                    url = if (intentUrl.startsWith("http") || intentUrl.startsWith("https")) {
                        intentUrl
                    } else {
                        url + intentUrl
                    }
                }
                LOG.fine("Loading web view: $url")
                loadUrl(url)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            connectivityChangeReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )
        registerReceiver(
            onDownloadCompleteReciever,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(connectivityChangeReceiver)
        unregisterReceiver(onDownloadCompleteReciever)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (webView != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            finish()
                        }
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
    }

    private fun reloadWebView() {
        var url = webView.url
        if ("about:blank" == url) {
            url = clientUrl
            LOG.fine("Reloading web view: $url")
            loadUrl(url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initializeWebView() {
        LOG.fine("Initializing web view")
        val webAppInterface = WebAppInterface(this)
        webView.addJavascriptInterface(webAppInterface, "MobileInterface")
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.setSupportMultipleWindows(true)
        webView.setOnLongClickListener { true }
        webView.isLongClickable = false
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                // When initialising Keycloak with an invalid offline refresh token (e.g. wrong nonce because
                // server was reinstalled), we detect the failure and then don't show an error view. We clear the stored
                // invalid token. The web app will then start a new login.
                if (request.url.lastPathSegment != null && request.url.lastPathSegment == "token" && request.method == "POST" && errorResponse.statusCode == 400) {
                    storeData(getString(R.string.SHARED_PREF_REFRESH_TOKEN), null)
                    return
                }
                handleError(
                    errorResponse.statusCode,
                    errorResponse.reasonPhrase,
                    request.url.toString(),
                    request.isForMainFrame
                )
            }

            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String,
                failingUrl: String
            ) {
                handleError(errorCode, description, failingUrl, true)
            }

            @TargetApi(Build.VERSION_CODES.M)
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
                    error.errorCode, error.description.toString(),
                    request.url.toString(), request.isForMainFrame
                )
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progressBar!!.visibility = View.VISIBLE
                timeOutRunnable = Runnable {
                    if (!webViewLoaded) {
                        handleError(ERROR_TIMEOUT, "Connection timed out", url, true)
                    }
                }
                timeOutHandler = Handler(Looper.myLooper())
                timeOutHandler!!.postDelayed(timeOutRunnable, 5000)
            }

            override fun onPageFinished(view: WebView, url: String) {
                webViewLoaded = true
                progressBar!!.visibility = View.GONE
                timeOutHandler!!.removeCallbacks(timeOutRunnable)
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
                return super.shouldOverrideUrlLoading(view, request)
            }

            private fun handleError(
                errorCode: Int,
                description: String,
                failingUrl: String?,
                isForMainFrame: Boolean
            ) {
                LOG.warning("Error requesting '$failingUrl': $errorCode($description)")

                // This will be the URL loaded into the webview itself (false for images etc. of the main page)
                if (isForMainFrame) {

                    // Check page load error URL
                    val errorUrl = getString(R.string.OR_CONSOLE_LOAD_ERROR_URL)
                    if (!TextUtils.isEmpty(errorUrl) && failingUrl != errorUrl) {
                        LOG.info("Loading error URL: $errorUrl")
                        loadUrl(errorUrl)
                        return
                    }
                } else {
                    if (java.lang.Boolean.parseBoolean(getString(R.string.OR_CONSOLE_IGNORE_PAGE_ERRORS))) {
                        return
                    }

                    //TODO should we always ignore image errors?
                    if (failingUrl != null && (failingUrl.endsWith("png")
                                || failingUrl.endsWith("jpg")
                                || failingUrl.endsWith("ico"))
                    ) {
                        LOG.info("Ignoring error loading image resource")
                        return
                    }
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
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

            override fun onProgressChanged(view: WebView, progress: Int) {
                progressBar!!.progress = progress
            }

            override fun onCreateWindow(
                view: WebView,
                dialog: Boolean,
                userGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val newWebView = WebView(this@MainActivity)
                view.addView(newWebView)
                val transport = resultMsg.obj as WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        val browserIntent = Intent(Intent.ACTION_VIEW)
                        browserIntent.data = Uri.parse(url)
                        startActivity(browserIntent)
                        return true
                    }
                }
                return true
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    writePermission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Write permission has not been granted yet, request it.
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(writePermission),
                    WRITE_PERMISSION_FOR_DOWNLOAD
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

    private fun loadUrl(url: String?) {
        webViewLoaded = false
        val temp = url!!.replace(" ", "%20")
        webView.loadUrl(temp)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == WRITE_PERMISSION_FOR_DOWNLOAD) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(applicationContext, R.string.downloading_file, Toast.LENGTH_LONG)
                    .show()
            }
        } else if (requestCode == GeofenceProvider.locationReponseCode) {
            geofenceProvider?.onRequestPermissionsResult(requestCode, permissions, grantResults)
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
            LOG.info("Received WebApp message: $data")

            when (messageType) {
                "error" -> {
                    LOG.fine("Received WebApp message, error: " + data?.getString("error"))
                    val mainHandler = Handler(mainLooper)
                    Toast.makeText(
                        this@MainActivity,
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
                            }
                        }
                    }
                }
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
                    (activity as MainActivity).consoleId = consoleId
                    val baseUrl = sharedPreferences?.let { pref ->
                        pref.getString("project", null)?.let { projectName ->
                            pref.getString("realm", null)?.let { realmName ->
                                "https://${projectName}.openremote.io/api/$realmName"
                            }
                        }
                    } ?: ""
                    geofenceProvider?.enable(this@MainActivity, baseUrl,
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
                        this@MainActivity,
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
                    response["disabled"] = sharedPreferences!!.contains(PUSH_PROVIDER_DISABLED_KEY)
                    response["requiresPermission"] = false
                    response["hasPermission"] = true
                    response["success"] = true
                    notifyClient(response)
                }
                action.equals("PROVIDER_ENABLE", ignoreCase = true) -> {
                    val consoleId = data.getString("consoleId")
                    sharedPreferences!!.edit()
                        .putString(GeofenceProvider.Companion.consoleIdKey, consoleId)
                        .remove(PUSH_PROVIDER_DISABLED_KEY)
                        .apply()
                    // TODO: Implement topic support
                    FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener { task ->
                        val response: MutableMap<String, Any?> =
                            HashMap()
                        response["action"] = "PROVIDER_ENABLE"
                        response["provider"] = "push"
                        response["hasPermission"] = true
                        response["success"] = true
                        val responseData: MutableMap<String, Any> =
                            HashMap()
                        if (task.isSuccessful) {
                            responseData["token"] = task.result.token
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
                    sharedPreferences!!.edit().putBoolean(PUSH_PROVIDER_DISABLED_KEY, true).apply()
                    notifyClient(response)
                }
            }
        }

        @Throws(JSONException::class)
        private fun handleStorageProviderMessage(data: JSONObject) {
            val action = data.getString("action")
            when {
                action.equals("PROVIDER_INIT", ignoreCase = true) -> {
                    val response: MutableMap<String, Any> = HashMap()
                    response["action"] = "PROVIDER_INIT"
                    response["provider"] = "storage"
                    response["version"] = "1.0.0"
                    response["enabled"] = true
                    response["requiresPermission"] = false
                    response["hasPermission"] = true
                    response["success"] = true
                    notifyClient(response)
                }
                action.equals("PROVIDER_ENABLE", ignoreCase = true) -> {
                    // Doesn't require enabling but just in case it gets called lets return a valid response
                    val response: MutableMap<String, Any> = HashMap()
                    response["action"] = "PROVIDER_ENABLE"
                    response["provider"] = "storage"
                    response["hasPermission"] = true
                    response["success"] = true
                    notifyClient(response)
                }
                action.equals("STORE", ignoreCase = true) -> {
                    try {
                        val key = data.getString("key")
                        val valueJson = data.getString("value")
                        storeData(key, valueJson)
                    } catch (e: JSONException) {
                        LOG.log(Level.SEVERE, "Failed to store data", e)
                    }
                }
                action.equals("RETRIEVE", ignoreCase = true) -> {
                    try {
                        val key = data.getString("key")
                        val dataJson = retrieveData(key)
                        val response: MutableMap<String, Any?> = HashMap()
                        response["action"] = "RETRIEVE"
                        response["provider"] = "storage"
                        response["key"] = key
                        response["value"] = dataJson
                        notifyClient(response)
                    } catch (e: JSONException) {
                        LOG.log(Level.SEVERE, "Failed to retrieve data", e)
                    }
                }
            }
        }

    }

    private fun notifyClient(data: Map<String, Any?>?) {
        try {
            val jsonString = ObjectMapper().writeValueAsString(data)
            runOnUiThread {
                webView.evaluateJavascript(
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

    private fun storeData(key: String?, data: String?) {
        val editor = sharedPreferences!!.edit()
        if (data == null) {
            editor.remove(key)
        } else {
            editor.putString(key, data)
        }
        editor.apply()
    }

    private fun retrieveData(key: String?): String? {
        return sharedPreferences!!.getString(key, null)
    }

    private fun onConnectivityChanged(connectivity: Boolean) {
        LOG.info("Connectivity changed: $connectivity")
        if (connectivity) {
            reloadWebView()
        } else {
            Toast.makeText(this, "Check your connection", Toast.LENGTH_LONG).show()
        }
    }

    private inner class ConnectivityChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            onConnectivityChanged(activeNetwork != null && activeNetwork.isConnectedOrConnecting)
        }
    }

    companion object {
        private val LOG = Logger.getLogger(
            MainActivity::class.java.name
        )
        private const val WRITE_PERMISSION_FOR_DOWNLOAD = 999
        private const val WRITE_PERMISSION_FOR_LOGGING = 1000
        private const val WEBVIEW_LOAD_TIMEOUT_DEFAULT = 5000
        const val ACTION_BROADCAST = "ACTION_BROADCAST"
        const val PUSH_PROVIDER_DISABLED_KEY = "PushProviderDisabled"
        const val CONSOLE_ID_KEY = "consoleId"
        const val APP_CONFIG_KEY = "APP_CONFIG_KEY"
    }
}