package com.personalday.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

object WidgetPinning {
    const val ACTION_PINNED = "com.personalday.android.WIDGET_PINNED"
    const val SHORTCUT_PERMISSION = "com.android.launcher.permission.INSTALL_SHORTCUT"
    const val MANUAL_HELP = "请长按桌面 → 添加小部件，在安卓小部件列表中查找“醒时区”。小米的小部件中心和安卓小部件列表是不同入口；入口名称随桌面版本变化。"
    const val XIAOMI_HELP = "小米桌面可能直接添加组件，不显示确认框。若点击添加后没有新增，请打开醒时区的系统应用信息 → 其他权限，检查“桌面快捷方式”（也可能叫“创建桌面快捷方式”），允许后返回本页，再点一次添加。入口名称随系统版本变化。"

    private fun launcherPackage(context: Context): String? = runCatching {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    fun isXiaomi(context: Context): Boolean =
        listOf("Xiaomi", "Redmi", "POCO").any { it.equals(Build.MANUFACTURER, ignoreCase = true) } ||
            launcherPackage(context) == "com.miui.home"

    fun help(context: Context): String =
        if (isXiaomi(context)) "$XIAOMI_HELP\n\n$MANUAL_HELP" else MANUAL_HELP

    fun shortcutPermissionStatus(context: Context): String {
        val declared = runCatching {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.contains(SHORTCUT_PERMISSION) == true
        }.getOrNull()
        val androidGrant = runCatching { context.checkSelfPermission(SHORTCUT_PERMISSION) == PackageManager.PERMISSION_GRANTED }.getOrNull()
        // Android's normal permission check cannot establish Xiaomi's separate user setting.
        return "Shortcut declaration: $declared; Android grant: $androidGrant\n" +
            "Xiaomi home-screen permission: unknown (check system settings manually)"
    }

    fun request(context: Context, provider: Class<*>): String {
        val store = SettingsStore(context)
        try {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) {
                store.pinStatus = "当前桌面不支持应用内添加。$MANUAL_HELP"
                return store.pinStatus
            }
            // Immutable, explicit callback: scan our bound IDs instead of depending on
            // launcher fill-in extras, which an immutable PendingIntent will ignore.
            val callback = PendingIntent.getBroadcast(context, provider.name.hashCode(),
                Intent(context, WidgetPinnedReceiver::class.java).setAction(ACTION_PINNED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            store.pinStatus = "已向桌面提交请求，尚未收到添加成功的确认。桌面可能直接添加，不一定显示弹窗。" +
                if (isXiaomi(context)) "若没有新增，请检查“桌面快捷方式”权限后重试。" else "如出现确认框，请确认添加。"
            val accepted = manager.requestPinAppWidget(ComponentName(context, provider), null, callback)
            if (!accepted) store.pinStatus = "桌面未接受添加请求。请尝试手动添加，或查看添加帮助。"
        } catch (error: RuntimeException) {
            store.pinStatus = "添加请求失败（${error.javaClass.simpleName}）。请尝试手动添加，或复制诊断信息。"
            android.util.Log.w("PersonalDay", "Widget pin request failed", error)
        }
        // true only means the launcher can handle the request; only its callback is confirmation.
        return store.pinStatus
    }

    fun confirm(context: Context) {
        val ids = WidgetRenderer.ids(context)
        SettingsStore(context).pinStatus = if (ids.isEmpty())
            "桌面已确认添加，等待组件绑定；可返回应用再刷新一次。"
        else "桌面已确认添加；当前共 ${ids.size} 个醒时区组件。"
        ids.forEach { WidgetRenderer.updateWidget(context, it) }
        RefreshControl.reconcile(context)
    }

    fun discovery(context: Context): String = runCatching {
        val registered = AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(context.packageName, Process.myUserHandle()).size
        "系统识别到 $registered 种组件 · 已绑定桌面 ${WidgetRenderer.ids(context).size} 个"
    }.getOrDefault("无法读取桌面组件识别状态，可复制诊断信息。")

    /** Local, user-copyable diagnostic; no serial number, account or installed-app inventory. */
    fun diagnostic(context: Context): String {
        val manager = AppWidgetManager.getInstance(context)
        val pm = context.packageManager
        val launcher = launcherPackage(context)
        val launcherVersion = runCatching { launcher?.let { pm.getPackageInfo(it, 0).versionName } }.getOrNull()
        val providers = runCatching {
            manager.getInstalledProvidersForPackage(context.packageName, Process.myUserHandle())
                .joinToString { "${it.provider.flattenToShortString()}(features=${it.widgetFeatures})" }
        }.getOrElse { "${it.javaClass.simpleName}: ${it.message}" }
        val version = runCatching { pm.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("unknown")
        return listOf("醒时区 $version", "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}", "Launcher: ${launcher ?: "unknown"} / ${launcherVersion ?: "unknown"}",
            shortcutPermissionStatus(context),
            "Pin supported: ${runCatching { manager.isRequestPinAppWidgetSupported }.getOrNull()}",
            "Providers: $providers", "Bound IDs: ${WidgetRenderer.ids(context).joinToString()}",
            "Add: ${SettingsStore(context).pinStatus}", "Refresh: ${SettingsStore(context).lastError}",
            "Refresh before opening: ${SettingsStore(context).refreshEvidence}",
            "Font: ${WidgetTypeface.NOTICE}").joinToString("\n")
    }
}

class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetPinning.ACTION_PINNED) WidgetPinning.confirm(context)
    }
}
