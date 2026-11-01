# Permissions

| Permission | Type | Why | If you say no |
|---|---|---|---|
| INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE | Install time | Network test, connection type, Wi-Fi speed and signal | Network screens show "not available" |
| SYSTEM_ALERT_WINDOW (Display over other apps) | You grant in Settings | Draws the game side panel | No side panel; everything else works |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE | Install time | Keeps the panel visible and stoppable | n/a |
| POST_NOTIFICATIONS (Android 13+) | You allow when asked | Shows the "panel is on" notification with a Stop button | Panel still runs, notification may be hidden |
| Do Not Disturb access | You grant in Settings | Silence notifications during a session, then restore | Skipped, listed as "not done" |
| WRITE_SETTINGS (Modify system settings) | You grant in Settings | Brightness slider only; original value restored | Slider is locked |

Package visibility uses launcher-intent `<queries>` only. No QUERY_ALL_PACKAGES, no location, no usage access, no root, Shizuku or ADB.
Everything the panel changes (Do Not Disturb, brightness) is saved before the change and restored when the panel stops, when GLFX closes, or on the next start after a crash.
