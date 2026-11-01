package com.glfx.gameturbo.core.modules

import com.glfx.gameturbo.core.device.DeviceProfile

enum class Category(val label: String, val target: Int) {
    CPU("CPU Performance Management", 50),
    GPU("GPU Performance Monitoring", 45),
    RAM("RAM and Memory Management", 40),
    GAMING("Gaming Performance Engine", 50),
    TOUCH("Touch and Input Diagnostics", 35),
    NETWORK("Network Diagnostics", 35),
    THERMAL("Thermal Intelligence", 35),
    BATTERY("Battery Management", 30),
    SYSTEM("Android System Diagnostics", 45),
    PROFILES("Gaming Profiles and Automation", 35)
}

enum class Status(val code: String, val label: String) {
    FULL("A", "Fully implemented"),
    PARTIAL("B", "Partially implemented"),
    MONITOR("C", "Monitoring only"),
    UNSUPPORTED("D", "Unsupported on this device"),
    PERMISSION("E", "Requires user permission")
}

data class Module(
    val id: String, val category: Category, val name: String, val description: String,
    val implementation: String, val minApi: Int, val permission: String?, val status: Status,
    val rollback: String?
)

object ModuleCatalog {
    /** Status after checking this device: API level, permission state and structural limits. */
    fun effectiveStatus(m: Module, device: DeviceProfile?, granted: Set<String>): Status = when {
        m.status == Status.UNSUPPORTED -> Status.UNSUPPORTED
        device != null && device.apiLevel < m.minApi -> Status.UNSUPPORTED
        m.permission?.let { it !in granted } == true -> Status.PERMISSION
        else -> m.status
    }

    private fun m(
        id: String, cat: Category, name: String, desc: String, impl: String,
        api: Int = 21, perm: String? = null, st: Status = Status.MONITOR, rb: String? = null
    ) = Module(id, cat, name, desc, impl, api, perm, st, rb)

    private val C = Category.CPU
    private val G = Category.GPU
    private val R = Category.RAM
    private val GM = Category.GAMING
    private val T = Category.TOUCH
    private val N = Category.NETWORK
    private val TH = Category.THERMAL
    private val B = Category.BATTERY
    private val S = Category.SYSTEM
    private val P = Category.PROFILES
    private val F = Status.FULL
    private val PA = Status.PARTIAL
    private val U = Status.UNSUPPORTED
    private val E = Status.PERMISSION
    const val DND = "Do Not Disturb access"
    const val OVERLAY = "Display over other apps"
    const val WRITE = "Modify system settings"

    val all: List<Module> = listOf(
        // CPU
        m("cpu.core_count", C, "Core count", "Shows how many logical cores the device exposes.", "Runtime.availableProcessors()"),
        m("cpu.abi_list", C, "Supported ABIs", "Lists the instruction sets this CPU can run.", "Build.SUPPORTED_ABIS"),
        m("cpu.hardware_id", C, "Hardware identifier", "Shows the board and hardware codename Android reports.", "Build.HARDWARE and Build.BOARD"),
        m("cpu.soc_model", C, "SoC model", "Names the chipset maker and model.", "Build.SOC_MODEL and SOC_MANUFACTURER", api = 31),
        m("cpu.load_system", C, "System CPU load", "Total CPU use across all apps. Android 8 and newer usually block this, so GLFX shows its own usage instead.", "Delta sampling of /proc/stat", st = PA),
        m("cpu.load_app", C, "GLFX CPU share", "How much CPU time GLFX itself is using.", "Process.getElapsedCpuTime() against wall-clock time"),
        m("cpu.core_freq", C, "Per-core frequency", "Current clock of each core, when the kernel lets apps read it.", "sysfs scaling_cur_freq, best effort", st = PA),
        m("cpu.load_trend", C, "CPU load trend", "Two-minute chart of CPU load.", "1 Hz sampler, 120-sample buffer in MainViewModel"),
        m("cpu.bottleneck", C, "CPU bottleneck advice", "Warns when load stays high for several seconds.", "Rule engine in Advisor.cpu()", st = F),
        m("cpu.governor", C, "CPU governor control", "Changing the governor needs root, so GLFX does not offer it.", "No public API", st = U),
        // GPU
        m("gpu.renderer", G, "GPU renderer name", "Shows the graphics chip reported by the driver.", "EGL pbuffer context, glGetString(GL_RENDERER)"),
        m("gpu.vendor", G, "GPU vendor", "Shows who makes the graphics driver.", "EGL pbuffer context, glGetString(GL_VENDOR)"),
        m("gpu.gles_version", G, "OpenGL ES version", "Highest OpenGL ES version the device supports.", "ActivityManager.deviceConfigurationInfo"),
        m("gpu.vulkan_version", G, "Vulkan version", "Vulkan API version the device reports.", "PackageManager system features"),
        m("gpu.vulkan_level", G, "Vulkan hardware level", "Vulkan hardware level, when reported.", "PackageManager system features"),
        m("gpu.refresh_current", G, "Current refresh rate", "Refresh rate the display is running at now.", "Display.getRefreshRate()"),
        m("gpu.refresh_modes", G, "Supported display modes", "Every resolution and refresh rate combination the panel offers.", "Display.getSupportedModes()", api = 23),
        m("gpu.frame_pacing", G, "Frame pacing test", "Measures frame-to-frame timing on a GLFX test surface.", "Choreographer-aligned withFrameNanos loop", st = F),
        m("gpu.frame_percentiles", G, "Frame time percentiles", "Reports 95th and 99th percentile frame times and the 1% low.", "FrameStats.compute()", st = F),
        m("gpu.dropped_frames", G, "Dropped frame counter", "Counts frames that took more than 1.5 refresh intervals.", "FrameStats.compute()", st = F),
        m("gpu.load_counter", G, "GPU load counter", "Android does not expose GPU load to normal apps, so GLFX reports it as unavailable.", "No public API", st = U),
        m("gpu.clock_control", G, "GPU clock control", "Changing GPU clocks needs root. GLFX never claims to do it.", "No public API", st = U),
        // RAM
        m("ram.total", R, "Total memory", "Total RAM the system reports.", "ActivityManager.MemoryInfo.totalMem", api = 16),
        m("ram.available", R, "Available memory", "RAM the system could give to a new app right now.", "ActivityManager.MemoryInfo.availMem"),
        m("ram.threshold", R, "Low-memory threshold", "Free memory level where Android starts to reclaim apps.", "ActivityManager.MemoryInfo.threshold"),
        m("ram.low_flag", R, "Low-memory warning", "Flags when the system says memory is low.", "ActivityManager.MemoryInfo.lowMemory"),
        m("ram.app_pss", R, "GLFX memory use", "Memory GLFX itself uses.", "ActivityManager.getProcessMemoryInfo()"),
        m("ram.pressure", R, "Memory pressure level", "Turns free memory into Comfortable, Tight or Critical.", "Ratio of available to total RAM", st = F),
        m("ram.trend", R, "Memory trend", "Two-minute chart of memory use.", "1 Hz sampler, 120-sample buffer"),
        m("ram.launch_advice", R, "Game launch memory check", "Checks free memory before you start a game and tells you if it is tight.", "Advisor.ram() and OptimizeEngine", st = F),
        m("ram.kill_background", R, "Background app killing", "Deliberately not offered. Modern Android restarts killed apps, which costs more than it saves.", "Intentionally omitted", st = U),
        // Gaming
        m("game.discover", GM, "Game discovery", "Finds installed games that appear in the launcher.", "PackageManager.queryIntentActivities with CATEGORY_GAME and FLAG_IS_GAME", st = F),
        m("game.manual_add", GM, "Add apps to library", "Lets you add any launcher app as a game.", "Store.userGames", st = F, rb = "Remove the app from the library"),
        m("game.launch", GM, "Game launch shortcut", "Starts a game from GLFX.", "PackageManager.getLaunchIntentForPackage()", st = F),
        m("game.per_game_mode", GM, "Per-game mode", "Remembers a mode for each game.", "Store.setGameMode", st = F),
        m("game.optimize_run", GM, "One-tap optimization", "Runs only the supported, safe actions and reports what happened.", "OptimizeEngine.run()", st = F, rb = "End session"),
        m("game.keep_awake", GM, "Keep screen awake", "Stops the screen sleeping while GLFX is on screen.", "FLAG_KEEP_SCREEN_ON on GLFX window", st = F, rb = "Flag cleared when the session ends"),
        m("game.refresh_pref", GM, "Preferred refresh rate", "Asks Android to run GLFX windows at the highest or a lower refresh rate. This affects GLFX test surfaces, not other apps.", "WindowManager.LayoutParams.preferredDisplayModeId", api = 23, st = PA, rb = "Previous mode restored"),
        m("game.sustained", GM, "Sustained performance window", "Requests steady performance for the GLFX window, where the hardware supports it.", "Window.setSustainedPerformanceMode()", api = 24, st = PA, rb = "Turned off when the session ends"),
        m("game.dnd", GM, "Silence notifications", "Limits interruptions to priority ones while you play.", "NotificationManager.setInterruptionFilter()", api = 23, perm = DND, st = E, rb = "Previous filter restored when the session ends"),
        m("game.thermal_gate", GM, "Thermal gate", "Refuses Performance mode when the device is already hot.", "PowerManager.currentThermalStatus", api = 29, st = F),
        m("game.before_after", GM, "Before and after report", "Shows memory, thermal and battery before and after each run.", "OptimizeEngine.Report", st = F),
        m("game.session_log", GM, "Session history", "Keeps the last 50 optimization runs.", "Store.push(\"records\")", st = F),
        m("game.unsupported_report", GM, "Unsupported-action report", "Lists what Android does not allow, in every report.", "OptimizeEngine.UNSUPPORTED", st = F),
        m("game.side_panel", GM, "Game side panel", "A slim handle on the screen edge opens quick controls on top of any game.", "Foreground service drawing a TYPE_APPLICATION_OVERLAY window", perm = OVERLAY, st = E, rb = "Panel is removed and its changes are undone when it stops"),
        m("game.panel_stats", GM, "Live stats in the panel", "Battery, temperature, free memory and thermal state while you play.", "1 Hz SystemMonitor sampling, only while the panel is open"),
        m("game.panel_awake", GM, "Keep screen awake over games", "Stops the screen sleeping while the panel is on screen.", "FLAG_KEEP_SCREEN_ON on the overlay window", st = F, rb = "Flag is removed with the panel"),
        m("game.panel_brightness", GM, "Brightness slider", "Changes brightness from the panel. If auto-brightness is on, Android may change it back.", "Settings.System.SCREEN_BRIGHTNESS", perm = WRITE, st = E, rb = "Original brightness restored when the panel stops"),
        m("game.panel_volume", GM, "Media volume slider", "Changes media volume without leaving the game.", "AudioManager.setStreamVolume(STREAM_MUSIC)", st = F),
        m("game.panel_side", GM, "Panel side", "Choose the left or right screen edge.", "Store.panelSide", st = F),
        m("game.session_recovery", GM, "Crash recovery", "If GLFX closes or crashes during a session, the next start restores Do Not Disturb and brightness.", "Previous values saved in SharedPreferences", st = F),
        // Touch
        m("touch.visualizer", T, "Touch visualizer", "Draws your touches inside GLFX.", "Compose pointerInput on an app-owned surface", st = F),
        m("touch.event_rate", T, "Touch event rate", "Counts touch events per second on the test surface.", "PointerEvent timestamps", st = F),
        m("touch.multitouch", T, "Multi-touch count", "Shows the most fingers detected at once.", "PointerEvent.changes", st = F),
        m("touch.global_sensitivity", T, "Global touch sensitivity", "Android has no supported API to change this, so GLFX does not claim to.", "No public API", st = U),
        // Network
        m("net.type", N, "Connection type", "Wi-Fi, mobile data, Ethernet or offline.", "NetworkCapabilities transports"),
        m("net.validated", N, "Internet validation", "Whether Android confirmed the connection reaches the internet.", "NET_CAPABILITY_VALIDATED", api = 23),
        m("net.metered", N, "Metered connection", "Whether the connection is metered.", "NET_CAPABILITY_NOT_METERED", api = 23),
        m("net.bandwidth", N, "Bandwidth estimate", "Android's estimate of up and down link speed. It is an estimate, not a speed test.", "linkDownstreamBandwidthKbps", api = 23),
        m("net.wifi_speed", N, "Wi-Fi link speed", "Negotiated speed with your router.", "WifiManager.connectionInfo.linkSpeed"),
        m("net.wifi_rssi", N, "Wi-Fi signal strength", "Signal strength in dBm.", "WifiManager.connectionInfo.rssi"),
        m("net.latency", N, "Latency test", "Average time to open a connection to a public endpoint.", "TCP connect timing to 1.1.1.1:443", st = F),
        m("net.jitter", N, "Jitter", "How much latency changes from one sample to the next.", "Mean absolute difference of consecutive samples", st = F),
        m("net.loss", N, "Connection loss test", "Share of test connections that failed.", "Timeouts and errors during the latency test", st = F),
        m("net.history", N, "Test history", "Keeps recent network test results.", "Store.push(\"net\")", st = F),
        m("net.advice", N, "Connection advice", "Explains the result in plain words.", "Advisor.network()", st = F),
        m("net.isp_routing", N, "Carrier and ISP routing", "No app can change how your carrier routes traffic.", "No public API", st = U),
        // Thermal
        m("therm.status", TH, "Thermal status", "Android's thermal level from None to Shutdown.", "PowerManager.currentThermalStatus", api = 29),
        m("therm.headroom", TH, "Thermal headroom", "Estimate of how close the device is to throttling.", "PowerManager.getThermalHeadroom()", api = 30),
        m("therm.battery_temp", TH, "Battery temperature", "Battery temperature in degrees Celsius.", "ACTION_BATTERY_CHANGED"),
        m("therm.zones", TH, "Sensor zones", "Temperatures from thermal zones the kernel lets apps read.", "sysfs thermal_zone*/temp, best effort", st = PA),
        m("therm.trend", TH, "Temperature trend", "Two-minute chart of battery temperature.", "1 Hz sampler"),
        m("therm.advice", TH, "Thermal advice", "Recommends a safer mode when the device is warm.", "Advisor.thermal()", st = F),
        m("therm.charge_warning", TH, "Charging while gaming warning", "Warns that charging adds heat during play.", "Advisor.thermal()", st = F),
        m("therm.limit_override", TH, "Thermal limit override", "GLFX never disables or bypasses thermal protection.", "Intentionally omitted", st = U),
        // Battery
        m("bat.level", B, "Battery level", "Current charge in percent.", "ACTION_BATTERY_CHANGED"),
        m("bat.charging", B, "Charging state", "Charging, full or discharging.", "EXTRA_STATUS"),
        m("bat.plug", B, "Power source", "AC, USB, wireless or none.", "EXTRA_PLUGGED"),
        m("bat.health", B, "Battery health state", "Good, overheated, cold and similar states, when reported.", "EXTRA_HEALTH"),
        m("bat.voltage", B, "Battery voltage", "Voltage in millivolts.", "EXTRA_VOLTAGE"),
        m("bat.technology", B, "Battery technology", "Chemistry reported by the device.", "EXTRA_TECHNOLOGY"),
        m("bat.trend", B, "Battery trend", "Two-minute chart of charge level.", "1 Hz sampler"),
        m("bat.session_delta", B, "Session battery change", "Battery change between the start and end of an optimization run.", "Report.battBefore and battAfter", st = F),
        m("bat.friendly_mode", B, "Battery-friendly mode", "Lowers GLFX refresh rate preference and skips keep-awake.", "OptimizeEngine mode BATTERY", st = PA, rb = "Restored when the session ends"),
        // System
        m("sys.api_level", S, "Android API level", "API level of this device.", "Build.VERSION.SDK_INT"),
        m("sys.release", S, "Android version", "Version name of this device.", "Build.VERSION.RELEASE"),
        m("sys.patch", S, "Security patch level", "Date of the latest security patch.", "Build.VERSION.SECURITY_PATCH", api = 23),
        m("sys.manufacturer", S, "Manufacturer and brand", "Who made the device.", "Build.MANUFACTURER and Build.BRAND"),
        m("sys.model", S, "Device model", "Model name.", "Build.MODEL"),
        m("sys.pkg_visibility", S, "Package visibility", "Uses a launcher-only query, so no broad app list permission is needed.", "Manifest queries element", st = F),
        m("sys.perm_detect", S, "Permission detection", "Checks which special access is granted.", "NotificationManager.isNotificationPolicyAccessGranted()", api = 23, st = F),
        m("sys.feature_flags", S, "API availability flags", "Shows which performance APIs this device offers.", "DeviceProfile.hasSustainedPerf, hasThermalApi, hasPerfHintApi"),
        m("sys.perfhint_detect", S, "Performance hint API detection", "Detects whether PerformanceHintManager exists. GLFX only detects it today.", "SDK_INT check for API 31", api = 31),
        m("sys.session_notice", S, "Visible session notification", "While the panel runs, a notification stays visible with a Stop button. Nothing runs hidden.", "Foreground service notification, type specialUse", st = F),
        // Profiles
        m("prof.modes", P, "Four built-in modes", "Performance, Balanced, Battery-friendly and Thermal-aware.", "OptimizeEngine.Mode", st = F),
        m("prof.default_mode", P, "Default mode", "The mode One-tap optimization uses.", "Store.activeMode", st = F),
        m("prof.per_game", P, "Per-game profile", "Each game can have its own mode.", "Store.gameModes", st = F),
        m("prof.backup", P, "Profile backup", "Saves modes, games, history and results to a file you choose.", "Storage Access Framework CreateDocument", st = F),
        m("prof.restore", P, "Profile restore", "Loads a GLFX backup file.", "Storage Access Framework OpenDocument", st = F),
        m("prof.clear_data", P, "Clear all data", "Deletes everything GLFX stored.", "Store.clearAll()", st = F)
    )

    fun count(c: Category) = all.count { it.category == c }
    const val TARGET = 400
}
