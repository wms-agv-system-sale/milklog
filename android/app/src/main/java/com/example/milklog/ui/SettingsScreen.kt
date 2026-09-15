package com.example.milklog.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.milklog.data.AppStore
import com.example.milklog.model.BottleProfile
import androidx.compose.ui.graphics.Color
import com.example.milklog.model.formatVolume

private val SettingsOrange = Color(0xFFE07B00)

/** 设置：奶瓶与标定、目标、记录管理、说明。 */
@Composable
fun SettingsScreen(
    store: AppStore,
    onEditBottle: (BottleProfile) -> Unit,
    onOpenHelp: () -> Unit
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var targetText by remember { mutableStateOf(formatVolume(store.settings.dailyTargetML)) }

    fun doExport() {
        val file = store.exportFile()
        if (file == null) {
            exportMessage = "导出失败，请稍后再试。"
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "奶量日记 记录导出")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出记录"))
        } catch (t: Throwable) {
            exportMessage = "已导出到手机上的这个位置：\n" + file.absolutePath
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 42.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            Text("设置", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        item(key = "bottles") {
            AppCard {
                Column {
                    SectionTitle("奶瓶与标定")
                    if (store.bottles.isEmpty()) {
                        Text(
                            "还没有奶瓶，先添加一个并完成标定。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    store.bottles.forEach { bottle ->
                        BottleRow(
                            bottle = bottle,
                            isActive = store.activeBottleId == bottle.id
                        ) { onEditBottle(bottle) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "＋ 添加奶瓶",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickableNoRipple { onEditBottle(BottleProfile()) }
                            .padding(vertical = 6.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "标定只需要做一次。之后把奶瓶放在同一个位置，App 就能自动读出奶量。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "target") {
            AppCard {
                Column {
                    SectionTitle("目标")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每日目标", fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { text ->
                                targetText = text
                                val parsed = text.toDoubleOrNull()
                                if (parsed != null) {
                                    store.updateSettings(
                                        store.settings.copy(dailyTargetML = Math.max(0.0, Math.min(parsed, 2000.0)))
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(110.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("ml", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "只影响统计图里的参考虚线，不会限制记录。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "records") {
            AppCard {
                Column {
                    SectionTitle("记录")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("保存识别时的照片", fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = store.settings.keepPhotos,
                            onCheckedChange = { value ->
                                store.updateSettings(store.settings.copy(keepPhotos = value))
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "导出全部记录",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickableNoRipple { doExport() }
                            .padding(vertical = 10.dp)
                    )
                    Text(
                        "清空所有记录",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickableNoRipple { confirmClear = true }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }

        item(key = "help") {
            AppCard {
                Column {
                    SectionTitle("帮助")
                    Text(
                        "使用说明与技巧",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickableNoRipple { onOpenHelp() }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }

        item(key = "about") {
            AppCard {
                Column {
                    SectionTitle("关于")
                    InfoRow("数据存储", "仅保存在本机")
                    InfoRow("联网", "完全不需要")
                    InfoRow("版本", "1.0")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "所有记录、照片和标定数据都只保存在这台手机上，App 不含任何联网功能，也不会收集任何信息。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有记录？") },
            text = { Text("所有奶量记录都会被删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteAllRecords()
                    confirmClear = false
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }

    val message = exportMessage
    if (message != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("导出记录") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) { Text("好") }
            }
        )
    }
}

@Composable
private fun BottleRow(bottle: BottleProfile, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple { onClick() }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                bottle.name,
                fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                if (bottle.isReady) "已标定 " + bottle.points.size + " 个点" else "未完成标定",
                fontSize = 11.sp,
                color = if (bottle.isReady) MaterialTheme.colorScheme.onSurfaceVariant else SettingsOrange
            )
        }
        if (isActive) {
            Text("当前", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(6.dp))
        Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
