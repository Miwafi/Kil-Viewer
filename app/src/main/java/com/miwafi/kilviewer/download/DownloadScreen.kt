package com.miwafi.kilviewer.download

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.miwafi.kilviewer.settings.SettingsStore
import java.io.File
import java.util.Locale

/** 「下載」Tab：任務列表 */
@Composable
fun DownloadScreen(modifier: Modifier = Modifier) {
    val tasks by DownloadManager.tasks.collectAsState()
    val context = LocalContext.current

    if (tasks.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(16.dp))
            Text("暫無下載任務", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "在影片播放頁點擊「下載影片」按鈕即可保存視頻",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tasks, key = { it.key }) { task ->
            TaskCard(
                task = task,
                onOpen = { openDownloadedVideo(context, task) }
            )
        }
    }
}

@Composable
private fun TaskCard(task: DownloadTask, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (task.quality > 0) "${task.quality}P" else "MP4",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            val statusText = statusText(task)
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (task.status == DownloadStatus.FAILED)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                Spacer(Modifier.height(8.dp))
                val progress: Float? = if (task.totalBytes > 0)
                    (task.downloadedBytes.toFloat() / task.totalBytes).coerceIn(0f, 1f)
                else null
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                if (progress == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatBytes(task.downloadedBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (task.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                        TextButton(onClick = { DownloadManager.pause(task.key) }) { Text("暫停") }
                    }
                    DownloadStatus.PAUSED -> {
                        TextButton(onClick = { DownloadManager.resume(task.key) }) { Text("繼續") }
                        TextButton(onClick = { DownloadManager.remove(task.key) }) { Text("刪除") }
                    }
                    DownloadStatus.FAILED -> {
                        TextButton(onClick = { DownloadManager.retry(task.key) }) { Text("重試") }
                        TextButton(onClick = { DownloadManager.remove(task.key) }) { Text("移除") }
                    }
                    DownloadStatus.COMPLETED -> {
                        TextButton(onClick = onOpen) { Text("打開") }
                        TextButton(onClick = { DownloadManager.remove(task.key) }) { Text("刪除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(task: DownloadTask): String {
    val size = if (task.totalBytes > 0) " / ${formatBytes(task.totalBytes)}" else ""
    return when (task.status) {
        DownloadStatus.QUEUED -> "排隊中…"
        DownloadStatus.DOWNLOADING -> {
            val percent = if (task.totalBytes > 0) "${task.downloadedBytes * 100 / task.totalBytes}% · " else ""
            "$percent${formatBytes(task.downloadedBytes)}$size · ${formatBytes(task.speedBytesPerSec)}/s"
        }
        DownloadStatus.PAUSED -> "已暫停 · ${formatBytes(task.downloadedBytes)}$size"
        DownloadStatus.COMPLETED -> "已完成 · ${formatBytes(task.totalBytes.takeIf { it > 0 } ?: task.downloadedBytes)}"
        DownloadStatus.FAILED -> "失敗：${task.error ?: "未知錯誤"}"
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / 1e9)
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
    else -> "$bytes B"
}

private fun openDownloadedVideo(context: android.content.Context, task: DownloadTask) {
    val file = File(DownloadManager.dir(), task.fileName)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "沒有可播放的應用", Toast.LENGTH_SHORT).show() }
}

/** 影片下載彈窗：列出已捕獲的畫質（偏好畫質優先並標記），點擊加入下載 */
@Composable
fun VideoDownloadDialog(videoId: String, onDismiss: () -> Unit) {
    val sources by VideoCapture.sources.collectAsState()
    val titles by VideoCapture.titles.collectAsState()
    val tasks by DownloadManager.tasks.collectAsState()
    val appSettings by SettingsStore.settings.collectAsState()
    val context = LocalContext.current

    val qualityList = sources[videoId]?.values?.sortedByDescending { it.quality } ?: emptyList()
    // 偏好畫質置頂（穩定排序，其餘仍按畫質從高到低）
    val sortedList = if (appSettings.preferredQuality == 0) qualityList
    else qualityList.sortedByDescending { it.quality == appSettings.preferredQuality }
    val title = titles[videoId] ?: "影片 $videoId"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下載影片", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                if (qualityList.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Movie, contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "尚未解析到影片地址\n開始播放視頻後再試",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    sortedList.forEach { source ->
                        val existing = tasks.firstOrNull {
                            it.key == DownloadManager.fileNameFor(videoId, source.quality)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = existing == null) {
                                    if (DownloadManager.enqueue(videoId, title, source.quality, source.url)) {
                                        Toast.makeText(context, "已加入下載隊列", Toast.LENGTH_SHORT).show()
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (existing == null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${source.quality}P",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (appSettings.preferredQuality != 0 &&
                                source.quality == appSettings.preferredQuality
                            ) {
                                Text(
                                    "偏好",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            if (existing != null) {
                                Text(
                                    when (existing.status) {
                                        DownloadStatus.COMPLETED -> "已下載"
                                        DownloadStatus.FAILED -> "下載失敗"
                                        else -> "下載中"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}
