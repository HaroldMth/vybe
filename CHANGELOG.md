# Changelog

All notable changes to Vybe are recorded in this file.

**1.0 is the main release.** Everything after it is a fix-only release: no new
features, just corrections, so the app stays stable for everyday use. New
entries go at the top.

The app's release number lives in code (`AppMeta.currentRelease`, backed by
`AppRelease`) and is deliberately separate from the Android
`versionName`/`versionCode` that the store and the release tooling use, so the
store/build numbering can move without disturbing the app's release history.
`AppMeta.recordRelease(...)` stores the release on each launch, so a future
release can compare the running release against the last one it saw.

---

## [1.0] — Main release

First stable release of Vybe.

### Highlights

- **Offline music player** built on Media3/ExoPlayer: songs, albums, artists,
  album artists, genres, folders and playlists, with folder include/exclude
  filters, sorting and search.
- **Video mode** — a full-bleed 16:9 stage with an animated audio → video morph,
  quality/fill controls, and transport that stays in step with the queue.
- **Downloads** to `Music/Vybe`, with per-song progress, a downloaded state, and
  offline playback preferred over streaming.
- **Favorites** as a built-in playlist, plus user playlists with add/remove and
  import/export.
- **Home feed** — For You rails, charts, recently played, and a browsable
  library, backed by the Vybe API.
- **Now playing** — seek controls, speed/pitch, sleep timer, shuffle/repeat,
  queue management, and synced/unsynced lyrics.
- **Theming** — light/dark, dynamic color, custom primary color, font and
  content scaling, and configurable layouts.
- Rebranded fork of [Symphony](https://github.com/zyrouge/symphony).
