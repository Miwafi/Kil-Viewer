package com.miwafi.kilviewer

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.miwafi.kilviewer.download.DownloadManager
import com.miwafi.kilviewer.download.DownloadScreen
import com.miwafi.kilviewer.download.JsBridge
import com.miwafi.kilviewer.download.VideoCapture
import com.miwafi.kilviewer.download.VideoDownloadDialog
import com.miwafi.kilviewer.settings.SettingsScreen
import com.miwafi.kilviewer.settings.SettingsStore
import com.miwafi.kilviewer.ui.theme.KilViewerTheme

/** 桌面模式 UA */
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

class MainActivity : ComponentActivity() {
    private lateinit var fullscreenContainer: FrameLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val isFullscreenVideo = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SettingsStore.init(this)
        DownloadManager.init(this)
        fullscreenContainer = FrameLayout(this)

        // 視頻全屏時，返回鍵優先退出全屏
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    exitFullscreen()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            val settings by SettingsStore.settings.collectAsState()
            KilViewerTheme(
                darkTheme = when (settings.themeMode) {
                    1 -> false
                    2 -> true
                    else -> isSystemInDarkTheme()
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(isFullscreenVideo = isFullscreenVideo.value)
                    // 視頻全屏容器，置於所有內容之上
                    AndroidView(
                        factory = { fullscreenContainer },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    /** 進入視頻全屏：顯示播放視圖並旋轉為橫屏 */
    fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        fullscreenContainer.addView(view)
        isFullscreenVideo.value = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /** 退出視頻全屏：移除播放視圖並恢復方向 */
    fun exitFullscreen() {
        customView?.let { fullscreenContainer.removeView(it) }
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        isFullscreenVideo.value = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

@Composable
fun MainScreen(isFullscreenVideo: Boolean) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // 視頻全屏時隱藏底部導航欄
            if (!isFullscreenVideo) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "瀏覽") },
                        label = { Text("瀏覽") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Filled.Download, contentDescription = "下載") },
                        label = { Text("下載") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "設置") },
                        label = { Text("設置") }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> WebViewScreen(
                isFullscreenVideo = isFullscreenVideo,
                modifier = Modifier.padding(innerPadding)
            )
            1 -> DownloadScreen(modifier = Modifier.padding(innerPadding))
            2 -> SettingsScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun WebViewScreen(isFullscreenVideo: Boolean, modifier: Modifier = Modifier) {
    // 主框架加載失敗時改為 true，顯示獨立錯誤頁面
    var loadFailed by rememberSaveable { mutableStateOf(false) }
    // 當前 watch 頁影片 id；非 watch 頁為 null
    val currentVideoId by VideoCapture.currentVideoId.collectAsState()
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 頂部橫幅；視頻全屏時隱藏
            if (!isFullscreenVideo) {
                Text(
                    text = "Kil Viewer By miwafi",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (loadFailed) {
                ErrorScreen(
                    onRetry = { loadFailed = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // 桌面模式：以桌面瀏覽器 UA 加載（切換後重新進入瀏覽 Tab 生效）
                            if (SettingsStore.settings.value.desktopMode) {
                                settings.userAgentString = DESKTOP_UA
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                            }
                            // 注入 JS 橋，供頁面回傳解析出的影片地址
                            addJavascriptInterface(JsBridge(), "AndroidBridge")
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    // 僅主框架加載失敗才顯示提示頁
                                    if (request.isForMainFrame) {
                                        loadFailed = true
                                    }
                                }

                                // 攔截播放器發起的 .mp4 請求，被動捕獲下載地址
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val url = request.url
                                    if (url.host?.endsWith("hembed.com") == true &&
                                        url.path?.endsWith(".mp4") == true
                                    ) {
                                        VideoCapture.addSource(url.toString())
                                    }
                                    return null
                                }

                                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    updateCurrentVideo(view, url)
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    updateCurrentVideo(view, url)
                                }
                            }
                            // 監聽 HTML5 視頻全屏，橫屏播放並隱藏標題與導航欄
                            webChromeClient = object : WebChromeClient() {
                                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                    (context as? MainActivity)?.enterFullscreen(view, callback)
                                        ?: callback.onCustomViewHidden()
                                }

                                override fun onHideCustomView() {
                                    (context as? MainActivity)?.exitFullscreen()
                                }

                                override fun onReceivedTitle(view: WebView, title: String) {
                                    VideoCapture.videoIdFromPageUrl(view.url)
                                        ?.let { VideoCapture.setTitle(it, title) }
                                }
                            }
                            loadUrl("https://hanime1.me/")
                        }
                    }
                )
            }
        }
        // watch 頁顯示下載按鈕；全屏播放時隱藏
        if (!isFullscreenVideo && currentVideoId != null) {
            ExtendedFloatingActionButton(
                text = { Text("下載影片") },
                icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                onClick = { showDownloadDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            )
        }
    }
    if (showDownloadDialog) {
        currentVideoId?.let { id ->
            VideoDownloadDialog(
                videoId = id,
                onDismiss = { showDownloadDialog = false }
            )
        }
    }
}

/** 更新當前 watch 頁影片 id，並注入地址解析腳本 */
private fun updateCurrentVideo(view: WebView, pageUrl: String?) {
    val videoId = VideoCapture.videoIdFromPageUrl(pageUrl)
    VideoCapture.setCurrentVideo(videoId)
    if (videoId != null) injectSourceExtractor(view, videoId)
}

/**
 * 向 watch 頁注入 JS：從頁面源碼（jwplayer 配置等）中提取所有 .mp4 地址
 * （含各畫質），連同頁面標題一併回傳給 AndroidBridge。
 */
private fun injectSourceExtractor(webView: WebView, videoId: String) {
    val js = """
        (function(){
          try {
            var html = document.documentElement.innerHTML
              .replace(/\\u002[fF]/g, '/')
              .replace(/\\\//g, '/');
            var re = /https?:\/\/[^\s"'<>\\]+?\.mp4[^\s"'<>\\]*/g;
            var out = [], seen = {};
            var m;
            while ((m = re.exec(html)) !== null) {
              if (!seen[m[0]]) { seen[m[0]] = 1; out.push(m[0]); }
            }
            var v = document.querySelector('video');
            var src = v && (v.currentSrc ||
              (v.querySelector('source') && v.querySelector('source').src));
            if (src && /^https?:/.test(src) && !seen[src]) {
              seen[src] = 1; out.push(src);
            }
            window.AndroidBridge.onVideoSources(
              JSON.stringify({id: '$videoId', title: document.title || '', sources: out})
            );
          } catch (e) {}
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

@Composable
fun ErrorScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "無形的大手阻斷了你的連接",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "如果你是内地用戶，別忘了挂梯子",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("點我重試")
        }
    }
}
