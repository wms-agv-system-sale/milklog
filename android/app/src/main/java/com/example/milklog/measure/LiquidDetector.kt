package com.example.milklog.measure

import android.graphics.Bitmap

/** 一次识别的结果。surfaceY 是液面在照片高度方向上的位置：0 = 顶部，1 = 底部。 */
data class LiquidDetection(
    val surfaceY: Double = 0.5,
    val confidence: Double = 0.0,
    val found: Boolean = false
)

/**
 * 在一张照片里找奶瓶的液面。
 *
 * 只看画面中间那条竖直条带（奶瓶一般放在画面中间），逐行统计三件事：
 * 1. 这一行有多少像素"像牛奶"（够亮、颜色很淡）；
 * 2. 这一行有多少列出现了明显的上下亮度变化 —— 真正的液面会横跨整条带子，
 *    而瓶身上印的刻度线只有一小段，会被这条规则筛掉；
 * 3. 液面以下应该是平整的牛奶，不该再出现横线。
 */
object LiquidDetector {

    /** 瓶身大致所在的横向范围（画面宽度的比例） */
    const val BAND_LEFT = 0.30
    const val BAND_RIGHT = 0.70

    fun detect(
        bitmap: Bitmap,
        searchFrom: Double = 0.06,
        searchTo: Double = 0.96
    ): LiquidDetection? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 32 || height < 64) return null

        val pixels = IntArray(width * height)
        try {
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        } catch (t: Throwable) {
            return null
        }

        val x0 = Math.max(0, Math.min(width - 6, (width * BAND_LEFT).toInt()))
        val x1 = Math.max(x0 + 4, Math.min(width, (width * BAND_RIGHT).toInt()))

        val lumaSum = DoubleArray(height)
        val milkyCount = IntArray(height)
        val sampleCount = IntArray(height)
        val edgeCount = IntArray(height)

        val columnCount = ((x1 - x0) + 1) / 2 + 1
        // -1 表示"还没采到"，避免照片最上面两行被当成一条横线
        val previousRow = IntArray(columnCount) { -1 }
        val twoRowsAbove = IntArray(columnCount) { -1 }

        var y = 0
        while (y < height) {
            val rowOffset = y * width
            var column = 0
            var x = x0
            while (x < x1 && column < columnCount) {
                val color = pixels[rowOffset + x]
                val luma = lumaOf(color)
                lumaSum[y] += luma.toDouble()
                sampleCount[y]++
                if (luma > 125 && isNeutral(color)) milkyCount[y]++
                val above = twoRowsAbove[column]
                if (above >= 0 && Math.abs(luma - above) > 12) edgeCount[y]++
                twoRowsAbove[column] = previousRow[column]
                previousRow[column] = luma
                column++
                x += 2
            }
            y++
        }

        val rowLuma = DoubleArray(height)
        val rowMilky = DoubleArray(height)
        val rowEdge = DoubleArray(height)
        for (i in 0 until height) {
            val n = sampleCount[i]
            rowLuma[i] = if (n > 0) lumaSum[i] / n.toDouble() else 0.0
            rowMilky[i] = if (n > 0) milkyCount[i].toDouble() / n.toDouble() else 0.0
            rowEdge[i] = if (n > 0) edgeCount[i].toDouble() / n.toDouble() else 0.0
        }

        val milkyPrefix = prefixSums(rowMilky)
        val lumaPrefix = prefixSums(rowLuma)
        val edgePrefix = prefixSums(rowEdge)

        val window = Math.max(4, height / 60)
        val startRow = Math.max(2, Math.min(height - 3, (height * searchFrom).toInt()))
        val endRow = Math.max(startRow, Math.min(height - 3, (height * searchTo).toInt()))

        val scores = DoubleArray(height)
        var bestRow = -1
        var bestScore = 0.0
        var row = startRow
        while (row <= endRow) {
            val score = scoreAt(row, milkyPrefix, lumaPrefix, edgePrefix, height, window)
            scores[row] = score
            if (score > bestScore) {
                bestScore = score
                bestRow = row
            }
            row++
        }

        if (bestRow < 0 || bestScore < 0.45) {
            return LiquidDetection(found = false)
        }

        var refined = bestRow.toDouble()
        if (bestRow > 1 && bestRow < height - 2) {
            val a = scores[bestRow - 1]
            val b = scores[bestRow]
            val c = scores[bestRow + 1]
            val denominator = a - 2 * b + c
            if (Math.abs(denominator) > 0.000001) {
                val delta = 0.5 * (a - c) / denominator
                if (Math.abs(delta) < 1.0) refined += delta
            }
        }

        return LiquidDetection(
            surfaceY = Math.min(1.0, Math.max(0.0, refined / height.toDouble())),
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

    private fun lumaOf(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return ((r * 299 + g * 587 + b * 114) / 1000)
    }

    /** 颜色很淡（接近灰白）就算，牛奶通常是这种颜色 */
    private fun isNeutral(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val cb = -0.168736 * r - 0.331264 * g + 0.5 * b
        val cr = 0.5 * r - 0.418688 * g - 0.081312 * b
        return Math.max(Math.abs(cb), Math.abs(cr)) < 32.0
    }
}
