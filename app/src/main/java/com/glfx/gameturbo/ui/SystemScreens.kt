package com.glfx.gameturbo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glfx.gameturbo.BuildConfig
import com.glfx.gameturbo.MainViewModel
import com.glfx.gameturbo.core.design.*
import com.glfx.gameturbo.feature.optimization.Mode
import com.glfx.gameturbo.feature.panel.Side

@Composable
fun DeviceScreen(vm: MainViewModel) {
    val d by vm.device.collectAsStateWithLifecycle()
    ScreenFrame("Device information", "Read from Android on this phone.") {
        val x = d
        if (x == null) Text("Reading device…", style = GType.Body) else TwoPane(
            left = {
                FactsCard("Device", listOf(
                    "Manufacturer" to x.manufacturer, "Brand" to x.brand, "Model" to x.model,
                    "Android" to "${x.release} (API ${x.apiLevel})", "Security patch" to (x.securityPatch ?: "Not reported"),
                    "Total memory" to "${x.totalRamMb} MB"
                ))
                FactsCard("Processor", listOf("Cores" to "${x.cores}", "SoC" to (x.soc ?: "Not reported"), "Hardware" to x.hardware, "ABIs" to x.abis.joinToString(", ")))
            },
            right = {
                FactsCard("Graphics and display", listOf(
                    "GPU" to (x.gpuRenderer ?: "Not reported"), "OpenGL ES" to x.glEs, "Vulkan" to x.vulkan, "Refresh rate" to "${x.currentHz.toInt()} Hz"
                ))
                FactsCard("API availability", listOf(
                    "Thermal status API (Android 10+)" to yn(x.hasThermalApi), "Sustained performance mode" to yn(x.hasSustainedPerf),
                    "Performance hint API (Android 12+)" to yn(x.hasPerfHintApi)
                ))
            }
        )
    }
}

private fun yn(b: Boolean) = if (b) "Available" else "Not available"

@Composable
private fun AccessCard(title: String, granted: Boolean, why: String, button: String?, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = GType.Title)
            Text(if (granted) "Granted" else "Not granted", style = GType.Label.copy(color = if (granted) Glfx.Good else Glfx.Warn))
        }
        Text(why, style = GType.Label, modifier = Modifier.padding(vertical = 8.dp))
        if (button != null) GhostButton(button, onClick)
    }
}

@Composable
fun PermissionsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val dnd by vm.dndGranted.collectAsStateWithLifecycle()
    val overlay by vm.overlayGranted.collectAsStateWithLifecycle()
    val write by vm.writeGranted.collectAsStateWithLifecycle()
    val notif by vm.notifGranted.collectAsStateWithLifecycle()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }

    fun open(action: String, withPackage: Boolean) {
        val i = Intent(action)
        if (withPackage) i.data = Uri.parse("package:${ctx.packageName}")
        runCatching { ctx.startActivity(i) }
    }

    ScreenFrame("Access center", "GLFX asks for the least access it can. Each one is optional and explained.") {
        TwoPane(
            left = {
                AccessCard("Display over other apps", overlay, "Needed to show the side panel on top of your game.",
                    if (Build.VERSION.SDK_INT >= 23) (if (overlay) "Review in Settings" else "Open Settings") else null
                ) { open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }
                AccessCard("Do Not Disturb access", dnd, "Lets GLFX silence non-priority notifications during a session and put your previous setting back afterwards.",
                    if (Build.VERSION.SDK_INT >= 23) (if (dnd) "Review in Settings" else "Open Settings") else null
                ) { open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false) }
                AccessCard("Modify system settings", write, "Only used by the panel's brightness slider. The original brightness is restored when the panel stops.",
                    if (Build.VERSION.SDK_INT >= 23) (if (write) "Review in Settings" else "Open Settings") else null
                ) { open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true) }
            },
            right = {
                if (Build.VERSION.SDK_INT >= 33) AccessCard("Notifications", notif, "Shows the notification that stays visible while the panel runs, with a Stop button.",
                    if (notif) null else "Allow notifications"
                ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                FactsCard("Granted at install", listOf(
                    "Internet" to "Used only by the network test", "Network state" to "Reads connection type",
                    "Wi-Fi state" to "Reads link speed and signal", "Foreground service" to "Keeps the panel visible and stoppable"
                ))
                FactsCard("Never required", listOf(
                    "Root" to "Not used", "Shizuku" to "Not used", "ADB or a PC" to "Not used",
                    "Location" to "Not requested", "All-apps visibility" to "Not requested. Launcher apps only.",
                    "Usage access" to "Not requested"
                ))
            }
        )
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val mode by vm.mode.collectAsStateWithLifecycle()
    val immersive by vm.immersive.collectAsStateWithLifecycle()
    val panelAuto by vm.panelAuto.collectAsStateWithLifecycle()
    val side by vm.panelSide.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.export().toByteArray()) } != null }.getOrDefault(false)
            vm.toast(if (ok) "Backup saved." else "Could not save the backup.")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            vm.toast(if (text != null && vm.import(text)) "Backup restored." else "That file is not a GLFX backup.")
        }
    }

    ScreenFrame("Settings") {
        TwoPane(
            left = {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Default mode", style = GType.Title)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mode.values().forEach { Chip(it.short, it == mode) { vm.setMode(it) } }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Full-screen mode", style = GType.Title)
                            Text("Hides the system bars. Swipe from the edge to show them.", style = GType.Label)
                        }
                        Switch(immersive, { vm.setImmersive(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Glfx.Ice, checkedThumbColor = Glfx.Ink))
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Side panel during sessions", style = GType.Title)
                            Text("Starts the game panel when you press Optimize now. Needs Display over other apps.", style = GType.Label)
                        }
                        Switch(panelAuto, { vm.setPanelAuto(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Glfx.Ice, checkedThumbColor = Glfx.Ink))
                    }
                    Text("Panel side", style = GType.Label, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Left", side == Side.LEFT) { vm.setPanelSide(Side.LEFT) }
                        Chip("Right", side == Side.RIGHT) { vm.setPanelSide(Side.RIGHT) }
                    }
                }
            },
            right = {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Backup and restore", style = GType.Title)
                    Text("Saves modes, library choices, history and results to a file you choose.", style = GType.Label, modifier = Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Save backup", { create.launch("glfx-backup.json") })
                        GhostButton("Restore backup", { open.launch(arrayOf("application/json", "text/plain", "*/*")) })
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Your data", style = GType.Title)
                    Text("Everything stays on this device. Deleting it cannot be undone.", style = GType.Label, modifier = Modifier.padding(vertical = 6.dp))
                    GhostButton("Delete all GLFX data", { confirm = true })
                }
            }
        )
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = Glfx.Graphite,
        title = { Text("Delete all GLFX data?", style = GType.Headline) },
        text = { Text("Modes, library choices, history and benchmark results will be removed.", style = GType.Body) },
        confirmButton = { TextButton({ vm.clearAll(); confirm = false }) { Text("Delete", style = GType.Title.copy(color = Glfx.Bad)) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Keep", style = GType.Title) } }
    )
}

@Composable
fun AboutScreen() {
    ScreenFrame("GLFX GAME TURBO", "Maximum real performance. Zero fake optimization.") {
        TwoPane(
            left = {
                FactsCard("App", listOf("Version" to BuildConfig.VERSION_NAME, "Package" to "com.glfx.gameturbo", "Orientation" to "Landscape"))
            },
            right = {
                AdviceCard(listOf(
                    "No root, Shizuku or ADB.",
                    "No fake optimization and no invented FPS numbers.",
                    "No hidden background activity. Monitoring runs only while GLFX is on screen.",
                    "Anything Android does not allow is shown as unsupported, never as done."
                ), "Rules GLFX follows")
            }
        )
    }
}
