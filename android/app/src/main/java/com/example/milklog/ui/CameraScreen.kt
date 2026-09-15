package com.example.milklog.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.milklog.data.AppStore
import com.example.milklog.measure.MeasurementEngine
import com.example.milklog.model.FeedRecord
import com.example.milklog.model.RecordSource
import com.example.milklog.model.formatVolume

/** 主界面：对着奶瓶，自动读出当前奶量。 */
@Composable
fun CameraScreen(
    store: AppStore,
    engine: MeasurementEngine,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenCalibration: () -> Unit,
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

    val profile = store.activeBottle
    val isCalibrated = profile?.isReady == true

    LaunchedEffect(profile) {
        engine.profile = profile
    }

    if (hasPermission) {
        DisposableEffect(previewView, lifecycleOwner) {
            engine.camera.bindPreview(previewView, lifecycleOwner)
            onDispose { engine.camera.releaseSurface(previewView) }
        }
    }

    val currentVolume: Double? = remember(
        pendingVolume,
        engine.settledVolume,
        engine.detection
    ) {
        val pending = pendingVolume
        if (pending != null && pending > 0) return@remember pending
        val settled = engine.settledVolume
        if (settled != null) return@remember settled
        val live = engine.detection
        if (live != null && live.found && live.volumeML != null) return@remember live.volumeML
        null
    }

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
                val frameW = engine.frameSize.width.toFloat()
                val frameH = engine.frameSize.height.toFloat()
                val geo = PreviewGeometry(frameW, frameH, size.width, size.height)
                val left = profile?.bandLeft ?: 0.32
                val right = profile?.bandRight ?: 0.68
                val x0 = geo.bandLeftX(left)
                val x1 = geo.bandRightX(right)
                val top = geo.bandTop()
                val bottom = geo.bandBottom()

                drawRect(
                    color = Color.White.copy(alpha = 0.06f),
                    topLeft = Offset(x0, top),
                    size = Size(Math.max(0f, x1 - x0), Math.max(0f, bottom - top))
                )
                val dash = PathEffect.dashPathEffect(floatArrayOf(20f, 16f), 0f)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x0, top), Offset(x0, bottom), strokeWidth = 4f, pathEffect = dash)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x1, top), Offset(x1, bottom), strokeWidth = 4f, pathEffect = dash)

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
                    store = store,
                    engine = engine,
                    profileName = profile?.name,
                    isCalibrated = isCalibrated,
                    currentVolume = currentVolume,
                    pendingVolume = pendingVolume,
                    onAdjust = { delta ->
                        val base = pendingVolume ?: engine.settledVolume ?: 0.0
                        pendingVolume = Math.max(0.0, base + delta)
                    },
                    onManual = {
                        onEdit(
                            FeedRecord(volumeML = 90.0, source = RecordSource.MANUAL),
                            true
                        )
                    },
                    onCalibrate = onOpenCalibration,
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
    store: AppStore,
    engine: MeasurementEngine,
    profileName: String?,
    isCalibrated: Boolean,
    currentVolume: Double?,
    pendingVolume: Double?,
    onAdjust: (Double) -> Unit,
    onManual: () -> Unit,
    onCalibrate: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        if (!isCalibrated) {
            Text(
                "还没标定奶瓶",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE07B00)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "先用 2~3 个已知奶量（比如 60ml、120ml、180ml）标定一次，之后每次把奶瓶放到固定位置，App 就能自动读出奶量。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f)) { Text("手动输入") }
                Button(onClick = onCalibrate, modifier = Modifier.weight(1f)) { Text("开始标定") }
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (pendingVolume != null) formatVolume(pendingVolume) else settledText(engine),
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
                    SmallAdjustButton("-5") { onAdjust(-5.0) }
                    SmallAdjustButton("+5") { onAdjust(5.0) }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (profileName != null) {
                Text(profileName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            if (currentVolume != null && currentVolume > 0) {
                if (engine.isStable) {
                    StatusPill(if (pendingVolume == null) "读数稳定" else "已手动调整", Color(0xFF2E7D32))
                } else {
                    StatusPill("识别中…", Color(0xFFE07B00))
                }
            } else {
                StatusPill("请把奶瓶放进虚线框内", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text("标定", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickableNoRipple { onCalibrate() })
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

private fun settledText(engine: MeasurementEngine): String {
    val settled = engine.settledVolume
    if (settled != null) return formatVolume(settled)
    val live = engine.detection
    if (live != null && live.found && live.volumeML != null) return formatVolume(live.volumeML!!)
    return "--"
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
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
