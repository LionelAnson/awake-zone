package com.personalday.core

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CalendarDisplayTest {
    @Test fun referenceThursdayAfternoonAndSystemTimeFormat() {
        val snapshot = calculateDay(Schedule(), Instant.parse("2026-10-01T05:05:00Z"))
        assertEquals(CalendarDisplay("1:05", "周四下午", "10/01"), calendarDisplay(snapshot, false))
        assertEquals("13:05", calendarDisplay(snapshot, true).time)
    }
    @Test fun calendarRollsOverWhilePersonalDayStaysAtRest() {
        val before = calculateDay(Schedule(), Instant.parse("2026-12-31T15:59:59.999Z"))
        val after = calculateDay(Schedule(), Instant.parse("2026-12-31T16:00:00Z"))
        assertEquals("12/31", calendarDisplay(before, false).date)
        assertEquals(CalendarDisplay("12:00", "周五上午", "01/01"), calendarDisplay(after, false))
        assertEquals("24:00", after.personalTime)
        assertEquals("100.00%", after.percentText)
    }
    @Test fun noonAndClockCorrectionsAreRecomputedFromSnapshot() {
        fun display(time: String) = calendarDisplay(calculateDay(Schedule(), Instant.parse(time)), false)
        assertEquals("周四上午", display("2026-10-01T03:59:00Z").weekdayPeriod)
        assertEquals("周四下午", display("2026-10-01T04:00:00Z").weekdayPeriod)
        assertEquals("周四上午", display("2026-10-01T03:59:00Z").weekdayPeriod)
    }
}
