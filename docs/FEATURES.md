# Features and honesty table

| Area | Works now | Not possible (and not claimed) |
|---|---|---|
| CPU | core count, ABI, SoC, per-core clocks (if readable), load (system-wide only where Android allows, otherwise GLFX's own) | governor, frequency changes |
| GPU | renderer, vendor, GLES, Vulkan, refresh modes, frame pacing test | GPU load, clocks |
| RAM | total, available, threshold, pressure, GLFX usage, advice | killing apps |
| Thermal | status (API 29+), headroom (API 30+), battery temp, sensor zones if readable | limit override |
| Network | type, validation, bandwidth estimate, Wi-Fi speed/signal, latency, jitter, failed connections | routing changes |
| Optimization | keep-awake, preferred refresh rate and sustained mode for GLFX windows, Do Not Disturb filter (user grants access) | anything outside GLFX's window except DND |

Module statuses: A Fully implemented, B Partially, C Monitoring only, D Unsupported on this device, E Requires user permission. The in-app catalog recomputes the status per device.

## Game side panel
A foreground service draws a slim edge handle over any app. Opening it shows live battery, temperature, free memory and thermal state, plus keep-awake, Do Not Disturb, brightness and media volume controls and a connection test. It has its own design and is not a copy of any manufacturer's panel.

Not possible for an app without root, and therefore not offered: an FPS counter inside other apps, touch sensitivity, CPU/GPU boost, blocking system gestures. Screenshot and screen recording would need MediaProjection consent and are not built yet.
