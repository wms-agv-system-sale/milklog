package com.example.milklog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.milklog.data.AppStore
import com.example.milklog.model.DateText
import com.example.milklog.model.FeedRecord
import com.example.milklog.model.RecordSource
import com.example.milklog.model.StatBucket
import com.example.milklog.model.Stats
import com.example.milklog.model.StatsRange
import com.example.milklog.model.formatVolume
import java.util.Calendar

private val ChartBlue = Color(0xFF1E88E5)
private val ChartOrange = Color(0xFFE07B00)

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
private val WeekDayNames = listOf("日", "一", "二", "三", "四", "五", "六")

/** 折线图上的一个点，x 是 0~1 的相对位置 */
private data class ChartPoint(val x: Float, val value: Double, val caption: String)

/** 横轴上的一个刻度 */
private data class AxisLabel(val x: Float, val text: String)

/**
 * 奶量统计：
 * - 日记录：今天的每一次喂奶画成折线
 * - 周记录：本周每一次喂奶画成折线，再加每天总奶量
 * - 月记录：本月每一次喂奶画成折线，再加每天总奶量
 */
@Composable
fun StatsScreen(store: AppStore, onEdit: (FeedRecord) -> Unit) {
    var range by remember { mutableStateOf(StatsRange.DAY) }
    var offset by remember { mutableStateOf(0) }

    val now = remember(store.feeds) { System.currentTimeMillis() }
    val start = remember(range, offset, now) { Stats.shift(range, Stats.periodStart(range, now), offset) }
    val end = remember(range, start) { Stats.shift(range, start, 1) }

    val records = remember(store.feeds, range, start, end) { Stats.recordsIn(range, store.feeds, start) }
    val daily = remember(store.feeds, range, start) { Stats.dailyTotals(range, store.feeds, start) }

    val totalML = records.fold(0.0) { acc, item -> acc + item.volumeML }
    val feedCount = records.size
    val averagePerFeed = if (feedCount > 0) totalML / feedCount else 0.0
    val maxFeed = records.maxOfOrNull { it.volumeML } ?: 0.0
    val recordedDays = daily.count { it.count > 0 }
    val averagePerDay = if (recordedDays > 0) totalML / recordedDays else 0.0
    val bestDayML = daily.maxOfOrNull { it.totalML } ?: 0.0

    val feedPoints = remember(records, start, end) {
        records.map { record ->
            ChartPoint(Stats.position(start, end, record.date), record.volumeML, DateText.time(record.date))
        }
    }

    val dayPoints = remember(daily) {
        val size = daily.size
        daily.mapIndexed { index, bucket ->
            ChartPoint(
                x = if (size > 1) index.toFloat() / (size - 1).toFloat() else 0.5f,
                value = bucket.totalML,
                caption = DateText.shortDay(bucket.start) + " · " + formatVolume(bucket.totalML) + " ml"
            )
        }
    }

    val dayGroups = remember(records) {
        records.groupBy { DateText.dayStart(it.date) }
            .toList()
            .sortedByDescending { it.first }
            .map { pair -> pair.first to pair.second.sortedByDescending { record -> record.date } }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 42.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            Text("奶量统计", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        item(key = "range") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.values().forEach { item ->
                    RangeChip(
                        text = item.title + "记录",
                        selected = range == item,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (range != item) {
                            range = item
                            offset = 0
                        }
                    }
                }
            }
        }

        item(key = "period") {
            PeriodNav(
                title = periodTitle(range, start, end),
                subtitle = periodSubtitle(range, offset),
                canGoNext = offset < 0,
                isCurrent = offset == 0,
                resetText = "回到" + range.currentName,
                onPrev = { offset -= 1 },
                onNext = { if (offset < 0) offset += 1 },
                onReset = { offset = 0 }
            )
        }

        item(key = "summary") {
            SummaryCard(
                range = range,
                totalML = totalML,
                feedCount = feedCount,
                averagePerFeed = averagePerFeed,
                maxFeed = maxFeed,
                averagePerDay = averagePerDay,
                bestDayML = bestDayML,
                recordedDays = recordedDays,
                dayCount = daily.size
            )
        }

        item(key = "feed-chart") {
            ChartCard(
                title = "每次奶量走势",
                subtitle = if (feedCount > 0)
                    "本期 " + feedCount + " 次喂奶，按实际时间一个一个点"
                else "这段时间还没有喂奶记录",
                points = feedPoints,
                lineColor = ChartBlue,
                reference = if (feedCount >= 3) averagePerFeed else 0.0,
                legend = if (feedCount >= 3)
                    "橙色虚线 = 平均每次 " + formatVolume(averagePerFeed) + " ml"
                else "",
                xLabels = axisLabels(range, start, end),
                emptyHint = "这段时间还没有喂奶记录"
            )
        }

        if (range != StatsRange.DAY) {
            item(key = "day-chart") {
                ChartCard(
                    title = "每天总奶量",
                    subtitle = "共 " + daily.size + " 天，其中 " + recordedDays + " 天有记录",
                    points = dayPoints,
                    lineColor = ChartOrange,
                    reference = 0.0,
                    legend = "",
                    xLabels = bucketLabels(daily),
                    emptyHint = "这段时间还没有喂奶记录"
                )
            }
        }

        if (dayGroups.isEmpty()) {
            item(key = "empty") {
                AppCard {
                    Column {
                        Text("这段时间还没有记录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "回到「记录」页，对准奶瓶拍一下就能记一笔；也可以手动输入奶量。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item(key = "detail-title") {
                SectionTitle("按天明细（点一条可以改数值）")
            }
            items(dayGroups, key = { it.first }) { group ->
                DayGroupCard(date = group.first, records = group.second, store = store, onEdit = onEdit)
            }
        }
    }
}

// MARK: - 周期

private fun periodTitle(range: StatsRange, start: Long, end: Long): String {
    return when (range) {
        StatsRange.DAY -> DateText.day(start)
        StatsRange.WEEK -> DateText.shortDay(start) + " - " + DateText.shortDay(end - DAY_MILLIS)
        StatsRange.MONTH -> yearMonthText(start)
    }
}

private fun periodSubtitle(range: StatsRange, offset: Int): String {
    if (offset == 0) return range.currentName
    return when (range) {
        StatsRange.DAY -> if (offset == -1) "昨天" else "日记录"
        StatsRange.WEEK -> "周记录"
        StatsRange.MONTH -> "月记录"
    }
}

private fun yearMonthText(date: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = date
    return cal.get(Calendar.YEAR).toString() + "年" + (cal.get(Calendar.MONTH) + 1) + "月"
}

private fun axisLabels(range: StatsRange, start: Long, end: Long): List<AxisLabel> {
    return when (range) {
        StatsRange.DAY -> listOf(
            AxisLabel(0f, "0时"),
            AxisLabel(0.25f, "6时"),
            AxisLabel(0.5f, "12时"),
            AxisLabel(0.75f, "18时"),
            AxisLabel(1f, "24时")
        )
        StatsRange.WEEK -> (0..6).map { index ->
            AxisLabel(index / 6f, weekDayText(start, index))
        }
        StatsRange.MONTH -> {
            val days = Math.max(1, ((end - start) / DAY_MILLIS).toInt())
            val last = Math.max(1, days - 1)
            listOf(0, 7, 14, 21, last).distinct().map { day ->
                AxisLabel(day.toFloat() / last.toFloat(), (day + 1).toString() + "日")
            }
        }
    }
}

private fun weekDayText(start: Long, offsetDays: Int): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = start
    cal.add(Calendar.DAY_OF_YEAR, offsetDays)
    return "周" + WeekDayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
}

private fun bucketLabels(buckets: List<StatBucket>): List<AxisLabel> {
    val size = buckets.size
    if (size == 0) return emptyList()
    val last = Math.max(1, size - 1)
    return listOf(0, size / 3, size * 2 / 3, size - 1).distinct().map { index ->
        AxisLabel(index.toFloat() / last.toFloat(), DateText.shortDay(buckets[index].start))
    }
}

@Composable
private fun PeriodNav(
    title: String,
    subtitle: String,
    canGoNext: Boolean,
    isCurrent: Boolean,
    resetText: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit
) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavButton("\u2039", enabled = true, onClick = onPrev)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                NavButton("\u203a", enabled = canGoNext, onClick = onNext)
            }
            if (!isCurrent) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickableNoRipple { onReset() }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        resetText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun NavButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickableNoRipple { if (enabled) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
    }
}

// MARK: - 概览

@Composable
private fun SummaryCard(
    range: StatsRange,
    totalML: Double,
    feedCount: Int,
    averagePerFeed: Double,
    maxFeed: Double,
    averagePerDay: Double,
    bestDayML: Double,
    recordedDays: Int,
    dayCount: Int
) {
    AppCard {
        Column {
            Row {
                MetricTile(
                    title = if (range == StatsRange.DAY) "今日总奶量" else "本期总奶量",
                    value = formatVolume(totalML),
                    unit = "ml",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "喂奶次数",
                    value = feedCount.toString(),
                    unit = "次",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row {
                MetricTile(
                    title = "平均每次",
                    value = formatVolume(averagePerFeed),
                    unit = "ml",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "单次最多",
                    value = if (feedCount > 0) formatVolume(maxFeed) else "--",
                    unit = "ml",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(14.dp))
            if (range != StatsRange.DAY) {
                Row {
                    MetricTile(
                        title = "日均",
                        value = formatVolume(averagePerDay),
                        unit = "ml",
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = "最多一天",
                        value = if (bestDayML > 0) formatVolume(bestDayML) else "--",
                        unit = "ml",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "本期 " + dayCount + " 天里，有 " + recordedDays + " 天记录了喂奶（日均按有记录的天数算）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// MARK: - 折线图

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    points: List<ChartPoint>,
    lineColor: Color,
    reference: Double,
    legend: String,
    xLabels: List<AxisLabel>,
    emptyHint: String
) {
    AppCard {
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emptyHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LineChartBody(
                    points = points,
                    lineColor = lineColor,
                    reference = reference,
                    xLabels = xLabels
                )
                if (legend.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(legend, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LineChartBody(
    points: List<ChartPoint>,
    lineColor: Color,
    reference: Double,
    xLabels: List<AxisLabel>
) {
    val ordered = remember(points) { points.sortedBy { it.x } }
    var selected by remember(ordered) { mutableStateOf(-1) }

    val maxValue = remember(ordered, reference) {
        val peak = ordered.maxOfOrNull { it.value } ?: 0.0
        val top = Math.max(peak, reference)
        if (top <= 0.0) 1.0 else top * 1.15
    }

    Column {
        Box(modifier = Modifier.fillMaxWidth().height(18.dp)) {
            val picked = ordered.getOrNull(selected)
            if (picked != null) {
                Text(
                    picked.caption + " · " + formatVolume(picked.value) + " ml",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lineColor
                )
            } else if (ordered.size >= 8) {
                Text(
                    "轻点折线上的点，可以看到具体那一次是多少",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row {
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .height(180.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                for (i in 4 downTo 0) {
                    Text(
                        formatVolume(maxValue * i / 4.0),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
                    .pointerInput(ordered, maxValue) {
                        detectTapGestures { tap ->
                            val viewWidth = size.width.toFloat()
                            val viewHeight = size.height.toFloat()
                            var best = -1
                            var bestDistance = Float.MAX_VALUE
                            ordered.forEachIndexed { index, point ->
                                val px = point.x * viewWidth
                                val py = viewHeight - viewHeight * (point.value / maxValue).toFloat()
                                val dx = px - tap.x
                                val dy = py - tap.y
                                val distance = dx * dx + dy * dy
                                if (distance < bestDistance) {
                                    bestDistance = distance
                                    best = index
                                }
                            }
                            selected = best
                        }
                    }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height
                val count = ordered.size

                for (i in 0..4) {
                    val y = chartHeight * i / 4f
                    drawLine(Color(0x14000000), Offset(0f, y), Offset(chartWidth, y), 1f)
                }

                fun xOf(point: ChartPoint): Float = point.x * chartWidth
                fun yOf(value: Double): Float = chartHeight - chartHeight * (value / maxValue).toFloat()

                if (reference > 0.0) {
                    val y = yOf(reference)
                    drawLine(
                        color = ChartOrange.copy(alpha = 0.8f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f)
                    )
                }

                if (count >= 2) {
                    val area = Path()
                    area.moveTo(xOf(ordered[0]), chartHeight)
                    for (point in ordered) {
                        area.lineTo(xOf(point), yOf(point.value))
                    }
                    area.lineTo(xOf(ordered[count - 1]), chartHeight)
                    area.close()
                    drawPath(
                        path = area,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.30f), lineColor.copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = chartHeight
                        )
                    )

                    val line = Path()
                    ordered.forEachIndexed { index, point ->
                        val x = xOf(point)
                        val y = yOf(point.value)
                        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                    drawPath(
                        path = line,
                        color = lineColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                val dotRadius = when {
                    count > 80 -> 1.4.dp.toPx()
                    count > 40 -> 2.0.dp.toPx()
                    count > 20 -> 2.8.dp.toPx()
                    else -> 3.4.dp.toPx()
                }

                ordered.forEachIndexed { index, point ->
                    val center = Offset(xOf(point), yOf(point.value))
                    if (index == selected) {
                        drawLine(
                            color = lineColor.copy(alpha = 0.35f),
                            start = Offset(center.x, 0f),
                            end = Offset(center.x, chartHeight),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    if (count <= 30) {
                        drawCircle(Color.White, dotRadius + 1.dp.toPx(), center)
                    }
                    drawCircle(
                        color = if (index == selected) ChartOrange else lineColor,
                        radius = if (index == selected) dotRadius + 1.5.dp.toPx() else dotRadius,
                        center = center
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row {
            Spacer(Modifier.width(46.dp))
            AxisLabelRow(labels = xLabels, modifier = Modifier.weight(1f).height(18.dp))
        }
    }
}

/** 横轴文字：按给定的 0~1 位置摆放，两端的字不会被切掉 */
@Composable
private fun AxisLabelRow(labels: List<AxisLabel>, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier,
        content = {
            labels.forEach { label ->
                Text(
                    label.text,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val target = labels[index].x * width.toFloat() - placeable.width / 2f
                val clamped = target.coerceIn(0f, Math.max(0f, width - placeable.width.toFloat()))
                placeable.placeRelative(clamped.toInt(), 0)
            }
        }
    }
}

// MARK: - 明细

@Composable
private fun RangeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickableNoRipple { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DayGroupCard(
    date: Long,
    records: List<FeedRecord>,
    store: AppStore,
    onEdit: (FeedRecord) -> Unit
) {
    val totalML = records.fold(0.0) { acc, item -> acc + item.volumeML }
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DateText.dayLabel(date), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    records.size.toString() + " 次",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatVolume(totalML) + " ml",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            records.forEach { record ->
                RecordRow(record = record, store = store, onEdit = onEdit)
            }
        }
    }
}

@Composable
private fun RecordRow(record: FeedRecord, store: AppStore, onEdit: (FeedRecord) -> Unit) {
    val thumbnail = remember(record.photoName) { store.thumbnail(record.photoName) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple { onEdit(record) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumb = thumbnail
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(9.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (record.source == RecordSource.CAMERA) "拍" else "手",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(DateText.time(record.date), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (record.note.isNotEmpty()) {
                Text(
                    record.note,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(record.volumeText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(3.dp))
        Text("ml", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
