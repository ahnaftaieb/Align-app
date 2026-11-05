package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import android.util.Base64
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var activeWebView: WebView? = null
    private var pendingSaveFileName: String? = null
    private var pendingSaveContent: String? = null

    // Dedicated launcher for web file inputs (<input type="file">)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (fileCallback != null) {
            val results: Array<Uri>? = if (result.resultCode == RESULT_OK && result.data != null) {
                val data = result.data!!
                if (data.data != null) {
                    arrayOf(data.data!!)
                } else if (data.clipData != null) {
                    val clipData = data.clipData!!
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else null
            } else null
            fileCallback?.onReceiveValue(results)
            fileCallback = null
        }
    }

    // Dedicated launcher for restoring backup files from Google Drive / Local Storage
    private val restoreFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val uri = result.data?.data
            if (uri != null) {
                readJsonAndLoadIntoWebView(uri)
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Dedicated launcher for Storage Access Framework (SAF) document creation in Google Drive / Storage
    private val saveDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingSaveContent != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingSaveContent!!.toByteArray(Charsets.UTF_8))
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Backup saved successfully to Google Drive / Storage!", Toast.LENGTH_LONG).show()
                }
                activeWebView?.post {
                    activeWebView?.evaluateJavascript(
                        "if (typeof showSalatToast === 'function') { showSalatToast('Backup saved to Google Drive / Storage!'); }",
                        null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Could not save file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        pendingSaveFileName = null
        pendingSaveContent = null
    }

    private fun readJsonAndLoadIntoWebView(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val jsonString = reader.readText()
                val base64 = Base64.encodeToString(
                    jsonString.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                activeWebView?.post {
                    activeWebView?.evaluateJavascript(
                        "if (typeof restoreAppDataFromBase64 === 'function') { restoreAppDataFromBase64('$base64'); } else if (typeof restoreAppDataFromJSON === 'function') { restoreAppDataFromJSON(decodeURIComponent(escape(atob('$base64')))); }",
                        null
                    )
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Backup data loaded successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Could not read selected backup: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun launchFileChooser(callback: ValueCallback<Array<Uri>>?, intent: Intent) {
        if (fileCallback != null) {
            fileCallback?.onReceiveValue(null)
        }
        fileCallback = callback
        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            fileCallback = null
            e.printStackTrace()
        }
    }

    fun launchRestoreChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "text/json", "application/octet-stream", "*/*"))
        }
        try {
            restoreFileLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
                }
                restoreFileLauncher.launch(fallbackIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Could not launch file picker", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun launchSaveDocumentChooser(fileName: String, content: String) {
        pendingSaveFileName = fileName
        pendingSaveContent = content
        try {
            saveDocumentLauncher.launch(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Could not open document creator", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchAndSyncFcmToken()
        }
    }

    private fun setupNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val prayerChannel = android.app.NotificationChannel(
                AlignFirebaseMessagingService.CHANNEL_PRAYER,
                "Prayer & Salat Reminders",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily prayer alerts and reflections"
                enableVibration(true)
            }
            val updatesChannel = android.app.NotificationChannel(
                AlignFirebaseMessagingService.CHANNEL_UPDATES,
                "App Updates & Announcements",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "General habit updates and community announcements"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(prayerChannel)
            notificationManager.createNotificationChannel(updatesChannel)
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                fetchAndSyncFcmToken()
            }
        } else {
            fetchAndSyncFcmToken()
        }
    }

    fun fetchAndSyncFcmToken() {
        try {
            val fcm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
            fcm.subscribeToTopic("all")
            fcm.token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    val prefs = getSharedPreferences(AlignFirebaseMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(AlignFirebaseMessagingService.KEY_FCM_TOKEN, token).apply()
                    activeWebView?.post {
                        activeWebView?.evaluateJavascript(
                            "if (typeof onFcmTokenReceived === 'function') { onFcmTokenReceived('$token'); }",
                            null
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupNotificationChannels()
        requestNotificationPermission()
        handleNotificationPayload(intent)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                activeWebView?.evaluateJavascript(
                    "(function() { if (document.body.classList.contains('timer-running')) { if (typeof toggleFocusTimer === 'function') toggleFocusTimer(); return true; } return false; })()"
                ) { result ->
                    if (result != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        setContent {
            WebViewScreen(
                activity = this,
                onWebViewCreated = { webView ->
                    activeWebView = webView
                    fetchAndSyncFcmToken()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        activeWebView?.onResume()
        activeWebView?.evaluateJavascript(
            "if (typeof syncFocusTimerFromRealTime === 'function') syncFocusTimerFromRealTime();",
            null
        )
    }

    override fun onPause() {
        super.onPause()
        activeWebView?.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationPayload(intent)
    }

    private fun handleNotificationPayload(intent: Intent?) {
        if (intent == null) return
        var targetUrl: String? = intent.getStringExtra("url")
            ?: intent.getStringExtra("update_url")
            ?: intent.getStringExtra("link")
            ?: intent.getStringExtra("download_url")
            ?: intent.getStringExtra("download")
            ?: intent.dataString

        if (targetUrl.isNullOrBlank() && intent.extras != null) {
            val bundle = intent.extras
            for (key in bundle!!.keySet()) {
                val value = bundle.get(key)?.toString()?.trim()
                if (value != null && (value.startsWith("http://") || value.startsWith("https://"))) {
                    targetUrl = value
                    break
                }
            }
        }

        if (!targetUrl.isNullOrBlank()) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(browserIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class WebAppInterface(
    private val context: Context,
    private val activity: MainActivity? = null
) {
    @JavascriptInterface
    fun fetchUrl(urlStr: String): String {
        return try {
            val future = java.util.concurrent.CompletableFuture.supplyAsync {
                val url = java.net.URL(urlStr)
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3500
                    readTimeout = 3500
                    instanceFollowRedirects = true
                    useCaches = false
                    defaultUseCaches = false
                    setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                    setRequestProperty("Pragma", "no-cache")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AlignApp/1.0")
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    ""
                }
            }
            future.get(4, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            ""
        }
    }
    @JavascriptInterface
    fun openJsonFileChooser() {
        Handler(Looper.getMainLooper()).post {
            activity?.launchRestoreChooser()
        }
    }

    @JavascriptInterface
    fun syncToGoogleDrive(fileName: String, content: String): Boolean {
        return try {
            val backupDir = File(context.cacheDir, "backups").apply { if (!exists()) mkdirs() }
            val file = File(backupDir, fileName)
            file.writeText(content, Charsets.UTF_8)

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Align App Backup ($fileName)")
                putExtra(Intent.EXTRA_TEXT, "Align App Data Backup - $fileName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Sync / Save Backup to Google Drive").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Select 'Save to Drive' to sync your backup to Google Drive", Toast.LENGTH_LONG).show()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JavascriptInterface
    fun saveToGoogleDriveOrSAF(fileName: String, content: String): Boolean {
        return try {
            Handler(Looper.getMainLooper()).post {
                activity?.launchSaveDocumentChooser(fileName, content)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JavascriptInterface
    fun shareToGoogleDrive(fileName: String, content: String): Boolean {
        return syncToGoogleDrive(fileName, content)
    }

    @JavascriptInterface
    fun saveFile(fileName: String, content: String, mimeType: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(content.toByteArray(Charsets.UTF_8))
                    }
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "File saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                    }
                    true
                } else {
                    false
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                file.writeText(content, Charsets.UTF_8)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "File saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                    putExtra(Intent.EXTRA_TEXT, content)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(shareIntent, "Save or Share $fileName").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                true
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }
        }
    }

    @JavascriptInterface
    fun copyToClipboard(label: String, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun vibrate(durationMs: Long) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun setKeepScreenOn(enabled: Boolean) {
        Handler(Looper.getMainLooper()).post {
            try {
                if (enabled) {
                    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JavascriptInterface
    fun syncSalatModuleActive(isActive: Boolean) {
        val prefs = context.getSharedPreferences(AlignFirebaseMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("salat_module_active", isActive).apply()
    }

    @JavascriptInterface
    fun getFcmToken(): String {
        val prefs = context.getSharedPreferences(AlignFirebaseMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(AlignFirebaseMessagingService.KEY_FCM_TOKEN, "") ?: ""
    }

    @JavascriptInterface
    fun requestPushPermission() {
        Handler(Looper.getMainLooper()).post {
            activity?.requestNotificationPermission()
        }
    }

    @JavascriptInterface
    fun triggerLocalNotification(title: String, message: String, isSalat: Boolean) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = if (isSalat) AlignFirebaseMessagingService.CHANNEL_PRAYER else AlignFirebaseMessagingService.CHANNEL_UPDATES
            
            if (isSalat) {
                val prefs = context.getSharedPreferences(AlignFirebaseMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
                val salatActive = prefs.getBoolean("salat_module_active", true)
                if (!salatActive) return
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)

            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun playSessionCompleteAlert() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = longArrayOf(0, 300, 150, 400)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
            }
            val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = android.media.RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun WebViewScreen(
    activity: MainActivity? = null,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                onWebViewCreated(this)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            if (!url.contains("localhost") && !url.contains("127.0.0.1")) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    return true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        return false
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        android.util.Log.w("AlignApp", "WebView resource error: ${error?.description}")
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        android.util.Log.e("AlignApp", "WebView render process gone. detail: ${detail?.didCrash()}")
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        activity?.launchFileChooser(filePathCallback, intent)
                        return true
                    }

                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        android.util.Log.d("AlignAppConsole", "${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                        return true
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    allowFileAccess = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = false
                    }
                }
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                val bridge = WebAppInterface(context, activity)
                addJavascriptInterface(bridge, "AndroidApp")
                addJavascriptInterface(bridge, "Android")
                setBackgroundColor(android.graphics.Color.parseColor("#08090d"))
                loadUrl("file:///android_asset/index.html")
            }
        }
    )
}



