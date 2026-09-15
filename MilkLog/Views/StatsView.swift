import SwiftUI
import UIKit
import Charts

struct StatsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var range: StatsRange = .day
    @State private var editing: FeedRecord?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("统计范围", selection: $range) {
                        ForEach(StatsRange.allCases) { item in
                            Text(item.title).tag(item)
                        }
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                }

                Section {
                    summaryGrid
                }

                Section(range.chartTitle) {
                    chart
                        .frame(height: 230)
                        .padding(.vertical, 8)
                }

                ForEach(dayGroups) { group in
                    Section(DateText.dayLabel(group.date)) {
                        ForEach(group.records) { record in
                            Button {
                                editing = record
                            } label: {
                                RecordRow(record: record)
                            }
                            .buttonStyle(.plain)
                            .swipeActions {
                                Button(role: .destructive) {
                                    store.delete(recordID: record.id)
                                } label: {
                                    Label("删除", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("奶量统计")
            .sheet(item: $editing) { record in
                RecordEditView(record: record, isNew: false)
            }
        }
    }

    // MARK: - 数据

    private var buckets: [StatBucket] { range.buckets(feeds: store.feeds) }

    private var activeBuckets: [StatBucket] { buckets.filter { $0.count > 0 } }

    private var totalML: Double { buckets.reduce(0) { $0 + $1.totalML } }

    private var totalCount: Int { buckets.reduce(0) { $0 + $1.count } }

    private var averagePerBucket: Double {
        guard !activeBuckets.isEmpty else { return 0 }
        return activeBuckets.reduce(0) { $0 + $1.totalML } / Double(activeBuckets.count)
    }

    private var averagePerFeed: Double {
        totalCount > 0 ? totalML / Double(totalCount) : 0
    }

    private var bestBucket: StatBucket? {
        activeBuckets.max { $0.totalML < $1.totalML }
    }

    private var axisFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = range.axisFormat
        return formatter
    }

    private struct DayGroup: Identifiable {
        var id: Date { date }
        let date: Date
        let records: [FeedRecord]
    }

    private var dayGroups: [DayGroup] {
        let start = range.spanStart()
        let items = store.feeds.filter { $0.date >= start }
        let grouped = Dictionary(grouping: items) { DateText.dayStart($0.date) }
        return grouped.keys.sorted(by: >).map { day in
            DayGroup(date: day, records: (grouped[day] ?? []).sorted { $0.date > $1.date })
        }
    }

    // MARK: - 视图

    private var summaryGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            metric(title: range.averageTitle, value: volumeString(averagePerBucket), unit: "ml")
            metric(title: "平均每次", value: volumeString(averagePerFeed), unit: "ml")
            metric(title: "记录次数", value: "\(totalCount)", unit: "次")
            metric(title: range.peakTitle, value: bestBucket.map { volumeString($0.totalML) } ?? "--", unit: "ml")
        }
        .padding(.vertical, 6)
    }

    private func metric(title: String, value: String, unit: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.title3.weight(.bold))
                    .monospacedDigit()
                Text(unit)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var chart: some View {
        Chart {
            ForEach(buckets) { bucket in
                AreaMark(
                    x: .value("时间", bucket.start),
                    y: .value("奶量", bucket.totalML)
                )
                .foregroundStyle(
                    LinearGradient(colors: [Color.accentColor.opacity(0.35), Color.accentColor.opacity(0.03)],
                                   startPoint: .top,
                                   endPoint: .bottom)
                )
                .interpolationMethod(.catmullRom)
            }

            ForEach(buckets) { bucket in
                LineMark(
                    x: .value("时间", bucket.start),
                    y: .value("奶量", bucket.totalML)
                )
                .foregroundStyle(Color.accentColor)
                .interpolationMethod(.catmullRom)
                .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
            }

            ForEach(buckets) { bucket in
                PointMark(
                    x: .value("时间", bucket.start),
                    y: .value("奶量", bucket.totalML)
                )
                .foregroundStyle(bucket.totalML > 0 ? Color.accentColor : Color.secondary.opacity(0.5))
                .symbolSize(bucket.totalML > 0 ? 34 : 12)
            }

            if range == .day, store.settings.dailyTargetML > 0 {
                RuleMark(y: .value("目标", store.settings.dailyTargetML))
                    .foregroundStyle(.orange.opacity(0.8))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 4]))
                    .annotation(position: .top, alignment: .leading) {
                        Text("目标 \(volumeString(store.settings.dailyTargetML)) ml")
                            .font(.caption2)
                            .foregroundStyle(.orange)
                    }
            }
        }
        .chartYAxis {
            AxisMarks(position: .leading)
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { value in
                AxisGridLine()
                AxisTick()
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        Text(axisFormatter.string(from: date))
                    }
                }
            }
        }
    }
}

struct RecordRow: View {
    @EnvironmentObject private var store: AppStore
    let record: FeedRecord
    @State private var thumbnail: UIImage?

    var body: some View {
        HStack(spacing: 12) {
            if let thumbnail {
                Image(uiImage: thumbnail)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Color.accentColor.opacity(0.12))
                        .frame(width: 44, height: 44)
                    Image(systemName: record.source.iconName)
                        .foregroundStyle(Color.accentColor)
                }
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(DateText.time(record.date))
                    .font(.subheadline.weight(.medium))
                    .monospacedDigit()
                if !record.note.isEmpty {
                    Text(record.note)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(record.volumeText)
                    .font(.headline)
                    .monospacedDigit()
                Text("ml")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
        .task(id: record.photoName) {
            guard let name = record.photoName else { return }
            thumbnail = store.photo(named: name)
        }
    }
}