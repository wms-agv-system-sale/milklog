package com.example.milklog.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun formatVolume(value: Double): String {
    val rounded = Math.round(value).toDouble()
    return if (Math.abs(value - rounded) < 0.05) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

enum class RecordSource(val label: String) {
    CAMERA("拍照识别"),
    MANUAL("手动输入")
}

/** 一次喂奶记录 */
data class FeedRecord(
    val id: String = newId(),
    val date: Long = System.currentTimeMillis(),
    val volumeML: Double = 0.0,
    val source: RecordSource = RecordSource.MANUAL,
    val note: String = "",
    val photoName: String? = null,
    val confidence: Double? = null
) {
    val volumeText: String get() = formatVolume(volumeML)
}

/** 一个已知奶量对应的液面高度（0~1，从画面顶部算起） */
data class CalibrationPoint(
    val id: String = newId(),
    val volumeML: Double,
    val y: Double
)

/** 一个奶瓶的标定信息 */
data class BottleProfile(
    val id: String = newId(),
    val name: String = "我的奶瓶",
    val points: List<CalibrationPoint> = emptyList(),
    val bandLeft: Double = 0.32,
    val bandRight: Double = 0.68
) {
    val isReady: Boolean get() = points.size >= 2

    val sortedPoints: List<CalibrationPoint> get() = points.sortedBy { it.y }

    /** 允许检测的液面范围（留一点余量） */
    val searchRange: ClosedFloatingPointRange<Double>
        get() {
            val pts = sortedPoints
            if (pts.size < 2) return 0.06..0.94
            val lo = pts.first().y
            val hi = pts[pts.size - 1].y
            if (hi - lo <= 0.01) return 0.06..0.94
            val margin = Math.max(0.05, (hi - lo) * 0.35)
            return Math.max(0.02, lo - margin)..Math.min(0.98, hi + margin)
        }

    /** 液面高度 -> 奶量（分段线性插值，范围外按最近一段斜率外推并限幅） */
    fun volumeFor(y: Double): Double? {
        val pts = sortedPoints
        if (pts.size < 2) return null

        if (y <= pts[0].y) {
            val span = firstSpan(pts) ?: return pts[0].volumeML
            return clampVolume(span.first.volumeML + span.second * (y - span.first.y))
        }
        val last = pts[pts.size - 1]
        if (y >= last.y) {
            val span = lastSpan(pts) ?: return last.volumeML
            return clampVolume(span.first.volumeML + span.second * (y - span.first.y))
        }
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            if (b.y - a.y <= 0.0005) continue
            if (y >= a.y && y <= b.y) {
                val t = (y - a.y) / (b.y - a.y)
                return clampVolume(a.volumeML + t * (b.volumeML - a.volumeML))
            }
        }
        return null
    }

    private fun firstSpan(pts: List<CalibrationPoint>): Pair<CalibrationPoint, Double>? {
        for (i in 0 until pts.size - 1) {
            if (pts[i + 1].y - pts[i].y > 0.0005) {
                val a = pts[i]
                val b = pts[i + 1]
                return Pair(a, (b.volumeML - a.volumeML) / (b.y - a.y))
            }
        }
        return null
    }

    private fun lastSpan(pts: List<CalibrationPoint>): Pair<CalibrationPoint, Double>? {
        if (pts.size < 2) return null
        for (i in pts.size - 2 downTo 0) {
            if (pts[i + 1].y - pts[i].y > 0.0005) {
                val a = pts[i]
                val b = pts[i + 1]
                return Pair(a, (b.volumeML - a.volumeML) / (b.y - a.y))
            }
        }
        return null
    }

    private fun clampVolume(value: Double): Double {
        val maxVolume = (points.maxOfOrNull { it.volumeML } ?: 300.0) * 1.15 + 10.0
        return Math.min(Math.max(0.0, value), maxVolume)
    }
}

data class AppSettings(
    /** 每日目标奶量（ml），只用于图表参考线 */
    val dailyTargetML: Double = 600.0,
    /** 是否保存每次识别的照片 */
    val keepPhotos: Boolean = true
)

/** 日 / 周 / 月 统计 */
enum class StatsRange {
    DAY, WEEK, MONTH;

    val title: String
        get() = when (this) {
            DAY -> "日"
            WEEK -> "周"
            MONTH -> "月"
        }

    val chartTitle: String
        get() = when (this) {
            DAY -> "最近 14 天每日奶量"
            WEEK -> "最近 8 周每周奶量"
            MONTH -> "最近 6 个月每月奶量"
        }

    val averageTitle: String
        get() = when (this) {
            DAY -> "平均每天"
            WEEK -> "平均每周"
            MONTH -> "平均每月"
        }

    val peakTitle: String
        get() = when (this) {
            DAY -> "单日最高"
            WEEK -> "单周最高"
            MONTH -> "单月最高"
        }

    val bucketCount: Int
        get() = when (this) {
            DAY -> 14
            WEEK -> 8
            MONTH -> 6
        }

    val field: Int
        get() = when (this) {
            DAY -> Calendar.DAY_OF_YEAR
            WEEK -> Calendar.WEEK_OF_YEAR
            MONTH -> Calendar.MONTH
        }

    val axisFormat: String
        get() = when (this) {
            DAY -> "M/d"
            WEEK -> "M/d"
            MONTH -> "yy/M"
        }
}

data class StatBucket(val start: Long, val totalML: Double, val count: Int) {
    val averagePerFeed: Double get() = if (count > 0) totalML / count.toDouble() else 0.0
}

object Stats {
    private fun currentStart(range: StatsRange, now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        return when (range) {
            StatsRange.DAY -> DateText.dayStart(now)
            StatsRange.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                DateText.dayStart(cal.timeInMillis)
            }
            StatsRange.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                DateText.dayStart(cal.timeInMillis)
            }
        }
    }

    fun spanStart(range: StatsRange, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentStart(range, now)
        cal.add(range.field, -(range.bucketCount - 1))
        return cal.timeInMillis
    }

    fun buckets(range: StatsRange, feeds: List<FeedRecord>, now: Long = System.currentTimeMillis()): List<StatBucket> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentStart(range, now)
        val starts = ArrayList<Long>()
        for (i in range.bucketCount - 1 downTo 0) {
            val c = Calendar.getInstance()
            c.timeInMillis = cal.timeInMillis
            c.add(range.field, -i)
            starts.add(c.timeInMillis)
        }
        val result = ArrayList<StatBucket>()
        for (start in starts) {
            val c = Calendar.getInstance()
            c.timeInMillis = start
            c.add(range.field, 1)
            val end = c.timeInMillis
            val items = feeds.filter { it.date >= start && it.date < end }
            val total = items.fold(0.0) { acc, item -> acc + item.volumeML }
            result.add(StatBucket(start, total, items.size))
        }
        return result
    }
}

object DateText {
    private fun fmt(pattern: String): SimpleDateFormat = SimpleDateFormat(pattern, Locale.CHINA)

    fun time(date: Long): String = fmt("HH:mm").format(Date(date))

    fun day(date: Long): String = fmt("M月d日 EEEE").format(Date(date))

    fun shortDay(date: Long): String = fmt("M/d").format(Date(date))

    fun full(date: Long): String = fmt("yyyy年M月d日 HH:mm").format(Date(date))

    fun dayStart(date: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun isToday(date: Long): Boolean {
        return dayStart(date) == dayStart(System.currentTimeMillis())
    }

    fun dayLabel(date: Long): String {
        val today = dayStart(System.currentTimeMillis())
        val target = dayStart(date)
        val dayMillis = 24L * 60L * 60L * 1000L
        return when {
            target == today -> "今天"
            target == today - dayMillis -> "昨天"
            else -> day(date)
        }
    }
}
