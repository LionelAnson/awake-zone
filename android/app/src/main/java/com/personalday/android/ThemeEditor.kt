package com.personalday.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.personalday.core.DesignSettings
import com.personalday.core.InkMode
import com.personalday.core.Preferences

/** Controls edit a draft only; the Activity owns preview work and persistence. */
@SuppressLint("ViewConstructor")
class ThemeEditor(context: Context, private val initial: Preferences, state: Bundle?) : LinearLayout(context) {
    var onDraftChanged: (() -> Unit)? = null
    private val backgroundMode = Spinner(context)
    private val background = EditText(context)
    private val customInk = EditText(context)
    private val ink = Spinner(context)
    private val sliders = linkedMapOf<String, Pair<SeekBar, Int>>()
    private var updating = true
    private var cleanState = ""
    private var lastBackgroundMode = if (initial.opacity == 0) 0 else 1
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun label(value: String, size: Float = 14f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(0xff24332c.toInt())
        setPadding(0, dp(8), 0, dp(4)); addView(this)
    }
    init {
        id = R.id.theme_editor; orientation = VERTICAL
        label("背景", 19f)
        setupSpinner(backgroundMode, R.id.theme_background_mode, listOf("全透明", "纯色背景"), if (initial.opacity == 0) 0 else 1)
        setupColor(background, R.id.theme_background, "背景颜色，六位色值", WidgetStyle.hex(initial.backgroundRgb))
        val palette = LinearLayout(context).apply { id = R.id.theme_presets }
        listOf("浅灰" to 0xD8D8D8, "白" to 0xFFFFFF, "黑" to 0x000000, "米色" to 0xE8DCC5).forEach { (name, rgb) ->
            palette.addView(Button(context).apply {
                text = name; isAllCaps = false; minWidth = 0; minimumWidth = 0
                setPadding(0, 0, 0, 0)
                contentDescription = "背景色$name ${WidgetStyle.hex(rgb)}"
                setOnClickListener { backgroundMode.setSelection(1); this@ThemeEditor.background.setText(WidgetStyle.hex(rgb)); if (number("opacity") == 0) setNumber("opacity", 18) }
            }, LayoutParams(0, dp(48), 1f))
        }
        addView(palette)
        slider("opacity", R.id.theme_opacity, "背景不透明度", 0, 100, initial.opacity)
        label("0% 完全透明，100% 完全不透明。", 12f)
        label("文字颜色", 19f)
        setupSpinner(ink, R.id.theme_ink_mode, listOf("自动参考背景", "黑色", "白色", "自定义颜色"), initial.ink.ordinal)
        setupColor(customInk, R.id.theme_ink_color, "文字颜色，六位色值", WidgetStyle.hex(initial.customInkRgb))
        label("色值格式 #RRGGBB。全不透明背景按背景明暗自动选字色；其余情况参考整张壁纸。局部看不清时可手动选色。", 12f)
        label("三行文字", 19f)
        label("左右两栏按行联动。100% 为当前布局；字号范围 60—110%，为最长时间和百分比保留空间。", 12f)
        slider("time_scale", R.id.design_time_scale, "第一行 · 时间字号", 60, 110, initial.design.timeScale)
        slider("time_opacity", R.id.design_time_opacity, "第一行 · 不透明度", 0, 100, initial.design.timeOpacity)
        slider("middle_scale", R.id.design_middle_scale, "第二行 · 星期字号", 60, 110, initial.design.middleScale)
        slider("middle_opacity", R.id.design_middle_opacity, "第二行 · 文字及进度条不透明度", 0, 100, initial.design.middleOpacity)
        slider("bottom_scale", R.id.design_bottom_scale, "第三行 · 日期及百分比字号", 60, 110, initial.design.bottomScale)
        slider("bottom_opacity", R.id.design_bottom_opacity, "第三行 · 不透明度", 0, 100, initial.design.bottomOpacity)
        label("进度条", 19f)
        slider("progress_thickness", R.id.design_progress_thickness, "进度条粗细", 50, 200, initial.design.progressThickness)
        label(WidgetTypeface.NOTICE, 12f)
        cleanState = fingerprint()
        if (state != null) {
            backgroundMode.setSelection(state.getInt("theme_background_mode", backgroundMode.selectedItemPosition))
            background.setText(state.getString("theme_background", background.text.toString()))
            ink.setSelection(state.getInt("theme_ink", ink.selectedItemPosition))
            customInk.setText(state.getString("theme_custom_ink", customInk.text.toString()))
            sliders.keys.forEach { key -> setNumber(key, state.getInt("design_$key", number(key))) }
            cleanState = state.getString("design_clean", cleanState) ?: cleanState
        }
        lastBackgroundMode = backgroundMode.selectedItemPosition
        updating = false
        changed()
    }
    private fun setupSpinner(view: Spinner, id: Int, choices: List<String>, selected: Int) {
        view.id = id
        view.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, choices)
        view.setSelection(selected)
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        view.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, selectedView: View?, position: Int, itemId: Long) {
                if (id == R.id.theme_background_mode && !updating && position != lastBackgroundMode) {
                    lastBackgroundMode = position
                    if (position == 0) setNumber("opacity", 0)
                    else if (number("opacity") == 0) setNumber("opacity", 18)
                }
                changed()
            }
        }
    }
    private fun setupColor(view: EditText, id: Int, description: String, value: String) {
        view.id = id; view.setSingleLine(true); view.contentDescription = description
        view.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        view.setText(value); addView(view, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        view.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    private fun slider(key: String, id: Int, title: String, minimum: Int, maximum: Int, value: Int) {
        val caption = label("$title：$value%")
        val row = LinearLayout(context)
        val seek = SeekBar(context).apply { this.id = id; max = maximum - minimum; progress = value - minimum; contentDescription = title }
        sliders[key] = seek to minimum
        fun step(text: String, direction: Int) = Button(context).apply {
            this.text = text; minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
            contentDescription = "$title${if (direction < 0) "减少" else "增加"}百分之一"
            setOnClickListener { seek.progress += direction }
        }
        row.addView(step("−", -1), LayoutParams(dp(48), dp(48)))
        row.addView(seek, LayoutParams(0, dp(48), 1f))
        row.addView(step("＋", 1), LayoutParams(dp(48), dp(48)))
        addView(row)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                caption.text = "$title：${progress + minimum}%"; changed()
            }
        })
    }
    private fun number(key: String): Int = sliders.getValue(key).let { (view, offset) -> view.progress + offset }
    private fun setNumber(key: String, value: Int) { sliders.getValue(key).let { (view, offset) -> view.progress = value - offset } }
    private fun changed() {
        if (updating) return
        val solid = backgroundMode.selectedItemPosition == 1
        background.isEnabled = solid
        (sliders.getValue("opacity").first.parent as LinearLayout).let { row ->
            for (index in 0 until row.childCount) row.getChildAt(index).isEnabled = solid
        }
        background.error = if (solid && WidgetStyle.parseRgb(background.text.toString()) == null) "请输入六位色值，如 #D8D8D8" else null
        val custom = ink.selectedItemPosition == InkMode.CUSTOM.ordinal
        customInk.visibility = if (custom) VISIBLE else GONE
        customInk.error = if (custom && WidgetStyle.parseRgb(customInk.text.toString()) == null) "请输入六位色值，如 #101010" else null
        onDraftChanged?.invoke()
    }
    /** Merge only design fields into the latest settings to preserve schedule/refresh changes. */
    fun applyTo(value: Preferences): Preferences = value.copy(
        ink = InkMode.entries[ink.selectedItemPosition],
        customInkRgb = if (ink.selectedItemPosition == InkMode.CUSTOM.ordinal)
            requireNotNull(WidgetStyle.parseRgb(customInk.text.toString())) { "文字颜色应为六位色值。" }
        else WidgetStyle.parseRgb(customInk.text.toString()) ?: initial.customInkRgb,
        opacity = if (backgroundMode.selectedItemPosition == 0) 0 else number("opacity"),
        backgroundRgb = if (backgroundMode.selectedItemPosition == 1)
            requireNotNull(WidgetStyle.parseRgb(background.text.toString())) { "背景颜色应为六位色值。" }
        else WidgetStyle.parseRgb(background.text.toString()) ?: initial.backgroundRgb,
        design = DesignSettings(number("time_scale"), number("middle_scale"), number("bottom_scale"),
            number("time_opacity"), number("middle_opacity"), number("bottom_opacity"), number("progress_thickness")),
    )
    fun resetDraft() {
        updating = true
        backgroundMode.setSelection(0); background.setText("#D8D8D8"); setNumber("opacity", 0)
        ink.setSelection(InkMode.AUTO.ordinal); customInk.setText("#101010")
        listOf("time_scale", "middle_scale", "bottom_scale", "time_opacity", "progress_thickness").forEach { setNumber(it, 100) }
        setNumber("middle_opacity", 50); setNumber("bottom_opacity", 50)
        lastBackgroundMode = 0; updating = false; changed()
    }
    private fun fingerprint() = listOf(backgroundMode.selectedItemPosition, background.text.toString(), ink.selectedItemPosition,
        customInk.text.toString(), sliders.keys.map(::number)).joinToString("|")
    fun hasUnsavedChanges(): Boolean = fingerprint() != cleanState
    fun markSaved() { cleanState = fingerprint() }
    fun saveState(out: Bundle) {
        out.putInt("theme_background_mode", backgroundMode.selectedItemPosition)
        out.putString("theme_background", background.text.toString())
        out.putString("theme_custom_ink", customInk.text.toString()); out.putInt("theme_ink", ink.selectedItemPosition)
        sliders.keys.forEach { out.putInt("design_$it", number(it)) }; out.putString("design_clean", cleanState)
    }
}
