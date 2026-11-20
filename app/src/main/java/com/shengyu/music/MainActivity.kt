package com.shengyu.music

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import java.util.concurrent.Executor

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val pickerRequest = 4107

    private var controllerFuture: ListenableFuture<MediaController>? = null
    @Volatile private var mediaController: MediaController? = null
    @Volatile private var pendingPlayback: PendingPlayback? = null
    @Volatile private var pendingQueue: PendingQueue? = null
    @Volatile private var currentIndexSnapshot: Int = -1
    private var lastPickedUris: List<Uri> = emptyList()
    @Volatile private var positionSnapshot: Long = 0L
    @Volatile private var durationSnapshot: Long = 0L
    @Volatile private var playingSnapshot: Boolean = false
    @Volatile private var stateSnapshot: Int = Player.STATE_IDLE
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class PendingPlayback(
        val url: String,
        val title: String,
        val artist: String,
        val artwork: String,
        val album: String = ""
    )

    private data class PendingQueue(
        val items: List<MediaItem>,
        val startIndex: Int
    )

    private val snapshotTask = object : Runnable {
        override fun run() {
            val player = mediaController
            if (player != null) {
                positionSnapshot = player.currentPosition.coerceAtLeast(0L)
                durationSnapshot = if (player.duration > 0L && player.duration < Long.MAX_VALUE / 2) player.duration else 0L
                playingSnapshot = player.isPlaying
                stateSnapshot = player.playbackState
                currentIndexSnapshot = player.currentMediaItemIndex
            }
            if (::webView.isInitialized && webView.visibility == android.view.View.VISIBLE) {
                webView.evaluateJavascript(
                    "if(window.shengyuNativeSync){window.shengyuNativeSync();}",
                    null
                )
            }
            mainHandler.postDelayed(this, 500L)
        }
    }

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 16, 20)
        window.navigationBarColor = android.graphics.Color.rgb(16, 16, 20)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        webView = WebView(this)
        webView.setBackgroundColor(android.graphics.Color.rgb(16, 16, 20))
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.addJavascriptInterface(NativePlaybackBridge(), "AndroidPlayer")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme ?: return false
                if (scheme == "file" || scheme == "about") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                return try {
                    startActivityForResult(intent, pickerRequest)
                    true
                } catch (_: Exception) {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = null
                    false
                }
            }
        }

        setContentView(webView)
        connectMediaController()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 712)
        }

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun connectMediaController() {
        if (controllerFuture != null) return
        val token = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                pendingQueue?.let { request ->
                    pendingQueue = null
                    startNativeQueue(request.items, request.startIndex)
                }
                pendingPlayback?.let { request ->
                    pendingPlayback = null
                    startNativePlayback(request)
                }
            } catch (_: Exception) {
                // The WebView player remains available if the native session cannot connect.
            }
        }, Executor { command -> runOnUiThread { command.run() } })
    }

    private fun startNativeQueue(items: List<MediaItem>, startIndex: Int) {
        val controller = mediaController ?: run {
            pendingQueue = PendingQueue(items, startIndex)
            connectMediaController()
            return
        }
        if (items.isEmpty()) return
        controller.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        controller.prepare()
        controller.play()
    }

    private fun startNativePlayback(request: PendingPlayback) {
        val controller = mediaController ?: run {
            pendingPlayback = request
            connectMediaController()
            return
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(request.title)
            .setArtist(request.artist)
            .apply {
                if (request.album.isNotBlank()) setAlbumTitle(request.album)
                if (request.artwork.startsWith("https://")) setArtworkUri(Uri.parse(request.artwork))
                setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            }
            .build()
        val item = MediaItem.Builder()
            .setUri(Uri.parse(request.url))
            .setMediaMetadata(metadata)
            .build()
        controller.setMediaItem(item)
        controller.prepare()
        controller.play()
    }

    inner class NativePlaybackBridge {
        @JavascriptInterface
        fun play(url: String, title: String, artist: String, artwork: String, album: String): Boolean {
            val uri = Uri.parse(url)
            if (uri.scheme != "https" || uri.host !in setOf("api.audius.co", "discoveryprovider.audius.co")) {
                return false
            }
            val request = PendingPlayback(url, title.take(180), artist.take(180), artwork.take(2048), album.take(180))
            runOnUiThread { startNativePlayback(request) }
            return true
        }

        @JavascriptInterface fun pause() { runOnUiThread { mediaController?.pause() } }
        @JavascriptInterface fun resume() { runOnUiThread { mediaController?.play() } }
        @JavascriptInterface fun stop() { runOnUiThread { mediaController?.stop() } }
        @JavascriptInterface fun seekTo(positionMs: Long) {
            runOnUiThread { mediaController?.seekTo(positionMs.coerceAtLeast(0L)) }
        }
        @JavascriptInterface fun setSpeed(speed: Float) {
            val safeSpeed = speed.coerceIn(0.5f, 2.0f)
            runOnUiThread { mediaController?.setPlaybackSpeed(safeSpeed) }
        }
        @JavascriptInterface fun positionMs(): Long = positionSnapshot
        @JavascriptInterface fun durationMs(): Long = durationSnapshot
        @JavascriptInterface fun isPlaying(): Boolean = playingSnapshot
        @JavascriptInterface fun playbackState(): Int = stateSnapshot
        @JavascriptInterface fun currentIndex(): Int = currentIndexSnapshot

        @JavascriptInterface
        fun registerLocalTrack(trackId: String, selectedIndex: Int): Boolean {
            val uri = lastPickedUris.getOrNull(selectedIndex) ?: return false
            return try {
                getSharedPreferences("shengyu_native_local", MODE_PRIVATE)
                    .edit()
                    .putString("localUri_$trackId", uri.toString())
                    .apply()
                true
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun playLocal(trackId: String, title: String, artist: String): Boolean {
            val uriText = getSharedPreferences("shengyu_native_local", MODE_PRIVATE)
                .getString("localUri_$trackId", null) ?: return false
            val uri = Uri.parse(uriText)
            if (uri.scheme != "content") return false
            val request = PendingPlayback(uri.toString(), title.take(180), artist.take(180), "")
            runOnUiThread { startNativePlayback(request) }
            return true
        }

        @JavascriptInterface
        fun playQueue(queueJson: String, startIndex: Int): Boolean {
            return try {
                val array = JSONArray(queueJson)
                if (array.length() == 0 || array.length() > 500) return false
                val items = ArrayList<MediaItem>(array.length())
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val source = item.optString("source")
                    val uri = if (source == "audius") {
                        val parsed = Uri.parse(item.optString("url"))
                        if (parsed.scheme != "https" || parsed.host !in setOf("api.audius.co", "discoveryprovider.audius.co")) return false
                        parsed
                    } else if (source == "local") {
                        val id = item.optString("id")
                        val stored = getSharedPreferences("shengyu_native_local", MODE_PRIVATE)
                            .getString("localUri_$id", null) ?: return false
                        Uri.parse(stored).also { if (it.scheme != "content") return false }
                    } else return false
                    val metadata = MediaMetadata.Builder()
                        .setTitle(item.optString("title", "未知歌曲"))
                        .setArtist(item.optString("artist", "未知歌手"))
                        .apply {
                            val album = item.optString("album")
                            if (album.isNotBlank()) setAlbumTitle(album)
                            val art = item.optString("artwork")
                            if (art.startsWith("https://")) setArtworkUri(Uri.parse(art))
                            setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        }
                        .build()
                    items.add(MediaItem.Builder().setUri(uri).setMediaMetadata(metadata).build())
                }
                runOnUiThread { startNativeQueue(items, startIndex) }
                true
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun setEqualizer(preset: Int) {
            val intent = Intent(this@MainActivity, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_SET_EQ
                putExtra(MusicPlaybackService.EXTRA_EQ_PRESET, preset.coerceIn(0, 5))
            }
            runOnUiThread {
                try { startService(intent) } catch (_: Exception) { }
            }
        }

        @JavascriptInterface
        fun setSleepTimer(minutes: Int) {
            val safeMinutes = minutes.coerceIn(0, 240)
            val intent = Intent(this@MainActivity, MusicPlaybackService::class.java).apply {
                action = if (safeMinutes == 0) MusicPlaybackService.ACTION_CANCEL_SLEEP_TIMER
                else MusicPlaybackService.ACTION_SET_SLEEP_TIMER
                putExtra(MusicPlaybackService.EXTRA_SLEEP_MINUTES, safeMinutes)
            }
            runOnUiThread {
                try { startService(intent) } catch (_: Exception) { }
            }
        }

        @JavascriptInterface
        fun download(url: String, fileName: String): Boolean {
            val uri = Uri.parse(url)
            if (uri.scheme != "https" || uri.host !in setOf("api.audius.co", "discoveryprovider.audius.co")) {
                return false
            }
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "shengyu-track.mp3" }
            runOnUiThread {
                try {
                    val request = DownloadManager.Request(uri)
                        .setTitle(safeName)
                        .setDescription("声屿 MUSIC 离线音乐")
                        .setMimeType("audio/*")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Shengyu/$safeName")
                    val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                    webView.evaluateJavascript("if(window.shengyuToast){window.shengyuToast('已加入下载队列');}", null)
                } catch (_: Exception) {
                    webView.evaluateJavascript("if(window.shengyuToast){window.shengyuToast('下载启动失败');}", null)
                }
            }
            return true
        }
    }

    override fun onStart() {
        super.onStart()
        connectMediaController()
        mainHandler.removeCallbacks(snapshotTask)
        mainHandler.post(snapshotTask)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(snapshotTask)
        super.onStop()
    }

    @Deprecated("Legacy callback retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickerRequest) return
        val callback = fileCallback ?: return
        fileCallback = null
        val selected = if (resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) {
                Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
            } else {
                data.data?.let { arrayOf(it) }
            }
        } else null
        lastPickedUris = selected?.toList() ?: emptyList()
        if (selected != null) {
            selected.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                    // Some document providers do not offer persistable grants.
                }
            }
        }
        callback.onReceiveValue(selected)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Legacy callback retained for broad Android compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        mainHandler.removeCallbacks(snapshotTask)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
