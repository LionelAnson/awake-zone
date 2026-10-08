package com.personalday.core

enum class InkMode { AUTO, BLACK, WHITE, CUSTOM }
data class Preferences(
    val schedule: Schedule = Schedule(),
    val opacity: Int = 0,
    val ink: InkMode = InkMode.AUTO,
    val refreshEnabled: Boolean = false,
    val backgroundRgb: Int = 0xD8D8D8,
    val customInkRgb: Int = 0x101010,
    val design: DesignSettings = DesignSettings(),
) {
    fun validate() {
        schedule.validate()
        require(opacity in 0..100) { "不透明度应为 0—100%。" }
        require(backgroundRgb in 0..0xFFFFFF && customInkRgb in 0..0xFFFFFF) { "颜色应为六位 RGB 色值。" }
        design.validate()
    }
}

/** Small persistence boundary so a fresh repository can be tested without Android. */
interface PreferenceStorage {
    fun read(): Map<String, String>
    fun write(values: Map<String, String>)
}

class PreferenceRepository(private val storage: PreferenceStorage) {
    fun load(): Preferences {
        val m = storage.read()
        val schedule = Schedule(m["wake"] ?: "08:00", m["sleep"] ?: "00:00")
        val hasDesign = m["design_version"] == "1"
        fun percent(key: String, default: Int, range: IntRange): Int =
            m[key]?.toIntOrNull()?.coerceIn(range.first, range.last) ?: default
        val design = if (hasDesign) DesignSettings(
            timeScale = percent("design_time_scale", 100, 60..110),
            middleScale = percent("design_middle_scale", 100, 60..110),
            bottomScale = percent("design_bottom_scale", 100, 60..110),
            timeOpacity = percent("design_time_opacity", 100, 0..100),
            middleOpacity = percent("design_middle_opacity", 50, 0..100),
            bottomOpacity = percent("design_bottom_opacity", 50, 0..100),
            progressThickness = percent("design_progress_thickness", 100, 50..200),
        ) else DesignSettings()
        return Preferences(
            runCatching { schedule.validate(); schedule }.getOrDefault(Schedule()),
            // Earlier digital layouts ignored the stored background. Do not activate it on upgrade.
            if (hasDesign && m["appearance_version"] == "2") percent("opacity", 0, 0..100) else 0,
            runCatching { InkMode.valueOf(m["ink"] ?: "AUTO") }.getOrDefault(InkMode.AUTO),
            m["refresh"] == "true",
            m["background_rgb"]?.toIntOrNull()?.takeIf { it in 0..0xFFFFFF } ?: 0xD8D8D8,
            m["custom_ink_rgb"]?.toIntOrNull()?.takeIf { it in 0..0xFFFFFF } ?: 0x101010,
            design,
        )
    }
    fun save(value: Preferences) {
        value.validate()
        storage.write(mapOf("wake" to value.schedule.wake, "sleep" to value.schedule.sleep,
            "opacity" to value.opacity.toString(), "ink" to value.ink.name,
            "refresh" to value.refreshEnabled.toString(),
            "background_rgb" to value.backgroundRgb.toString(), "custom_ink_rgb" to value.customInkRgb.toString(),
            "appearance_version" to "2", "design_version" to "1",
            "design_time_scale" to value.design.timeScale.toString(),
            "design_middle_scale" to value.design.middleScale.toString(),
            "design_bottom_scale" to value.design.bottomScale.toString(),
            "design_time_opacity" to value.design.timeOpacity.toString(),
            "design_middle_opacity" to value.design.middleOpacity.toString(),
            "design_bottom_opacity" to value.design.bottomOpacity.toString(),
            "design_progress_thickness" to value.design.progressThickness.toString()))
    }
}
