package com.example.milklog.measure

import androidx.camera.core.ImageProxy

/** 一次识别的结果。surfaceY 是液面在画面高度方向上的位置：0 = 顶部，1 = 底部。 */
data class LiquidDetection(
    val surfaceY: Double = 0.5,
    val confidence: Double = 0.0,
    val found: Boolean = false
)

/**
 * 找奶瓶里的液面。
 *
 * 只看画面中间那条竖直条带（奶瓶一般放在画面中间），逐行统计三件事：
 * 1. 这一行有多少像素"像牛奶"（够亮、颜色很淡）；
 * 2. 这一行有多少列出现了明显的上下亮度变化 —— 真正的液面会横跨整条带子，
 *    而瓶身上印的刻度线只有一小段，会被这条规则筛掉；
 * 3. 液面以下应该是平整的牛奶，不该再有横线。
 *
 * 结果还会做多帧记忆（新的分数取最大值、旧分数慢慢衰减），
 * 这样液面线不会一帧一跳，手抖一下也不会乱跑。
 */
object LiquidDetector {

    /** 瓶身大致所在的横向范围（画面宽度的比例） */
    const val BAND_LEFT = 0.30
    const val BAND_RIGHT = 0.70

    private var memory = DoubleArray(0)

    fun reset() {
        memory = DoubleArray(0)
    }

    fun detect(
        image: ImageProxy,
        searchFrom: Double = 0.06,
        searchTo: Double = 0.96
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
        val yLimit = yBuf.limit()
        val uLimit = uBuf.limit()
        val vLimit = vBuf.limit()

        val x0 = Math.max(0, Math.min(dispW - 6, (dispW * BAND_LEFT).toInt()))
        val x1 = Math.max(x0 + 4, Math.min(dispW, (dispW * BAND_RIGHT).toInt()))

        val lumaSum = DoubleArray(dispH)
        val milkyCount = IntArray(dispH)
        val sampleCount = IntArray(dispH)
        val edgeCount = IntArray(dispH)

        val columns = ((x1 - x0) + 1) / 2 + 1
        val previousRow = IntArray(columns)
        val twoRowsAbove = IntArray(columns)

        var dy = 0
        while (dy < dispH) {
            var dx = x0
            var column = 0
            while (dx < x1 && column < columns) {
                var rx = 0
                var ry = 0
                when (rotation) {
                    90 -> { rx = dy; ry = rawH - 1 - dx }
                    180 -> { rx = rawW - 1 - dx; ry = rawH - 1 - dy }
                    270 -> { rx = rawW - 1 - dy; ry = dx }
                    else -> { rx = dx; ry = dy }
                }

                var luma = -1
                if (rx in 0 until rawW && ry in 0 until rawH) {
                    val yIndex = ry * yRowStride + rx * yPixelStride
                    if (yIndex in 0 until yLimit) {
                        val value = yBuf.get(yIndex).toInt() and 0xFF
                        luma = value
                        lumaSum[dy] += value.toDouble()
                        sampleCount[dy]++
                        if (value > 125 && isNeutral(uBuf, vBuf, uRowStride, vRowStride, uPixelStride, vPixelStride, uLimit, vLimit, rx, ry)) {
                            milkyCount[dy]++
                        }
                    }
                }

                val above = twoRowsAbove[column]
                if (luma >= 0 && above >= 0 && Math.abs(luma - above) > 12) {
                    edgeCount[dy]++
                }
                twoRowsAbove[column] = previousRow[column]
                previousRow[column] = luma
                column++
                dx += 2
            }
            dy++
        }

        val rowLuma = DoubleArray(dispH)
        val rowMilky = DoubleArray(dispH)
        val rowEdge = DoubleArray(dispH)
        for (i in 0 until dispH) {
            val n = sampleCount[i]
            rowLuma[i] = if (n > 0) lumaSum[i] / n.toDouble() else 0.0
            rowMilky[i] = if (n > 0) milkyCount[i].toDouble() / n.toDouble() else 0.0
            rowEdge[i] = if (n > 0) edgeCount[i].toDouble() / n.toDouble() else 0.0
        }

        val milkyPrefix = prefixSums(rowMilky)
        val lumaPrefix = prefixSums(rowLuma)
        val edgePrefix = prefixSums(rowEdge)

        if (memory.size != dispH) memory = DoubleArray(dispH)
        for (i in memory.indices) memory[i] = memory[i] * 0.8

        val window = Math.max(4, dispH / 60)
        val startRow = Math.max(2, Math.min(dispH - 3, (dispH * searchFrom).toInt()))
        val endRow = Math.max(startRow, Math.min(dispH - 3, (dispH * searchTo).toInt()))

        var bestRow = -1
        var bestScore = 0.0
        var y = startRow
        while (y <= endRow) {
            val score = scoreAt(y, milkyPrefix, lumaPrefix, edgePrefix, dispH, window)
            if (score > memory[y]) memory[y] = score
            if (memory[y] > bestScore) {
                bestScore = memory[y]
                bestRow = y
            }
            y++
        }

        if (bestRow < 0 || bestScore < 0.45) {
            return LiquidDetection(found = false)
        }

        var refined = bestRow.toDouble()
        if (bestRow > 1 && bestRow < dispH - 2) {
            val a = memory[bestRow - 1]
            val b = memory[bestRow]
            val c = memory[bestRow + 1]
            val denominator = a - 2 * b + c
            if (Math.abs(denominator) > 0.000001) {
                val delta = 0.5 * (a - c) / denominator
                if (Math.abs(delta) < 1.0) refined += delta
            }
        }

        return LiquidDetection(
            surfaceY = Math.min(1.0, Math.max(0.0, refined / dispH.toDouble())),
            confidence = Math.min(1.0, bestScore),
            found = true
        )
    }

    /** 这一行像不像液面：横跨整条带子 + 下面更像牛奶 + 下面更平 */
    private fun scoreAt(
        row: Int,
        milky: DoubleArray,
        luma: DoubleArray,
        edge: DoubleArray,
        height: Int,
        window: Int
    ): Double {
        if (row <= 2 || row >= height - 3) return 0.0
        val lineEdge = edge[row + 1] - edge[row]
        if (lineEdge < 0.30) return 0.0

        val belowTo = row + window * 3
        val aboveFrom = row - window * 3
        if (belowTo <= row + 3) return 0.0

        val milkBelow = rangeAverage(milky, row + 3, belowTo)
        val milkAbove = rangeAverage(milky, aboveFrom, row - 3)
        val lumaBelow = rangeAverage(luma, row + 3, belowTo)
        val lumaAbove = rangeAverage(luma, aboveFrom, row - 3)

        // 液面以下应该是平整的牛奶，不该再出现横线
        val edgeBelow = rangeAverage(edge, row + 3, row + window * 4)
        if (edgeBelow > 0.45) return 0.0

        // 牛奶比上面（空瓶透出的背景）亮，至少不能暗很多
        if (lumaBelow < lumaAbove - 25.0) return 0.0

        val milkGain = Math.max(0.0, milkBelow - milkAbove)
        val lumaGain = Math.max(0.0, (lumaBelow - lumaAbove) / 40.0)

        return lineEdge * 0.62 +
            Math.min(1.0, milkGain / 0.5) * 0.26 +
            Math.min(1.0, lumaGain) * 0.12
    }

    /** 前缀和数组，让"某一段的平均值"能 O(1) 算出来 */
    private fun prefixSums(values: DoubleArray): DoubleArray {
        val out = DoubleArray(values.size + 1)
        for (i in values.indices) out[i + 1] = out[i] + values[i]
        return out
    }

    private fun rangeAverage(prefix: DoubleArray, from: Int, to: Int): Double {
        val last = prefix.size - 2
        val lower = Math.max(0, from)
        val upper = Math.min(last, to)
        if (upper < lower) return 0.0
        return (prefix[upper + 1] - prefix[lower]) / (upper - lower + 1).toDouble()
    }

    private fun isNeutral(
        uBuf: java.nio.ByteBuffer,
        vBuf: java.nio.ByteBuffer,
        uRowStride: Int,
        vRowStride: Int,
        uPixelStride: Int,
        vPixelStride: Int,
        uLimit: Int,
        vLimit: Int,
        rx: Int,
        ry: Int
    ): Boolean {
        val cx = rx / 2
        val cy = ry / 2
        val uIndex = cy * uRowStride + cx * uPixelStride
        val vIndex = cy * vRowStride + cx * vPixelStride
        if (uIndex !in 0 until uLimit || vIndex !in 0 until vLimit) return false
        val cb = uBuf.get(uIndex).toInt() and 0xFF
        val cr = vBuf.get(vIndex).toInt() and 0xFF
        return Math.max(Math.abs(cb - 128), Math.abs(cr - 128)) < 32
    }

}
