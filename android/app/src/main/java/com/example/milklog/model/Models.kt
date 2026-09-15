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

    /** 周期名字，例如「今天」「本周」「本月」 */
    val currentName: String
        get() = when (this) {
            DAY -> "今天"
            WEEK -> "本周"
            MONTH -> "本月"
        }

    val field: Int
        get() = when (this) {
            DAY -> Calendar.DAY_OF_YEAR
            WEEK -> Calendar.WEEK_OF_YEAR
            MONTH -> Calendar.MONTH
        }
}

/** 一段时间（一天 / 一周 / 一月）的奶量汇总 */
data class StatBucket(val start: Long, val totalML: Double, val count: Int) {
    val averagePerFeed: Double get() = if (count > 0) totalML / count.toDouble() else 0.0
}

object Stats {

    /** 某个时间点所属周期的起点：当天 0 点 / 本周第一天 / 本月 1 号 */
    fun periodStart(range: StatsRange, time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        return when (range) {
            StatsRange.DAY -> DateText.dayStart(time)
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

    /** 周期起点前后平移 delta 个周期，用来翻上一天 / 上一周 / 上一月 */
    fun shift(range: StatsRange, start: Long, delta: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        cal.add(range.field, delta)
        return periodStart(range, cal.timeInMillis)
    }

    /** 周期结束时间（不含），也就是下一个周期的起点 */
    fun periodEnd(range: StatsRange, start: Long): Long = shift(range, start, 1)

    /** 后一天的 0 点 */
    fun nextDay(dayStart: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStart
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return DateText.dayStart(cal.timeInMillis)
    }

    /** 周期内的所有记录，按时间从早到晚排好 */
    fun recordsIn(range: StatsRange, feeds: List<FeedRecord>, start: Long): List<FeedRecord> {
        val end = periodEnd(range, start)
        return feeds.filter { it.date >= start && it.date < end }.sortedBy { it.date }
    }

    /** 周期内每一天的汇总，没有记录的日子也会有一条（奶量为 0） */
    fun dailyTotals(range: StatsRange, feeds: List<FeedRecord>, start: Long): List<StatBucket> {
        val end = periodEnd(range, start)
        val result = ArrayList<StatBucket>()
        var cursor = DateText.dayStart(start)
        while (cursor < end) {
            val next = nextDay(cursor)
            val items = feeds.filter { it.date >= cursor && it.date < next }
            result.add(
                StatBucket(
                    start = cursor,
                    totalML = items.fold(0.0) { acc, item -> acc + item.volumeML },
                    count = items.size
                )
            )
            cursor = next
        }
        return result
    }

    /** 记录落在周期时间轴上的位置（0~1），横轴按真实时间分布 */
    fun position(start: Long, end: Long, date: Long): Float {
        if (end <= start) return 0.5f
        val ratio = (date - start).toDouble() / (end - start).toDouble()
        return ratio.coerceIn(0.0, 1.0).toFloat()
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
