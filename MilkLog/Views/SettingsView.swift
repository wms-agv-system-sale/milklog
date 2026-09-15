import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var engine: MeasurementEngine

    @State private var editingBottle: BottleProfile?
    @State private var confirmClear = false
    @State private var exportURL: URL?
    @State private var targetText: String = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if store.bottles.isEmpty {
                        Text("还没有奶瓶，先添加一个并完成标定。")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    ForEach(store.bottles) { bottle in
                        Button {
                            editingBottle = bottle
                        } label: {
                            BottleRow(bottle: bottle, isActive: store.activeBottleID == bottle.id)
                        }
                        .buttonStyle(.plain)
                    }
                    Button {
                        editingBottle = BottleProfile()
                    } label: {
                        Label("添加奶瓶", systemImage: "plus.circle.fill")
                    }
                } header: {
                    Text("奶瓶与标定")
                } footer: {
                    Text("标定只需要做一次。之后把奶瓶放在同一个位置，App 就能自动读出奶量。")
                }

                Section {
                    HStack {
                        Text("每日目标")
                        Spacer()
                        TextField("600", text: $targetText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 90)
                            .monospacedDigit()
                            .onChange(of: targetText) { _, newValue in
                                if let value = Double(newValue) {
                                    store.settings.dailyTargetML = min(max(0, value), 2000)
                                }
                            }
                        Text("ml").foregroundStyle(.secondary)
                    }
                } header: {
                    Text("目标")
                } footer: {
                    Text("只影响统计图里的参考虚线，不会限制记录。")
                }

                Section("记录") {
                    Toggle("保存识别时的照片", isOn: $store.settings.keepPhotos)
                    if let exportURL {
                        ShareLink(item: exportURL) {
                            Label("导出全部记录", systemImage: "square.and.arrow.up")
                        }
                    }
                    Button("清空所有记录", role: .destructive) {
                        confirmClear = true
                    }
                }

                Section("帮助") {
                    NavigationLink {
                        HelpView()
                    } label: {
                        Label("使用说明与技巧", systemImage: "questionmark.circle")
                    }
                }

                Section {
                    HStack {
                        Text("数据存储")
                        Spacer()
                        Text("仅保存在本机").foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("联网")
                        Spacer()
                        Text("完全不需要").foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("版本")
                        Spacer()
                        Text("1.0").foregroundStyle(.secondary)
                    }
                } header: {
                    Text("关于")
                } footer: {
                    Text("所有记录、照片和标定数据都只保存在这台手机上，App 不含任何联网功能，也不会收集任何信息。")
                }
            }
            .navigationTitle("设置")
            .onAppear {
                targetText = volumeString(store.settings.dailyTargetML)
                exportURL = store.exportFileURL()
            }
            .onChange(of: store.feeds.count) { _, _ in
                exportURL = store.exportFileURL()
            }
            .onChange(of: store.settings) { _, _ in
                store.save()
            }
            .sheet(item: $editingBottle) { bottle in
                CalibrationView(bottle: bottle) { saved in
                    store.upsert(bottle: saved)
                    store.setActiveBottle(saved.id)
                    engine.profile = saved
                } onCancel: {
                    engine.clearBand()
                    engine.profile = store.activeBottle
                }
            }
            .confirmationDialog("确定要清空所有奶量记录吗？此操作无法撤销。",
                                isPresented: $confirmClear,
                                titleVisibility: .visible) {
                Button("清空", role: .destructive) {
                    store.deleteAllRecords()
                }
            }
        }
    }
}

struct BottleRow: View {
    let bottle: BottleProfile
    let isActive: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "drop.fill")
                .foregroundStyle(isActive ? Color.accentColor : Color.secondary)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 2) {
                Text(bottle.name)
                    .font(.body.weight(isActive ? .semibold : .regular))
                Text(bottle.isReady ? "已标定 \(bottle.points.count) 个点" : "未完成标定")
                    .font(.caption)
                    .foregroundStyle(bottle.isReady ? Color.secondary : Color.orange)
            }
            Spacer()
            if isActive {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Color.accentColor)
            }
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
    }
}