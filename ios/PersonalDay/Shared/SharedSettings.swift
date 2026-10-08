import Foundation

public enum PersonalDaySettings {
    public static let appGroup = "group.com.personalday.widget"

    public static var store: UserDefaults {
        UserDefaults(suiteName: appGroup) ?? .standard
    }

    public static var schedule: PersonalDaySchedule {
        PersonalDaySchedule(
            wakeMinutes: store.object(forKey: "wakeMinutes") as? Int ?? 8 * 60,
            sleepMinutes: store.object(forKey: "sleepMinutes") as? Int ?? 0
        )
    }

    public static func save(schedule: PersonalDaySchedule) {
        store.set(schedule.wakeMinutes, forKey: "wakeMinutes")
        store.set(schedule.sleepMinutes, forKey: "sleepMinutes")
    }
}
