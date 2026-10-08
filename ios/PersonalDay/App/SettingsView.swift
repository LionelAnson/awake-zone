import SwiftUI
import WidgetKit

struct SettingsView: View {
    @State private var wakeMinutes: Int
    @State private var sleepMinutes: Int
    @State private var message = ""
    private let calendar = personalDayCalendar()

    init() {
        let schedule = PersonalDaySettings.schedule
        _wakeMinutes = State(initialValue: schedule.wakeMinutes)
        _sleepMinutes = State(initialValue: schedule.sleepMinutes)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("计划作息") {
                    DatePicker("计划起床", selection: binding(for: $wakeMinutes), displayedComponents: .hourAndMinute)
                    DatePicker("计划入睡", selection: binding(for: $sleepMinutes), displayedComponents: .hourAndMinute)
                    Text("默认 08:00—24:00（次日 00:00）。个人时间按这段计划清醒时长等比例换算。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section {
                    Button("保存并更新小组件") { save() }
                    if !message.isEmpty { Text(message).font(.footnote).foregroundStyle(.secondary) }
                }
            }
            .navigationTitle("醒时区")
        }
        .environment(\.calendar, calendar)
        .environment(\.timeZone, calendar.timeZone)
    }

    private func binding(for value: Binding<Int>) -> Binding<Date> {
        Binding(
            get: { date(for: value.wrappedValue) },
            set: { value.wrappedValue = minutes(from: $0) }
        )
    }

    private func date(for minutes: Int) -> Date {
        var components = calendar.dateComponents([.year, .month, .day], from: Date())
        components.hour = minutes / 60
        components.minute = minutes % 60
        return calendar.date(from: components) ?? Date()
    }

    private func minutes(from date: Date) -> Int {
        let components = calendar.dateComponents([.hour, .minute], from: date)
        return (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }

    private func save() {
        let schedule = PersonalDaySchedule(wakeMinutes: wakeMinutes, sleepMinutes: sleepMinutes)
        do {
            try schedule.validate()
            PersonalDaySettings.save(schedule: schedule)
            WidgetCenter.shared.reloadAllTimelines()
            message = "已保存，组件会按系统调度刷新。"
        } catch {
            message = error.localizedDescription
        }
    }
}
