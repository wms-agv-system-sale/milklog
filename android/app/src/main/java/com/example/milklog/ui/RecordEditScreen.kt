package com.example.milklog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.milklog.data.AppStore
import com.example.milklog.model.DateText
import com.example.milklog.model.FeedRecord
import com.example.milklog.model.formatVolume
import java.util.Calendar

/** 确认 / 修改一条记录。识别结果可以在这里按实际情况改数值。 */
@Composable
fun RecordEditScreen(
    store: AppStore,
    record: FeedRecord,
    isNew: Boolean,
    onClose: () -> Unit
) {
    var volumeText by remember { mutableStateOf(if (record.volumeML > 0) formatVolume(record.volumeML) else "") }
    var volume by remember { mutableStateOf(record.volumeML) }
    var note by remember { mutableStateOf(record.note) }
    var calMillis by remember { mutableStateOf(record.date) }
    var confirmDelete by remember { mutableStateOf(false) }

    val quick = listOf(30.0, 60.0, 90.0, 120.0, 150.0, 180.0, 210.0, 240.0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = if (isNew) "确认记录" else "编辑记录",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))

            AppCard {
                Column {
                    Text("奶量", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = volumeText,
                            onValueChange = { text ->
                                volumeText = text
                                val parsed = text.toDoubleOrNull()
                                if (parsed != null) volume = Math.min(Math.max(0.0, parsed), 300.0)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(140.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ml",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = Math.min(volume, 300.0).toFloat(),
                        onValueChange = { value ->
                            volume = value.toDouble()
                            volumeText = formatVolume(volume)
                        },
                        valueRange = 0f..300f
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quick.forEach { value ->
                            QuickChip(
                                text = value.toInt().toString(),
                                selected = Math.abs(volume - value) < 0.5
                            ) {
                                volume = value
                                volumeText = formatVolume(value)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            AppCard {
                Column {
                    Text("时间", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    TimeStepper(calMillis) { calMillis = it }
                }
            }

            Spacer(Modifier.height(12.dp))
            AppCard {
                Column {
                    Text("备注", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val photoName = record.photoName
            if (photoName != null) {
                val bitmap = remember(photoName) { store.photo(photoName) }
                if (bitmap != null) {
                    Spacer(Modifier.height(12.dp))
                    AppCard {
                        Column {
                            Text("识别时的照片", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.source.label,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    val confidence = record.confidence
                    if (confidence != null) {
                        Text(
                            "识别置信度 " + (confidence * 100).toInt() + "%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val fixed = record.copy(
                            volumeML = Math.min(Math.max(0.0, volume), 300.0),
                            note = note,
                            date = calMillis
                        )
                        if (isNew) store.add(fixed) else store.update(fixed)
                        onClose()
                    },
                    enabled = volume > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }

            if (!isNew) {
                Spacer(Modifier.height(10.dp))
                if (confirmDelete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) {
                            Text("不删了")
                        }
                        Button(
                            onClick = {
                                store.deleteRecord(record.id)
                                onClose()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("确认删除")
                        }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TimeStepper(millis: Long, onChange: (Long) -> Unit) {
    val stepMillis = 10L * 60L * 1000L
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onChange(millis - stepMillis) }) { Text("-10分") }
            Spacer(Modifier.width(10.dp))
            Text(DateText.full(millis), fontSize = 14.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { onChange(millis + stepMillis) }) { Text("+10分") }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onChange(System.currentTimeMillis()) }) { Text("设为现在") }
            OutlinedButton(onClick = { onChange(millis - 24L * 60L * 60L * 1000L) }) { Text("前一天") }
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = millis
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    onChange(cal.timeInMillis)
                }
            ) { Text("后一天") }
        }
    }
}
