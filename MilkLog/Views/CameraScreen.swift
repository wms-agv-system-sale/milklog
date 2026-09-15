import SwiftUI
import UIKit

/// 主界面：对着奶瓶，自动读出当前奶量。
struct CameraScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var engine: MeasurementEngine

    @State private var showCalibration = false
    @State private var sheetRecord: FeedRecord?
    @State private var sheetIsNew = true
    @State private var pendingVolume: Double?

    private var profile: BottleProfile? { store.activeBottle }
    private var isCalibrated: Bool { profile?.isReady == true }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                GeometryReader { geo in
                    ZStack(alignment: .topLeading) {
                        CameraPreview(session: engine.camera.session)
                            .frame(width: geo.size.width, height: geo.size.height)
                            .clipped()
                        overlay(size: geo.size)
                    }
                    .frame(width: geo.size.width, height: geo.size.height)
                }
                .ignoresSafeArea()

                VStack(spacing: 0) {
                    topBar
                    Spacer(minLength: 0)
                    bottomPanel
                }

                if engine.camera.permissionDenied {
                    permissionView
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $showCalibration) {
                CalibrationView(bottle: profile ?? BottleProfile()) { saved in
                    store.upsert(bottle: saved)
                    store.setActiveBottle(saved.id)
                    syncProfile(force: true)
                } onCancel: {
                    engine.clearBand()
                    syncProfile(force: true)
                }
            }
            .sheet(item: $sheetRecord) { record in
                RecordEditView(record: record, isNew: sheetIsNew)
            }
            .onAppear { syncProfile() }
            .onChange(of: store.activeBottleID) { _, _ in syncProfile(force: true) }
            .onChange(of: store.bottles) { _, _ in syncProfile() }
        }
    }

    // MARK: - 叠加层

    @ViewBuilder
    private func overlay(size: CGSize) -> some View {
        let geometry = PreviewGeometry(frameSize: engine.frameSize, viewSize: size)
        let left = profile?.bandLeft ?? 0.32
        let right = profile?.bandRight ?? 0.68
        let band = geometry.bandRect(left: left, right: right)

        ZStack(alignment: .topLeading) {
            Path { path in
                path.addRect(band)
            }
            .fill(Color.white.opacity(0.06))
            .overlay(
                Path { path in
                    path.move(to: CGPoint(x: band.minX, y: band.minY))
                    path.addLine(to: CGPoint(x: band.minX, y: band.maxY))
                    path.move(to: CGPoint(x: band.maxX, y: band.minY))
                    path.addLine(to: CGPoint(x: band.maxX, y: band.maxY))
                }
                .stroke(Color.white.opacity(0.45), style: StrokeStyle(lineWidth: 1.5, dash: [6, 5]))
            )

            if let detection = engine.detection, detection.found {
                let y = geometry.point(x: 0, y: detection.surfaceY).y
                let color: Color = engine.isStable ? .green : .yellow

                Path { path in
                    path.move(to: CGPoint(x: 0, y: y))
                    path.addLine(to: CGPoint(x: size.width, y: y))
                }
                .stroke(color, style: StrokeStyle(lineWidth: 3, dash: [10, 7]))
                .shadow(color: .black.opacity(0.4), radius: 2, y: 1)

                HStack(spacing: 5) {
                    Image(systemName: "drop.fill")
                    Text(detection.volumeML.map { "\(volumeString($0)) ml" } ?? "液面已识别")
                }
                .font(.footnote.weight(.semibold))
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(color)
                .foregroundStyle(.black)
                .clipShape(Capsule())
                .position(x: size.width - 72, y: max(30, y - 22))
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    // MARK: - 顶部

    private var topBar: some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("今天")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.75))
                Text("\(volumeString(store.todayTotalML)) ml · \(store.todayRecords.count) 次")
                    .font(.headline)
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(.black.opacity(0.35))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

            Spacer()

            if engine.camera.torchAvailable {
                circleButton(icon: engine.torchOn ? "flashlight.on.fill" : "flashlight.off.fill",
                             highlighted: engine.torchOn) {
                    engine.torchOn.toggle()
                }
            }
            circleButton(icon: "slider.horizontal.3", highlighted: false) {
                showCalibration = true
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 56)
    }

    private func circleButton(icon: String, highlighted: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 42, height: 42)
                .background(highlighted ? Color.accentColor : Color.black.opacity(0.35))
                .clipShape(Circle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - 底部面板

    private var bottomPanel: some View {
        VStack(spacing: 14) {
            if isCalibrated {
                readingPanel
            } else {
                calibrationPrompt
            }
        }
        .padding(16)
        .padding(.bottom, 8)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.ultraThinMaterial)
                .ignoresSafeArea(edges: .bottom)
        )
    }

    private var readingPanel: some View {
        VStack(spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(pendingVolume.map { volumeString($0) } ?? settledText)
                    .font(.system(size: 54, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                Text("ml")
                    .font(.title3.weight(.medium))
                    .foregroundStyle(.secondary)
                Spacer()
                if canAdjust {
                    HStack(spacing: 8) {
                        adjustButton(symbol: "minus", delta: -5)
                        adjustButton(symbol: "plus", delta: 5)
                    }
                }
            }

            HStack(spacing: 8) {
                if let profile {
                    Text(profile.name)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                statusPill
                Spacer()
            }

            HStack(spacing: 12) {
                Button {
                    var record = FeedRecord(date: Date(), volumeML: 0, source: .manual)
                    record.volumeML = 90
                    sheetIsNew = true
                    sheetRecord = record
                } label: {
                    Label("手动输入", systemImage: "hand.tap")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.bordered)

                Button {
                    save()
                } label: {
                    Label("保存记录", systemImage: "checkmark.circle.fill")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .disabled(currentVolume == nil)
            }
        }
    }

    private var calibrationPrompt: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("还没标定奶瓶", systemImage: "exclamationmark.triangle.fill")
                .font(.headline)
                .foregroundStyle(.orange)
            Text("先用 2~3 个已知奶量（例如 60ml、120ml、180ml）标定一次，之后每次只要把奶瓶放到固定位置，App 就能自动读出奶量。")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 12) {
                Button {
                    var record = FeedRecord(date: Date(), volumeML: 90, source: .manual)
                    record.volumeML = 90
                    sheetIsNew = true
                    sheetRecord = record
                } label: {
                    Label("手动输入", systemImage: "hand.tap").frame(maxWidth: .infinity).padding(.vertical, 10)
                }
                .buttonStyle(.bordered)

                Button {
                    showCalibration = true
                } label: {
                    Label("开始标定", systemImage: "camera.metering.center.weighted")
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
            }
        }
    }

    private var permissionView: some View {
        VStack(spacing: 16) {
            Image(systemName: "camera.fill")
                .font(.system(size: 44))
                .foregroundStyle(.secondary)
            Text("需要相机权限")
                .font(.title3.weight(.semibold))
            Text("请在「设置 → 隐私与安全性 → 相机」中允许本 App 使用相机。")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button("打开设置") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(28)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .padding(32)
    }

    private func adjustButton(symbol: String, delta: Double) -> some View {
        Button {
            let base = pendingVolume ?? engine.settledVolume ?? 0
            pendingVolume = max(0, base + delta)
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .bold))
                .frame(width: 40, height: 40)
                .background(Color(.tertiarySystemFill))
                .clipShape(Circle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var statusPill: some View {
        if let volume = currentVolume, volume > 0 {
            if engine.isStable {
                StatusPill(text: pendingVolume == nil ? "读数稳定" : "已手动调整", color: .green, systemImage: "checkmark.circle")
            } else {
                StatusPill(text: "识别中…", color: .orange, systemImage: "waveform.path.ecg")
            }
        } else {
            StatusPill(text: "请把奶瓶放进虚线框内", color: .secondary, systemImage: "viewfinder")
        }
    }

    // MARK: - 逻辑

    private var settledText: String {
        if let value = engine.settledVolume { return volumeString(value) }
        if let value = engine.detection?.volumeML, engine.detection?.found == true { return volumeString(value) }
        return "--"
    }

    private var currentVolume: Double? {
        if let pendingVolume { return pendingVolume > 0 ? pendingVolume : nil }
        if let settled = engine.settledVolume { return settled }
        if let live = engine.detection?.volumeML, engine.detection?.found == true { return live }
        return nil
    }

    private var canAdjust: Bool { currentVolume != nil }

    private func syncProfile(force: Bool = false) {
        let target = store.activeBottle
        if force || engine.profile != target {
            engine.profile = target
            pendingVolume = nil
        }
    }

    private func save() {
        guard let volume = currentVolume, volume > 0 else { return }
        var record = FeedRecord(date: Date(), volumeML: volume, source: .camera)
        record.confidence = engine.detection?.confidence
        if store.settings.keepPhotos, let image = engine.latestImage {
            record.photoName = store.savePhoto(image)
        }
        sheetIsNew = true
        sheetRecord = record
    }
}