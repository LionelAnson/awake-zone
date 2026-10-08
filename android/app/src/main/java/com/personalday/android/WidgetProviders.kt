package com.personalday.android

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

open class DayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Use the IDs delivered by the host even if enumeration briefly lags behind binding.
        ids.forEach { WidgetRenderer.updateWidget(context, it) }
        // A host update is not a guaranteed exemption to background FGS restrictions.
        // Try recovery, catch rejection, and leave a visible paused state if prohibited.
        RefreshControl.reconcile(context)
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        WidgetRenderer.updateAll(context, force = true)
    }
    override fun onDeleted(context: Context, ids: IntArray) { RefreshControl.reconcile(context) }
    override fun onDisabled(context: Context) { RefreshControl.reconcile(context) }
}
class WideWidgetProvider : DayWidgetProvider()
class CompactWidgetProvider : DayWidgetProvider()

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            WidgetRenderer.updateAll(context, force = true)
            RefreshControl.reconcile(context)
        }
    }
}
