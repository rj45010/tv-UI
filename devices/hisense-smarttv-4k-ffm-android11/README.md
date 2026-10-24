# Hisense SmartTV 4K FFM (Android TV 11)

| | |
|---|---|
| Model string | SmartTV 4K FFM |
| OS | Android TV 11, build RTT2.220118.001 |
| Device | songshan (MediaTek) |
| Junk apps | [debloat.txt](debloat.txt) |

## Quirks

- **Bluetooth button**: the standard `ACTION_BLUETOOTH_SETTINGS` does not exist. The launcher opens `com.android.tv.settings/.accessories.AddAccessoryActivity` instead.
- **Inputs button**: opens the Live TV app `com.mediatek.wwtv.tvcenter`. Keep every `com.mediatek.*` package enabled; HDMI and the tuner depend on them.
- **Volume keys** cannot be intercepted by an accessibility service on this TV, so a custom volume overlay does not work.
- **Standby** kills every third-party app except the one on screen (reported by CleanYourTV), the launcher included. It restarts when you press Home.
- **Screenshots** over ADB come back black while the TV is in standby. Wake it with `adb shell input keyevent KEYCODE_WAKEUP`.
- **Upgrades**: Hisense does not usually ship major Android versions for this model.

## Remote key codes

From CleanYourTV's notes for this model (Hisense SmartRC): YouTube 293, Prime Video 295, gear 83, Input 178. Netflix is handled by the firmware.

## Storage

Data partition 7.1 GB. The "12 of 16 GB used" figure in Settings includes about 9 GB of system partitions that cannot be reclaimed without root. Google TTS (274 MB) can be disabled if you do not use spoken accessibility.
