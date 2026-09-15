import Foundation
import CoreVideo

/// 一次识别的结果
struct LiquidDetection: Equatable {
    /// 液面在画面中的高度，0 = 画面顶部，1 = 画面底部
    var surfaceY: Double = 0.5
    /// 置信度 0~1
    var confidence: Double = 0
    /// 换算后的奶量（没标定时为 nil）
    var volumeML: Double? = nil
    /// 是否真的找到了液面
    var found: Bool = false
    var bandLeft: Double = 0.32
    var bandRight: Double = 0.68
}

/// 液面识别：在相机画面里找“上方是空的、下方是白色液体”的那条分界线。
///
/// 原理：
/// 1. 只在奶瓶所在的竖直条带（band）里做统计，避开背景；
/// 2. 逐行统计亮度均值，以及“像牛奶”的像素比例（够亮 + 没有明显颜色）；
/// 3. 找到一条分界线：线上方的“牛奶比例”低、下方高，并且亮度有明显跳变（液面反光/折射）。
enum LiquidDetector {

    static func detect(pixelBuffer: CVPixelBuffer,
                       profile: BottleProfile?,
                       bandLeft: Double? = nil,
                       bandRight: Double? = nil) -> LiquidDetection? {

        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }

        guard CVPixelBufferGetPlaneCount(pixelBuffer) >= 2,
              let lumaBase = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0),
              let chromaBase = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 1) else { return nil }

        let width = CVPixelBufferGetWidthOfPlane(pixelBuffer, 0)
        let height = CVPixelBufferGetHeightOfPlane(pixelBuffer, 0)
        let lumaStride = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0)
        let chromaWidth = CVPixelBufferGetWidthOfPlane(pixelBuffer, 1)
        let chromaHeight = CVPixelBufferGetHeightOfPlane(pixelBuffer, 1)
        let chromaStride = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 1)

        guard width > 16, height > 16, chromaWidth > 4, chromaHeight > 4 else { return nil }

        let left = min(bandLeft ?? profile?.bandLeft ?? 0.32, bandRight ?? profile?.bandRight ?? 0.68)
        let right = max(bandLeft ?? profile?.bandLeft ?? 0.32, bandRight ?? profile?.bandRight ?? 0.68)
        let x0 = max(0, min(width - 4, Int(Double(width) * left)))
        let x1 = max(x0 + 4, min(width, Int(Double(width) * right)))

        let lumaRow = lumaBase.assumingMemoryBound(to: UInt8.self)
        let chromaRow = chromaBase.assumingMemoryBound(to: UInt8.self)

        var rowLuma = [Double](repeating: 0, count: height)
        var rowMilky = [Double](repeating: 0, count: height)

        let chromaScaleY = Double(chromaHeight) / Double(height)

        for y in 0..<height {
            let chromaY = min(chromaHeight - 1, max(0, Int(Double(y) * chromaScaleY)))
            let chromaRowStart = chromaY * chromaStride
            let lumaRowStart = y * lumaStride

            var lumaSum = 0.0
            var milkyCount = 0
            var sampleCount = 0

            var x = x0
            while x < x1 {
                let luma = Double(lumaRow[lumaRowStart + x])
                lumaSum += luma
                sampleCount += 1

                if luma > 140 {
                    let cx = min(chromaWidth - 1, x / 2)
                    let cb = Int(chromaRow[chromaRowStart + cx * 2])
                    let cr = Int(chromaRow[chromaRowStart + cx * 2 + 1])
                    // 灰度像素的 Cb/Cr 都接近 128
                    if max(abs(cb - 128), abs(cr - 128)) < 24 { milkyCount += 1 }
                }
                x += 2
            }

            rowLuma[y] = sampleCount > 0 ? lumaSum / Double(sampleCount) : 0
            rowMilky[y] = sampleCount > 0 ? Double(milkyCount) / Double(sampleCount) : 0
        }

        let range = profile?.searchRange ?? 0.05...0.95
        let startRow = max(3, Int(Double(height) * range.lowerBound))
        let endRow = min(height - 4, Int(Double(height) * range.upperBound))

        var result = LiquidDetection()
        result.bandLeft = left
        result.bandRight = right
        result.found = false

        guard endRow > startRow + 6 else { return result }

        let window = max(4, height / 80)
        var scores = [Double](repeating: 0, count: height)
        var bestScore = 0.0
        var bestRow = -1

        for y in startRow...endRow {
            let below = average(rowMilky, from: y + 2, to: min(height - 1, y + window))
            let above = average(rowMilky, from: max(0, y - window), to: y - 2)
            guard below > 0.45 else { continue }

            let contrast = below - above
            guard contrast > 0.12 else { continue }

            let jump = lumaJump(rowLuma, at: y)
            let score = contrast * (0.7 + min(1.0, jump / 26.0))
            scores[y] = score
            if score > bestScore {
                bestScore = score
                bestRow = y
            }
        }

        guard bestRow > 0 else { return result }

        // 抛物插值，得到亚像素级位置
        var refined = Double(bestRow)
        if bestRow > 1, bestRow < height - 2 {
            let a = scores[bestRow - 1]
            let b = scores[bestRow]
            let c = scores[bestRow + 1]
            let denominator = a - 2 * b + c
            if abs(denominator) > 0.000001 {
                let delta = 0.5 * (a - c) / denominator
                if abs(delta) < 1.0 { refined += delta }
            }
        }

        result.surfaceY = min(1, max(0, refined / Double(height)))
        result.confidence = min(1.0, bestScore / 1.15)
        result.found = true
        result.volumeML = profile?.volume(forY: result.surfaceY)
        return result
    }

    // MARK: - 小工具

    private static func average(_ values: [Double], from: Int, to: Int) -> Double {
        guard to >= from else { return 0 }
        let lower = max(0, from)
        let upper = min(values.count - 1, to)
        guard upper >= lower else { return 0 }
        var sum = 0.0
        for index in lower...upper { sum += values[index] }
        return sum / Double(upper - lower + 1)
    }

    private static func lumaJump(_ values: [Double], at row: Int) -> Double {
        let upper = average(values, from: row - 3, to: row - 1)
        let lower = average(values, from: row + 1, to: row + 3)
        return abs(lower - upper)
    }
}