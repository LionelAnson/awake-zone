package com.personalday.android

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import com.personalday.core.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [StructureOnlyCanvasShadow::class])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DesignActivityTest {
    private lateinit var app: Application
    @Before fun reset() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("personal_day", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun saveMergesAppearanceIntoLatestScheduleAndRefresh() {
        val controller = Robolectric.buildActivity(DesignActivity::class.java).create()
        val activity = controller.get()
        activity.findViewById<SeekBar>(R.id.design_time_scale).progress = 50
        val latest = Preferences(schedule = Schedule("09:00", "01:00"), refreshEnabled = true)
        SettingsStore(app).save(latest)
        assertTrue(activity.saveDesign())
        assertEquals(latest.copy(design = DesignSettings(timeScale = 110)), SettingsStore(app).load())
        assertFalse(activity.findViewById<ThemeEditor>(R.id.theme_editor).hasUnsavedChanges())
        controller.destroy()
    }
    @Test fun backWithDraftOffersDiscardAndLeavesSavedAppearanceUntouched() {
        val controller = Robolectric.buildActivity(DesignActivity::class.java).create()
        val activity = controller.get()
        activity.findViewById<SeekBar>(R.id.design_bottom_opacity).progress = 0
        activity.requestLeave()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        assertFalse(activity.isFinishing)
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
        assertEquals(Preferences(), SettingsStore(app).load())
        controller.destroy()
    }
    @Test fun resetIsOnlyADraftUntilSaveAndKeepsSchedule() {
        val saved = Preferences(schedule = Schedule("10:00", "02:00"), opacity = 60,
            ink = InkMode.BLACK, design = DesignSettings(timeOpacity = 40))
        SettingsStore(app).save(saved)
        val controller = Robolectric.buildActivity(DesignActivity::class.java).create()
        val activity = controller.get()
        activity.findViewById<Button>(R.id.design_reset).performClick()
        assertEquals(saved, SettingsStore(app).load())
        assertTrue(activity.saveDesign())
        assertEquals(Preferences(schedule = saved.schedule), SettingsStore(app).load())
        controller.destroy()
    }
    @Test fun settingsScheduleSaveKeepsDesignSavedAfterOpeningTheForm() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val activity = controller.get()
        val controls = views(activity.findViewById(android.R.id.content))
        controls.filterIsInstance<Button>().single { it.contentDescription == "计划起床时间" }.text = "09:30"
        val design = Preferences(opacity = 28, ink = InkMode.CUSTOM, customInkRgb = 0xAABBCC,
            design = DesignSettings(timeScale = 85, bottomOpacity = 70))
        SettingsStore(app).save(design)
        controls.filterIsInstance<Button>().single { it.text == "保存并应用" }.performClick()
        assertEquals(design.copy(schedule = Schedule("09:30", "00:00")), SettingsStore(app).load())
        assertNotNull(activity.findViewById<Button>(R.id.design_entry))
        assertNull(activity.findViewById<Spinner>(R.id.theme_ink_mode))
        controller.destroy()
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
}
