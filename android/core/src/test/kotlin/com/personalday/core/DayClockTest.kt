package com.personalday.core

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DayClockTest {
    private fun at(s: String) = OffsetDateTime.parse(s).toInstant()
    private fun beijing(s: String, schedule: Schedule = Schedule()) = calculateDay(schedule, at("${s}+08:00"))

    @Test fun defaultSchedule() {
        val rows = listOf(
            Triple("2026-09-28T08:00:00", "00:00", "0.00%"),
            Triple("2026-09-28T12:00:00", "06:00", "25.00%"),
            Triple("2026-09-28T14:00:00", "09:00", "37.50%"),
            Triple("2026-09-28T16:00:00", "12:00", "50.00%"),
            Triple("2026-09-28T20:00:00", "18:00", "75.00%"),
            Triple("2026-09-29T00:00:00", "24:00", "100.00%"),
            Triple("2026-09-29T07:59:59", "24:00", "100.00%"),
            Triple("2026-09-29T08:00:00", "00:00", "0.00%"),
        )
        rows.forEach { (now, personal, percent) ->
            val s = beijing(now)
            assertEquals(now, personal, s.personalTime); assertEquals(percent, s.percentText)
            assertEquals(if (personal == "24:00") Phase.REST else Phase.AWAKE, s.phase)
        }
    }
    @Test fun midnightDoesNotReset() {
        val s = beijing("2026-09-29T00:00:00", Schedule("10:00", "02:00"))
        assertEquals("21:00", s.personalTime); assertEquals("87.50%", s.percentText)
        assertEquals(at("2026-09-28T10:00:00+08:00"), s.start)
        assertEquals(7200000L, s.remainingMs)
    }
    @Test fun sameDayAndCalendarBoundaries() {
        assertEquals("50.00%", beijing("2026-09-28T14:00:00", Schedule("06:00", "22:00")).percentText)
        listOf("2027-01-01T01:00:00", "2028-03-01T01:00:00").forEach {
            val s = beijing(it, Schedule("10:00", "02:00"))
            assertEquals("22:30", s.personalTime); assertEquals("93.75%", s.percentText)
        }
    }
    @Test fun neverRoundsEarly() {
        val s = beijing("2026-09-28T23:59:59.999")
        assertEquals("23:59", s.personalTime); assertEquals("99.99%", s.percentText)
        assertEquals(1L, s.remainingMs); assertTrue(s.progress < 1)
        assertEquals("0.00%", beijing("2026-09-28T08:00:05.759").percentText)
        assertEquals("0.01%", beijing("2026-09-28T08:00:05.760").percentText)
    }
    @Test fun recomputesAfterSleepAndClockChange() {
        assertEquals("93.75%", beijing("2026-09-28T23:00:00").percentText)
        assertEquals("25.00%", beijing("2026-10-02T12:00:00").percentText)
        assertEquals("25.00%", beijing("2026-09-28T12:00:00").percentText)
    }
    @Test fun rejectsInvalidSchedules() {
        listOf(Schedule("08:00", "08:00"), Schedule("24:00", "08:00"),
            Schedule("8:00", "00:00"), Schedule("08:60", "00:00"), Schedule("aa:bb", "00:00"))
            .forEach { schedule -> assertThrows(IllegalArgumentException::class.java) { calculateDay(schedule) } }
    }
    @Test fun newYorkDstActualDuration() {
        val zone = ZoneId.of("America/New_York"); val schedule = Schedule("22:00", "06:00")
        val spring = calculateDay(schedule, at("2026-03-08T03:00:00-04:00"), zone)
        assertEquals(7 * 3600000L, spring.durationMs); assertEquals(4.0 / 7, spring.progress, 0.0)
        val first = calculateDay(schedule, at("2026-11-01T01:30:00-04:00"), zone)
        val second = calculateDay(schedule, at("2026-11-01T01:30:00-05:00"), zone)
        assertEquals(9 * 3600000L, first.durationMs); assertEquals(first.start, second.start)
        assertEquals(3.5 / 9, first.progress, 0.0); assertEquals(4.5 / 9, second.progress, 0.0)
    }
    @Test fun calendarWakeCanBe23Or25HoursApart() {
        listOf("2026-03-08T01:00:00-05:00" to 23, "2026-11-01T01:00:00-04:00" to 25).forEach { (date, h) ->
            val s = calculateDay(Schedule(), at(date), ZoneId.of("America/New_York"))
            assertEquals(h * 3600000L, s.nextWake.toEpochMilli() - s.start.toEpochMilli())
        }
    }
    @Test fun dstGapOverlapAndCollapsedSchedule() {
        val zone = ZoneId.of("America/New_York")
        val spring = calculateDay(Schedule("02:30", "10:00"), at("2026-03-08T03:30:00-04:00"), zone)
        assertEquals(0.0, spring.progress, 0.0)
        val fall = calculateDay(Schedule("01:30", "10:00"), at("2026-11-01T01:30:00-05:00"), zone)
        assertEquals(3600000L, fall.now.toEpochMilli() - fall.start.toEpochMilli())
        assertThrows(IllegalArgumentException::class.java) {
            calculateDay(Schedule("02:30", "03:00"), at("2026-03-08T04:00:00-04:00"), zone)
        }
    }
    @Test fun halfHourDstAndBeijingDefault() {
        val s = calculateDay(Schedule("22:00", "06:00"), at("2026-10-04T03:00:00+11:00"), ZoneId.of("Australia/Lord_Howe"))
        assertEquals(27000000L, s.durationMs); assertEquals(4.5 / 7.5, s.progress, 0.0)
        assertEquals("06:00", calculateDay(Schedule(), Instant.parse("2026-09-28T04:00:00Z")).personalTime)
    }
}
