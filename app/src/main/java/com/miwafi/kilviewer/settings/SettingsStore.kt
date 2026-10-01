package com.miwafi.kilviewer.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppSettings(
    /** 0 跟隨系統 / 1 淺色 / 2 深色 */
    val themeMode: Int = 0,
    /** 以桌面版瀏覽器 UA 加載網頁 */
    val desktopMode: Boolean = false,
    /** 同時下載任務數 1~3 */
    val maxConcurrent: Int = 2,
    /** 偏好畫質：0 最高畫質，其餘為具體畫質值（1080/720…） */
    val preferredQuality: Int = 0,
)

/** 全局設置倉庫：SharedPreferences 持久化 + StateFlow 驅動 UI */
object SettingsStore {

    private lateinit var prefs: SharedPreferences

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
        _settings.value = AppSettings(
            themeMode = prefs.getInt("themeMode", 0),
            desktopMode = prefs.getBoolean("desktopMode", false),
            maxConcurrent = prefs.getInt("maxConcurrent", 2).coerceIn(1, 3),
            preferredQuality = prefs.getInt("preferredQuality", 0),
        )
    }

    fun setThemeMode(value: Int) = update("themeMode", value)

    fun setDesktopMode(value: Boolean) {
        prefs.edit().putBoolean("desktopMode", value).apply()
        _settings.update { it.copy(desktopMode = value) }
    }

    fun setMaxConcurrent(value: Int) {
        val v = value.coerceIn(1, 3)
        prefs.edit().putInt("maxConcurrent", v).apply()
        _settings.update { it.copy(maxConcurrent = v) }
    }

    fun setPreferredQuality(value: Int) = update("preferredQuality", value)

    private fun update(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        _settings.update {
            when (key) {
                "themeMode" -> it.copy(themeMode = value)
                else -> it.copy(preferredQuality = value)
            }
        }
    }
}
