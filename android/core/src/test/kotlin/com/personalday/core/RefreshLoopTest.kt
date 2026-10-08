package com.personalday.core

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RefreshLoopTest {
    private class Scheduler : TickScheduler {
        var pending: (() -> Unit)? = null
        override fun cancel() { pending = null }
        var delay = 0L
        override fun after(milliseconds: Long, task: () -> Unit) { delay = milliseconds; pending = task }
        fun fire() { val callback = pending; pending = null; callback?.invoke() }
    }
    @Test fun singleLoopPausesAndRecoversWithFreshTime() {
        val scheduler = Scheduler(); var state = RefreshEnvironment(true, 2, true, true)
        var now = Instant.parse("2026-09-28T04:00:00Z"); val times = mutableListOf<Instant>()
        val loop = RefreshLoop(scheduler, { state }, { now }, { times.add(it) })
        loop.reconcile(); loop.reconcile(); assertNotNull(scheduler.pending)
        val before = times.size; scheduler.fire(); assertEquals(before + 1, times.size)
        state = state.copy(interactive = false); loop.reconcile(); assertNull(scheduler.pending)
        now = now.plusSeconds(1800); state = state.copy(interactive = true, unlocked = false)
        loop.reconcile(); assertNull(scheduler.pending)
        state = state.copy(unlocked = true); loop.reconcile(); assertEquals(now, times.last())
        state = state.copy(enabled = false); loop.reconcile(); assertNull(scheduler.pending)
        state = state.copy(enabled = true, widgetCount = 0); loop.reconcile(); assertNull(scheduler.pending)
    }
    @Test fun staleCallbacksCannotCreateAnotherLoop() {
        val scheduler = Scheduler(); var calls = 0
        val loop = RefreshLoop(scheduler, { RefreshEnvironment(true, 1, true, true) }, { Instant.now() }, { calls++ })
        loop.reconcile(); val old = scheduler.pending!!; loop.reconcile()
        val before = calls; old(); assertEquals(before, calls)
        loop.stop(); scheduler.fire(); assertEquals(before, calls)
    }
    @Test fun unlockRaceRecoversWithoutAnotherBroadcastAndRetriesAreBounded() {
        val scheduler = Scheduler(); var state = RefreshEnvironment(true, 1, true, false)
        var calls = 0
        val loop = RefreshLoop(scheduler, { state }, { Instant.EPOCH }, { calls++ })
        loop.reconcile(unlockRechecks = 8)
        repeat(3) { assertEquals(250L, scheduler.delay); scheduler.fire() }
        assertEquals(0, calls)
        state = state.copy(unlocked = true)
        scheduler.fire(); assertEquals(1, calls); assertEquals(1000L, scheduler.delay)
        state = state.copy(unlocked = false)
        loop.reconcile(unlockRechecks = 8)
        repeat(8) { scheduler.fire() }
        assertNull(scheduler.pending)
        loop.reconcile(unlockRechecks = 8)
        state = state.copy(interactive = false)
        scheduler.fire(); assertNull(scheduler.pending)
    }
    @Test fun nextTickAlignsAfterRenderingAndReadsFreshTimeAfterDelay() {
        val scheduler = Scheduler()
        var now = Instant.parse("2026-10-01T08:49:59.750Z")
        val seen = mutableListOf<Instant>()
        val loop = RefreshLoop(scheduler, { RefreshEnvironment(true, 1, true, true) }, { now }) {
            seen.add(it); now = now.plusMillis(100)
        }
        loop.reconcile(); assertEquals(150L, scheduler.delay)
        now = Instant.parse("2026-10-01T09:20:00Z")
        scheduler.fire(); assertEquals(Instant.parse("2026-10-01T09:20:00Z"), seen.last())
        assertEquals(900L, scheduler.delay)
    }
    @Test fun stopDuringRenderDoesNotLeaveAPendingCallback() {
        val scheduler = Scheduler()
        lateinit var loop: RefreshLoop
        loop = RefreshLoop(scheduler, { RefreshEnvironment(true, 1, true, true) }, { Instant.EPOCH }) { loop.stop() }
        loop.reconcile(); assertNull(scheduler.pending)
    }
    @Test fun onlyVisibleChangesTriggerUpdates() {
        val prefs = Preferences(); val start = Instant.parse("2026-09-28T00:00:00Z")
        fun content(seconds: Long) = displayContent(calculateDay(prefs.schedule, start.plusSeconds(seconds)), prefs, true)
        val cache = RenderCache(); val first = content(0)
        assertTrue(cache.changed(1, first)); cache.committed(1, first)
        assertFalse(cache.changed(1, content(1))); assertTrue(cache.changed(1, content(6)))
        assertTrue(cache.changed(2, first))
        assertTrue(cache.changed(1, first.copy(status = "刷新已暂停 · 点击设置")))
        cache.retain(emptySet()); assertTrue(cache.changed(1, first))
    }
}
