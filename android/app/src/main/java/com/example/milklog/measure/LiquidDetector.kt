package com.example.milklog.measure

import androidx.camera.core.ImageProxy
import com.example.milklog.model.BottleProfile

/** 一次识别的结果。surfaceY 为画面高度方向上的位置，0 = 顶部，1 = 底部。 */
data class LiquidDetection(
    val surfaceY: Double = 0.5,
    val confidence: Double = 0.0,
    val volumeML: Double? = null,
    val found: Boolean = false,
    val bandLeft: Double = 0.32,
    val bandRight: Double = 0.68
)

/**
 * 液面识别：在相机画面里找"上方是空的、下方是白色液体"的那条分界线。
 *
 * 原理：
 * 1. 只在奶瓶所在的竖直条带（band）里做统计，避开背景；
 * 2. 逐行统计亮度均值、以及"像牛奶"的像素比例（够亮 + 没有明显颜色）；
 * 3. 找一条分界线：线上方牛奶比例低、下方高，并且亮度有明显跳变。
 *
 * 相机给出的原始画面可能相对屏幕旋转了 90/180/270 度，这里统一换算到"屏幕方向"再分析，
 * 这样界面上的参考线坐标可以直接对应。
 */
object LiquidDetector {

    fun detect(
        image: ImageProxy,
        profile: BottleProfile?,
        bandLeft: Double? = null,
        bandRight: Double? = null
    ): LiquidDetection? {
        val planes = image.planes
        if (planes.size < 3) return null
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val rawW = image.width
        val rawH = image.height
        if (rawW < 16 || rawH < 16) return null

        var rotation = image.imageInfo.rotationDegrees % 360
        if (rotation < 0) rotation += 360
        val swapped = rotation == 90 || rotation == 270
        val dispW = if (swapped) rawH else rawW
        val dispH = if (swapped) rawW else rawH
        if (dispW < 8 || dispH < 32) return null

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uLimit = uBuf.limit()
        val vLimit = vBuf.limit()
        val yLimit = yBuf.limit()

        val left = Math.min(bandLeft ?: profile?.bandLeft ?: 0.32, bandRight ?: profile?.bandRight ?: 0.68)
        val right = Math.max(bandLeft ?: profile?.bandLeft ?: 0.32, bandRight ?: profile?.bandRight ?: 0.68)
        val x0 = Math.max(0, Math.min(dispW - 4, (dispW * left).toInt()))
        val x1 = Math.max(x0 + 4, Math.min(dispW, (dispW * right).toInt()))

        val lumaSum = DoubleArray(dispH)
        val milkyCount = IntArray(dispH)
        val sampleCount = IntArray(dispH)

        // 外层遍历取样条带的横向位置，内层遍历竖直方向，内存访问更连续
        var dx = x0
        while (dx < x1) {
            var dy = 0
            while (dy < dispH) {
                var rx: Int
                var ry: Int
                when (rotation) {
                    90 -> { rx = dy; ry = rawH - 1 - dx }
                    180 -> { rx = rawW - 1 - dx; ry = rawH - 1 - dy }
                    270 -> { rx = rawW - 1 - dy; ry = dx }
                    else -> { rx = dx; ry = dy }
                }
                if (rx in 0 until rawW && ry in 0 until rawH) {
                    val yIndex = ry * yRowStride + rx * yPixelStride
                    if (yIndex in 0 until yLimit) {
                        val luma = yBuf.get(yIndex).toInt() and 0xFF
                        lumaSum[dy] += luma
                        sampleCount[dy]++
                        if (luma > 140) {
                            val cx = rx / 2
                            val cy = ry / 2
                            val uIndex = cy * uRowStride + cx * uPixelStride
                            val vIndex = cy * vRowStride + cx * vPixelStride
                            if (uIndex in 0 until uLimit && vIndex in 0 until vLimit) {
                                val cb = uBuf.get(uIndex).toInt() and 0xFF
                                val cr = vBuf.get(vIndex).toInt() and 0xFF
                                if (Math.max(Math.abs(cb - 128), Math.abs(cr - 128)) < 24) {
                                    milkyCount[dy]++
                                }
                            }
                        }
                    }
                }
                dy++
            }
            dx += 2
        }

        val rowLuma = DoubleArray(dispH)
        val rowMilky = DoubleArray(dispH)
        for (i in 0 until dispH) {
            val n = sampleCount[i]
            rowLuma[i] = if (n > 0) lumaSum[i] / n.toDouble() else 0.0
            rowMilky[i] = if (n > 0) milkyCount[i].toDouble() / n.toDouble() else 0.0
        }

        var result = LiquidDetection(bandLeft = left, bandRight = right, found = false)

        val range = profile?.searchRange ?: 0.05..0.95
        val startRow = Math.max(3, (dispH * range.start).toInt())
        val endRow = Math.min(dispH - 4, (dispH * range.endInclusive).toInt())
        if (endRow <= startRow + 6) return result

        val window = Math.max(4, dispH / 80)
        val scores = DoubleArray(dispH)
        var bestScore = 0.0
        var bestRow = -1

        for (y in startRow..endRow) {
            val below = average(rowMilky, y + 2, Math.min(dispH - 1, y + window))
            if (below <= 0.45) continue
            val above = average(rowMilky, Math.max(0, y - window), y - 2)
            val contrast = below - above
            if (contrast <= 0.12) continue
            val jump = lumaJump(rowLuma, y)
            val score = contrast * (0.7 + Math.min(1.0, jump / 26.0))
            scores[y] = score
            if (score > bestScore) {
                bestScore = score
                bestRow = y
            }
        }

        if (bestRow <= 0) return result

        var refined = bestRow.toDouble()
        if (bestRow > 1 && bestRow < dispH - 2) {
            val a = scores[bestRow - 1]
            val b = scores[bestRow]
            val c = scores[bestRow + 1]
            val denominator = a - 2 * b + c
            if (Math.abs(denominator) > 0.000001) {
                val delta = 0.5 * (a - c) / denominator
                if (Math.abs(delta) < 1.0) refined += delta
            }
        }

        result = LiquidDetection(
            surfaceY = Math.min(1.0, Math.max(0.0, refined / dispH.toDouble())),
            confidence = Math.min(1.0, bestScore / 1.15),
            volumeML = profile?.volumeFor(refined / dispH.toDouble()),
            found = true,
            bandLeft = left,
            bandRight = right
        )
        return result
    }

    private fun average(values: DoubleArray, from: Int, to: Int): Double {
        if (to < from) return 0.0
        val lower = Math.max(0, from)
        val upper = Math.min(values.size - 1, to)
        if (upper < lower) return 0.0
        var sum = 0.0
        for (i in lower..upper) sum += values[i]
        return sum / (upper - lower + 1).toDouble()
    }

    private fun lumaJump(values: DoubleArray, row: Int): Double {
        val upper = average(values, row - 3, row - 1)
        val lower = average(values, row + 1, row + 3)
        return Math.abs(lower - upper)
    }
}
