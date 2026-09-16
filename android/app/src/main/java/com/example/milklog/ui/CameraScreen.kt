package com.example.milklog.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.milklog.data.AppStore
import com.example.milklog.measure.MeasurementEngine
import com.example.milklog.measure.ScaleMark
import com.example.milklog.model.FeedRecord
import com.example.milklog.model.RecordSource
import com.example.milklog.model.formatVolume

/** 主界面：对着奶瓶拍，自动读出奶量。 */
@Composable
fun CameraScreen(
    store: AppStore,
    engine: MeasurementEngine,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onEdit: (FeedRecord, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var pendingVolume by remember { mutableStateOf<Double?>(null) }

    if (hasPermission) {
        DisposableEffect(previewView, lifecycleOwner) {
            engine.camera.bindPreview(previewView, lifecycleOwner)
            onDispose { engine.camera.releaseSurface(previewView) }
        }
    }

    val currentVolume: Double? = pendingVolume ?: engine.volumeML
    val marks = if (engine.fittedMarks.isNotEmpty()) engine.fittedMarks else engine.marks

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("需要相机权限", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "这个 App 要用相机拍摄奶瓶才能自动识别奶量。所有画面只在本机处理，不会上传。",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onRequestPermission) { Text("允许使用相机") }
            }
        } else {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val geo = PreviewGeometry(
                    engine.frameSize.width.toFloat(),
                    engine.frameSize.height.toFloat(),
                    size.width,
                    size.height
                )

                // 读到的刻度数字所在的高度，用蓝色细线标出来
                for (mark in marks) {
                    val y = geo.pointY(mark.y)
                    drawLine(
                        color = Color(0xFF6EC6FF).copy(alpha = 0.55f),
                        start = Offset(size.width * 0.05f, y),
                        end = Offset(size.width * 0.95f, y),
                        strokeWidth = 2f
                    )
                }

                // 识别到的液面
                val detection = engine.detection
                if (detection != null && detection.found) {
                    val y = geo.pointY(detection.surfaceY)
                    val lineColor = if (engine.isStable) Color(0xFF43D17A) else Color(0xFFFFD54F)
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 6f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 20f), 0f)
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(store, engine)
                Spacer(Modifier.weight(1f))
                BottomPanel(
                    engine = engine,
                    marks = marks,
                    currentVolume = currentVolume,
                    pendingVolume = pendingVolume,
                    onAdjust = { delta ->
                        val base = pendingVolume ?: engine.volumeML ?: 0.0
                        pendingVolume = Math.max(0.0, base + delta)
                    },
                    onManual = {
                        onEdit(
                            FeedRecord(
                                volumeML = currentVolume ?: 60.0,
                                source = RecordSource.MANUAL
                            ),
                            true
                        )
                    },
                    onSave = {
                        val volume = currentVolume
                        if (volume != null && volume > 0) {
                            var record = FeedRecord(
                                volumeML = volume,
                                source = RecordSource.CAMERA,
                                confidence = engine.detection?.confidence
                            )
                            if (store.settings.keepPhotos) {
                                val image = engine.latestImage
                                if (image != null) {
                                    val name = store.savePhoto(image)
                                    if (name != null) record = record.copy(photoName = name)
                                }
                            }
                            onEdit(record, true)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TopBar(store: AppStore, engine: MeasurementEngine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.38f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("今天", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            Text(
                formatVolume(store.todayTotalML) + " ml · " + store.todayRecords.size + " 次",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.weight(1f))
        if (engine.camera.torchAvailable) {
            CircleButton(
                label = if (engine.torchOn) "关灯" else "补光",
                highlighted = engine.torchOn
            ) { engine.toggleTorch() }
        }
    }
}

@Composable
private fun CircleButton(label: String, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (highlighted) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.38f))
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BottomPanel(
    engine: MeasurementEngine,
    marks: List<ScaleMark>,
    currentVolume: Double?,
    pendingVolume: Double?,
    onAdjust: (Double) -> Unit,
    onManual: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            val shown = pendingVolume ?: currentVolume
            Text(
                text = if (shown == null) "--" else formatVolume(shown),
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "ml",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Spacer(Modifier.weight(1f))
            if (currentVolume != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAdjustButton("-10") { onAdjust(-10.0) }
                    SmallAdjustButton("+10") { onAdjust(10.0) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        StatusPill(statusText(engine, marks), statusColor(engine, marks))

        Spacer(Modifier.height(8.dp))
        if (marks.isNotEmpty()) {
            Text(
                "读到的刻度：" + marks.joinToString(" / ") { formatVolume(it.valueML) } + " ml",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "还没读到刻度数字：把奶瓶凑近些，让瓶身上的数字正对镜头、占满画面中间。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f)) { Text("手动输入") }
            Button(
                onClick = onSave,
                enabled = currentVolume != null,
                modifier = Modifier.weight(1f)
            ) { Text("保存记录") }
        }
    }
}

private fun statusText(engine: MeasurementEngine, marks: List<ScaleMark>): String {
    return when {
        engine.volumeML != null && engine.isStable -> "读数稳定"
        engine.volumeML != null -> "识别中…"
        marks.size >= 2 -> "正在找液面…"
        marks.size == 1 -> "只读到一个刻度，稍微退后一点拍全"
        else -> "正在找瓶身上的刻度数字…"
    }
}

private fun statusColor(engine: MeasurementEngine, marks: List<ScaleMark>): Color {
    return when {
        engine.volumeML == null -> Color(0xFFE07B00)
        engine.isStable -> Color(0xFF2E7D32)
        else -> Color(0xFFE07B00)
    }
}

@Composable
private fun SmallAdjustButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
