package com.miwafi.kilviewer.download

import android.net.Uri
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * 影片地址捕獲倉庫。
 *
 * 兩種捕獲途徑：
 * 1. JS 注入：watch 頁加載完成後提取頁面內嵌的 .mp4 地址（含各畫質）
 * 2. 網絡攔截：shouldInterceptRequest 捕獲播放器實際請求的 .mp4 地址
 *
 * 真實地址形如：
 * https://vdownload.hembed.com/408465-1080p.mp4?secure=xxx,1790888422
 * secure token 帶過期時間，因此同一畫質始終保留最新捕獲的地址。
 */
object VideoCapture {

    data class Source(val url: String, val quality: Int)

    /** videoId -> (畫質 -> 地址) */
    private val _sources = MutableStateFlow<Map<String, Map<Int, Source>>>(emptyMap())
    val sources: StateFlow<Map<String, Map<Int, Source>>> = _sources.asStateFlow()

    /** videoId -> 標題 */
    private val _titles = MutableStateFlow<Map<String, String>>(emptyMap())
    val titles: StateFlow<Map<String, String>> = _titles.asStateFlow()

    /** 當前正在瀏覽的 watch 頁影片 id，離開時置空 */
    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId: StateFlow<String?> = _currentVideoId.asStateFlow()

    private val idRegex = Regex("/(\\d{4,})-\\d{3,4}p\\.mp4")
    private val qualityRegex = Regex("-(\\d{3,4})p\\.mp4")

    /** watch 頁地址 -> 影片 id，非 watch 頁返回 null */
    fun videoIdFromPageUrl(pageUrl: String?): String? {
        if (pageUrl == null) return null
        val uri = runCatching { Uri.parse(pageUrl) }.getOrNull() ?: return null
        if (uri.host?.endsWith("hanime1.me") != true) return null
        return uri.getQueryParameter("v")?.takeIf { it.isNotEmpty() }
    }

    /** 影片文件地址 -> (影片id, 畫質) */
    fun parseVideoFileUrl(url: String): Pair<String, Int>? {
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: return null
        val id = idRegex.find(path)?.groupValues?.get(1) ?: return null
        val quality = qualityRegex.find(path)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return id to quality
    }

    /** 記錄一條影片地址（同一畫質覆蓋為最新，保證 secure token 新鮮） */
    fun addSource(rawUrl: String) {
        val url = rawUrl.replace("\\/", "/").replace("\\u002F", "/").trim()
        if (!url.startsWith("https://") || url.contains("blob:")) return
        val (id, quality) = parseVideoFileUrl(url) ?: return
        _sources.update { map ->
            val forVideo = (map[id] ?: emptyMap()).toMutableMap()
            forVideo[quality] = Source(url, quality)
            map + (id to forVideo)
        }
    }

    fun setTitle(videoId: String, title: String) {
        if (title.isBlank()) return
        // 去掉站點後綴，僅保留影片名
        val cleaned = title.substringBefore("- hanime1.me").trim()
            .ifEmpty { title.trim() }
        _titles.update { it + (videoId to cleaned) }
    }

    fun titleOf(videoId: String): String = _titles.value[videoId] ?: "影片 $videoId"

    fun setCurrentVideo(videoId: String?) {
        _currentVideoId.value = videoId
    }

    /** 指定影片的可下載畫質列表，畫質從高到低 */
    fun qualitiesOf(videoId: String): List<Source> =
        _sources.value[videoId]?.values?.sortedByDescending { it.quality } ?: emptyList()

    /** 用最新捕獲的地址刷新任務 URL（過期重試用），返回是否刷新 */
    fun refreshUrl(videoId: String, quality: Int, oldUrl: String): String =
        qualitiesOf(videoId).firstOrNull { it.quality == quality }?.url ?: oldUrl
}

/** 暴露給頁面 JS 的回調橋：接收注入腳本解析出的影片地址 */
class JsBridge {
    @JavascriptInterface
    fun onVideoSources(json: String) {
        runCatching {
            val obj = JSONObject(json)
            val id = obj.optString("id")
            if (id.isNotEmpty()) {
                VideoCapture.setTitle(id, obj.optString("title"))
                val arr = obj.optJSONArray("sources") ?: return
                for (i in 0 until arr.length()) {
                    VideoCapture.addSource(arr.optString(i))
                }
            }
        }
    }
}
