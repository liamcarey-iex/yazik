# Yazik

Yazik is a personal Android Russian and Japanese practice app built with Kotlin and Jetpack Compose. It has guided beginner lessons, free conversation, typed and tap-to-speak turns, locally saved chat history, and separate on-device progress for each language.

## Requirements

- Android Studio with Android SDK Platform 36 and Java 17.
- An OpenAI API key with API billing enabled. This is separate from a ChatGPT subscription.
- A GrapheneOS Pixel 8 or newer for the final device install.

The project uses Android Gradle Plugin 8.13.2 and Gradle 8.13. Open the project in Android Studio and sync it as a Gradle project. If no Gradle distribution is configured, install Gradle 8.13 or use Android Studio's Gradle setup to provision that version before syncing. No SDK, Gradle, or `adb` installation was available in the environment where the source was created, so a local APK has not been built here.

## Build and install

1. Open this directory in Android Studio and let Gradle sync dependencies.
2. For a quick development install, connect the phone over USB with Android debugging enabled, select the phone as the run target, and press **Run**.
3. For an APK you can transfer/install yourself, use **Build → Generate Signed Bundle / APK → APK → Create new**. Save the signing key somewhere backed up and private. Build the `release` variant.
4. Transfer `app/build/outputs/apk/release/app-release.apk` to the phone. Open it from Files and allow the installer to install apps from that source when GrapheneOS asks.
5. Keep using the same signing key for future versions so Android can update the installed app in place.

## First use

Choose Russian or Japanese on first launch; you can switch languages from the home screen later. Open **Settings**, enter your API key, and save it. Start a guided lesson or free conversation. Type a message or tap the microphone to record one turn; tap the stop icon to send it. The transcript and reply are saved locally. Use **Listen** on an assistant reply to request speech audio.

Text practice uses `gpt-6-luna`; voice input uses OpenAI transcription, and reply audio uses OpenAI speech generation. API usage is billed to the API account attached to the key. Audio usage is billed separately from normal text tokens. The app does not include a backend, sign-in, cloud sync, or built-in spending cap.

## Key and privacy notes

The key is entered by the user at runtime, encrypted with AES-GCM using a key held in Android Keystore, and is not embedded in the APK or written to logs. Conversations and progress are stored in the app's local Room database. Raw microphone recordings are placed in app cache and deleted after transcription is attempted.

An API key used by a client app can still be extracted from a compromised device. This design is for personal use on your own phone; don't distribute an APK containing your key. Set spending limits on your OpenAI API account and revoke the key if the phone is lost. OpenAI's text-to-speech voices are currently optimized for English, so check Russian and Japanese pronunciation quality for your own learning needs.
