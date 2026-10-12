# Android 17 application guidance

Use these instructions as a starting point when creating or maintaining Android applications in this workspace. Keep project-specific choices in the app's own documentation.

## Android 17 SDK

- Android 17 uses API level 37. The installed platform in this environment is `/home/liam/android-sdk/platforms/android-37.2` (API 37.2, extension level 24).
- Set `compileSdk` and `targetSdk` to 37 when the chosen Android Gradle Plugin supports that SDK level. If the build plugin does not support API 37, update the plugin and related Kotlin/Gradle versions to compatible versions before building; do not silently claim an API 36 build targets API 37.
- Choose `minSdk` based on the app's actual supported device range. Do not raise it to 37 unless the app genuinely requires Android 17.
- Installed SDK locations may differ on another machine. Use `ANDROID_HOME` or `ANDROID_SDK_ROOT` when set; this machine's SDK root is `/home/liam/android-sdk`.

## Project setup and build

- Prefer Kotlin and Jetpack Compose for new UI unless the project already uses another established UI stack.
- Use Gradle Kotlin DSL and include the Gradle wrapper (`gradlew`, `gradlew.bat`, and `gradle/wrapper/`) in new projects so builds do not depend on a machine-wide Gradle installation.
- Build a debug APK with `./gradlew :app:assembleDebug`. The usual output is `app/build/outputs/apk/debug/app-debug.apk`.
- For a distributable release, configure a release signing key outside source control and build with `./gradlew :app:assembleRelease`. A debug APK is suitable for local installation, not a production release.
- Check the Android 17 SDK and installed build tools before changing project versions. The SDK manager is at `/home/liam/android-sdk/cmdline-tools/latest/bin/sdkmanager`; installed platforms are under `/home/liam/android-sdk/platforms`, and build tools are under `/home/liam/android-sdk/build-tools`.

## ADB and emulator paths

Use the full platform-tools path in shell commands when `adb` is not on `PATH`:

```bash
/home/liam/android-sdk/platform-tools/adb
```

Examples:

```bash
/home/liam/android-sdk/platform-tools/adb devices -l
/home/liam/android-sdk/platform-tools/adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
/home/liam/android-sdk/platform-tools/adb -s DEVICE_SERIAL shell monkey -p APPLICATION_ID 1
```

The emulator executable is `/home/liam/android-sdk/emulator/emulator`. List available virtual devices with:

```bash
/home/liam/android-sdk/emulator/emulator -list-avds
```

Start a named device with `/home/liam/android-sdk/emulator/emulator -avd AVD_NAME`, then use `adb devices -l` to find its serial and wait for boot before installing an APK.

## Device compatibility

- Check permissions and behavior changes introduced by the app's target SDK, especially for microphone, notifications, background work, media, and storage.
- Request sensitive runtime permissions only when the related feature is used, and give users a clear alternative when permission is denied.
- Confirm the manifest package/application ID before writing install or launch commands. Use `adb -s DEVICE_SERIAL` when more than one device or emulator may be connected.
- Keep API keys, signing credentials, and other secrets out of source control and APK build configuration.
