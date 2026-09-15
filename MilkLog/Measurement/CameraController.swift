import Foundation
import AVFoundation
import Combine

/// 相机采集：只会把画面交给 App 本地处理，不做任何上传。
final class CameraController: NSObject, ObservableObject {

    let session = AVCaptureSession()

    @Published var permissionDenied = false
    @Published var torchAvailable = false

    /// 每帧回调，运行在后台线程；请在回调内同步完成分析。
    var onFrame: ((CVPixelBuffer, CGSize) -> Void)?
    var frameInterval: CFAbsoluteTime = 0.12

    private let sessionQueue = DispatchQueue(label: "MilkLog.camera.session")
    private let outputQueue = DispatchQueue(label: "MilkLog.camera.output", qos: .userInitiated)
    private let output = AVCaptureVideoDataOutput()
    private var device: AVCaptureDevice?
    private var isConfigured = false
    private var lastFrameTime: CFAbsoluteTime = 0

    // MARK: - 控制

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndRun()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                guard let self else { return }
                if granted {
                    self.configureAndRun()
                } else {
                    DispatchQueue.main.async { self.permissionDenied = true }
                }
            }
        default:
            DispatchQueue.main.async { self.permissionDenied = true }
        }
    }

    func stop() {
        sessionQueue.async { [session] in
            if session.isRunning { session.stopRunning() }
        }
    }

    func setTorch(_ on: Bool) {
        sessionQueue.async { [weak self] in
            guard let device = self?.device, device.hasTorch else { return }
            guard (try? device.lockForConfiguration()) != nil else { return }
            defer { device.unlockForConfiguration() }
            if on {
                try? device.setTorchModeOn(level: 0.7)
            } else {
                device.torchMode = .off
            }
        }
    }

    // MARK: - 配置

    private func configureAndRun() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            if !self.isConfigured { self.configure() }
            guard self.isConfigured else { return }
            if !self.session.isRunning { self.session.startRunning() }
        }
    }

    private func configure() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device) else { return }

        session.beginConfiguration()
        if session.canSetSessionPreset(.high) { session.sessionPreset = .high }

        if session.canAddInput(input) { session.addInput(input) }

        output.alwaysDiscardsLateVideoFrames = true
        output.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_420YpCbCr8BiPlanarFullRange)
        ]
        output.setSampleBufferDelegate(self, queue: outputQueue)
        if session.canAddOutput(output) { session.addOutput(output) }

        if let connection = output.connection(with: .video), connection.isVideoRotationAngleSupported(90) {
            connection.videoRotationAngle = 90
        }

        session.commitConfiguration()
        self.device = device
        self.isConfigured = true

        DispatchQueue.main.async { self.torchAvailable = device.hasTorch }
    }
}

extension CameraController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput,
                       didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        let now = CFAbsoluteTimeGetCurrent()
        guard now - lastFrameTime >= frameInterval else { return }
        lastFrameTime = now

        guard let handler = onFrame, let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let size = CGSize(width: CVPixelBufferGetWidth(buffer), height: CVPixelBufferGetHeight(buffer))
        handler(buffer, size)
    }
}