package com.personalday.core

import org.junit.Assert.*
import org.junit.Test

class PreferencesTest {
    private class MemoryStorage : PreferenceStorage {
        var data = emptyMap<String, String>()
        override fun read() = data
        override fun write(values: Map<String, String>) { data = values.toMap() }
    }
    @Test fun persistsAcrossRepositoryInstances() {
        val store = MemoryStorage()
        val value = Preferences(Schedule("10:00", "02:00"), 35, InkMode.CUSTOM, true, 0x123456, 0xABCDEF,
            DesignSettings(timeScale = 105, middleScale = 90, bottomScale = 80,
                timeOpacity = 95, middleOpacity = 0, bottomOpacity = 65, progressThickness = 150))
        PreferenceRepository(store).save(value)
        assertEquals(value, PreferenceRepository(store).load())
    }
    @Test fun defaultsAndCorruption() {
        val store = MemoryStorage(); assertEquals(Preferences(), PreferenceRepository(store).load())
        store.data = mapOf("wake" to "25:00", "opacity" to "150", "ink" to "wrong",
            "appearance_version" to "2", "design_version" to "1")
        assertEquals(Preferences(opacity = 100), PreferenceRepository(store).load())
    }
    @Test fun invalidSaveDoesNotOverwrite() {
        val store = MemoryStorage(); val repo = PreferenceRepository(store)
        repo.save(Preferences())
        assertThrows(IllegalArgumentException::class.java) { repo.save(Preferences(Schedule("08:00", "08:00"))) }
        assertThrows(IllegalArgumentException::class.java) {
            repo.save(Preferences(design = DesignSettings(timeScale = 111)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.save(Preferences(design = DesignSettings(middleOpacity = -1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.save(Preferences(design = DesignSettings(progressThickness = 201)))
        }
        assertEquals(Preferences(), repo.load())
    }
    @Test fun oldSettingsGainDefaultColorsWithoutLosingScheduleOrRefresh() {
        val store = MemoryStorage()
        store.data = mapOf("wake" to "10:00", "sleep" to "02:00", "opacity" to "35", "ink" to "WHITE", "refresh" to "true")
        val repo = PreferenceRepository(store)
        assertEquals(Preferences(Schedule("10:00", "02:00"), 0, InkMode.WHITE, true), repo.load())
        store.data = store.data + mapOf("background_rgb" to "-1", "custom_ink_rgb" to "16777216")
        assertEquals(0xD8D8D8, repo.load().backgroundRgb)
        assertEquals(0x101010, repo.load().customInkRgb)
        assertThrows(IllegalArgumentException::class.java) { repo.save(repo.load().copy(backgroundRgb = -1)) }
    }
    @Test fun transparentUpgradePreservesCustomInkAndAllowsLaterOpacityChoice() {
        val store = MemoryStorage()
        store.data = mapOf("wake" to "10:00", "sleep" to "02:00", "opacity" to "65", "ink" to "CUSTOM",
            "refresh" to "true", "background_rgb" to "123456", "custom_ink_rgb" to "654321",
            "appearance_version" to "2")
        val repo = PreferenceRepository(store)
        val upgraded = repo.load()
        assertEquals(0, upgraded.opacity)
        assertEquals(654321, upgraded.customInkRgb)
        assertEquals(123456, upgraded.backgroundRgb)
        assertTrue(upgraded.refreshEnabled)
        assertEquals(Schedule("10:00", "02:00"), upgraded.schedule)
        assertEquals(InkMode.CUSTOM, upgraded.ink)
        assertEquals(DesignSettings(), upgraded.design)
        repo.save(upgraded.copy(opacity = 45))
        assertEquals(upgraded.copy(opacity = 45), PreferenceRepository(store).load())
    }

    @Test fun corruptDesignValuesCannotReachTheRenderer() {
        val store = MemoryStorage()
        store.data = mapOf("design_version" to "1", "appearance_version" to "2",
            "design_time_scale" to "999", "design_middle_scale" to "-20",
            "design_bottom_scale" to "not a number", "design_time_opacity" to "-1",
            "design_middle_opacity" to "101", "design_bottom_opacity" to "99999999999999999999",
            "design_progress_thickness" to "0", "opacity" to "-50")
        val loaded = PreferenceRepository(store).load()
        assertEquals(DesignSettings(timeScale = 110, middleScale = 60, bottomScale = 100,
            timeOpacity = 0, middleOpacity = 100, bottomOpacity = 50, progressThickness = 50), loaded.design)
        assertEquals(0, loaded.opacity)
        loaded.validate()
    }

    @Test fun unversionedDesignFieldsCannotActivateDuringUpgrade() {
        val store = MemoryStorage()
        store.data = mapOf("appearance_version" to "2", "opacity" to "90",
            "design_time_scale" to "60", "design_time_opacity" to "0",
            "design_progress_thickness" to "200")
        assertEquals(Preferences(), PreferenceRepository(store).load())
    }

    @Test fun designCanBeResetWithoutChangingScheduleOrRefresh() {
        val store = MemoryStorage()
        val repo = PreferenceRepository(store)
        val customized = Preferences(schedule = Schedule("10:00", "02:00"), refreshEnabled = true,
            opacity = 30, ink = InkMode.CUSTOM, backgroundRgb = 0x112233, customInkRgb = 0xABCDEF,
            design = DesignSettings(timeScale = 110, middleOpacity = 75))
        repo.save(customized)
        val defaults = Preferences()
        repo.save(repo.load().copy(opacity = defaults.opacity, ink = defaults.ink,
            backgroundRgb = defaults.backgroundRgb, customInkRgb = defaults.customInkRgb,
            design = defaults.design))
        assertEquals(Preferences(schedule = customized.schedule, refreshEnabled = true), repo.load())
    }
}
