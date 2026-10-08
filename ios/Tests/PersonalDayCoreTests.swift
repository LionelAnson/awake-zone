import XCTest
@testable import PersonalDay

final class PersonalDayCoreTests: XCTestCase {
    private let zone = "Asia/Shanghai"

    func testDefaultScheduleMatchesReferencePoints() throws {
        let schedule = PersonalDaySchedule()
        let values: [(String, String, Int)] = [
            ("2026-10-08T08:00:00+08:00", "00:00", 0),
            ("2026-10-08T12:00:00+08:00", "06:00", 2500),
            ("2026-10-08T14:00:00+08:00", "09:00", 3750),
            ("2026-10-08T16:00:00+08:00", "12:00", 5000),
            ("2026-10-08T20:00:00+08:00", "18:00", 7500),
            ("2026-10-09T00:00:00+08:00", "24:00", 10000),
            ("2026-10-09T08:00:00+08:00", "00:00", 0)
        ]
        for (iso, personal, units) in values {
            let snapshot = try calculateDay(schedule: schedule, now: ISO8601DateFormatter().date(from: iso)!, timeZoneIdentifier: zone)
            XCTAssertEqual(snapshot.personalTime, personal)
            XCTAssertEqual(snapshot.progressUnits, units)
        }
    }

    func testCustomScheduleDoesNotResetAtMidnight() throws {
        let schedule = PersonalDaySchedule(wakeMinutes: 10 * 60, sleepMinutes: 2 * 60)
        let now = ISO8601DateFormatter().date(from: "2026-10-09T00:00:00+08:00")!
        let snapshot = try calculateDay(schedule: schedule, now: now, timeZoneIdentifier: zone)
        XCTAssertEqual(snapshot.personalTime, "21:00")
        XCTAssertEqual(snapshot.percentText, "87.50%")
    }

    func testEqualTimesAreRejected() {
        XCTAssertThrowsError(try PersonalDaySchedule(wakeMinutes: 8 * 60, sleepMinutes: 8 * 60).validate())
    }
}
