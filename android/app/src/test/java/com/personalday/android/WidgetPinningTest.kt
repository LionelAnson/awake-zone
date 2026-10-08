package com.personalday.android

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import java.time.Instant
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAppWidgetManager

/** The real launcher can accept a request without confirming a placement. */
@Implements(AppWidgetManager::class)
class DeferredPinManager : ShadowAppWidgetManager() {
    companion object {
        var accepted = true
        var rejectWithException = false
        var enumerationLags = false
        var callback: PendingIntent? = null
    }
    @Implementation
    override fun requestPinAppWidget(provider: ComponentName, extras: Bundle?, successCallback: PendingIntent?): Boolean {
        if (rejectWithException) throw IllegalStateException("No foreground launcher request allowed")
        callback = successCallback
        return accepted
    }
    @Implementation
    override fun getAppWidgetIds(provider: ComponentName): IntArray =
        if (enumerationLags) intArrayOf() else super.getAppWidgetIds(provider)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [DeferredPinManager::class, StructureOnlyCanvasShadow::class])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class WidgetPinningTest {
    private lateinit var context: Application
    private lateinit var manager: AppWidgetManager
    private lateinit var shadow: ShadowAppWidgetManager
    private fun info() = AppWidgetProviderInfo().apply {
        provider = ComponentName(context, WideWidgetProvider::class.java)
        initialLayout = R.layout.widget_wide
    }
    private fun deliverSuccessCallback() {
        val pending = requireNotNull(DeferredPinManager.callback)
        val intent = shadowOf(pending).savedIntent
        assertEquals(ComponentName(context, WidgetPinnedReceiver::class.java), intent.component)
        assertEquals(WidgetPinning.ACTION_PINNED, intent.action)
        assertTrue(pending.isImmutable)
        val receiver = context.packageManager.getReceiverInfo(requireNotNull(intent.component), 0)
        assertTrue(receiver.enabled); assertFalse(receiver.exported)
        // Robolectric's sendBroadcast reports no registered wrappers for manifest receivers.
        // Verify the compiled manifest/PendingIntent wiring above, then exercise the OS entry
        // point directly. Actual launcher-to-receiver delivery still requires a device.
        WidgetPinnedReceiver().onReceive(context, intent)
    }
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personal_day", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowAppWidgetManager.reset()
        DeferredPinManager.accepted = true; DeferredPinManager.rejectWithException = false
        DeferredPinManager.enumerationLags = false; DeferredPinManager.callback = null
        manager = AppWidgetManager.getInstance(context); shadow = shadowOf(manager)
        shadow.setRequestPinAppWidgetSupported(true)
        WidgetRenderer.wallpaperChanged()
    }
    @Test fun unsupportedLauncherShowsManualInstructions() {
        shadow.setRequestPinAppWidgetSupported(false)
        val message = WidgetPinning.request(context, WideWidgetProvider::class.java)
        assertTrue(message.contains("不支持")); assertTrue(message.contains("安卓小部件"))
        assertNull(DeferredPinManager.callback)
    }
    @Test fun supportedLauncherCanStillRejectRequest() {
        DeferredPinManager.accepted = false
        assertTrue(WidgetPinning.request(context, WideWidgetProvider::class.java).contains("未接受"))
    }
    @Test fun trueDoesNotClaimPlacementBeforeCallback() {
        val message = WidgetPinning.request(context, WideWidgetProvider::class.java)
        assertTrue(message.contains("尚未收到")); assertTrue(WidgetRenderer.ids(context).isEmpty())
        assertNotNull(DeferredPinManager.callback)
    }
    @Test fun callbackPublishesInitialClockWithoutConfigurationActivity() {
        shadow.addInstalledProvider(info())
        WidgetPinning.request(context, WideWidgetProvider::class.java)
        shadow.addBoundWidget(101, info())
        // No fill-in widget ID: the immutable callback enumerates only our own IDs.
        deliverSuccessCallback()
        assertTrue(SettingsStore(context).pinStatus.contains("桌面已确认添加"))
        assertNotNull(shadow.getViewFor(101))
        val root = shadow.getViewFor(101)
        assertTrue(Regex("个人时钟 [0-2][0-9]:[0-5][0-9]").containsMatchIn(root.contentDescription))
        assertNotNull(root.findViewById<android.widget.ImageView>(R.id.widget_image).drawable)
    }
    @Test fun callbackWithoutVisibleBindingDoesNotClaimWidgetIsPresent() {
        WidgetPinning.request(context, WideWidgetProvider::class.java)
        deliverSuccessCallback()
        assertTrue(SettingsStore(context).pinStatus.contains("等待组件绑定"))
    }
    @Test fun platformExceptionBecomesReadableFailure() {
        DeferredPinManager.rejectWithException = true
        val message = WidgetPinning.request(context, WideWidgetProvider::class.java)
        assertTrue(message.contains("IllegalStateException")); assertTrue(message.contains("失败"))
    }
    @Test fun directWidgetUpdateWorksWhenEnumerationLags() {
        shadow.addBoundWidget(102, info())
        DeferredPinManager.enumerationLags = true
        assertTrue(WidgetRenderer.ids(context).isEmpty())
        WidgetRenderer.updateWidget(context, 102, Instant.parse("2026-09-28T04:00:00Z"))
        assertTrue(shadow.getViewFor(102).contentDescription.toString().contains("个人时钟 06:00"))
    }
    @Test fun manifestContainsBothProvidersWithOptionalConfiguration() {
        val pm = context.packageManager
        for (type in listOf(WideWidgetProvider::class.java, CompactWidgetProvider::class.java)) {
            val receiver = pm.getReceiverInfo(ComponentName(context, type), PackageManager.GET_META_DATA)
            assertTrue(receiver.enabled)
            val parser = receiver.loadXmlMetaData(pm, "android.appwidget.provider")
            assertNotNull(parser)
            parser.use {
                while (it.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
                val flags = it.getAttributeIntValue("http://schemas.android.com/apk/res/android", "widgetFeatures", 0)
                assertTrue(flags and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0)
                assertTrue(flags and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0)
            }
        }
    }
    @Test fun packagedAppDeclaresShortcutCompatibilityPermission() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        assertTrue(permissions?.contains("com.android.launcher.permission.INSTALL_SHORTCUT") == true)
    }
    @Test fun launcherAndWidgetLabelsUseNewProductName() {
        val pm = context.packageManager
        assertEquals("醒时区", pm.getApplicationLabel(pm.getApplicationInfo(context.packageName, 0)))
        assertEquals("醒时区 · 4×2", pm.getReceiverInfo(ComponentName(context, WideWidgetProvider::class.java), 0).loadLabel(pm))
        assertEquals("醒时区 · 2×1", pm.getReceiverInfo(ComponentName(context, CompactWidgetProvider::class.java), 0).loadLabel(pm))
    }
    @Test fun androidGrantDoesNotClaimXiaomiSettingIsAllowed() {
        shadowOf(context).grantPermissions(WidgetPinning.SHORTCUT_PERMISSION)
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(WidgetPinning.SHORTCUT_PERMISSION))
        val report = WidgetPinning.shortcutPermissionStatus(context)
        assertTrue(report.contains("Shortcut declaration: true; Android grant: true"))
        assertTrue(report.contains("Xiaomi home-screen permission: unknown"))
    }
    @Test fun xiaomiRequestExplainsNoDialogAndPreservesUserSettings() {
        val manufacturer = android.os.Build.MANUFACTURER
        org.robolectric.shadows.ShadowBuild.setManufacturer("Xiaomi")
        try {
            val saved = com.personalday.core.Preferences(com.personalday.core.Schedule("10:00", "02:00"),
                35, com.personalday.core.InkMode.BLACK, false)
            SettingsStore(context).save(saved)
            val message = WidgetPinning.request(context, WideWidgetProvider::class.java)
            assertTrue(message.contains("不一定显示弹窗"))
            assertTrue(message.contains("桌面快捷方式"))
            assertFalse(message.contains("请在桌面弹窗中确认"))
            assertEquals(saved, SettingsStore(context).load())
        } finally { org.robolectric.shadows.ShadowBuild.setManufacturer(manufacturer) }
    }
}
