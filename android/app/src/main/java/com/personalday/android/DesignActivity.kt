package com.personalday.android

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.*
import com.personalday.core.Preferences
import java.time.Instant
import java.util.concurrent.Executors

/** Design changes stay in this page's draft until explicitly saved. */
class DesignActivity : Activity() {
    private lateinit var editor: ThemeEditor
    private lateinit var store: SettingsStore
    private lateinit var preview: FrameLayout
    private lateinit var sample: FrameLayout
    private lateinit var hint: TextView
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { work -> Thread(work, "PersonalDay-design-preview").apply { isDaemon = true } }
    private var compact = false
    private var lightBackdrop = false
    private var active = false
    private var generation = 0L
    private var busy = false
    private var pending: PreviewRequest? = null
    private var leaveDialog: AlertDialog? = null
    private var backCallback: OnBackInvokedCallback? = null
    private data class PreviewRequest(val generation: Long, val preferences: Preferences, val widthDp: Int, val heightDp: Int)
    private val previewTick = Runnable { submitLatestPreview() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        compact = savedInstanceState?.getBoolean("design_compact") ?: false
        lightBackdrop = savedInstanceState?.getBoolean("design_light_backdrop") ?: false
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0) }
        setContentView(body)
        body.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(dp(18) + bars.left, bars.top, dp(18) + bars.right, maxOf(bars.bottom, ime.bottom)); insets
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(Button(this).apply {
            text = "返回"; isAllCaps = false; id = R.id.design_back
            setOnClickListener { requestLeave() }
        }, LinearLayout.LayoutParams(dp(76), dp(48)))
        titleRow.addView(TextView(this).apply { text = "设计"; textSize = 24f; setPadding(dp(12), 0, 0, 0) })
        body.addView(titleRow)
        val toggles = LinearLayout(this)
        val size = Button(this).apply { id = R.id.design_size; isAllCaps = false }
        val backdrop = Button(this).apply { id = R.id.design_backdrop; isAllCaps = false }
        fun updateToggles() {
            size.text = if (compact) "预览 2×1 · 切换" else "预览 4×2 · 切换"
            backdrop.text = if (lightBackdrop) "浅底 · 切换" else "深底 · 切换"
            preview.setBackgroundColor(if (lightBackdrop) 0xffe9ecef.toInt() else 0xff252930.toInt())
        }
        toggles.addView(size, LinearLayout.LayoutParams(0, dp(48), 1f))
        toggles.addView(backdrop, LinearLayout.LayoutParams(0, dp(48), 1f))
        body.addView(toggles)
        preview = FrameLayout(this).apply { id = R.id.theme_preview }
        sample = FrameLayout(this)
        preview.addView(sample, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        body.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(164)))
        hint = TextView(this).apply { id = R.id.design_preview_hint; textSize = 11f; setPadding(0, dp(4), 0, dp(4)) }
        body.addView(hint)
        updateToggles()
        size.setOnClickListener { compact = !compact; updateToggles(); schedulePreview() }
        backdrop.setOnClickListener { lightBackdrop = !lightBackdrop; updateToggles() }
        preview.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ -> if (right - left != oldRight - oldLeft) schedulePreview() }
        editor = ThemeEditor(this, store.load(), savedInstanceState)
        val scroll = ScrollView(this).apply { id = R.id.design_scroll; addView(editor); isFillViewport = false }
        body.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val footer = LinearLayout(this)
        footer.addView(Button(this).apply {
            text = "恢复默认"; isAllCaps = false; id = R.id.design_reset
            setOnClickListener { editor.resetDraft(); Toast.makeText(this@DesignActivity, "已恢复默认预览，保存后生效", Toast.LENGTH_SHORT).show() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        footer.addView(Button(this).apply {
            text = "保存并应用"; isAllCaps = false; id = R.id.design_save
            setOnClickListener { saveDesign() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        body.addView(footer)
        editor.onDraftChanged = { schedulePreview() }
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = OnBackInvokedCallback { requestLeave() }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        }
        hint.text = previewLabel()
    }

    private fun previewLabel() = "固定时刻排版预览 · 10/01 16:49；观察底色不保存，实际尺寸由桌面决定。"
    private fun schedulePreview() {
        generation++
        pending = null
        main.removeCallbacks(previewTick)
        if (active) main.postDelayed(previewTick, 120)
    }
    private fun submitLatestPreview() {
        if (!active || preview.width <= 0) return
        val draft = runCatching { editor.applyTo(store.load()).also { it.validate() } }.getOrElse {
            hint.text = "预览未更新：${it.message}"; return
        }
        val available = ((preview.width - dp(12)) / resources.displayMetrics.density).toInt().coerceAtLeast(80)
        val width = if (compact) (available / 2).coerceAtLeast(80) else available
        val height = minOf(154, (width * 470f / 1080).toInt().coerceAtLeast(40))
        pending = PreviewRequest(generation, draft, width, height)
        startPendingPreview()
    }
    private fun startPendingPreview() {
        if (busy || !active) return
        val request = pending ?: return
        pending = null; busy = true
        val app = applicationContext
        worker.execute {
            val result = runCatching { WidgetRenderer.preview(app, request.preferences, Instant.parse("2026-10-01T08:49:00Z"), request.widthDp, request.heightDp) }
            main.post {
                busy = false
                if (active && request.generation == generation) {
                    result.fold(onSuccess = { views ->
                        sample.layoutParams = FrameLayout.LayoutParams(dp(request.widthDp), dp(request.heightDp), Gravity.CENTER)
                        // Only the host inflation/reapply occurs on the main thread.
                        runCatching {
                            if (sample.childCount == 0) sample.addView(views.apply(this, sample)) else views.reapply(this, sample.getChildAt(0))
                            sample.getChildAt(0).setOnClickListener(null)
                        }.onSuccess { hint.text = previewLabel() }
                            .onFailure { hint.text = "预览显示失败：${it.javaClass.simpleName}" }
                    }, onFailure = { hint.text = "预览生成失败：${it.javaClass.simpleName}" })
                }
                startPendingPreview()
            }
        }
    }

    internal fun saveDesign(): Boolean {
        val value = try { editor.applyTo(store.load()).also { it.validate() } } catch (error: IllegalArgumentException) {
            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show(); return false
        }
        store.save(value)
        editor.markSaved()
        WidgetRenderer.wallpaperChanged()
        WidgetRenderer.updateAll(this, force = true)
        Toast.makeText(this, "设计已应用到所有组件", Toast.LENGTH_SHORT).show()
        return true
    }
    internal fun requestLeave() {
        if (!editor.hasUnsavedChanges()) { finish(); return }
        if (leaveDialog?.isShowing == true) return
        leaveDialog = AlertDialog.Builder(this).setTitle("保存设计修改？")
            .setMessage("尚未保存的调整只显示在预览中。")
            .setPositiveButton("保存并退出") { _, _ -> if (saveDesign()) finish() }
            .setNeutralButton("放弃修改") { _, _ -> finish() }
            .setNegativeButton("继续编辑", null).show()
    }
    @Deprecated("For Android 12 and earlier; newer versions use OnBackInvokedCallback")
    // API 33+ is handled by the platform callback registered in onCreate. This
    // override remains necessary on API 31/32; no AndroidX Activity dependency.
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed() { requestLeave() }
    override fun onStart() { super.onStart(); active = true; schedulePreview() }
    override fun onStop() {
        active = false; generation++; pending = null; main.removeCallbacks(previewTick)
        super.onStop()
    }
    override fun onDestroy() {
        active = false; generation++; pending = null
        editor.onDraftChanged = null; main.removeCallbacksAndMessages(null); worker.shutdownNow()
        leaveDialog?.dismiss()
        if (Build.VERSION.SDK_INT >= 33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        editor.saveState(outState); outState.putBoolean("design_compact", compact); outState.putBoolean("design_light_backdrop", lightBackdrop)
    }
}
