# Compatibility matrix
| Android | API | Notes |
|---|---|---|
| 5.0-5.1 | 21-22 | Monitoring works; no display-mode or DND features |
| 6.0 | 23 | Display modes, DND, security patch |
| 7.0+ | 24 | Sustained performance mode |
| 10+ | 29 | Thermal status |
| 11+ | 30 | Thermal headroom |
| 12+ | 31 | SoC model, performance-hint detection |
Every feature checks the API level at runtime and degrades to "not available".
