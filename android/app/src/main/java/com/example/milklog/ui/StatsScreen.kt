package com.example.milklog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ChartOrange = Color(0xFFE07B00)

/** 奶量统计：日 / 周 / 月折线图 + 明细。 */
@Composable
fun StatsScreen(store: AppStore, onEdit: (FeedRecord) -> Unit) {
    var range by remember { mutableStateOf(StatsRange.DAY) }

    val buckets = remember(store.feeds, range) { Stats.buckets(range, store.feeds) }
    val activeBuckets = buckets.filter { it.count > 0 }
    val totalCount = buckets.sumOf { it.count }
    val averagePerBucket = if (activeBuckets.isEmpty()) 0.0 else activeBuckets.sumOf { it.totalML } / activeBuckets.size.toDouble()
    val averagePerFeed = if (totalCount > 0) buckets.sumOf { it.totalML } / totalCount.toDouble() else 0.0
    val bestBucket = activeBuckets.maxByOrNull { it.totalML }

    val dayGroups = remember(store.feeds, range) {
        val start = Stats.spanStart(range)
        store.feeds
            .filter { it.date >= start }
            .groupBy { DateText.dayStart(it.date) }
            .toList()
            .sortedByDescending { it.first }
            .map { it.first to it.second.sortedByDescending { r -> r.date } }
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
                        text = item.title,
                        selected = range == item,
                        modifier = Modifier.weight(1f)
                    ) { range = item }
                }
            }
        }

        item(key = "summary") {
            AppCard {
                Column {
                    Row {
                        MetricTile(
                            title = range.averageTitle,
                            value = formatVolume(averagePerBucket),
                            unit = "ml",
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "平均每次",
                            value = formatVolume(averagePerFeed),
                            unit = "ml",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row {
                        MetricTile(
                            title = "记录次数",
                            value = totalCount.toString(),
                            unit = "次",
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = range.peakTitle,
                            value = bestBucket?.let { formatVolume(it.totalML) } ?: "--",
                            unit = "ml",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "chart") {
            AppCard {
                Column {
                    Text(
                        range.chartTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    MilkChart(buckets = buckets, range = range, dailyTarget = store.settings.dailyTargetML)
                    if (range == StatsRange.DAY && store.settings.dailyTargetML > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "橙色虚线 = 每日目标 " + formatVolume(store.settings.dailyTargetML) + " ml（可在设置里修改）",
                            fontSize = 10.sp,
                            color = ChartOrange
                        )
                    }
                }
            }
        }

        if (dayGroups.isEmpty()) {
            item(key = "empty") {
                AppCard {
                    Column {
                        Text("这段时间还没有记录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "回到「记录」页，对准奶瓶拍一下就能记一笔；也可以手动输入。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(dayGroups, key = { it.first }) { group ->
            DayGroupCard(date = group.first, records = group.second, store = store, onEdit = onEdit)
        }
    }
}

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
private fun MilkChart(buckets: List<StatBucket>, range: StatsRange, dailyTarget: Double) {
    val maxValue = remember(buckets, range, dailyTarget) {
        var value = buckets.maxOfOrNull { it.totalML } ?: 0.0
        if (range == StatsRange.DAY && dailyTarget > 0) value = Math.max(value, dailyTarget)
        if (value <= 0.0) 1.0 else value * 1.15
    }

    val labelIndices = remember(buckets) {
        val n = buckets.size
        if (n == 0) emptyList()
        else listOf(0, (n - 1) / 3, (n - 1) * 2 / 3, n - 1).distinct()
    }

    Column {
        Row {
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .height(190.dp),
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

            Canvas(modifier = Modifier.weight(1f).height(190.dp)) {
                val w = size.width
                val h = size.height
                val n = buckets.size
                val lineColor = Color(0xFF1E88E5)

                fun xAt(index: Int): Float {
                    if (n <= 1) return w / 2f
                    return w * index.toFloat() / (n - 1).toFloat()
                }

                fun yAt(value: Double): Float {
                    val ratio = (value / maxValue).coerceIn(0.0, 1.0)
                    return (h - h * ratio).toFloat()
                }

                for (i in 0..4) {
                    val y = h * i / 4f
                    drawLine(Color(0x14000000), Offset(0f, y), Offset(w, y), 1f)
                }

                if (range == StatsRange.DAY && dailyTarget > 0) {
                    val y = yAt(dailyTarget)
                    drawLine(
                        color = ChartOrange.copy(alpha = 0.85f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                }

                if (n == 0) return@Canvas

                val area = Path()
                area.moveTo(xAt(0), h)
                for (i in 0 until n) {
                    area.lineTo(xAt(i), yAt(buckets[i].totalML))
                }
                area.lineTo(xAt(n - 1), h)
                area.close()
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.03f)),
                        startY = 0f,
                        endY = h
                    )
                )

                val line = Path()
                for (i in 0 until n) {
                    val x = xAt(i)
                    val y = yAt(buckets[i].totalML)
                    if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }
                drawPath(
                    path = line,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                for (i in 0 until n) {
                    val x = xAt(i)
                    val y = yAt(buckets[i].totalML)
                    if (buckets[i].totalML > 0) {
                        drawCircle(lineColor, 4.dp.toPx(), Offset(x, y))
                    } else {
                        drawCircle(Color(0xFF9E9E9E).copy(alpha = 0.45f), 2.5.dp.toPx(), Offset(x, y))
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row {
            Spacer(Modifier.width(46.dp))
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    labelIndices.forEach { index ->
                        Text(
                            axisLabel(range, buckets[index].start),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun axisLabel(range: StatsRange, date: Long): String {
    return SimpleDateFormat(range.axisFormat, Locale.CHINA).format(Date(date))
}

@Composable
private fun DayGroupCard(date: Long, records: List<FeedRecord>, store: AppStore, onEdit: (FeedRecord) -> Unit) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DateText.dayLabel(date), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    formatVolume(records.sumOf { it.volumeML }) + " ml",
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
