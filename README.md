<div align="center">

# 🎵 Vybe

**Your offline library — with cloud streaming, downloads, and video on top.**

<a href="https://github.com/HaroldMth/vybe/releases/latest"><img src="https://img.shields.io/github/v/release/HaroldMth/vybe?label=release&color=545DFF" alt="Latest release"></a>
<a href="https://github.com/HaroldMth/vybe/releases"><img src="https://img.shields.io/badge/release%20line-1.0-c9833c" alt="Release line 1.0"></a>
<a href="./LICENSE"><img src="https://img.shields.io/github/license/HaroldMth/vybe?color=3DA639" alt="License"></a>
<img src="https://img.shields.io/badge/platform-Android%209%2B%20(API%2028)-3DDC84?logo=android&logoColor=white" alt="Android 9+">
<img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
<img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">

<!-- Screenshots coming soon. -->

</div>

Vybe is a lightweight, elegant Android music player for the music you already
own, with a cloud layer for the music you don't. Scan your device, browse it by
folder, album, artist, genre, or playlist — then search and stream the wider
catalog, download it for offline play, and switch any song to its music video.

Built with Kotlin and Jetpack Compose, and a fork of
[Symphony](https://github.com/zyrouge/symphony) by [Zyrouge](https://github.com/zyrouge).

## ✨ Features

**📂 Your library**
- Folder-first browsing with include/exclude folders, sorting, and search
- Albums, artists, album artists, genres, and a unified song list
- Mini player, queue management, shuffle and repeat

**☁️ Cloud layer**
- Search and stream the Vybe catalog alongside your local files
- Home feed with charts, "for you" rails, and recently played
- Downloads to `Music/Vybe` with per-song progress and offline playback

**🎬 Video mode**
- Morphs the player into a full-bleed 16:9 stage without restarting playback
- Quality and fill/fit controls, plus download of the video itself
- Choose audio or video per song — each has its own download state

**🎛️ Playback**
- Media3 / ExoPlayer engine with seek controls, speed and pitch, sleep timer
- Gapless playback, audio-focus handling, and a persistent queue
- Synced and unsynced lyrics, including on the video stage

**🎨 Looks**
- Light / dark theme with dynamic color and a custom primary color
- Font and content scaling, configurable layouts, and artwork quality settings

**❤️ Library extras**
- Favorites as a built-in playlist
- User playlists with import/export
- Full-text fuzzy search across everything

## 📥 Install

Grab the latest build from the
[releases page](https://github.com/HaroldMth/vybe/releases/latest).

> Prefer to build it yourself? See below.

## 🛠️ Build from source

**Prerequisites**

- JDK 17
- Android SDK (compile SDK 35; the app targets Android 14 and supports Android 9+)
- Node.js 20+ and npm (used to generate translations before the Gradle build)

```bash
# 1. Install the tooling that generates i18n sources
npm ci

# 2. Build a debug APK
npm run prebuild      # regenerates the translation classes
./gradlew assembleDebug

# ...or a release build
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/`.

## 🧱 Tech

| Layer | What it uses |
| --- | --- |
| UI | Jetpack Compose + Material 3 |
| Playback | AndroidX Media3 / ExoPlayer (HLS + DASH) |
| Storage | Room, SharedPreferences, MediaStore |
| Networking | OkHttp + kotlinx.serialization |
| Metadata | [Metaphony](https://github.com/zyrouge/metaphony) (TagLib) |
| Images | Coil |

## 🗂️ Project layout

```
app/         Android app (UI, services, playback, downloads)
metaphony/   Native tag-reading library
cli/         Build/release helper scripts (versioning, i18n, changelogs)
metadata/    Store listing text and per-release changelogs
i18n/        Translation sources
```

## 🏷️ Releases & versioning

**1.0 is the main release.** Every release after it is fix-only — no new
features, just corrections. See [CHANGELOG.md](./CHANGELOG.md) for the history.

The app's own release number is tracked in code (`AppMeta.currentRelease`,
backed by `AppRelease`) and kept separate from the Android `versionName` /
`versionCode` that the store uses.

## 🙏 Credits

Vybe would not exist without **[Symphony](https://github.com/zyrouge/symphony)**
by [Zyrouge](https://github.com/zyrouge) and its contributors. The player
engine, library scanning, playlists, lyrics, and much of the UI are their work;
Vybe adds the cloud streaming, downloads, and video layer on top. If you like
what this app does under the hood, go star the original.

## 📄 License

Licensed under the [GNU AGPL-3.0](./LICENSE), the same license as Symphony.
