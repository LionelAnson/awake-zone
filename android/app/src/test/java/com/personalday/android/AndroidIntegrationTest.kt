package com.personalday.android

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.personalday.core.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** LEGACY host simulation checks adapters, payloads and persistence only.
 * Pixel alpha, font loading and visual similarity require the Android instrumentation run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [StructureOnlyCanvasShadow::class])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class AndroidIntegrationTest {
    private lateinit var context: Application
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personal_day", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun refreshEvidencePreservesTheSubmissionBeforeOpening() {
        val store = SettingsStore(context)
        val start = Instant.parse("2026-10-01T08:49:00Z").toEpochMilli()
        store.recordWidgetSubmission(start)
        store.recordWidgetSubmission(start + 5_000)
        store.captureBeforeOpen()
        val evidence = store.refreshEvidence
        assertTrue(evidence.contains("2026-10-01T08:49:00Z"))
        store.recordWidgetSubmission(start + 60_000)
        assertEquals(evidence, store.refreshEvidence)
        store.captureBeforeOpen()
        assertTrue(store.refreshEvidence.contains("2026-10-01T08:50:00Z"))
    }
    @Test fun sharedPreferencesRoundTripPreservesValuesAndError() {
        val saved = Preferences(Schedule("10:00", "02:00"), 35, InkMode.CUSTOM, true, 0x123456, 0xABCDEF,
            DesignSettings(timeScale = 105, middleOpacity = 72, bottomOpacity = 0, progressThickness = 150))
        SettingsStore(context).save(saved)
        SettingsStore(context).lastError = "模拟系统拒绝"
        assertEquals(saved, SettingsStore(context).load())
        assertEquals("模拟系统拒绝", SettingsStore(context).lastError)
        SettingsStore(context).save(saved.copy(refreshEnabled = false))
        assertEquals("模拟系统拒绝", SettingsStore(context).lastError)
    }
    @Test fun bothWidgetSizesReapplyRestWithoutAddingAStatusTextRow() {
        val prefs = Preferences()
        val awake = displayContent(calculateDay(prefs.schedule, Instant.parse("2026-09-28T04:00:00Z")), prefs, true)
        val rest = displayContent(calculateDay(prefs.schedule, Instant.parse("2026-09-28T16:00:00Z")), prefs, false)
        for ((layout, width, height) in listOf(
            Triple(R.layout.widget_wide, 320, 140), Triple(R.layout.widget_compact, 160, 70))) {
            val views = WidgetRenderer.surface(context, layout, awake, 0xff101010.toInt(), width, height)
            val root = views.apply(context, FrameLayout(context))
            val before = bitmap(root)
            assertTrue(root.contentDescription.toString().contains("常规时间 12:00，个人时钟 06:00"))
            assertTrue(root.contentDescription.toString().contains("25.00%"))
            assertTrue(root.contentDescription.toString().contains("周一下午 09/28"))
            assertEquals(0, textViewCount(root))
            WidgetRenderer.surface(context, layout, rest, 0xfffafafa.toInt(), width, height).reapply(context, root)
            val description = root.contentDescription.toString()
            assertTrue(description.contains("个人时钟 24:00")); assertTrue(description.contains("100.00%"))
            assertTrue(description.contains("上一清醒日已结束")); assertTrue(description.contains("09-29 08:00"))
            assertTrue(description.contains("刷新已暂停"))
            assertEquals(0, textViewCount(root))
            assertEquals(before.width, bitmap(root).width)
            assertEquals(before.height, bitmap(root).height)
        }
    }
    @Test fun notificationIsQuietAndDoesNotOverrideUserChannelChoice() {
        RefreshControl.ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(RefreshControl.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound); assertFalse(channel.shouldVibrate()); assertFalse(channel.canShowBadge())
    }
    @Test fun solidBackgroundAutoInkUsesItsColorAndKeepsManualOverride() {
        val light = Preferences(opacity = 100, backgroundRgb = 0xFFFFFF)
        val dark = light.copy(backgroundRgb = 0x101010)
        assertEquals(0xff101010.toInt(), WidgetRenderer.ink(context, light))
        assertEquals(0xfffafafa.toInt(), WidgetRenderer.ink(context, dark))
        assertEquals(0xfffafafa.toInt(), WidgetRenderer.ink(context, light.copy(ink = InkMode.WHITE)))
        assertEquals(0xff44aaff.toInt(), WidgetRenderer.ink(context,
            dark.copy(ink = InkMode.CUSTOM, customInkRgb = 0x44AAFF)))
    }
    @Test fun previewAndWidgetUseBitmapSurfacesFromTheSameInjectedSnapshot() {
        val prefs = Preferences(ink = InkMode.CUSTOM, customInkRgb = 0xA5D9FF)
        android.provider.Settings.System.putString(context.contentResolver,
            android.provider.Settings.System.TIME_12_24, "12")
        assertFalse(android.text.format.DateFormat.is24HourFormat(context))
        val now = Instant.parse("2026-10-01T08:49:00Z")
        val display = displayContent(calculateDay(prefs.schedule, now), prefs, RefreshService.running,
            use24Hour = true)
        assertEquals("16:49", display.regular)
        assertEquals("13:13", display.personal)
        assertEquals("55.10%", display.percent)
        assertEquals(5510, display.units)
        val preview = WidgetRenderer.preview(context, prefs, now, 320, 140)
        val widget = WidgetRenderer.surface(context, R.layout.widget_wide, display,
            WidgetStyle.ink(prefs.ink, null, prefs.customInkRgb), 320, 140)
        assertEquals(R.layout.widget_surface, preview.layoutId)
        assertEquals(R.layout.widget_surface, widget.layoutId)
        val previewRoot = preview.apply(context, FrameLayout(context))
        val widgetRoot = widget.apply(context, FrameLayout(context))
        assertEquals(widgetRoot.contentDescription.toString(), previewRoot.contentDescription.toString())
        assertTrue(previewRoot.contentDescription.toString().contains("常规时间 16:49"))
        assertTrue(previewRoot.contentDescription.toString().contains("55.10%"))
        assertEquals(bitmap(widgetRoot).width, bitmap(previewRoot).width)
        assertEquals(bitmap(widgetRoot).height, bitmap(previewRoot).height)
        assertEquals(0, textViewCount(previewRoot))
        // Do not compare pixels here: LEGACY graphics can return empty raster content.
    }
    @Test fun settingsActivityCanOpenWithoutWidgets() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        assertNotNull(activity.get())
        assertFalse(SettingsStore(activity.get()).load().refreshEnabled)
        activity.pause().stop().destroy()
    }
    @Test fun applicationSummaryKeepsRestAndPauseInformationAfterRemovingTheFourthWidgetRow() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).get()
        val prefs = Preferences()
        val rest = activity.stateSummary(prefs, Instant.parse("2026-09-28T16:00:00Z"))
        assertTrue(rest.contains("上一清醒日已结束"))
        assertTrue(rest.contains("下次起床 09-29 08:00"))
        assertTrue(rest.contains("刷新已暂停"))
        val nextWake = activity.stateSummary(prefs, Instant.parse("2026-09-29T00:00:00Z"))
        assertFalse(nextWake.contains("清醒日已结束"))
        assertFalse(nextWake.contains("下次起床"))
        assertTrue(nextWake.contains("刷新已暂停"))
    }
    @Test fun widgetSurfaceCarriesReadableDescriptionAndBoundsBitmapMemory() {
        val prefs = Preferences()
        val display = displayContent(calculateDay(prefs.schedule, Instant.parse("2026-09-28T04:00:00Z")), prefs, true)
        val surface = WidgetRenderer.surface(context, R.layout.widget_wide, display, 0xff101010.toInt(), 1600, 1200)
        val root = surface.apply(context, FrameLayout(context))
        val image = root.findViewById<android.widget.ImageView>(R.id.widget_image)
        val bitmap = (image.drawable as android.graphics.drawable.BitmapDrawable).bitmap
        assertTrue(bitmap.byteCount <= 480_000 * 4)
        assertTrue(bitmap.hasAlpha())
        assertTrue(root.contentDescription.toString().contains("常规时间 12:00，个人时钟 06:00"))
        assertTrue(root.contentDescription.toString().contains("25.00%"))
    }

    private fun bitmap(root: View): Bitmap =
        (root.findViewById<ImageView>(R.id.widget_image).drawable as BitmapDrawable).bitmap

    private fun textViewCount(view: View): Int = when (view) {
        is TextView -> 1
        is ViewGroup -> (0 until view.childCount).sumOf { textViewCount(view.getChildAt(it)) }
        else -> 0
    }
}
