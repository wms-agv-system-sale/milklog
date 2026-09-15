import SwiftUI
import AVFoundation

// MARK: - 相机预览

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        applyRotation(view.previewLayer)
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        if uiView.previewLayer.session !== session {
            uiView.previewLayer.session = session
        }
        applyRotation(uiView.previewLayer)
    }

    private func applyRotation(_ layer: AVCaptureVideoPreviewLayer) {
        guard let connection = layer.connection, connection.isVideoRotationAngleSupported(90) else { return }
        if connection.videoRotationAngle != 90 { connection.videoRotationAngle = 90 }
    }

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

// MARK: - 坐标换算

/// 画面归一化坐标（0~1）与屏幕坐标之间的换算（等比填满模式）
struct PreviewGeometry {
    let frameSize: CGSize
    let viewSize: CGSize

    var scale: CGFloat {
        guard frameSize.width > 0, frameSize.height > 0, viewSize.width > 0, viewSize.height > 0 else { return 1 }
        return max(viewSize.width / frameSize.width, viewSize.height / frameSize.height)
    }

    var displaySize: CGSize {
        CGSize(width: frameSize.width * scale, height: frameSize.height * scale)
    }

    var origin: CGPoint {
        CGPoint(x: (viewSize.width - displaySize.width) / 2, y: (viewSize.height - displaySize.height) / 2)
    }

    func point(x: Double, y: Double) -> CGPoint {
        CGPoint(x: origin.x + CGFloat(x) * displaySize.width, y: origin.y + CGFloat(y) * displaySize.height)
    }

    /// 竖直条带在屏幕上的矩形
    func bandRect(left: Double, right: Double) -> CGRect {
        let topLeft = point(x: min(left, right), y: 0)
        let bottomRight = point(x: max(left, right), y: 1)
        return CGRect(x: topLeft.x, y: topLeft.y, width: bottomRight.x - topLeft.x, height: bottomRight.y - topLeft.y)
    }

    /// 屏幕坐标 -> 归一化坐标
    func normalized(_ location: CGPoint) -> CGPoint? {
        guard displaySize.width > 0, displaySize.height > 0 else { return nil }
        let nx = (location.x - origin.x) / displaySize.width
        let ny = (location.y - origin.y) / displaySize.height
        guard nx >= 0, nx <= 1, ny >= 0, ny <= 1 else { return nil }
        return CGPoint(x: nx, y: ny)
    }
}

// MARK: - 小控件

func volumeString(_ value: Double) -> String {
    let rounded = value.rounded()
    if abs(value - rounded) < 0.05 { return String(Int(rounded)) }
    return String(format: "%.1f", value)
}

struct Chip: View {
    let title: String
    var isSelected: Bool = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(isSelected ? .semibold : .regular))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(isSelected ? Color.accentColor.opacity(0.18) : Color(.secondarySystemBackground))
                .foregroundStyle(isSelected ? Color.accentColor : Color.primary)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

struct StatusPill: View {
    let text: String
    let color: Color
    let systemImage: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage)
            Text(text)
        }
        .font(.footnote.weight(.medium))
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(color.opacity(0.18))
        .foregroundStyle(color)
        .clipShape(Capsule())
    }
}

struct Card<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}