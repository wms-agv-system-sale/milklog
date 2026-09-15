import Foundation
import UIKit
import CoreVideo
import Combine

/// 实时测量引擎：把相机画面变成“稳定的奶量读数”。
final class MeasurementEngine: ObservableObject {

    @Published private(set) var detection: LiquidDetection?
    @Published private(set) var frameSize: CGSize = CGSize(width: 1080, height: 1920)
    @Published private(set) var isStable = false
    @Published private(set) var settledVolume: Double?
    @Published private(set) var latestImage: UIImage?

    @Published var torchOn: Bool = false {
        didSet { camera.setTorch(torchOn) }
    }

    let camera = CameraController()

    private var cancellables = Set<AnyCancellable>()
    private let lock = NSLock()
    private var profileStorage: BottleProfile?
    private var bandStorage: (Double, Double)?

    private let ciContext = CIContext(options: [.useSoftwareRenderer: false])
    private var history: [Double] = []
    private var stableValue: Double?
    private var lastValidTime = Date.distantPast
    private var lastImageTime = Date.distantPast
    private var isRunning = false

    init() {
        // 让相机的状态变化（权限、闪光灯）也能刷新界面
        camera.objectWillChange
            .sink { [weak self] _ in
                guard let self else { return }
                DispatchQueue.main.async { self.objectWillChange.send() }
            }
            .store(in: &cancellables)
    }

    // MARK: - 输入

    var profile: BottleProfile? {
        get {
            lock.lock(); defer { lock.unlock() }
            return profileStorage
        }
        set {
            lock.lock(); profileStorage = newValue; lock.unlock()
            reset()
        }
    }

    /// 清除临时取样区域，改回使用奶瓶自己的设置
    func clearBand() {
        lock.lock(); bandStorage = nil; lock.unlock()
    }

    func setBand(left: Double, right: Double) {
        lock.lock(); bandStorage = (left, right); lock.unlock()
        reset()
    }

    func reset() {
        DispatchQueue.main.async {
            self.history.removeAll()
            self.stableValue = nil
            self.settledVolume = nil
            self.isStable = false
            self.detection = nil
        }
    }

    // MARK: - 采集

    func start() {
        guard !isRunning else { return }
        isRunning = true
        camera.onFrame = { [weak self] buffer, size in
            self?.process(buffer: buffer, size: size)
        }
        camera.start()
    }

    func stop() {
        isRunning = false
        camera.stop()
    }

    private func process(buffer: CVPixelBuffer, size: CGSize) {
        let profile = self.profile
        let band = currentBand()
        let result = LiquidDetector.detect(pixelBuffer: buffer,
                                           profile: profile,
                                           bandLeft: band.0,
                                           bandRight: band.1)

        var image: UIImage?
        let now = Date()
        if now.timeIntervalSince(lastImageTime) > 1.0 {
            lastImageTime = now
            image = snapshot(from: buffer)
        }

        DispatchQueue.main.async {
            if self.frameSize != size { self.frameSize = size }
            if let image { self.latestImage = image }
            if let result {
                self.apply(result)
            } else {
                self.applyMiss()
            }
        }
    }

    private func currentBand() -> (Double, Double) {
        lock.lock(); defer { lock.unlock() }
        if let band = bandStorage { return band }
        let profile = profileStorage
        return (profile?.bandLeft ?? 0.32, profile?.bandRight ?? 0.68)
    }

    private func currentProfile() -> BottleProfile? {
        lock.lock(); defer { lock.unlock() }
        return profileStorage
    }

    private func snapshot(from buffer: CVPixelBuffer) -> UIImage? {
        let image = CIImage(cvPixelBuffer: buffer)
        let extent = image.extent
        guard extent.width > 1, extent.height > 1 else { return nil }
        let scale = min(1.0, 720.0 / max(extent.width, extent.height))
        let scaled = scale < 1 ? image.transformed(by: CGAffineTransform(scaleX: scale, y: scale)) : image
        guard let cgImage = ciContext.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    // MARK: - 平滑读数

    private func apply(_ result: LiquidDetection) {
        var smoothed = result

        guard result.found else {
            applyMiss()
            return
        }

        history.append(result.surfaceY)
        if history.count > 6 { history.removeFirst() }

        let sorted = history.sorted()
        let median = sorted[sorted.count / 2]
        let spread = (sorted.last ?? 0) - (sorted.first ?? 0)
        smoothed.surfaceY = median
        if let profile = currentProfile() {
            smoothed.volumeML = profile.volume(forY: median)
        }

        let stableNow = history.count >= 4 && spread < 0.015 && result.confidence > 0.2
        isStable = stableNow

        if stableNow, let volume = smoothed.volumeML {
            lastValidTime = Date()
            if let previous = stableValue {
                stableValue = previous * 0.6 + volume * 0.4
            } else {
                stableValue = volume
            }
            settledVolume = stableValue
        }

        detection = smoothed
    }

    private func applyMiss() {
        isStable = false
        if Date().timeIntervalSince(lastValidTime) > 2.0 {
            stableValue = nil
            settledVolume = nil
            history.removeAll()
            detection = LiquidDetection(found: false)
        } else if var current = detection {
            current.found = false
            detection = current
        }
    }
}