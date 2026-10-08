package com.personalday.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")
data class Schedule(val wake: String = "08:00", val sleep: String = "00:00") {
    fun validate() {
        require(parseTime(wake) != parseTime(sleep)) { "起床时间与入睡时间不能相同。" }
    }
}

fun parseTime(value: String): Int {
    require(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$").matches(value)) { "请输入有效的时刻（HH:mm）。" }
    return value.substring(0, 2).toInt() * 60 + value.substring(3).toInt()
}

enum class Phase { AWAKE, REST }
data class DaySnapshot(
    val phase: Phase, val now: Instant, val start: Instant, val end: Instant, val nextWake: Instant,
    val durationMs: Long, val remainingMs: Long, val progress: Double,
    val personalMinutes: Int, val progressUnits: Int, val regularTime: String,
    val personalTime: String, val percentText: String, val nextWakeText: String,
)

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val nextFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)

/** One instant, calendar boundaries, then elapsed timestamps. Never a ticking counter. */
fun calculateDay(schedule: Schedule, now: Instant = Instant.now(), zone: ZoneId = BEIJING): DaySnapshot {
    schedule.validate()
    val wake = parseTime(schedule.wake)
    val sleep = parseTime(schedule.sleep)
    val date = now.atZone(zone).toLocalDate()
    fun boundary(day: LocalDate, minutes: Int): Instant = day
        .atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zone).toInstant()
    // atZone moves gaps forward and chooses the earlier offset in an overlap,
    // matching Temporal's compatible disambiguation in the desktop core.
    val startDate = if (now >= boundary(date, wake)) date else date.minusDays(1)
    val start = boundary(startDate, wake)
    val end = boundary(if (sleep < wake) startDate.plusDays(1) else startDate, sleep)
    val nextWake = boundary(startDate.plusDays(1), wake)
    val duration = end.toEpochMilli() - start.toEpochMilli()
    require(duration > 0) { "该日期的夏令时变化使清醒时段无效，请调整作息。" }
    val rest = now >= end
    val elapsed = (now.toEpochMilli() - start.toEpochMilli()).coerceIn(0, duration)
    // Integer division prevents floating point rounding from reaching the next display unit early.
    val personal = if (rest) 1440 else (elapsed * 1440 / duration).toInt().coerceAtMost(1439)
    val units = if (rest) 10000 else (elapsed * 10000 / duration).toInt().coerceAtMost(9999)
    return DaySnapshot(
        if (rest) Phase.REST else Phase.AWAKE, now, start, end, nextWake, duration,
        (end.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0),
        if (rest) 1.0 else elapsed.toDouble() / duration,
        personal, units, clockFormat.format(now.atZone(zone)),
        String.format(Locale.ROOT, "%02d:%02d", personal / 60, personal % 60),
        String.format(Locale.ROOT, "%d.%02d%%", units / 100, units % 100),
        nextFormat.format(nextWake.atZone(zone)),
    )
}
