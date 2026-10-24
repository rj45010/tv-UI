# tv-UI

A small home screen (launcher) for Android TV and Google TV that replaces the stock home: no sponsored rows, no recommendations, no analytics. One Kotlin file, no libraries; the debug APK is about 800 KB without artwork.

- Your favourite apps in one translucent row; the focused tile gets a white border that slides between tiles.
- All Apps, Wi-Fi, Bluetooth, Inputs and Settings as round buttons in a pill at the top.
- Clock, date, greeting and local weather.
- An All Apps list with last-opened time, storage used, and Open, Info and Uninstall.
- Optional: your own background image and openpets.dev pets that walk along the app row.

Built and tested on one TV, a Hisense SmartTV 4K FFM on Android TV 11. Notes for it are in [devices/](devices/). Other models should work; add yours.

## Mac setup (one time)

```bash
brew install android-platform-tools
brew install --cask temurin@17 android-commandlinetools
export ANDROID_HOME=$HOME/Library/Android/sdk
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

AGP 8.7 needs JDK 17. Java 22 does not work for the build.

## Enable ADB on the TV

1. Settings > System > About, press OK on **Android TV OS build** 7 times.
2. Settings > System > Developer options, turn on **USB debugging** (and Network debugging if listed).
3. Find the TV's IP in Settings > System > About > Status, then on the Mac: `adb connect <TV_IP>:5555`
4. Accept the prompt on the TV and tick "Always allow from this computer".

## Build and install

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleDebug
adb install -r build/outputs/apk/debug/tv-launcher-debug.apk
```

The favourite apps are the `favourites` list at the top of `src/main/java/com/rahul/home/MainActivity.kt`.

## Make it the home screen

```bash
# The launcher animates on its own timer, so system animations can be off
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# Google TV home and its setup wizard outrank third-party launchers, so disable both
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb shell cmd package set-home-activity com.rahul.home/.MainActivity

# Let the All Apps list show "last opened" and storage size
adb shell appops set com.rahul.home GET_USAGE_STATS allow
```

To remove preinstalled junk, use your model's `debloat.txt` in [devices/](devices/), or start from the [CleanYourTV](https://github.com/Gathin23/cleanyourtv) lists, which cover far more TVs. Read the warnings in the file before running it.

## Undo everything

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
adb uninstall com.rahul.home
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global $k 1
done
# Re-enable anything else you disabled with: adb shell pm enable <package>
```

A factory reset also restores the stock setup.

## Background and pets (optional)

The repo ships no artwork. The launcher looks for these files in `src/main/res/drawable-nodpi/` (ignored by git) and skips whatever is missing:

| File | What it is |
|---|---|
| `bg.jpg` | Background, cropped to fill the screen. Without it the background is plain. |
| `pet_left.webp` | A pet from [openpets.dev](https://openpets.dev) that walks the left half of the app row. |
| `pet_right.webp` | A second pet for the right half. |

[docs/pets.md](docs/pets.md) covers the sheet format, `tools/fit_pet.py` (which stops pets floating), which animations to use, and rights. The pets are not covered by this repo's licence.

## How it works

- **Weather**: location from [ipwho.is](https://ipwho.is) (IP based, city level), temperature and condition from [Open-Meteo](https://open-meteo.com). Both free, no key. Fetched at most every 30 minutes.
- **Animation**: the focus border and pets run their own frame loop, because `ValueAnimator` does nothing once `animator_duration_scale` is 0.
- **Limits**: shows and trailers behind the home screen are not possible, because only system-signed launchers can read the TV provider data. The signed-in Google account cannot be read by apps since Android 11. Preinstalled apps such as Netflix, YouTube and Prime Video can be disabled but not uninstalled.

## Credits and licence

Code: MIT, see [LICENSE](LICENSE). Icons: [Material Icons](https://github.com/google/material-design-icons) by Google, Apache License 2.0. Several device notes come from [CleanYourTV](https://github.com/Gathin23/cleanyourtv).
