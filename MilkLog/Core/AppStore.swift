import Foundation
import UIKit
import Combine

/// 本地数据仓库：全部数据保存在手机的沙盒目录里，不联网、不上传。
final class AppStore: ObservableObject {

    @Published private(set) var feeds: [FeedRecord] = []
    @Published private(set) var bottles: [BottleProfile] = []
    @Published var activeBottleID: UUID?
    @Published var settings: AppSettings = AppSettings()

    private let rootURL: URL
    private let photosURL: URL
    private let dataURL: URL

    private struct Payload: Codable {
        var feeds: [FeedRecord]
        var bottles: [BottleProfile]
        var activeBottleID: UUID?
        var settings: AppSettings
    }

    // MARK: - 生命周期

    init() {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        rootURL = documents.appendingPathComponent("MilkLog", isDirectory: true)
        photosURL = rootURL.appendingPathComponent("Photos", isDirectory: true)
        dataURL = rootURL.appendingPathComponent("data.json")
        try? FileManager.default.createDirectory(at: photosURL, withIntermediateDirectories: true)
        load()
    }

    // MARK: - 读写

    private func load() {
        guard let data = try? Data(contentsOf: dataURL) else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        guard let payload = try? decoder.decode(Payload.self, from: data) else { return }
        feeds = payload.feeds
        bottles = payload.bottles
        activeBottleID = payload.activeBottleID
        settings = payload.settings
        if activeBottleID == nil { activeBottleID = bottles.first?.id }
    }

    func save() {
        let payload = Payload(feeds: feeds, bottles: bottles, activeBottleID: activeBottleID, settings: settings)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted]
        guard let data = try? encoder.encode(payload) else { return }
        try? data.write(to: dataURL, options: .atomic)
    }

    // MARK: - 奶瓶

    var activeBottle: BottleProfile? {
        guard let id = activeBottleID else { return bottles.first }
        return bottles.first { $0.id == id } ?? bottles.first
    }

    func upsert(bottle: BottleProfile) {
        if let index = bottles.firstIndex(where: { $0.id == bottle.id }) {
            bottles[index] = bottle
        } else {
            bottles.append(bottle)
        }
        if activeBottleID == nil { activeBottleID = bottle.id }
        save()
    }

    /// 切换当前使用的奶瓶，并立即写盘
    func setActiveBottle(_ id: UUID?) {
        activeBottleID = id
        save()
    }

    func delete(bottleID: UUID) {
        bottles.removeAll { $0.id == bottleID }
        if activeBottleID == bottleID { activeBottleID = bottles.first?.id }
        save()
    }

    // MARK: - 记录

    func add(_ record: FeedRecord) {
        feeds.append(record)
        feeds.sort { $0.date < $1.date }
        save()
    }

    func update(_ record: FeedRecord) {
        guard let index = feeds.firstIndex(where: { $0.id == record.id }) else { return }
        feeds[index] = record
        feeds.sort { $0.date < $1.date }
        save()
    }

    func delete(recordID: UUID) {
        guard let index = feeds.firstIndex(where: { $0.id == recordID }) else { return }
        if let photo = feeds[index].photoName { deletePhoto(named: photo) }
        feeds.remove(at: index)
        save()
    }

    func deleteAllRecords() {
        for feed in feeds {
            if let photo = feed.photoName { deletePhoto(named: photo) }
        }
        feeds.removeAll()
        save()
    }

    var todayRecords: [FeedRecord] {
        feeds.filter { DateText.isToday($0.date) }
    }

    var todayTotalML: Double {
        todayRecords.reduce(0) { $0 + $1.volumeML }
    }

    /// 最近 7 天平均值（不含今天）
    var recentDailyAverage: Double {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let values = (1...7).compactMap { offset -> Double? in
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today),
                  let end = calendar.date(byAdding: .day, value: 1, to: day) else { return nil }
            let items = feeds.filter { $0.date >= day && $0.date < end }
            guard !items.isEmpty else { return nil }
            return items.reduce(0) { $0 + $1.volumeML }
        }
        guard !values.isEmpty else { return 0 }
        return values.reduce(0, +) / Double(values.count)
    }

    // MARK: - 照片

    func savePhoto(_ image: UIImage) -> String? {
        guard let data = image.jpegData(compressionQuality: 0.7) else { return nil }
        let name = UUID().uuidString + ".jpg"
        let url = photosURL.appendingPathComponent(name)
        do {
            try data.write(to: url, options: .atomic)
            return name
        } catch {
            return nil
        }
    }

    func photo(named name: String) -> UIImage? {
        let url = photosURL.appendingPathComponent(name)
        guard let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }

    private func deletePhoto(named name: String) {
        try? FileManager.default.removeItem(at: photosURL.appendingPathComponent(name))
    }

    // MARK: - 导出

    func exportFileURL() -> URL? {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted]
        guard let data = try? encoder.encode(feeds) else { return nil }
        let url = rootURL.appendingPathComponent("奶量记录导出.json")
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }
}