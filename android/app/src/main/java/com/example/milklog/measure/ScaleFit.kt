package com.example.milklog.measure

/**
 * 刻度数字和画面高度之间的换算关系：奶量 = slope * y + intercept。
 * y 是画面高度方向的位置（0 = 顶部，1 = 底部），越往上奶越多，所以 slope 是负数。
 */
class ScaleFit(
    val slope: Double,
    val intercept: Double,
    val marks: List<ScaleMark>
) {
    fun volumeAt(y: Double): Double = slope * y + intercept

    /** 参与换算的最大刻度值，用来限制外推 */
    val maxMarkML: Double get() = marks.maxOfOrNull { it.valueML } ?: 300.0
}

/** 用照片里读到的刻度数字求换算关系（最小二乘拟合，并剔除明显不合群的数字） */
object ScaleFitBuilder {

    fun build(rawMarks: List<ScaleMark>): ScaleFit? {
        if (rawMarks.size < 2) return null

        // 同一个数字可能被读到好几次，先按数值合并，位置取中位数
        val grouped = LinkedHashMap<Int, ArrayList<ScaleMark>>()
        for (mark in rawMarks) {
            val key = Math.round(mark.valueML).toInt()
            val list = grouped[key]
            if (list == null) grouped[key] = arrayListOf(mark) else list.add(mark)
        }

        var points = ArrayList<ScaleMark>()
        for (entry in grouped.entries) {
            val list = entry.value
            val ys = ArrayList<Double>()
            for (item in list) ys.add(item.y)
            ys.sort()
            points.add(ScaleMark(entry.key.toDouble(), ys[ys.size / 2], list[0].raw))
        }
        points.sortBy { it.y }

        if (points.size < 2) return null

        // 刻度数字必须"越往上数值越大"
        for (i in 0 until points.size - 1) {
            if (points[i + 1].valueML <= points[i].valueML) return null
        }
        if (points[points.size - 1].y - points[0].y < 0.04) return null

        var fit = leastSquares(points)
        var removed = 0
        while (removed < 2 && points.size > 2) {
            var worstIndex = -1
            var worstError = 0.0
            for (i in points.indices) {
                val error = Math.abs(fit.volumeAt(points[i].y) - points[i].valueML)
                if (error > worstError) {
                    worstError = error
                    worstIndex = i
                }
            }
            val tolerance = Math.max(8.0, fit.maxMarkML * 0.06)
            if (worstIndex < 0 || worstError <= tolerance) break
            val kept = ArrayList<ScaleMark>()
            for (i in points.indices) {
                if (i != worstIndex) kept.add(points[i])
            }
            points = kept
            fit = leastSquares(points)
            removed++
        }

        if (points.size < 2) return null
        // 画面高度方向至少要能拉开 30ml 的差距，否则这个关系不可信
        if (fit.slope > -30.0) return null
        return ScaleFit(fit.slope, fit.intercept, points)
    }

    private fun leastSquares(points: List<ScaleMark>): ScaleFit {
        val n = points.size.toDouble()
        var sumY = 0.0
        var sumV = 0.0
        var sumYY = 0.0
        var sumYV = 0.0
        for (point in points) {
            sumY += point.y
            sumV += point.valueML
            sumYY += point.y * point.y
            sumYV += point.y * point.valueML
        }
        val denominator = n * sumYY - sumY * sumY
        if (Math.abs(denominator) < 0.000000001) {
            return ScaleFit(0.0, 0.0, points)
        }
        val slope = (n * sumYV - sumY * sumV) / denominator
        val intercept = (sumV - slope * sumY) / n
        return ScaleFit(slope, intercept, points)
    }
}
