import SwiftUI
import UIKit

/// 确认 / 修改一条记录。识别的结果可以在这里按实际情况改数值。
struct RecordEditView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    let isNew: Bool

    @State private var draft: FeedRecord
    @State private var volumeText: String
    @State private var confirmDelete = false

    init(record: FeedRecord, isNew: Bool) {
        self.isNew = isNew
        _draft = State(initialValue: record)
        _volumeText = State(initialValue: record.volumeML > 0 ? volumeString(record.volumeML) : "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("奶量") {
                    HStack(spacing: 6) {
                        TextField("0", text: $volumeText)
                            .keyboardType(.numberPad)
                            .font(.system(size: 40, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .onChange(of: volumeText) { _, newValue in
                                if let value = Double(newValue) {
                                    draft.volumeML = min(max(0, value), 300)
                                }
                            }
                        Text("ml")
                            .font(.title3)
                            .foregroundStyle(.secondary)
                    }

                    Slider(value: $draft.volumeML, in: 0...300, step: 1)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach([30.0, 60, 90, 120, 150, 180, 210, 240], id: \.self) { value in
                                Chip(title: "\(Int(value))", isSelected: abs(draft.volumeML - value) < 0.5) {
                                    draft.volumeML = value
                                    volumeText = volumeString(value)
                                }
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }

                Section("时间") {
                    DatePicker("喂奶时间", selection: $draft.date, displayedComponents: [.date, .hourAndMinute])
                }

                Section("备注") {
                    TextField("例如：睡前奶 / 没喝完", text: $draft.note, axis: .vertical)
                        .lineLimit(1...3)
                }

                if let photoName = draft.photoName, let image = store.photo(named: photoName) {
                    Section("识别时的照片") {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 220)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }

                Section {
                    HStack {
                        Label(draft.source.label, systemImage: draft.source.iconName)
                            .foregroundStyle(.secondary)
                        Spacer()
                        if let confidence = draft.confidence {
                            Text("识别置信度 \(Int(confidence * 100))%")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                if !isNew {
                    Section {
                        Button("删除这条记录", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(isNew ? "确认记录" : "编辑记录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        var record = draft
                        record.volumeML = max(0, min(300, record.volumeML))
                        if isNew {
                            store.add(record)
                        } else {
                            store.update(record)
                        }
                        dismiss()
                    }
                    .disabled(draft.volumeML <= 0)
                }
            }
            .confirmationDialog("确定删除这条记录吗？", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("删除", role: .destructive) {
                    store.delete(recordID: draft.id)
                    dismiss()
                }
            }
        }
    }
}