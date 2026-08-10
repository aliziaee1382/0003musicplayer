# 🎵 0003 Player - Modern Glassmorphic Audio Player

A premium, highly-optimized Android audio player featuring a frosted glass aesthetic, custom color themes, 5-band equalizer, sleep timer, dynamic stats, and fast local media scanning.

## ✨ Key Features

* **Glassmorphic Aesthetic:** Beautiful frosted glass UI built with 100% Jetpack Compose.
* **High Performance Playback:** Powered by **Media3 ExoPlayer** for seamless, gapless playback and FLAC support.
* **Fast Storage Scanner:** Optimized MediaStore scanner capable of indexing thousands of local audio files in seconds.
* **5-Band Equalizer & Presets:** Integrated sound customization with hardware-level equalizer support.
* **Smart Listening Stats:** Tracks real playback time, top artists, and top songs based on listening habits.
* **Sleep Timer:** Quick presets and custom timer options to auto-pause audio.
* **Home Widgets:** Includes 4 different responsive glassmorphism desktop widgets (Compact, Standard, Vinyl, Full Center).
* **Android 11+ Media Management:** Standard native file deletion permissions.

## 🛠️ Tech Stack & Architecture

* **UI:** Jetpack Compose, Material 3
* **Audio Engine:** `androidx.media3:media3-exoplayer`
* **Local Database:** Room Database
* **Image Loading:** Coil (Memory & Disk cached)
* **Architecture:** MVVM + Clean Architecture principles
* **Target Package:** `ir.ali0003.musicplayer`

## 🚀 How to Build & Run Locally

### Prerequisites
* [Android Studio Jellyfish / Ladybug or newer](https://developer.android.com/studio)
* Android SDK 36 (Min SDK 24)
* JDK 11

### Steps

1. Clone or download this repository.
2. Open Android Studio and select **Open** -> choose the project directory.
3. Sync project with Gradle files.
4. Place your release keystore file `my-upload-key.jks` in the root directory if building a signed release APK.
5. Connect a physical Android device or start an emulator.
6. Click **Run** (`Shift + F10`) to build and launch the app.

---
*Created & Maintained by Ali Ziaee*
