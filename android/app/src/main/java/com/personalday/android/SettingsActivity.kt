package com.personalday.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.*
import com.personalday.core.*
import java.util.Locale
import java.time.Instant

class SettingsActivity : Activity() {
    private lateinit var store: SettingsStore
    private lateinit var wake: Button
    private lateinit var sleep: Button
    private lateinit var designSummary: TextView
    private lateinit var refresh: Switch
    private lateinit var status: TextView
    private lateinit var addStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var permissionDraft: Preferences? = null
    private val statusTick = object : Runnable {
        override fun run() { updateStatus(); handler.postDelayed(this, 1000) }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        RefreshControl.ensureChannel(this)
        val prefs = store.load()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(16), dp(22), dp(24)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        fun text(value: String, size: Float = 14f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(0xff24332c.toInt()); setPadding(0, dp(8), 0, dp(8)); body.addView(this)
        }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { action() }; body.addView(this,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        text("醒时区", 25f)
        text("北京时间 · Asia/Shanghai\n根据计划作息换算时间，不检测睡眠。")
        text("添加到桌面", 18f)
        button("添加 4×2 组件") { pin(WideWidgetProvider::class.java) }
        button("添加 2×1 组件") { pin(CompactWidgetProvider::class.java) }
        addStatus = text("", 13f)
        text("添加后使用已保存的作息；默认作息为 08:00—24:00（次日 00:00），可在下方设置实际预期起床和入睡时间。", 12f)
        if (WidgetPinning.isXiaomi(this)) {
            text(WidgetPinning.XIAOMI_HELP, 13f)
            button("检查“桌面快捷方式”权限") { openAppSettings() }
        }
        button("添加帮助 / 诊断信息") {
            AlertDialog.Builder(this).setTitle("桌面添加帮助")
                .setMessage(WidgetPinning.help(this) + "\n\n" + WidgetPinning.diagnostic(this))
                .setPositiveButton("复制诊断信息") { _, _ ->
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("醒时区诊断", WidgetPinning.diagnostic(this)))
                    Toast.makeText(this, "诊断信息已复制", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("关闭", null).show()
        }
        text("组件设计", 18f)
        button("设计 · 背景、字号与不透明度") { startActivity(Intent(this, DesignActivity::class.java)) }.id = R.id.design_entry
        designSummary = text("", 13f).apply { id = R.id.design_summary }
        text("计划作息", 18f)
        wake = button(savedInstanceState?.getString("wake") ?: prefs.schedule.wake) { chooseTime(wake, "计划起床") }
        wake.contentDescription = "计划起床时间"
        sleep = button(savedInstanceState?.getString("sleep") ?: prefs.schedule.sleep) { chooseTime(sleep, "计划入睡") }
        sleep.contentDescription = "计划入睡时间"
        text("上方为起床，下方为入睡；入睡早于起床时按次日处理。")
        refresh = Switch(this).apply {
            text = "持续刷新（显示无声通知）"; isChecked = savedInstanceState?.getBoolean("refresh") ?: prefs.refreshEnabled
            setPadding(0, dp(14), 0, dp(14))
        }
        body.addView(refresh)
        button(if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) "保存并应用" else "保存并添加组件") { saveDraft() }
        button("立即刷新一次") { WidgetRenderer.wallpaperChanged(); WidgetRenderer.updateAll(this, force = true); updateStatus() }
        status = text("", 13f)
        button("打开通知设置") {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        button("应用系统设置") { openAppSettings() }
        text("小米桌面的后台限制可能暂停刷新。可在系统应用设置中检查自启动与省电策略。强行停止后需重新打开应用。\n\n所有组件共用作息与外观。熄屏和锁屏时暂停周期计算；屏幕亮起且解锁后恢复。", 12f)
    }
    private fun chooseTime(button: Button, title: String) {
        val time = parseTime(button.text.toString())
        TimePickerDialog(this, { _, h, m -> button.text = String.format(Locale.ROOT, "%02d:%02d", h, m) }, time / 60, time % 60, true)
            .apply { setTitle(title); show() }
    }
    private fun draft() = store.load().copy(
        schedule = Schedule(wake.text.toString(), sleep.text.toString()), refreshEnabled = refresh.isChecked)
    private fun saveDraft() {
        val value = try { draft().also { it.validate() } } catch (error: IllegalArgumentException) {
            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show(); return
        }
        if (value.refreshEnabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionDraft = value
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        } else commit(value)
    }
    private fun commit(value: Preferences) {
        val allowed = !value.refreshEnabled || RefreshControl.notificationsAllowed(this)
        // A permission dialog or a visit to Design may outlive this form's original snapshot.
        // Save only the schedule and refresh controls into the latest stored appearance.
        val actual = store.load().copy(schedule = value.schedule, refreshEnabled = value.refreshEnabled && allowed)
        store.save(actual); refresh.isChecked = actual.refreshEnabled
        store.lastError = if (allowed) "" else "通知未开启，已保存作息。当前为手动刷新模式。"
        WidgetRenderer.wallpaperChanged()
        RefreshControl.reconcile(this)
        WidgetRenderer.updateAll(this, force = true)
        updateStatus()
        if (!allowed) Toast.makeText(this, store.lastError, Toast.LENGTH_LONG).show()
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetRenderer.updateWidget(this, widgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
        } else Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42) { val pending = permissionDraft ?: draft(); permissionDraft = null; commit(pending) }
    }
    private fun pin(provider: Class<*>) {
        val message = WidgetPinning.request(this, provider)
        updateStatus()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
        } catch (error: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "请在手机设置中搜索醒时区，打开应用信息和其他权限。", Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Toast.makeText(this, "系统未允许跳转，请手动打开醒时区的应用信息。", Toast.LENGTH_LONG).show()
        }
    }
    private fun updateStatus() {
        val count = WidgetRenderer.ids(this).size
        val prefs = store.load()
        addStatus.text = listOf(WidgetPinning.discovery(this), store.pinStatus).filter { it.isNotEmpty() }.joinToString("\n")
        val summary = when {
            !prefs.refreshEnabled -> "手动刷新模式；桌面时间可能停留在上次更新。"
            count == 0 -> "已启用，添加桌面组件后开始刷新。"
            RefreshService.running -> "刷新服务运行中 · $count 个组件"
            else -> "刷新尚未运行，请点击保存重试。"
        }
        status.text = listOf(summary, stateSummary(prefs), store.lastError).filter { it.isNotEmpty() }.joinToString("\n")
        val background = if (prefs.opacity == 0) "全透明背景" else "${WidgetStyle.hex(prefs.backgroundRgb)} · ${prefs.opacity}% 不透明"
        val color = when (prefs.ink) { InkMode.AUTO -> "自动字色"; InkMode.BLACK -> "黑色文字"; InkMode.WHITE -> "白色文字"; InkMode.CUSTOM -> "文字 ${WidgetStyle.hex(prefs.customInkRgb)}" }
        designSummary.text = "$background · $color\n三行字号 ${prefs.design.timeScale}% / ${prefs.design.middleScale}% / ${prefs.design.bottomScale}% · 所有组件共用"
    }
    internal fun stateSummary(preferences: Preferences, now: Instant = Instant.now()): String =
        runCatching { displayContent(calculateDay(preferences.schedule, now), preferences, RefreshService.running).status }
            .getOrElse { it.message ?: "时间计算失败，请检查作息。" }
    override fun onResume() {
        super.onResume()
        store.captureBeforeOpen()
        WidgetRenderer.wallpaperChanged()
        RefreshControl.reconcile(this)
        handler.removeCallbacks(statusTick); handler.post(statusTick)
    }
    override fun onPause() { handler.removeCallbacks(statusTick); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("wake", wake.text.toString()); out.putString("sleep", sleep.text.toString())
        out.putBoolean("refresh", refresh.isChecked)
    }
}
