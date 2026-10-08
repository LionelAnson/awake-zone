package com.personalday.core

import java.time.Instant

data class RefreshEnvironment(val enabled: Boolean, val widgetCount: Int, val interactive: Boolean, val unlocked: Boolean) {
    val shouldTick: Boolean get() = enabled && widgetCount > 0 && interactive && unlocked
}
interface TickScheduler {
    fun cancel()
    fun after(milliseconds: Long, task: () -> Unit)
}

/** Owns exactly one pending callback, including after repeated starts or screen broadcasts. */
class RefreshLoop(
    private val scheduler: TickScheduler,
    private val environment: () -> RefreshEnvironment,
    private val now: () -> Instant,
    private val render: (Instant) -> Unit,
) {
    private var stopped = true
    private var generation = 0
    fun reconcile(unlockRechecks: Int = 0) {
        generation++
        scheduler.cancel()
        stopped = false
        tick(generation, unlockRechecks.coerceIn(0, 8))
    }
    fun stop() { stopped = true; generation++; scheduler.cancel() }
    private fun tick(token: Int, unlockRechecks: Int = 0) {
        if (stopped || token != generation) return
        val state = environment()
        if (!state.shouldTick) {
            // Unlock broadcasts can precede Keyguard's state change. Retry for at most 2 s,
            // without drawing while locked or keeping a periodic locked-screen loop.
            if (state.enabled && state.widgetCount > 0 && state.interactive && unlockRechecks > 0) {
                scheduler.after(250) { tick(token, unlockRechecks - 1) }
            } else stop()
            return
        }
        render(now())
        // Rendering may stop/restart the loop. Never resurrect that old generation.
        if (stopped || token != generation) return
        val delay = 1000L - Math.floorMod(now().toEpochMilli(), 1000L)
        scheduler.after(delay) { tick(token) }
    }
}

data class DisplayContent(val regular: String, val personal: String, val percent: String,
                          val units: Int, val status: String, val opacity: Int, val ink: InkMode,
                          val compactStatus: String = status, val backgroundRgb: Int = 0xD8D8D8,
                          val weekdayPeriod: String = "", val date: String = "")
fun displayContent(snapshot: DaySnapshot, preferences: Preferences, running: Boolean, use24Hour: Boolean = true): DisplayContent {
    val rest = if (snapshot.phase == Phase.REST) "上一清醒日已结束\n下次起床 ${snapshot.nextWakeText}" else ""
    val status = listOf(rest, if (running) "" else "刷新已暂停 · 点击设置").filter { it.isNotEmpty() }.joinToString("\n")
    val compactRest = if (snapshot.phase == Phase.REST) "已结束 · 下次${snapshot.nextWakeText}" else ""
    val compactStatus = listOf(compactRest, if (running) "" else "已暂停").filter { it.isNotEmpty() }.joinToString("\n")
    val calendar = calendarDisplay(snapshot, use24Hour)
    return DisplayContent(calendar.time, snapshot.personalTime, snapshot.percentText,
        snapshot.progressUnits, status, preferences.opacity, preferences.ink, compactStatus, preferences.backgroundRgb,
        calendar.weekdayPeriod, calendar.date)
}

/** Compare actual display data, not now/progress Double, which change every second. */
class RenderCache {
    private val previous = mutableMapOf<Int, Any>()
    fun changed(id: Int, content: Any): Boolean = previous[id] != content
    fun committed(id: Int, content: Any) { previous[id] = content }
    fun retain(ids: Set<Int>) { previous.keys.retainAll(ids) }
    fun clear() { previous.clear() }
}
