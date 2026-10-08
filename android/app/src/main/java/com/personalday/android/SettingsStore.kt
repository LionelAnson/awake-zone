package com.personalday.android

import android.content.Context
import com.personalday.core.*

class SettingsStore(context: Context) {
    private val file = context.applicationContext.getSharedPreferences("personal_day", Context.MODE_PRIVATE)
    private val repository = PreferenceRepository(object : PreferenceStorage {
        override fun read(): Map<String, String> = file.all.mapNotNull { (key, value) ->
            (value as? String)?.let { key to it }
        }.toMap()
        override fun write(values: Map<String, String>) {
            file.edit().also { editor -> values.forEach { (key, value) -> editor.putString(key, value) } }.apply()
        }
    })
    fun load() = repository.load()
    fun save(preferences: Preferences) = repository.save(preferences)
    var lastError: String
        get() = file.getString("last_error", "") ?: ""
        set(value) { file.edit().putString("last_error", value).apply() }
    // Rate-limited persistent evidence: a successful Binder submission is not proof of launcher display.
    fun recordWidgetSubmission(nowMillis: Long) {
        val previous = file.getLong("last_widget_submission", 0)
        if (previous == 0L || nowMillis < previous || nowMillis - previous >= 60_000) {
            file.edit().putLong("last_widget_submission", nowMillis).apply()
        }
    }
    fun captureBeforeOpen() {
        val last = file.getLong("last_widget_submission", 0)
        val stamp = if (last == 0L) "尚无记录" else java.time.Instant.ofEpochMilli(last).toString()
        file.edit().putString("refresh_before_open",
            "打开时间=${java.time.Instant.now()}；服务运行=${RefreshService.running}；最近提交（每分钟记录）=$stamp").apply()
    }
    val refreshEvidence: String get() = file.getString("refresh_before_open", "尚无记录") ?: "尚无记录"
    var pinStatus: String
        get() = file.getString("pin_status", "") ?: ""
        set(value) { file.edit().putString("pin_status", value).apply() }
}
