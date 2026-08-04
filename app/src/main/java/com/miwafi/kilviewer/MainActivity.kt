package com.miwafi.kilviewer

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.miwafi.kilviewer.ui.theme.KilViewerTheme

class MainActivity : ComponentActivity() {
    private lateinit var fullscreenContainer: FrameLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val isFullscreenVideo = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            KilViewerTheme {
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
            1 -> PlaceholderScreen(title = "下載", modifier = Modifier.padding(innerPadding))
            2 -> PlaceholderScreen(title = "設置", modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun WebViewScreen(isFullscreenVideo: Boolean, modifier: Modifier = Modifier) {
    // 主框架加載失敗時改為 true，顯示獨立錯誤頁面
    var loadFailed by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
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
                        }
                        loadUrl("https://hanime1.me/")
                    }
                }
            )
        }
    }
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

@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}
