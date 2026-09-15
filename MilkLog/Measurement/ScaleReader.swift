import Foundation
import UIKit
import Vision
import CoreVideo

/// 读取奶瓶上印的刻度数字（本地识别，不联网）。
/// 用于标定时给出建议数值，以及拍完照后辅助校对。
enum ScaleReader {

    struct Reading {
        var value: Double
        /// 归一化坐标（原点在左下角，与 Vision 一致）
        var box: CGRect
    }

    static func readNumbers(from pixelBuffer: CVPixelBuffer, orientation: CGImagePropertyOrientation = .up) -> [Reading] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        request.recognitionLanguages = ["en-US"]
        request.minimumTextHeight = 0.01

        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: orientation, options: [:])
        do {
            try handler.perform([request])
        } catch {
            return []
        }

        guard let observations = request.results else { return [] }

        var readings: [Reading] = []
        for observation in observations {
            guard let candidate = observation.topCandidates(1).first else { continue }
            let digits = candidate.string.filter { $0.isNumber }
            guard digits.count >= 2, digits.count <= 4, let value = Double(digits) else { continue }
            guard value >= 10, value <= 400 else { continue }
            readings.append(Reading(value: value, box: observation.boundingBox))
        }
        return readings.sorted { $0.value < $1.value }
    }
}