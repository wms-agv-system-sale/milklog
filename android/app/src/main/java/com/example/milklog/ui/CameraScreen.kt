package com.example.milklog.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

/** 主界面：对准奶瓶按一下快门，App 只识别这一张照片。 */
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

    val photo = engine.photo

    LaunchedEffect(photo) {
        pendingVolume = null
    }

    if (hasPermission && photo == null) {
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
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(store, engine)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 相机取景画面一直保留在最底层
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                if (photo == null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val frameWidth = size.width * 0.74f
                        val frameHeight = size.height * 0.62f
                        val left = (size.width - frameWidth) / 2f
                        val top = (size.height - frameHeight) / 2f
                        val dash = PathEffect.dashPathEffect(floatArrayOf(20f, 16f), 0f)
                        drawRect(
                            color = Color.White.copy(alpha = 0.75f),
                            topLeft = Offset(left, top),
                            size = Size(frameWidth, frameHeight),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 4f,
                                pathEffect = dash
                            )
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        Image(
                            bitmap = photo.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val imageWidth = photo.width.toFloat()
                            val imageHeight = photo.height.toFloat()
                            if (imageWidth <= 0f || imageHeight <= 0f) return@Canvas
                            val scale = Math.min(size.width / imageWidth, size.height / imageHeight)
                            val drawnWidth = imageWidth * scale
                            val drawnHeight = imageHeight * scale
                            val originX = (size.width - drawnWidth) / 2f
                            val originY = (size.height - drawnHeight) / 2f

                            // 读到的刻度数字所在的高度
                            for (mark in marks) {
                                val lineY = originY + mark.y.toFloat() * drawnHeight
                                drawLine(
                                    color = Color(0xFF6EC6FF).copy(alpha = 0.7f),
                                    start = Offset(originX, lineY),
                                    end = Offset(originX + drawnWidth, lineY),
                                    strokeWidth = 2f
                                )
                            }

                            // 识别到的液面
                            val detection = engine.detection
                            if (detection != null && detection.found) {
                                val lineY = originY + detection.surfaceY.toFloat() * drawnHeight
                                drawLine(
                                    color = Color(0xFF43D17A),
                                    start = Offset(originX, lineY),
                                    end = Offset(originX + drawnWidth, lineY),
                                    strokeWidth = 5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f), 0f)
                                )
                            }
                        }
                    }
                }
            }

            BottomPanel(
                engine = engine,
                marks = marks,
                currentVolume = currentVolume,
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
                onRetake = { engine.reset() },
                onSave = {
                    val volume = currentVolume
                    val picture = engine.photo
                    if (volume != null && volume > 0) {
                        var record = FeedRecord(
                            volumeML = volume,
                            source = RecordSource.CAMERA,
                            confidence = engine.detection?.confidence
                        )
                        if (store.settings.keepPhotos && picture != null) {
                            val name = store.savePhoto(picture)
                            if (name != null) record = record.copy(photoName = name)
                        }
                        engine.reset()
                        onEdit(record, true)
                    }
                }
            )
        }
    }
}

@Composable
private fun TopBar(store: AppStore, engine: MeasurementEngine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
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
    onAdjust: (Double) -> Unit,
    onManual: () -> Unit,
    onRetake: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        if (engine.analyzing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("正在识别这张照片…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "读瓶身上的刻度数字 + 找牛奶的液面，一两秒就好。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        if (engine.photo == null) {
            Text(
                "把奶瓶放正，让瓶身上的刻度数字落在中间的方框里，然后按下面这个按钮。",
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickableNoRipple { engine.capture() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("拍照", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                Text("直接手动输入")
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (currentVolume == null) "--" else formatVolume(currentVolume),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAdjustButton("-10") { onAdjust(-10.0) }
                SmallAdjustButton("+10") { onAdjust(10.0) }
            }
        }

        Spacer(Modifier.height(8.dp))
        val hint = engine.message
        if (hint == null) {
            StatusPill("识别完成", Color(0xFF2E7D32))
        } else {
            StatusPill("没识别出来", Color(0xFFE07B00))
        }

        Spacer(Modifier.height(8.dp))
        if (marks.isNotEmpty()) {
            Text(
                "照片里读到的刻度：" + marks.joinToString(" / ") { formatVolume(it.valueML) } + " ml",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, fontSize = 12.sp, color = Color(0xFFE07B00))
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("重拍") }
            Button(
                onClick = onSave,
                enabled = (currentVolume ?: 0.0) > 0,
                modifier = Modifier.weight(1f)
            ) { Text("保存记录") }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
            Text("手动输入奶量")
        }
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
