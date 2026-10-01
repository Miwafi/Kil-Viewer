package com.miwafi.kilviewer.download

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import com.miwafi.kilviewer.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadTask(
    val videoId: String,
    val title: String,
    val quality: Int,
    val url: String,
    val fileName: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val speedBytesPerSec: Long = 0,
    val error: String? = null,
) {
    /** 任務唯一鍵：文件名（同一影片不同畫質互不衝突） */
    val key: String get() = fileName
}

/**
 * 下載管理器。
 *
 * - 保存到應用專屬外部存儲 Movies 目錄，無需存儲權限
 * - 支持斷點續傳（Range 請求，續傳時以磁盤上實際文件長度為準）
 * - 任務列表持久化到 filesDir/downloads.json，重啟後恢復
 * - secure 鏈接會過期：403 時提示重新打開影片頁，重試自動換用最新捕獲的地址
 */
object DownloadManager {

    private const val UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dispatchMutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private lateinit var downloadDir: File
    private lateinit var storeFile: File

    fun init(context: Context) {
        if (::downloadDir.isInitialized) return
        val app = context.applicationContext
        downloadDir = app.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(app.filesDir, "downloads").apply { mkdirs() }
        downloadDir.mkdirs()
        storeFile = File(app.filesDir, "downloads.json")
        _tasks.value = load()
        // 併發數設置變化時重新調度隊列
        scope.launch {
            SettingsStore.settings.map { it.maxConcurrent }
                .distinctUntilChanged()
                .collect { dispatchNext() }
        }
    }

    fun dir(): File = downloadDir

    fun fileNameFor(videoId: String, quality: Int): String =
        "${videoId}-${if (quality > 0) "${quality}p" else "video"}.mp4"

    /** 加入隊列並開始下載；任務已存在時返回 false */
    fun enqueue(videoId: String, title: String, quality: Int, url: String): Boolean {
        val task = DownloadTask(videoId, title, quality, url, fileNameFor(videoId, quality))
        var added = false
        _tasks.update { list ->
            if (list.any { it.key == task.key }) list
            else {
                added = true
                list + task
            }
        }
        if (added) {
            save()
            dispatchNext()
        }
        return added
    }

    /** 繼續下載（以磁盤上實際文件長度續傳） */
    fun resume(key: String) {
        _tasks.update { list ->
            list.map {
                if (it.key == key && it.status == DownloadStatus.PAUSED) {
                    val partial = File(downloadDir, key).takeIf { f -> f.exists() }?.length() ?: 0L
                    it.copy(status = DownloadStatus.QUEUED, downloadedBytes = partial, error = null)
                } else it
            }
        }
        save()
        dispatchNext()
    }

    /** 重試失敗任務：自動換用最新捕獲的 secure 地址 */
    fun retry(key: String) {
        _tasks.update { list ->
            list.map {
                if (it.key == key && it.status == DownloadStatus.FAILED) {
                    it.copy(
                        url = VideoCapture.refreshUrl(it.videoId, it.quality, it.url),
                        status = DownloadStatus.QUEUED,
                        error = null
                    )
                } else it
            }
        }
        save()
        dispatchNext()
    }

    fun pause(key: String) {
        _tasks.update { list ->
            list.map {
                if (it.key == key && it.status == DownloadStatus.DOWNLOADING)
                    it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0)
                else it
            }
        }
        jobs.remove(key)?.cancel()
        save()
        dispatchNext()
    }

    /** 取消/移除任務並刪除未完成文件 */
    fun remove(key: String) {
        jobs.remove(key)?.cancel()
        _tasks.update { list -> list.filter { it.key != key } }
        save()
        File(downloadDir, key).delete()
        dispatchNext()
    }

    /** 清空所有任務記錄並刪除文件 */
    fun removeAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _tasks.value.forEach { File(downloadDir, it.fileName).delete() }
        _tasks.value = emptyList()
        save()
    }

    /**
     * 隊列調度：按設置的併發數，把 QUEUED 任務依次標記為 DOWNLOADING 並啟動。
     * 用互斥鎖保證「計數-認領」原子，避免併發調度超限或重複啟動。
     */
    private fun dispatchNext() {
        scope.launch {
            dispatchMutex.withLock {
                val max = runCatching { SettingsStore.settings.value.maxConcurrent }
                    .getOrDefault(2).coerceIn(1, 3)
                while (_tasks.value.count { it.status == DownloadStatus.DOWNLOADING } < max) {
                    val next = _tasks.value.firstOrNull { it.status == DownloadStatus.QUEUED } ?: break
                    _tasks.update { list ->
                        list.map {
                            if (it.key == next.key && it.status == DownloadStatus.QUEUED)
                                it.copy(status = DownloadStatus.DOWNLOADING) else it
                        }
                    }
                    startJob(next.key)
                }
            }
        }
    }

    private fun startJob(key: String) {
        jobs.remove(key)?.cancel()
        jobs[key] = scope.launch {
            runDownload(key)
        }
    }

    private suspend fun runDownload(key: String) {
        val task = _tasks.value.firstOrNull { it.key == key } ?: return
        val file = File(downloadDir, task.fileName)
        var conn: HttpURLConnection? = null
        try {
            updateTask(key) { it.copy(status = DownloadStatus.DOWNLOADING, error = null) }
            val partial = file.takeIf { f -> f.exists() }?.length() ?: 0L

            conn = URL(task.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", "https://hanime1.me/")
            if (partial > 0) conn.setRequestProperty("Range", "bytes=$partial-")

            val code = conn.responseCode
            when {
                code == 403 || code == 410 ->
                    throw IOException("鏈接已過期，請重新打開影片頁面後重試")
                code !in 200..299 ->
                    throw IOException("伺服器返回 HTTP $code")
            }

            val resumed = code == 206 && partial > 0
            val start = if (resumed) partial else 0L
            if (!resumed && partial > 0) file.delete()
            val total = if (code == 206) {
                conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
            } else {
                conn.contentLengthLong.takeIf { it > 0 }?.let { it + start } ?: -1L
            }

            var downloaded = start
            var lastFlushAt = SystemClock.elapsedRealtime()
            var lastFlushBytes = downloaded

            conn.inputStream.use { input ->
                FileOutputStream(file, resumed).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        // 暫停/移除後及時斷開，避免阻塞在 read 上
                        if (_tasks.value.firstOrNull { it.key == key }?.status != DownloadStatus.DOWNLOADING) {
                            conn.disconnect()
                            return
                        }
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        downloaded += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastFlushAt >= 300) {
                            flushProgress(key, downloaded, total, lastFlushBytes, lastFlushAt, now)
                            lastFlushAt = now
                            lastFlushBytes = downloaded
                        }
                    }
                }
            }
            flushProgress(key, downloaded, total, lastFlushBytes, lastFlushAt, SystemClock.elapsedRealtime())
            updateTask(key) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = downloaded,
                    totalBytes = if (total > 0) total else downloaded,
                    speedBytesPerSec = 0,
                    error = null
                )
            }
            save()
            dispatchNext()
        } catch (e: CancellationException) {
            updateTask(key) {
                if (it.status == DownloadStatus.DOWNLOADING)
                    it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0)
                else it
            }
            save()
            throw e
        } catch (e: Exception) {
            // 僅當任務仍在下載狀態才標記失敗（暫停/移除競態時保持原狀態）
            val stillDownloading = _tasks.value.firstOrNull { it.key == key }?.status == DownloadStatus.DOWNLOADING
            if (stillDownloading) {
                updateTask(key) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        speedBytesPerSec = 0,
                        error = e.message ?: "下載失敗"
                    )
                }
                save()
                dispatchNext()
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun flushProgress(key: String, downloaded: Long, total: Long, prevBytes: Long, prevAt: Long, now: Long) {
        val speed = (downloaded - prevBytes) * 1000 / (now - prevAt).coerceAtLeast(1)
        _tasks.update { list ->
            list.map {
                if (it.key == key && it.status == DownloadStatus.DOWNLOADING)
                    it.copy(downloadedBytes = downloaded, totalBytes = total, speedBytesPerSec = speed)
                else it
            }
        }
    }

    private fun updateTask(key: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.key == key) transform(it) else it } }
    }

    // ---------- 持久化 ----------

    private fun save() {
        runCatching {
            val arr = JSONArray()
            _tasks.value.forEach { t ->
                arr.put(JSONObject().apply {
                    put("videoId", t.videoId)
                    put("title", t.title)
                    put("quality", t.quality)
                    put("url", t.url)
                    put("fileName", t.fileName)
                    put("status", t.status.name)
                    put("downloadedBytes", t.downloadedBytes)
                    put("totalBytes", t.totalBytes)
                })
            }
            storeFile.writeText(arr.toString())
        }
    }

    private fun load(): List<DownloadTask> = runCatching {
        if (!storeFile.exists()) return emptyList()
        val arr = JSONArray(storeFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val status = runCatching { DownloadStatus.valueOf(o.getString("status")) }
                .getOrDefault(DownloadStatus.PAUSED)
            DownloadTask(
                videoId = o.getString("videoId"),
                title = o.getString("title"),
                quality = o.getInt("quality"),
                url = o.getString("url"),
                fileName = o.getString("fileName"),
                // 重啟後進行中的任務恢復為已暫停
                status = if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED)
                    DownloadStatus.PAUSED else status,
                downloadedBytes = o.getLong("downloadedBytes"),
                totalBytes = o.optLong("totalBytes", -1),
            )
        }
    }.getOrDefault(emptyList())
}
