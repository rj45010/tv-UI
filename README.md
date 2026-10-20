# my-tv

Custom home screen (launcher) for a Hisense Android TV, replacing the stock Google TV home.

## The TV

| | |
|---|---|
| Model | Hisense SmartTV 4K FFM |
| OS | Android TV 11, build RTT2.220118.001 |
| Local IP | 192.168.29.193 (check Settings > System > About > Status if it changes) |
| ADB | Network debugging on port 5555 |

## Mac setup (one time)

```bash
brew install android-platform-tools
brew install --cask temurin@17 android-commandlinetools
brew install gradle
export ANDROID_HOME=$HOME/Library/Android/sdk
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

AGP 8.7 needs JDK 17. Java 22 does not work for the build.

## Enable ADB on the TV (one time, or after a reset)

1. Settings > System > About, press OK on **Android TV OS build** 7 times.
2. Settings > System > Developer options, turn on **USB debugging** (and Network debugging if listed).
3. On the Mac: `adb connect 192.168.29.193:5555`
4. Accept the prompt on the TV and tick "Always allow from this computer".

## Build and install

```bash
cd ~/tv-launcher
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleDebug
adb connect 192.168.29.193:5555
adb install -r build/outputs/apk/debug/tv-launcher-debug.apk
```

All UI code is in `src/main/java/com/rahul/home/MainActivity.kt`. Row 1 apps are the `favourites` list at the top of that file.

## TV-side settings (not stored in the app)

Run these after a factory reset, after installing the APK.

```bash
# Turn off UI animations
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# Disable preinstalled junk
for p in fusion.android.tv.demo com.hisense.storemode com.hisense.alexa com.hisense.hibrowser \
         com.google.android.videos com.google.android.play.games com.google.android.youtube.tvmusic \
         com.google.android.apps.tv.dreamx; do
  adb shell pm disable-user --user 0 $p
done

# Make this launcher the home screen.
# Google TV home and the setup wizard both outrank third-party launchers, so they must be disabled.
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb shell cmd package set-home-activity com.rahul.home/.MainActivity

# Let the All Apps card show "last opened" and storage size
adb shell appops set com.rahul.home GET_USAGE_STATS allow

# Optional: Wi-Fi network name on the Wi-Fi tile
adb shell pm grant com.rahul.home android.permission.ACCESS_FINE_LOCATION

# Optional: free storage
adb shell pm trim-caches 64G
adb shell pm clear com.google.android.apps.tv.launcherx
```

## Undo everything

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
adb uninstall com.rahul.home
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global $k 1
done
# Re-enable any disabled app with: adb shell pm enable <package>
```

A factory reset also restores the stock setup.

## How things work

- **Weather**: location from https://ipwho.is (IP based, city level), temperature and condition from https://api.open-meteo.com. Both free, no key. Fetched every 30 minutes. ipapi.co was tried first and is rate limited.
- **Background**: `LavaView`, 4 radial gradient blobs drawn about 25 times a second on its own timer, because the system animator is switched off. Stops when another app is open. About 10% CPU on this TV. A low-resolution bitmap version was tried and used 3 times more CPU.
- **Bluetooth tile**: opens `com.android.tv.settings/.accessories.AddAccessoryActivity`. The standard `ACTION_BLUETOOTH_SETTINGS` does not exist on this TV and needs the `BLUETOOTH_ADMIN` permission declared.
- **Inputs tile**: opens the Live TV app `com.mediatek.wwtv.tvcenter`.

## Limits found

- App artwork (shows, trailers) behind the home screen is not possible: only system-signed launchers can read the TV provider data.
- The signed-in Google account cannot be read by apps since Android 11.
- Preinstalled apps (Netflix, YouTube, Prime) cannot be uninstalled, only disabled.
- Screenshots over ADB come back black while the TV is in standby. Wake it with `adb shell input keyevent KEYCODE_WAKEUP`.
- Hisense does not usually ship major Android upgrades for this model. Check Settings > System > About > Upgrade.

## Storage notes (20 Oct 2026)

Data partition 7.1 GB, 63% free after cleanup. The "12 of 16 GB used" figure in Settings includes about 9 GB of system partitions that cannot be reclaimed without root. Largest apps: JioHotstar (about 400 MB with data), Google Play Services (275 MB, keep), Google TTS (274 MB, can be disabled if accessibility speech is unused).
