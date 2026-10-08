package com.personalday.core

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Both calendar lines use the same instant as the personal clock, including at midnight. */
data class CalendarDisplay(val time: String, val weekdayPeriod: String, val date: String)

fun calendarDisplay(snapshot: DaySnapshot, use24Hour: Boolean, zone: ZoneId = BEIJING): CalendarDisplay {
    val local = snapshot.now.atZone(zone)
    val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[local.dayOfWeek.value - 1]
    return CalendarDisplay(
        local.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm", Locale.ROOT)),
        weekday + if (local.hour < 12) "上午" else "下午",
        local.format(DateTimeFormatter.ofPattern("MM/dd", Locale.ROOT)),
    )
}
