package com.example.milklog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.example.milklog.data.AppStore
import com.example.milklog.model.FeedRecord
import com.example.milklog.model.RecordSource
import com.example.milklog.ui.HelpScreen
import com.example.milklog.ui.MilkLogTheme
import com.example.milklog.ui.RecordEditScreen
import com.example.milklog.ui.SettingsScreen
import com.example.milklog.ui.StatsScreen
import com.example.milklog.ui.TodayScreen

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

    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<FeedRecord?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
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
                        0 -> TodayScreen(
                            store = store,
                            onAdd = {
                                editingIsNew = true
                                editing = FeedRecord(volumeML = 0.0, source = RecordSource.MANUAL)
                            },
                            onEdit = { record ->
                                editingIsNew = false
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
