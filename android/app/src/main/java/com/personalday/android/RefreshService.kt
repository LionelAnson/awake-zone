package com.personalday.android

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import com.personalday.core.*
import java.time.Instant

object RefreshControl {
    const val CHANNEL = "clock_refresh"
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "时钟刷新", NotificationManager.IMPORTANCE_LOW).apply {
            description = "保持醒时区小组件更新；熄屏或锁屏时暂停计算"
            setSound(null, null); enableVibration(false); setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun reconcile(context: Context) {
        val store = SettingsStore(context)
        if (!store.load().refreshEnabled || WidgetRenderer.ids(context).isEmpty()) {
            context.stopService(Intent(context, RefreshService::class.java))
            WidgetRenderer.updateAll(context, force = true)
            return
        }
        ensureChannel(context)
        if (!notificationsAllowed(context)) {
            store.lastError = "通知未开启，持续刷新已暂停。请打开通知后重新启用。"
            context.stopService(Intent(context, RefreshService::class.java))
            WidgetRenderer.updateAll(context, force = true)
            return
        }
        try {
            context.startForegroundService(Intent(context, RefreshService::class.java))
        } catch (error: RuntimeException) {
            store.lastError = "系统暂未允许启动刷新（${error.javaClass.simpleName}），请打开应用重试。"
            WidgetRenderer.updateAll(context, force = true)
            android.util.Log.w("PersonalDay", "Refresh start rejected", error)
        }
    }
}

class RefreshService : Service() {
    companion object { var running = false; private set }
    private val handler = Handler(Looper.getMainLooper())
    private var tickTask: Runnable? = null
    private lateinit var loop: RefreshLoop
    private var registered = false
    private var wallpaperRegistered = false
    private val screenRecheck = Runnable { loop.reconcile(unlockRechecks = 8) }
    private val wallpaperListener = WallpaperManager.OnColorsChangedListener { _, _ ->
        WidgetRenderer.wallpaperChanged()
        if (environment().shouldTick) WidgetRenderer.updateAll(this, force = true)
    }
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // SCREEN_OFF can arrive just before PowerManager changes. Cancel immediately.
            handler.removeCallbacks(screenRecheck)
            if (intent.action == Intent.ACTION_SCREEN_OFF) loop.stop() else {
                loop.reconcile(unlockRechecks = 8)
                if (intent.action == Intent.ACTION_SCREEN_ON) handler.postDelayed(screenRecheck, 250)
            }
        }
    }
    private fun environment() = RefreshEnvironment(
        SettingsStore(this).load().refreshEnabled, WidgetRenderer.ids(this).size,
        getSystemService(PowerManager::class.java).isInteractive,
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked,
    )
    override fun onCreate() {
        super.onCreate()
        loop = RefreshLoop(object : TickScheduler {
            override fun cancel() { tickTask?.let { handler.removeCallbacks(it) }; tickTask = null }
            override fun after(milliseconds: Long, task: () -> Unit) {
                cancel(); tickTask = Runnable { task() }; handler.postDelayed(tickTask!!, milliseconds)
            }
        }, { environment() }, { Instant.now() }) { now ->
            if (!RefreshControl.notificationsAllowed(this)) {
                SettingsStore(this).lastError = "通知已关闭，刷新暂停。"
                stopSelf()
            } else WidgetRenderer.updateAll(this, now)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK) // Recovery while this service is alive; no wakeup alarm.
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(changes, filter)
        registered = true
        wallpaperRegistered = runCatching {
            getSystemService(WallpaperManager::class.java).addOnColorsChangedListener(wallpaperListener, handler); true
        }.getOrDefault(false)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        RefreshControl.ensureChannel(this)
        val settings = Intent(this, SettingsActivity::class.java)
        val notification = Notification.Builder(this, RefreshControl.CHANNEL)
            .setSmallIcon(R.drawable.ic_clock).setContentTitle("醒时区正在刷新")
            .setContentText("熄屏或锁屏时暂停 · 点击设置")
            .setContentIntent(PendingIntent.getActivity(this, 0, settings, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(1, notification)
        } catch (error: RuntimeException) {
            SettingsStore(this).lastError = "刷新服务启动失败：${error.javaClass.simpleName}"
            stopSelf(); return START_NOT_STICKY
        }
        if (!SettingsStore(this).load().refreshEnabled || WidgetRenderer.ids(this).isEmpty() || !RefreshControl.notificationsAllowed(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        running = true
        SettingsStore(this).lastError = ""
        WidgetRenderer.wallpaperChanged()
        // Initial/recovery update is immediate even when currently locked; no continuing locked loop.
        WidgetRenderer.updateAll(this, force = true)
        loop.reconcile(unlockRechecks = 8)
        return START_STICKY
    }
    override fun onDestroy() {
        running = false
        loop.stop()
        handler.removeCallbacks(screenRecheck)
        if (registered) unregisterReceiver(changes)
        if (wallpaperRegistered) getSystemService(WallpaperManager::class.java).removeOnColorsChangedListener(wallpaperListener)
        stopForeground(STOP_FOREGROUND_REMOVE)
        WidgetRenderer.updateAll(this, force = true)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
