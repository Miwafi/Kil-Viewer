package com.miwafi.kilviewer.settings

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miwafi.kilviewer.download.DownloadManager

/** 「設置」Tab */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val settings by SettingsStore.settings.collectAsState()
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showClearDownloadsDialog by remember { mutableStateOf(false) }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        // ---- 下載 ----
        item { GroupHeader("下載") }
        item {
            SettingRow(
                title = "同時下載任務數",
                subtitle = "${settings.maxConcurrent} 個",
                onClick = { showConcurrencyDialog = true }
            )
        }
        item {
            SettingRow(
                title = "偏好畫質",
                subtitle = qualityLabel(settings.preferredQuality),
                onClick = { showQualityDialog = true }
            )
        }
        item {
            SettingRow(
                title = "下載目錄",
                subtitle = DownloadManager.dir().absolutePath
            )
        }

        // ---- 瀏覽器 ----
        item { GroupHeader("瀏覽器") }
        item {
            SettingRow(
                title = "桌面模式",
                subtitle = "以桌面版瀏覽器標識加載網頁，切換後重新進入「瀏覽」生效",
                trailing = {
                    Switch(
                        checked = settings.desktopMode,
                        onCheckedChange = { SettingsStore.setDesktopMode(it) }
                    )
                },
                onClick = { SettingsStore.setDesktopMode(!settings.desktopMode) }
            )
        }
        item {
            SettingRow(
                title = "清除瀏覽數據",
                subtitle = "清除 Cookie、緩存與表單數據",
                onClick = { clearBrowsingData(context) }
            )
        }

        // ---- 外觀 ----
        item { GroupHeader("外觀") }
        item {
            SettingRow(
                title = "主題",
                subtitle = themeLabel(settings.themeMode),
                onClick = { showThemeDialog = true }
            )
        }

        // ---- 數據 ----
        item { GroupHeader("數據") }
        item {
            SettingRow(
                title = "清空下載列表",
                subtitle = "刪除所有任務記錄及已下載的文件，不可恢復",
                danger = true,
                onClick = { showClearDownloadsDialog = true }
            )
        }

        // ---- 關於 ----
        item { GroupHeader("關於") }
        item {
            SettingRow(
                title = "Kil Viewer",
                subtitle = "By miwafi · 版本 $version"
            )
        }
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = "主題",
            options = listOf(
                0 to "跟隨系統",
                1 to "淺色",
                2 to "深色"
            ),
            selected = settings.themeMode,
            onSelect = {
                SettingsStore.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    if (showConcurrencyDialog) {
        ChoiceDialog(
            title = "同時下載任務數",
            options = listOf(1 to "1 個", 2 to "2 個", 3 to "3 個"),
            selected = settings.maxConcurrent,
            onSelect = {
                SettingsStore.setMaxConcurrent(it)
                showConcurrencyDialog = false
            },
            onDismiss = { showConcurrencyDialog = false }
        )
    }
    if (showQualityDialog) {
        ChoiceDialog(
            title = "偏好畫質",
            options = listOf(
                0 to "最高畫質",
                1080 to "1080P",
                720 to "720P"
            ),
            selected = settings.preferredQuality,
            onSelect = {
                SettingsStore.setPreferredQuality(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }
    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text("清空下載列表") },
            text = { Text("將刪除所有任務記錄及已下載的文件，此操作不可恢復。確定繼續？") },
            confirmButton = {
                TextButton(onClick = {
                    DownloadManager.removeAll()
                    showClearDownloadsDialog = false
                    Toast.makeText(context, "下載列表已清空", Toast.LENGTH_SHORT).show()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) { Text("取消") }
            }
        )
    }
}

private fun themeLabel(mode: Int) = when (mode) {
    1 -> "淺色"
    2 -> "深色"
    else -> "跟隨系統"
}

private fun qualityLabel(quality: Int) = when (quality) {
    0 -> "最高畫質"
    else -> "${quality}P"
}

/** 清除 WebView 瀏覽數據（Cookie / 緩存 / 表單 / DOM 存儲之外的站點數據） */
private fun clearBrowsingData(context: Context) {
    runCatching {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        // 借助臨時 WebView 實例清理磁盤緩存與表單數據
        WebView(context).apply {
            clearCache(true)
            clearFormData()
            destroy()
        }
        Toast.makeText(context, "瀏覽數據已清除", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "清除失敗：${it.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            it()
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp)
                    ) {
                        RadioButton(selected = selected == value, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}
