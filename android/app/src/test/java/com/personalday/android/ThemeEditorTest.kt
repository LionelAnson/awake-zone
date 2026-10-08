package com.personalday.android

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import com.personalday.core.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [StructureOnlyCanvasShadow::class])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ThemeEditorTest {
    private lateinit var context: Application
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personal_day", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun editsDraftWithoutSavingAndPreservesLatestNonDesignSettings() {
        val saved = Preferences(Schedule("10:00", "02:00"), opacity = 65, refreshEnabled = true, backgroundRgb = 0x123456)
        SettingsStore(context).save(saved)
        val editor = ThemeEditor(context, saved, null)
        editor.findViewById<Spinner>(R.id.theme_ink_mode).setSelection(InkMode.CUSTOM.ordinal)
        editor.findViewById<EditText>(R.id.theme_ink_color).setText("#ABCDEF")
        editor.findViewById<SeekBar>(R.id.design_time_scale).progress = 50
        val latest = saved.copy(schedule = Schedule("09:00", "01:00"), refreshEnabled = false)
        val draft = editor.applyTo(latest)
        assertEquals(latest.copy(ink = InkMode.CUSTOM, customInkRgb = 0xABCDEF, design = DesignSettings(timeScale = 110)), draft)
        assertEquals(saved, SettingsStore(context).load())
        assertTrue(editor.hasUnsavedChanges())
    }
    @Test fun invalidCustomInkCannotBeSaved() {
        val editor = ThemeEditor(context, Preferences(), null)
        editor.findViewById<Spinner>(R.id.theme_ink_mode).setSelection(InkMode.CUSTOM.ordinal)
        editor.findViewById<EditText>(R.id.theme_ink_color).setText("#oops")
        assertThrows(IllegalArgumentException::class.java) { SettingsStore(context).save(editor.applyTo(Preferences())) }
        assertEquals(Preferences(), SettingsStore(context).load())
    }
    @Test fun invalidSolidBackgroundCannotBeSaved() {
        val editor = ThemeEditor(context, Preferences(opacity = 18), null)
        editor.findViewById<EditText>(R.id.theme_background).setText("#oops")
        assertThrows(IllegalArgumentException::class.java) { editor.applyTo(Preferences()) }
        assertEquals(Preferences(), SettingsStore(context).load())
    }
    @Test fun draftSurvivesRecreationIncludingIncompleteColorAndDirtyFlag() {
        val editor = ThemeEditor(context, Preferences(), null)
        editor.findViewById<Spinner>(R.id.theme_ink_mode).setSelection(InkMode.CUSTOM.ordinal)
        editor.findViewById<EditText>(R.id.theme_ink_color).setText("#12")
        editor.findViewById<SeekBar>(R.id.design_bottom_opacity).progress = 27
        val state = Bundle(); editor.saveState(state)
        val restored = ThemeEditor(context, Preferences(), state)
        assertEquals("#12", restored.findViewById<EditText>(R.id.theme_ink_color).text.toString())
        assertEquals(InkMode.CUSTOM.ordinal, restored.findViewById<Spinner>(R.id.theme_ink_mode).selectedItemPosition)
        assertEquals(27, restored.findViewById<SeekBar>(R.id.design_bottom_opacity).progress)
        assertTrue(restored.hasUnsavedChanges())
        assertEquals(Preferences(), SettingsStore(context).load())
    }
    @Test fun resetOnlyChangesDraftAppearanceAndDoesNotPersist() {
        val saved = Preferences(Schedule("10:00", "02:00"), opacity = 75, ink = InkMode.BLACK,
            refreshEnabled = true, backgroundRgb = 0x314159, design = DesignSettings(timeScale = 75, timeOpacity = 20))
        SettingsStore(context).save(saved)
        val editor = ThemeEditor(context, saved, null)
        editor.resetDraft()
        assertEquals(Preferences(schedule = saved.schedule, refreshEnabled = true), editor.applyTo(saved))
        assertEquals(saved, SettingsStore(context).load())
        assertTrue(editor.hasUnsavedChanges())
        editor.markSaved()
        assertFalse(editor.hasUnsavedChanges())
    }
    @Test fun allOpacityEndpointsAreAcceptedAndFontRangeIsBounded() {
        val editor = ThemeEditor(context, Preferences(opacity = 18), null)
        editor.findViewById<SeekBar>(R.id.theme_opacity).progress = 100
        editor.findViewById<SeekBar>(R.id.design_time_opacity).progress = 0
        editor.findViewById<SeekBar>(R.id.design_middle_opacity).progress = 100
        editor.findViewById<SeekBar>(R.id.design_bottom_opacity).progress = 0
        editor.findViewById<SeekBar>(R.id.design_middle_scale).progress = -20
        editor.findViewById<SeekBar>(R.id.design_bottom_scale).progress = 200
        editor.findViewById<SeekBar>(R.id.design_progress_thickness).progress = 200
        val draft = editor.applyTo(Preferences())
        draft.validate()
        assertEquals(100, draft.opacity)
        assertEquals(DesignSettings(timeOpacity = 0, middleOpacity = 100, bottomOpacity = 0,
            middleScale = 60, bottomScale = 110, progressThickness = 200), draft.design)
    }
    @Test fun cleanDraftRestoredAfterRotationStaysClean() {
        val original = ThemeEditor(context, Preferences(), null)
        assertFalse(original.hasUnsavedChanges())
        val bundle = Bundle(); original.saveState(bundle)
        assertFalse(ThemeEditor(context, Preferences(), bundle).hasUnsavedChanges())
    }
}
