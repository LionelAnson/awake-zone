import SwiftUI
import WidgetKit

struct PersonalDayEntry: TimelineEntry {
    let date: Date
    let snapshot: PersonalDaySnapshot
}

struct PersonalDayProvider: TimelineProvider {
    func placeholder(in context: Context) -> PersonalDayEntry {
        PersonalDayEntry(date: Date(timeIntervalSince1970: 1_790_841_600), snapshot: previewSnapshot())
    }

    func getSnapshot(in context: Context, completion: @escaping (PersonalDayEntry) -> Void) {
        let now = Date()
        completion(entry(at: now) ?? fallbackEntry(at: now))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PersonalDayEntry>) -> Void) {
        let now = Date()
        let minute = floor(now.timeIntervalSince1970 / 60) * 60
        let generated = (0...120).compactMap { offset -> PersonalDayEntry? in
            entry(at: Date(timeIntervalSince1970: minute + Double(offset * 60)))
        }
        let entries = generated.isEmpty ? [fallbackEntry(at: now)] : generated
        completion(Timeline(entries: entries, policy: .atEnd))
    }

    private func entry(at date: Date) -> PersonalDayEntry? {
        let schedule = PersonalDaySettings.schedule
        guard let snapshot = try? calculateDay(schedule: schedule, now: date) else { return nil }
        return PersonalDayEntry(date: date, snapshot: snapshot)
    }

    private func fallbackEntry(at date: Date) -> PersonalDayEntry {
        PersonalDayEntry(date: date, snapshot: previewSnapshot())
    }

    private func previewSnapshot() -> PersonalDaySnapshot {
        (try? calculateDay(schedule: PersonalDaySchedule(), now: Date(timeIntervalSince1970: 1_790_841_600))) ??
            (try! calculateDay(schedule: PersonalDaySchedule(), now: Date()))
    }
}

struct PersonalDayWidgetView: View {
    let entry: PersonalDayEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            if family == .systemSmall {
                smallLayout
            } else {
                wideLayout
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .widgetURL(URL(string: "personalday://settings"))
    }

    private var wideLayout: some View {
        HStack(alignment: .top, spacing: 22) {
            regularColumn
            personalColumn
        }
    }

    private var smallLayout: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text("常规时间").secondaryStyle(size: 11)
            Text(entry.snapshot.regularTime).timeStyle(size: 30)
            Text(entry.snapshot.weekdayPeriod).secondaryStyle(size: 13)
            Text(entry.snapshot.dateText).secondaryStyle(size: 13)
            Spacer(minLength: 2)
            Text("个人时钟").secondaryStyle(size: 11)
            Text(entry.snapshot.personalTime).timeStyle(size: 25)
            progressBar.frame(height: 10)
            Text(entry.snapshot.percentText).secondaryStyle(size: 12)
        }
    }

    private var regularColumn: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("常规时间").secondaryStyle(size: 12)
            Text(entry.snapshot.regularTime).timeStyle(size: 40)
            Text(entry.snapshot.weekdayPeriod).secondaryStyle(size: 16)
            Text(entry.snapshot.dateText).secondaryStyle(size: 16)
        }
    }

    private var personalColumn: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("个人时钟").secondaryStyle(size: 12)
            Text(entry.snapshot.personalTime).timeStyle(size: 40)
            progressBar.frame(height: 14)
            Text(entry.snapshot.percentText).secondaryStyle(size: 16)
            if entry.snapshot.isRest {
                Text("清醒日已结束").secondaryStyle(size: 11)
            }
        }
    }

    private var progressBar: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(Color.primary.opacity(0.16))
                Capsule().fill(Color.primary.opacity(0.72))
                    .frame(width: width * CGFloat(entry.snapshot.progressUnits) / 10000)
                ForEach([0.25, 0.50, 0.75], id: \.self) { mark in
                    Rectangle().fill(Color.primary.opacity(0.55)).frame(width: 1, height: 8)
                        .position(x: width * mark, y: proxy.size.height / 2)
                }
            }
        }
    }
}

private extension Text {
    func timeStyle(size: CGFloat) -> some View {
        font(.system(size: size, weight: .thin, design: .default)).monospacedDigit().foregroundStyle(.primary)
    }

    func secondaryStyle(size: CGFloat) -> some View {
        font(.system(size: size, weight: .light, design: .default)).foregroundStyle(.primary.opacity(0.5))
    }
}

@main
struct PersonalDayWidgetBundle: WidgetBundle {
    var body: some Widget { PersonalDayWidget() }
}

struct PersonalDayWidget: Widget {
    let kind = "PersonalDayWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PersonalDayProvider()) { entry in
            PersonalDayWidgetView(entry: entry)
        }
        .configurationDisplayName("醒时区")
        .description("北京时间、个人时钟和计划清醒日进度。")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
