package com.personalday.android

import android.app.PendingIntent
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.RemoteViews
import com.personalday.core.*
import java.time.Instant

object WidgetRenderer {
    private val cache = RenderCache()
    private var wallpaperHint: Boolean? = null
    private var wallpaperLoaded = false
    private data class Key(val display: DisplayContent, val color: Int, val design: DesignSettings,
                           val widthDp: Int, val heightDp: Int, val density: Float)
    private data class FaceKey(val regular: String, val personal: String, val period: String,
        val date: String, val percent: String, val units: Int, val color: Int, val width: Int, val height: Int,
        val design: DesignSettings, val backgroundRgb: Int, val backgroundOpacity: Int)
    // RemoteViews may still reference an evicted bitmap: leave disposal to GC.
    private val faces = object : LruCache<FaceKey, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: FaceKey, value: Bitmap) = value.allocationByteCount
    }

    fun ids(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return (manager.getAppWidgetIds(ComponentName(context, WideWidgetProvider::class.java)) +
            manager.getAppWidgetIds(ComponentName(context, CompactWidgetProvider::class.java))).distinct().toIntArray()
    }
    @Synchronized
    fun wallpaperChanged() { wallpaperLoaded = false; cache.clear() }
    @Synchronized
    internal fun ink(context: Context, preferences: Preferences): Int {
        if (preferences.ink == InkMode.AUTO && preferences.opacity == 100) {
            return WidgetStyle.ink(InkMode.AUTO,
                android.graphics.Color.luminance(preferences.backgroundRgb or 0xff000000.toInt()) > .5f)
        }
        if (!wallpaperLoaded) {
            wallpaperHint = runCatching {
                context.getSystemService(WallpaperManager::class.java).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    ?.let { it.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0 }
            }.getOrNull()
            wallpaperLoaded = true
        }
        return WidgetStyle.ink(preferences.ink, wallpaperHint, preferences.customInkRgb)
    }
    fun updateAll(context: Context, now: Instant = Instant.now(), force: Boolean = false) {
        val ids = ids(context); cache.retain(ids.toSet())
        render(context, ids, now, force)
    }
    fun updateWidget(context: Context, id: Int, now: Instant = Instant.now()) {
        render(context, intArrayOf(id), now, true)
    }
    private fun render(context: Context, ids: IntArray, now: Instant, force: Boolean) {
        if (ids.isEmpty()) return
        val preferences = SettingsStore(context).load()
        val display = runCatching { displayContent(calculateDay(preferences.schedule, now), preferences,
            RefreshService.running, use24Hour = true) }
            .getOrElse { DisplayContent("--:--", "--:--", "--", 0,
                it.message ?: "时间计算失败，请检查设置", preferences.opacity, preferences.ink) }
        val color = ink(context, preferences)
        val manager = AppWidgetManager.getInstance(context)
        for (id in ids) {
            val provider = manager.getAppWidgetInfo(id)?.provider?.className ?: continue
            if (provider !in setOf(WideWidgetProvider::class.java.name, CompactWidgetProvider::class.java.name)) continue
            val options = manager.getAppWidgetOptions(id)
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val minWidth = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
            val compact = provider == CompactWidgetProvider::class.java.name || minWidth < 240
            val widthDp = minWidth.coerceIn(80, 2048)
            val heightDp = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                if (compact) 70 else 150).coerceIn(50, 2048)
            val key = Key(display, color, preferences.design, widthDp, heightDp, context.resources.displayMetrics.density)
            if (force || cache.changed(id, key)) {
                try {
                    manager.updateAppWidget(id, surface(context, R.layout.widget_surface, display, color, widthDp, heightDp,
                        preferences.design))
                    cache.committed(id, key)
                    SettingsStore(context).recordWidgetSubmission(System.currentTimeMillis())
                } catch (error: RuntimeException) {
                    SettingsStore(context).lastError = "组件更新失败：${error.javaClass.simpleName}"
                    android.util.Log.e("PersonalDay", "Widget update failed for $id", error)
                }
            }
        }
    }
    fun preview(context: Context, p: Preferences = SettingsStore(context).load(),
                now: Instant = Instant.now(), widthDp: Int = 320, heightDp: Int = 140): RemoteViews =
        surface(context, R.layout.widget_surface,
            displayContent(calculateDay(p.schedule, now), p, RefreshService.running, use24Hour = true),
            ink(context, p), widthDp, heightDp, p.design)

    /** Both preview and launcher use the same transparent Canvas face.
     * The finished bitmap crosses Binder, capped at 480k pixels (~1.9 MB).
     * Status stays in the accessibility description and settings, never a fourth row.
     */
    @Suppress("UNUSED_PARAMETER")
    internal fun surface(context: Context, layout: Int, d: DisplayContent, color: Int, widthDp: Int, heightDp: Int,
                         design: DesignSettings = DesignSettings()): RemoteViews {
        val density = context.resources.displayMetrics.density
        val width = (widthDp.coerceIn(1, 2048) * density).toInt().coerceAtLeast(1)
        val height = (heightDp.coerceIn(1, 2048) * density).toInt().coerceAtLeast(1)
        val cap = minOf(1.0, kotlin.math.sqrt(480_000.0 / (width.toDouble() * height)))
        val pixelsWide = (width * cap).toInt().coerceAtLeast(1)
        val pixelsHigh = (height * cap).toInt().coerceAtLeast(1)
        val personal = d.personal.removePrefix("0").let { if (it.startsWith(":")) "0$it" else it }
        val key = FaceKey(d.regular, personal, d.weekdayPeriod, d.date, d.percent, d.units, color, pixelsWide, pixelsHigh,
            design, d.backgroundRgb, d.opacity)
        val bitmap = faces.get(key) ?: ClockFaceRenderer.render(context, pixelsWide, pixelsHigh,
            d.regular, personal, d.weekdayPeriod, d.date, d.percent, d.units, color,
            design, d.backgroundRgb, d.opacity).also { faces.put(key, it) }
        return RemoteViews(context.packageName, R.layout.widget_surface).apply {
            setImageViewBitmap(R.id.widget_image, bitmap)
            setContentDescription(R.id.widget_root,
                "常规时间 ${d.regular}，个人时钟 ${d.personal}，${d.weekdayPeriod} ${d.date}，清醒日已过 ${d.percent}；刻度 25%、50%、75%。${d.status}。点击打开醒时区设置")
            setOnClickPendingIntent(R.id.widget_root, settingsIntent(context))
        }
    }
    private fun settingsIntent(context: Context): PendingIntent {
        val intent = Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
