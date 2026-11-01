package com.glfx.gameturbo.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glfx.gameturbo.MainViewModel
import com.glfx.gameturbo.core.design.*

enum class Screen(val code: String, val label: String, val group: Int) {
    Home("HM", "Home", 0), Games("GM", "Games", 0), Optimize("OPT", "Optimize", 0), Profiles("PRF", "Profiles", 0),
    Cpu("CPU", "CPU", 1), Gpu("GPU", "GPU", 1), Ram("RAM", "RAM", 1), Thermal("THM", "Thermal", 1), Network("NET", "Network", 1), Battery("BAT", "Battery", 1),
    Benchmark("BNC", "Benchmark", 2), History("HST", "History", 2), Modules("MOD", "Modules", 2),
    Device("DEV", "Device", 3), Permissions("PRM", "Access", 3), Settings("SET", "Settings", 3), About("GLX", "About", 3)
}

@Composable
fun AppRoot(vm: MainViewModel) {
    var current by rememberSaveable { mutableStateOf(Screen.Home) }
    val msg by vm.message.collectAsStateWithLifecycle()

    GlfxBackdrop {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            Rail(current) { current = it }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (current) {
                    Screen.Home -> HomeScreen(vm) { current = it }
                    Screen.Games -> GamesScreen(vm) { current = it }
                    Screen.Optimize -> OptimizeScreen(vm)
                    Screen.Profiles -> ProfilesScreen(vm)
                    Screen.Cpu -> CpuScreen(vm)
                    Screen.Gpu -> GpuScreen(vm)
                    Screen.Ram -> RamScreen(vm)
                    Screen.Thermal -> ThermalScreen(vm)
                    Screen.Network -> NetworkScreen(vm)
                    Screen.Battery -> BatteryScreen(vm)
                    Screen.Benchmark -> BenchmarkScreen(vm)
                    Screen.History -> HistoryScreen(vm)
                    Screen.Modules -> ModulesScreen(vm)
                    Screen.Device -> DeviceScreen(vm)
                    Screen.Permissions -> PermissionsScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.About -> AboutScreen()
                }
                msg?.let { m ->
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).glass(16.dp).clickable { vm.clearMessage() }.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(m, style = GType.Body)
                    }
                }
            }
        }
    }
}

@Composable
private fun Rail(current: Screen, onSelect: (Screen) -> Unit) {
    Column(
        Modifier.width(86.dp).fillMaxHeight().glass(26.dp).verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var last = -1
        Screen.values().forEach { s ->
            if (last != -1 && s.group != last) {
                Box(Modifier.padding(vertical = 6.dp).width(40.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            }
            last = s.group
            val sel = s == current
            Column(Modifier.fillMaxWidth().clickable { onSelect(s) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier.size(width = 54.dp, height = 34.dp).clip(shape)
                        .background(if (sel) Glfx.Ice.copy(alpha = 0.16f) else Color.Transparent)
                        .then(if (sel) Modifier.border(1.dp, Brush.linearGradient(Glfx.Holo), shape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s.code, style = GType.Label.copy(color = if (sel) Glfx.Ice else Glfx.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                }
                Text(s.label, style = GType.Label.copy(fontSize = 10.sp, color = if (sel) Glfx.Text else Glfx.Muted), maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}
