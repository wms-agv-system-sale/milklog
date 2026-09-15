import Foundation

// MARK: - 记录来源

enum RecordSource: String, Codable, CaseIterable {
    case camera
    case manual

    var label: String {
        switch self {
        case .camera: return "拍照识别"
        case .manual: return "手动输入"
        }
    }

    var iconName: String {
        switch self {
        case .camera: return "camera.viewfinder"
        case .manual: return "hand.tap"
        }
    }
}

// MARK: - 一次喂奶记录

struct FeedRecord: Identifiable, Codable, Equatable, Hashable {
    var id: UUID = UUID()
    var date: Date = Date()
    var volumeML: Double = 0
    var source: RecordSource = .manual
    var note: String = ""
    var photoName: String? = nil
    var confidence: Double? = nil

    var volumeText: String {
        volumeML.rounded() == volumeML ? "\(Int(volumeML))" : String(format: "%.1f", volumeML)
    }
}

// MARK: - 奶瓶标定

/// 一个已知奶量对应画面中的液面高度（0~1，从画面顶部算起）
struct CalibrationPoint: Identifiable, Codable, Equatable, Hashable {
    var id: UUID = UUID()
    var volumeML: Double
    var y: Double
}

/// 一个奶瓶的标定信息。标定好之后，任意液面高度都能换算出奶量。
struct BottleProfile: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var name: String = "我的奶瓶"
    var points: [CalibrationPoint] = []
    /// 画面中用于分析的竖直取样条带（左右边界，0~1）
    var bandLeft: Double = 0.32
    var bandRight: Double = 0.68

    var isReady: Bool { points.count >= 2 }

    var sanitizedPoints: [CalibrationPoint] {
        points.sorted { $0.y < $1.y }
    }

    var minY: Double? { sanitizedPoints.first?.y }
    var maxY: Double? { sanitizedPoints.last?.y }

    /// 允许检测的液面范围（留出一点余量，方便识别略多/略少的情况）
    var searchRange: ClosedRange<Double> {
        guard let lo = minY, let hi = maxY, hi - lo > 0.01 else { return 0.06...0.94 }
        let margin = max(0.05, (hi - lo) * 0.35)
        return max(0.02, lo - margin)...min(0.98, hi + margin)
    }

    /// 液面高度 -> 奶量（分段线性插值，范围外按最近一段的斜率外推）
    func volume(forY y: Double) -> Double? {
        let pts = sanitizedPoints
        guard pts.count >= 2 else { return nil }

        if y <= pts[0].y {
            guard let pair = firstSpan(pts) else { return pts[0].volumeML }
            return clampVolume(pair.a.volumeML + pair.slope * (y - pair.a.y))
        }
        if let last = pts.last, y >= last.y {
            guard let pair = lastSpan(pts) else { return last.volumeML }
            return clampVolume(pair.a.volumeML + pair.slope * (y - pair.a.y))
        }
        for i in 0..<(pts.count - 1) {
            let a = pts[i]
            let b = pts[i + 1]
            guard b.y - a.y > 0.0005 else { continue }
            if y >= a.y && y <= b.y {
                let t = (y - a.y) / (b.y - a.y)
                return clampVolume(a.volumeML + t * (b.volumeML - a.volumeML))
            }
        }
        return nil
    }

    private func firstSpan(_ pts: [CalibrationPoint]) -> (a: CalibrationPoint, slope: Double)? {
        for i in 0..<(pts.count - 1) where pts[i + 1].y - pts[i].y > 0.0005 {
            let a = pts[i]
            let b = pts[i + 1]
            return (a, (b.volumeML - a.volumeML) / (b.y - a.y))
        }
        return nil
    }

    private func lastSpan(_ pts: [CalibrationPoint]) -> (a: CalibrationPoint, slope: Double)? {
        guard pts.count >= 2 else { return nil }
        for i in stride(from: pts.count - 2, through: 0, by: -1) where pts[i + 1].y - pts[i].y > 0.0005 {
            let a = pts[i]
            let b = pts[i + 1]
            return (a, (b.volumeML - a.volumeML) / (b.y - a.y))
        }
        return nil
    }

    private func clampVolume(_ value: Double) -> Double {
        let maxVolume = (points.map(\.volumeML).max() ?? 300) * 1.15 + 10
        return min(max(0, value), maxVolume)
    }
}

// MARK: - 设置

struct AppSettings: Codable, Equatable {
    /// 每日目标奶量（ml），只用于图表参考线
    var dailyTargetML: Double = 600
    /// 是否保存每次识别的照片
    var keepPhotos: Bool = true
}

// MARK: - 统计

enum StatsRange: String, CaseIterable, Identifiable, Codable {
    case day
    case week
    case month

    var id: String { rawValue }

    var title: String {
        switch self {
        case .day: return "日"
        case .week: return "周"
        case .month: return "月"
        }
    }

    var chartTitle: String {
        switch self {
        case .day: return "最近 14 天每日奶量"
        case .week: return "最近 8 周每周奶量"
        case .month: return "最近 6 个月每月奶量"
        }
    }

    var bucketTitle: String {
        switch self {
        case .day: return "每天"
        case .week: return "每周"
        case .month: return "每月"
        }
    }

    var averageTitle: String {
        switch self {
        case .day: return "平均每天"
        case .week: return "平均每周"
        case .month: return "平均每月"
        }
    }

    var peakTitle: String {
        switch self {
        case .day: return "单日最高"
        case .week: return "单周最高"
        case .month: return "单月最高"
        }
    }

    var bucketCount: Int {
        switch self {
        case .day: return 14
        case .week: return 8
        case .month: return 6
        }
    }

    var component: Calendar.Component {
        switch self {
        case .day: return .day
        case .week: return .weekOfYear
        case .month: return .month
        }
    }

    var axisFormat: String {
        switch self {
        case .day: return "M/d"
        case .week: return "M/d"
        case .month: return "yy/M"
        }
    }
}

struct StatBucket: Identifiable {
    let id = UUID()
    let start: Date
    let totalML: Double
    let count: Int

    var averagePerFeed: Double { count > 0 ? totalML / Double(count) : 0 }
}

extension StatsRange {
    func buckets(feeds: [FeedRecord], calendar: Calendar = .current, now: Date = Date()) -> [StatBucket] {
        let currentStart = calendar.dateInterval(of: component, for: now)?.start ?? calendar.startOfDay(for: now)
        var starts: [Date] = []
        for offset in stride(from: bucketCount - 1, through: 0, by: -1) {
            if let start = calendar.date(byAdding: component, value: -offset, to: currentStart) {
                starts.append(start)
            }
        }
        return starts.map { start in
            let end = calendar.date(byAdding: component, value: 1, to: start) ?? start
            let items = feeds.filter { $0.date >= start && $0.date < end }
            let total = items.reduce(0) { $0 + $1.volumeML }
            return StatBucket(start: start, totalML: total, count: items.count)
        }
    }

    func spanStart(calendar: Calendar = .current, now: Date = Date()) -> Date {
        let currentStart = calendar.dateInterval(of: component, for: now)?.start ?? calendar.startOfDay(for: now)
        return calendar.date(byAdding: component, value: -(bucketCount - 1), to: currentStart) ?? currentStart
    }
}

// MARK: - 日期工具

enum DateText {
    static let calendar = Calendar.current

    static func dayStart(_ date: Date) -> Date { Calendar.current.startOfDay(for: date) }

    static func time(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "HH:mm"
        return f.string(from: date)
    }

    static func day(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "M月d日 EEEE"
        return f.string(from: date)
    }

    static func shortDay(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "M/d"
        return f.string(from: date)
    }

    static func full(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "yyyy年M月d日 HH:mm"
        return f.string(from: date)
    }

    static func isToday(_ date: Date) -> Bool { Calendar.current.isDateInToday(date) }

    static func dayLabel(_ date: Date) -> String {
        if isToday(date) { return "今天" }
        if Calendar.current.isDateInYesterday(date) { return "昨天" }
        return day(date)
    }
}