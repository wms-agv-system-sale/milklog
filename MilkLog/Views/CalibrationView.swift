import SwiftUI
import UIKit

/// 奶瓶标定：用几个已知奶量，教会 App 把“液面高度”换算成“毫升”。
struct CalibrationView: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var engine: MeasurementEngine
    @Environment(\.dismiss) private var dismiss

    private let onSave: (BottleProfile) -> Void
    private let onCancel: () -> Void

    @State private var draft: BottleProfile
    @State private var name: String
    @State private var selectedVolume: Double = 120
    @State private var customText: String = ""
    @State private var tapMode = false
    @State private var busy = false
    @State private var message: String?
    @State private var lastCapturedID: UUID?
    @State private var didSave = false

    private let quickVolumes: [Double] = [0, 30, 60, 90, 120, 150, 180, 210, 240]

    init(bottle: BottleProfile,
         onSave: @escaping (BottleProfile) -> Void,
         onCancel: @escaping () -> Void) {
        self.onSave = onSave
        self.onCancel = onCancel
        _draft = State(initialValue: bottle)
        _name = State(initialValue: bottle.name)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    previewSection
                    stepSection
                    pointsSection
                    bandSection
                    tipsSection
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("标定奶瓶")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        onCancel()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        didSave = true
                        var saved = draft
                        saved.name = name.trimmingCharacters(in: .whitespaces).isEmpty ? "我的奶瓶" : name
                        onSave(saved)
                        dismiss()
                    }
                    .disabled(!draft.isReady)
                }
            }
            .onAppear {
                applyDraft()
                engine.start()
            }
            .onDisappear {
                // 直接下滑关闭时，也要把临时标定撤掉
                if !didSave { onCancel() }
            }
        }
    }

    // MARK: - 预览

    private var previewSection: some View {
        VStack(spacing: 10) {
            GeometryReader { geo in
                ZStack(alignment: .topLeading) {
                    CameraPreview(session: engine.camera.session)
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                    previewOverlay(size: geo.size)

                    if tapMode {
                        Text("点击画面中液面的位置")
                            .font(.footnote.weight(.semibold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(.black.opacity(0.55))
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                            .padding(10)
                    }
                }
                .frame(width: geo.size.width, height: geo.size.height)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { value in
                    handleTap(value.location, size: geo.size)
                })
            }
            .frame(height: 300)

            if let message {
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(.orange)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    @ViewBuilder
    private func previewOverlay(size: CGSize) -> some View {
        let geometry = PreviewGeometry(frameSize: engine.frameSize, viewSize: size)
        let band = geometry.bandRect(left: draft.bandLeft, right: draft.bandRight)

        ZStack(alignment: .topLeading) {
            Path { path in path.addRect(band) }
                .fill(Color.white.opacity(0.06))
                .overlay(
                    Path { path in
                        path.move(to: CGPoint(x: band.minX, y: band.minY))
                        path.addLine(to: CGPoint(x: band.minX, y: band.maxY))
                        path.move(to: CGPoint(x: band.maxX, y: band.minY))
                        path.addLine(to: CGPoint(x: band.maxX, y: band.maxY))
                    }
                    .stroke(Color.white.opacity(0.5), style: StrokeStyle(lineWidth: 1.5, dash: [6, 5]))
                )

            if let detection = engine.detection, detection.found {
                let y = geometry.point(x: 0, y: detection.surfaceY).y
                Path { path in
                    path.move(to: CGPoint(x: 0, y: y))
                    path.addLine(to: CGPoint(x: size.width, y: y))
                }
                .stroke(Color.yellow, style: StrokeStyle(lineWidth: 2.5, dash: [9, 6]))
            }

            ForEach(draft.points) { point in
                let y = geometry.point(x: 0, y: point.y).y
                Path { path in
                    path.move(to: CGPoint(x: 0, y: y))
                    path.addLine(to: CGPoint(x: size.width, y: y))
                }
                .stroke(Color.green, lineWidth: 2)

                Text("\(volumeString(point.volumeML))")
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.green)
                    .foregroundStyle(.black)
                    .clipShape(Capsule())
                    .position(x: 42, y: y)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    // MARK: - 记录步骤

    private var stepSection: some View {
        Card {
            VStack(alignment: .leading, spacing: 12) {
                Text("选择奶瓶里的奶量")
                    .font(.headline)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(quickVolumes, id: \.self) { value in
                            Chip(title: value == 0 ? "空瓶(0)" : "\(Int(value))ml",
                                 isSelected: abs(selectedVolume - value) < 0.5) {
                                selectedVolume = value
                                customText = ""
                                message = nil
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }

                HStack(spacing: 10) {
                    TextField("自定义数值", text: $customText)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.roundedBorder)
                        .frame(maxWidth: 140)
                        .onChange(of: customText) { _, newValue in
                            if let value = Double(newValue) { selectedVolume = value }
                        }
                    Text("ml")
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("当前：\(volumeString(selectedVolume)) ml")
                        .font(.subheadline.weight(.semibold))
                }

                HStack(spacing: 10) {
                    Button {
                        autoCapture()
                    } label: {
                        Label(busy ? "识别中…" : "识别并记录", systemImage: "camera.metering.center.weighted")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(busy)

                    Button {
                        tapMode.toggle()
                        message = tapMode ? "现在点击画面上液面的位置" : nil
                    } label: {
                        Label("点选液面", systemImage: "hand.point.up.left")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.bordered)
                }

                Text("提示：记录 0ml 时，请点选瓶底的位置（也就是「空瓶时液面」所在的位置）。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - 已记录的点

    private var pointsSection: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("已记录 \(draft.points.count) 个点")
                        .font(.headline)
                    Spacer()
                    if draft.isReady {
                        Label("可以保存", systemImage: "checkmark.seal.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.green)
                    } else {
                        Label("至少 2 个点", systemImage: "exclamationmark.circle")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                }

                if draft.points.isEmpty {
                    Text("还没有记录任何点。装入一个已知奶量后点「识别并记录」。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(draft.points.sorted(by: { $0.volumeML < $1.volumeML })) { point in
                        HStack {
                            Text("\(volumeString(point.volumeML)) ml")
                                .font(.subheadline.weight(.semibold))
                                .monospacedDigit()
                            Text("液面位置 \(Int(point.y * 100))%")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Spacer()
                            if lastCapturedID == point.id {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                            }
                            Button(role: .destructive) {
                                draft.points.removeAll { $0.id == point.id }
                                applyDraft()
                            } label: {
                                Image(systemName: "trash")
                            }
                            .buttonStyle(.borderless)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
    }

    // MARK: - 取样区域

    private var bandSection: some View {
        Card {
            VStack(alignment: .leading, spacing: 8) {
                Text("取样区域")
                    .font(.headline)
                Text("虚线框是 App 分析的范围，请让它正好罩住瓶身（不含背景），可以提高识别准确度。")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                HStack {
                    Text("左边界").font(.footnote).frame(width: 56, alignment: .leading)
                    Slider(value: Binding(get: { draft.bandLeft }, set: { draft.bandLeft = min($0, draft.bandRight - 0.05); applyDraft() }), in: 0...1)
                }
                HStack {
                    Text("右边界").font(.footnote).frame(width: 56, alignment: .leading)
                    Slider(value: Binding(get: { draft.bandRight }, set: { draft.bandRight = max($0, draft.bandLeft + 0.05); applyDraft() }), in: 0...1)
                }
            }
        }
    }

    private var tipsSection: some View {
        Card {
            VStack(alignment: .leading, spacing: 8) {
                Text("奶瓶名称")
                    .font(.headline)
                TextField("例如：贝亲 240ml", text: $name)
                    .textFieldStyle(.roundedBorder)

                Divider().padding(.vertical, 4)

                Text("怎么标定才准？")
                    .font(.subheadline.weight(.semibold))
                Text("""
                1. 把手机固定在一个位置（用支架最好），奶瓶每次放在同一个点上。
                2. 倒出/冲调一个已知奶量，放进画面，点「识别并记录」。
                3. 换 1~2 个不同的奶量（比如 60 和 180）再记录一次。
                4. 完成后，每次只要把奶瓶放到同一个位置，App 就会自动读数。
                """)
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - 逻辑

    private func handleTap(_ location: CGPoint, size: CGSize) {
        guard tapMode else { return }
        let geometry = PreviewGeometry(frameSize: engine.frameSize, viewSize: size)
        guard let normalized = geometry.normalized(location) else { return }
        addPoint(y: Double(normalized.y))
        tapMode = false
        message = "已记录 \(volumeString(selectedVolume)) ml 的液面位置"
    }

    private func autoCapture() {
        busy = true
        message = nil
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
            busy = false
            guard let detection = engine.detection, detection.found else {
                message = "没有识别到液面。请把奶瓶对准虚线框，或者改用「点选液面」手动指定。"
                return
            }
            addPoint(y: detection.surfaceY)
        }
    }

    private func addPoint(y: Double) {
        if let index = draft.points.firstIndex(where: { abs($0.volumeML - selectedVolume) < 0.5 }) {
            draft.points[index].y = y
        } else {
            draft.points.append(CalibrationPoint(volumeML: selectedVolume, y: y))
        }
        let sorted = draft.points.sorted { $0.volumeML < $1.volumeML }
        if let newest = sorted.first(where: { abs($0.volumeML - selectedVolume) < 0.5 }) {
            lastCapturedID = newest.id
        }
        applyDraft()
        message = nil
    }

    private func applyDraft() {
        engine.setBand(left: draft.bandLeft, right: draft.bandRight)
        engine.profile = draft
    }
}