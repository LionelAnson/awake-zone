import Foundation

public struct PersonalDaySchedule: Equatable, Codable {
    public var wakeMinutes: Int
    public var sleepMinutes: Int

    public init(wakeMinutes: Int = 8 * 60, sleepMinutes: Int = 0) {
        self.wakeMinutes = wakeMinutes
        self.sleepMinutes = sleepMinutes
    }

    public func validate() throws {
        guard (0..<1440).contains(wakeMinutes), (0..<1440).contains(sleepMinutes) else {
            throw PersonalDayError.invalidSchedule("时间必须在 00:00—23:59 内。")
        }
        guard wakeMinutes != sleepMinutes else {
            throw PersonalDayError.invalidSchedule("起床时间与入睡时间不能相同。")
        }
    }
}

public enum PersonalDayPhase: Equatable {
    case awake
    case rest
}

public enum PersonalDayError: LocalizedError, Equatable {
    case invalidSchedule(String)

    public var errorDescription: String {
        switch self { case .invalidSchedule(let message): return message }
    }
}

public struct PersonalDaySnapshot: Equatable {
    public let now: Date
    public let phase: PersonalDayPhase
    public let regularTime: String
    public let personalTime: String
    public let percentText: String
    public let progressUnits: Int
    public let start: Date
    public let end: Date
    public let nextWake: Date
    public let weekdayPeriod: String
    public let dateText: String

    public var isRest: Bool { phase == .rest }
}

public func personalDayCalendar(timeZoneIdentifier: String = "Asia/Shanghai") -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: timeZoneIdentifier) ?? TimeZone(secondsFromGMT: 8 * 3600)!
    calendar.locale = Locale(identifier: "zh_CN")
    return calendar
}

public func calculateDay(
    schedule: PersonalDaySchedule,
    now: Date = Date(),
    timeZoneIdentifier: String = "Asia/Shanghai"
) throws -> PersonalDaySnapshot {
    try schedule.validate()
    let calendar = personalDayCalendar(timeZoneIdentifier: timeZoneIdentifier)
    let today = calendar.startOfDay(for: now)

    func boundary(dayOffset: Int, minutes: Int) -> Date {
        let day = calendar.date(byAdding: .day, value: dayOffset, to: today)!
        var components = calendar.dateComponents([.year, .month, .day], from: day)
        components.hour = minutes / 60
        components.minute = minutes % 60
        components.second = 0
        components.nanosecond = 0
        return calendar.date(from: components)!
    }

    let todayWake = boundary(dayOffset: 0, minutes: schedule.wakeMinutes)
    let startOffset = now >= todayWake ? 0 : -1
    let start = boundary(dayOffset: startOffset, minutes: schedule.wakeMinutes)
    let end = boundary(
        dayOffset: startOffset + (schedule.sleepMinutes < schedule.wakeMinutes ? 1 : 0),
        minutes: schedule.sleepMinutes
    )
    let nextWake = boundary(dayOffset: startOffset + 1, minutes: schedule.wakeMinutes)
    let durationMs = Int64((end.timeIntervalSince(start) * 1000).rounded())
    guard durationMs > 0 else {
        throw PersonalDayError.invalidSchedule("该时区的日期变化使清醒时段无效，请调整作息。")
    }

    let nowMs = Int64((now.timeIntervalSince1970 * 1000).rounded())
    let startMs = Int64((start.timeIntervalSince1970 * 1000).rounded())
    let rest = now >= end
    let elapsedMs = max(0, min(durationMs, nowMs - startMs))
    let personalMinutes = rest ? 1440 : min(1439, Int(elapsedMs * 1440 / durationMs))
    let progressUnits = rest ? 10000 : min(9999, Int(elapsedMs * 10000 / durationMs))
    let components = calendar.dateComponents([.hour, .minute, .weekday, .month, .day], from: now)
    let hour = components.hour ?? 0
    let minute = components.minute ?? 0
    let weekdayNames = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"]
    let weekday = weekdayNames[max(1, min(7, components.weekday ?? 1)) - 1]
    let period = hour < 12 ? "上午" : "下午"

    return PersonalDaySnapshot(
        now: now,
        phase: rest ? .rest : .awake,
        regularTime: String(format: "%02d:%02d", hour, minute),
        personalTime: String(format: "%02d:%02d", personalMinutes / 60, personalMinutes % 60),
        percentText: String(format: "%d.%02d%%", progressUnits / 100, progressUnits % 100),
        progressUnits: progressUnits,
        start: start,
        end: end,
        nextWake: nextWake,
        weekdayPeriod: weekday + period,
        dateText: String(format: "%02d/%02d", components.month ?? 1, components.day ?? 1)
    )
}
