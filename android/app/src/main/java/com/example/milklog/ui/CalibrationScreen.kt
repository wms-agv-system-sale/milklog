package com.example.milklog.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.milklog.data.AppStore
import com.example.milklog.measure.MeasurementEngine
import com.example.milklog.model.BottleProfile
import com.example.milklog.model.CalibrationPoint
import com.example.milklog.model.formatVolume
import com.example.milklog.model.newId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CalibGreen = Color(0xFF2E7D32)
private val CalibYellow = Color(0xFFFFC400)

/**
 * 奶瓶标定：用几个已知奶量，教会 App 把"液面高度"换算成"毫升"。
 * 标定只需要做一次。
 */
@Composable
fun CalibrationScreen(
    store: AppStore,
    engine: MeasurementEngine,
    bottle: BottleProfile,
    onSave: (BottleProfile) -> Unit,
    onCancel: () -> Unit
) {
    var draft by remember { mutableStateOf(bottle) }
    var name by remember { mutableStateOf(bottle.name) }
    var selectedVolume by remember { mutableStateOf(120.0) }
    var customText by remember { mutableStateOf("") }
    var tapMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var lastCapturedId by remember { mutableStateOf<String?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    // 保存之后不要再撤销临时标定
    val didSave = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val quickVolumes = listOf(0.0, 30.0, 60.0, 90.0, 120.0, 150.0, 180.0, 210.0, 240.0)

    fun applyDraft(profile: BottleProfile) {
        engine.setBand(profile.bandLeft, profile.bandRight)
        engine.profile = profile
    }

    fun addPoint(y: Double) {
        val volume = selectedVolume
        val existing = draft.points.firstOrNull { Math.abs(it.volumeML - volume) < 0.5 }
        val point = CalibrationPoint(id = existing?.id ?: newId(), volumeML = volume, y = y)
        val points = if (existing != null) {
            draft.points.map { if (it.id == existing.id) point else it }
        } else {
            draft.points + point
        }
        val updated = draft.copy(points = points)
        draft = updated
        lastCapturedId = point.id
        applyDraft(updated)
        message = null
    }

    DisposableEffect(previewView, lifecycleOwner) {
        engine.camera.bindPreview(previewView, lifecycleOwner)
        onDispose { engine.camera.releaseSurface(previewView) }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!didSave.value) {
                engine.clearBand()
                engine.profile = store.activeBottle
            }
        }
    }

    LaunchedEffect(Unit) {
        applyDraft(bottle)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 36.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onCancel() }) { Text("取消") }
            Spacer(Modifier.weight(1f))
            Text("标定奶瓶", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    didSave.value = true
                    val finalName = name.trim().ifEmpty { "我的奶瓶" }
                    onSave(draft.copy(name = finalName))
                },
                enabled = draft.isReady
            ) {
                Text("完成", fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                Box(modifier = Modifier.fillMaxSize().onSizeChanged { previewSize = it }) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val geo = PreviewGeometry(
                            engine.frameSize.width.toFloat(),
                            engine.frameSize.height.toFloat(),
                            size.width,
                            size.height
                        )
                        val x0 = geo.bandLeftX(draft.bandLeft)
                        val x1 = geo.bandRightX(draft.bandRight)
                        val top = geo.pointY(0.0)
                        val bottom = geo.pointY(1.0)

                        drawRect(
                            color = Color.White.copy(alpha = 0.06f),
                            topLeft = Offset(x0, top),
                            size = Size(Math.max(0f, x1 - x0), Math.max(0f, bottom - top))
                        )
                        val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
                        drawLine(Color.White.copy(alpha = 0.55f), Offset(x0, top), Offset(x0, bottom), 3f, pathEffect = dash)
                        drawLine(Color.White.copy(alpha = 0.55f), Offset(x1, top), Offset(x1, bottom), 3f, pathEffect = dash)

                        val detection = engine.detection
                        if (detection != null && detection.found) {
                            val y = geo.pointY(detection.surfaceY)
                            drawLine(
                                color = CalibYellow,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 18f), 0f)
                            )
                        }

                        draft.points.forEach { point ->
                            val y = geo.pointY(point.y)
                            drawLine(CalibGreen, Offset(0f, y), Offset(size.width, y), 4f)
                        }
                    }

                    if (previewSize.width > 0) {
                        val geo = PreviewGeometry(
                            engine.frameSize.width.toFloat(),
                            engine.frameSize.height.toFloat(),
                            previewSize.width.toFloat(),
                            previewSize.height.toFloat()
                        )
                        draft.points.sortedBy { it.y }.forEach { point ->
                            val yPx = geo.pointY(point.y)
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(10.dp.roundToPx(), (yPx - 10.dp.toPx()).roundToInt()) }
                                    .clip(RoundedCornerShape(50))
                                    .background(CalibGreen)
                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    formatVolume(point.volumeML) + "ml",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(tapMode) {
                                detectTapGestures { offset ->
                                    if (tapMode) {
                                        val geo = PreviewGeometry(
                                            engine.frameSize.width.toFloat(),
                                            engine.frameSize.height.toFloat(),
                                            size.width.toFloat(),
                                            size.height.toFloat()
                                        )
                                        val ny = geo.normalizedY(offset.y)
                                        if (ny != null) {
                                            val volume = selectedVolume
                                            addPoint(ny)
                                            tapMode = false
                                            message = "已记录 " + formatVolume(volume) + " ml 的液面位置"
                                        } else {
                                            message = "请点在画面里面的位置"
                                        }
                                    }
                                }
                            }
                    )

                    if (tapMode) {
                        Box(
                            modifier = Modifier
                                .padding(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("点击画面中液面的位置", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            val currentMessage = message
            if (currentMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    currentMessage,
                    fontSize = 12.sp,
                    color = Color(0xFFE07B00),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(14.dp))

            AppCard {
                Column {
                    Text("选择奶瓶里的奶量", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickVolumes.forEach { value ->
                            Chip(
                                text = if (value == 0.0) "空瓶(0)" else formatVolume(value) + "ml",
                                selected = Math.abs(selectedVolume - value) < 0.5
                            ) {
                                selectedVolume = value
                                customText = ""
                                message = null
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { text ->
                                customText = text
                                val parsed = text.toDoubleOrNull()
                                if (parsed != null) selectedVolume = Math.max(0.0, Math.min(parsed, 400.0))
                            },
                            label = { Text("自定义数值") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(140.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("当前 " + formatVolume(selectedVolume) + " ml", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                busy = true
                                message = null
                                scope.launch {
                                    delay(700)
                                    busy = false
                                    val detection = engine.detection
                                    if (detection == null || !detection.found) {
                                        message = "没有识别到液面。请把奶瓶对准虚线框，或者改用「点选液面」手动指定。"
                                    } else {
                                        addPoint(detection.surfaceY)
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (busy) "识别中…" else "识别并记录")
                        }
                        OutlinedButton(
                            onClick = {
                                tapMode = !tapMode
                                message = if (tapMode) "现在点击画面上液面的位置" else null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (tapMode) "取消点选" else "点选液面")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "提示：记录 0ml 时，请点选瓶底的位置（也就是「空瓶时液面」所在的位置）。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            AppCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("已记录 " + draft.points.size + " 个点", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (draft.isReady) {
                            Text("可以保存", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CalibGreen)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (draft.points.isEmpty()) {
                        Text(
                            "还没有记录点。至少需要 2 个不同奶量才能保存。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        draft.points.sortedBy { it.volumeML }.forEach { point ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(formatVolume(point.volumeML) + " ml", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "液面位置 " + (point.y * 100).roundToInt() + "%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.weight(1f))
                                if (lastCapturedId == point.id) {
                                    Text("已记录", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CalibGreen)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    "删除",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickableNoRipple {
                                        val updated = draft.copy(points = draft.points.filter { it.id != point.id })
                                        draft = updated
                                        applyDraft(updated)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            AppCard {
                Column {
                    Text("取样区域", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "虚线框是 App 分析的范围，请让它正好罩住瓶身（不含背景），可以提高识别准确度。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("左边界", fontSize = 12.sp, modifier = Modifier.width(52.dp))
                        Slider(
                            value = draft.bandLeft.toFloat(),
                            onValueChange = { value ->
                                val left = Math.min(value.toDouble(), draft.bandRight - 0.05)
                                val updated = draft.copy(bandLeft = Math.max(0.0, left))
                                draft = updated
                                applyDraft(updated)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("右边界", fontSize = 12.sp, modifier = Modifier.width(52.dp))
                        Slider(
                            value = draft.bandRight.toFloat(),
                            onValueChange = { value ->
                                val right = Math.max(value.toDouble(), draft.bandLeft + 0.05)
                                val updated = draft.copy(bandRight = Math.min(1.0, right))
                                draft = updated
                                applyDraft(updated)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            AppCard {
                Column {
                    Text("奶瓶名称", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("例如：贝亲 240ml") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("怎么标定才准？", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. 把手机固定在一个位置（用支架最好），奶瓶每次放在同一个点上。\n" +
                            "2. 倒出或冲调一个已知奶量，放进画面，点「识别并记录」。\n" +
                            "3. 换 1~2 个不同的奶量（比如 60 和 180）再记录一次。\n" +
                            "4. 完成后，每次只要把奶瓶放到同一个位置，App 就会自动读数。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickableNoRipple { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
