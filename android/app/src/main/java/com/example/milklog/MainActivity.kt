package com.example.milklog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.milklog.data.AppStore
import com.example.milklog.measure.CameraController
import com.example.milklog.measure.MeasurementEngine
import com.example.milklog.model.BottleProfile
import com.example.milklog.model.FeedRecord
import com.example.milklog.ui.CalibrationScreen
import com.example.milklog.ui.CameraScreen
import com.example.milklog.ui.HelpScreen
import com.example.milklog.ui.MilkLogTheme
import com.example.milklog.ui.RecordEditScreen
import com.example.milklog.ui.SettingsScreen
import com.example.milklog.ui.StatsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MilkLogTheme {
                MilkLogApp()
            }
        }
    }
}

@Composable
private fun MilkLogApp() {
    val context = LocalContext.current
    val store = remember { AppStore(context.applicationContext) }
    val engine = remember { MeasurementEngine(CameraController(context.applicationContext)) }

    var tab by rememberSaveable { mutableStateOf(0) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var editing by remember { mutableStateOf<FeedRecord?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var calibration by remember { mutableStateOf<BottleProfile?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) engine.camera.permissionDenied = true
    }

    // 不在记录页的时候关掉相机，省电
    LaunchedEffect(tab, calibration, showHelp) {
        if (calibration == null && !showHelp && tab != 0) {
            engine.setTorch(false)
            engine.camera.unbind()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.setTorch(false)
            engine.camera.unbind()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val bottleForCalibration = calibration
        when {
            bottleForCalibration != null -> CalibrationScreen(
                store = store,
                engine = engine,
                bottle = bottleForCalibration,
                onSave = { saved ->
                    store.upsert(saved)
                    store.setActiveBottle(saved.id)
                    engine.clearBand()
                    engine.profile = saved
                    calibration = null
                },
                onCancel = { calibration = null }
            )

            showHelp -> HelpScreen(onBack = { showHelp = false })

            else -> Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Text("🍼", fontSize = 18.sp) },
                            label = { Text("记录") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Text("📊", fontSize = 18.sp) },
                            label = { Text("统计") }
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Text("⚙️", fontSize = 18.sp) },
                            label = { Text("设置") }
                        )
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when (tab) {
                        0 -> CameraScreen(
                            store = store,
                            engine = engine,
                            hasPermission = hasPermission,
                            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            onOpenCalibration = { calibration = store.activeBottle ?: BottleProfile() },
                            onEdit = { record, isNew ->
                                editingIsNew = isNew
                                editing = record
                            }
                        )

                        1 -> StatsScreen(
                            store = store,
                            onEdit = { record ->
                                editingIsNew = false
                                editing = record
                            }
                        )

                        else -> SettingsScreen(
                            store = store,
                            onEditBottle = { bottle -> calibration = bottle },
                            onOpenHelp = { showHelp = true }
                        )
                    }
                }
            }
        }

        val record = editing
        if (record != null) {
            RecordEditScreen(
                store = store,
                record = record,
                isNew = editingIsNew,
                onClose = { editing = null }
            )
        }
    }
}
