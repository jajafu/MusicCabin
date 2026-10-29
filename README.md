[English](README.md) | [繁體中文](README.zh-TW.md)

# MusicCabin

MusicCabin is a customized Android head-unit- and TV-oriented fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist), an open-source YouTube Music client for Android.

## Highlights

What this fork adds on top of Metrolist. Full version history lives in [`changelog.md`](changelog.md); this page describes current features only.

- **Photo frame KTV**:
  - Full-screen local/USB photo slideshow with clock, song info, music and photo controls, and in-frame settings.
  - Playback interval slider (5/10/15/30/60 s).
  - Fit to screen / crop to fill.
  - Mismatched-orientation photos pair up on the same interval.
  - Show lyrics toggle and Simplified-to-Traditional lyrics toggle.
  - Lyrics at the bottom of the screen show the previous, current and next line together, sliding with the playback direction and sweeping word by word.
  - One-tap lyrics-source switch in the control row.
  - Lyrics-offset −/+ buttons in the control row, 50 ms per tap.
  - The head unit shows a QR code; scanning it with a phone opens the page (over a phone hotspot on the same local network). Pick photos on the phone, which compresses them and sends them straight to the frame with no USB drive. The same phone page has a "Song requests" tab: search songs, play next, add to the end of the queue, reorder or remove queued songs, and watch the now playing and upcoming queue refresh every 10 seconds.
  - GMS builds can be compiled with your own Google OAuth settings to read photos directly from your personal Drive; see the [Google Drive test guide](docs/photo-frame-google-drive-testing.zh-TW.md).

- **TV remote experience** (Google TV/Android TV):
  - A dedicated TV interface.
  - Like and add-to-playlist from song rows with the remote.
  - Sidebar Library (liked songs plus saved playlists).
  - Playlist detail with Play all, Save, and local playlist editing.
  - Screen off keeps playing; Stop and exit ends playback and the service while Back keeps background playback.
  - The TV photo frame offers remote slideshow, settings, and lyrics switching.
  - The sidebar Account page shows a QR code, TV address, and 6-digit pairing code while logged out; authorize from the phone account menu on the same Wi-Fi to log in.
  - After login, YouTube playlists and likes sync to the TV.

- **Car UI**:
  - MiniPlayer and frame text scale from the phone-size 1× baseline with the screen short edge up to the auto-scale cap (default 2×, up to 3×; Settings → Appearance, or TV frame Display settings).
  - Home shows category chips, 12 quick-access items, and account playlists.
  - Independent music volume, which can be lowered so music does not drown out navigation guidance. On the song page, tap the three dots at the bottom right to open volume adjustment and EQ settings.

- **MusicCabin suits these scenarios**:

  - Passengers who want to sing along can switch to the photo frame KTV or the song lyrics page.
  - Families singing together on the TV, with travel, landscape, or other personal photos in the background, enjoying memories of good times while singing.
  - Friends and family gathering at home: basic KTV features without buying professional KTV gear, and every guest can upload photos to join in.

- **Branding and variants**: `MusicCabin` name with the black music-car logo across launcher, About, notifications, and store artwork. FOSS (`com.jajafu.musiccabin`) and GMS (`com.jajafu.musiccabin.gms`) install side by side with independent data; FOSS updates in-app from GitHub Releases while GMS updates manually.

## Features

Baseline music features inherited from upstream:

- Stream music from YouTube Music.
- Background playback and offline downloads.
- Skip silence, sleep timer, audio normalization, tempo and pitch control.
- Synced lyrics and lyrics translation, with a "Switch lyrics source" action in the lyrics menu.
- Search for songs, albums, artists and playlists.
- Library, local playlist and account synchronization.
- Material 3 interface with light, dark, black, dynamic and preset color themes.
- Android Auto-focused layout and playback controls.

## Build and updates

Build the FOSS release variant locally with:

```bash
./gradlew :app:assembleFossRelease
```

Build the separately installable GMS release variant locally with `./gradlew :app:assembleGmsRelease`. Its launcher name is `MusicCabin GMS`.

GitHub Actions workflows run manually. The release workflow builds only the FOSS Release APK and publishes `MusicCabin-v<version>-car.apk` to this repository's GitHub Releases. Release notes use the matching version entry in `changelog.md`.

The in-app updater is enabled only in FOSS builds. It checks [this repository's releases](https://github.com/jajafu/MusicCabin/releases), downloads the matching APK in-app with progress, and opens the system installer; Android still requires the user to approve installation. FOSS builds declare the `REQUEST_INSTALL_PACKAGES` permission for this flow; other variants do not. Self-installed GMS builds are updated manually.

FOSS uses `com.jajafu.musiccabin` and GMS uses `com.jajafu.musiccabin.gms` (Debug appends `.debug` / `.gms.debug`), so the two Release variants install side by side with independent settings, login, downloads, database, and permissions. A GMS installation under an older package ID does not upgrade or migrate data; register its actual package ID plus signing SHA-1 as a separate Android OAuth client before reconnecting Drive. The database schema is unchanged, so later releases with the same variant package ID and signing key update in place.

## Original project and acknowledgements

This project is a modified version of [Metrolist](https://github.com/MetrolistGroup/Metrolist). The original authors, contributors and copyright notices remain acknowledged in the source tree and [`LICENSE`](LICENSE).

Metrolist also builds on work from projects including [InnerTune](https://github.com/z-huang/InnerTune), [OuterTune](https://github.com/DD3Boh/OuterTune), [Better Lyrics](https://better-lyrics.boidu.dev), [metroserver](https://github.com/MetrolistGroup/metroserver), [MusicRecognizer](https://github.com/aleksey-saenko/MusicRecognizer), and [zemer-cipher](https://github.com/ZemerTeam/zemer-cipher).

## GPLv3 notices for modified distributions

This project is licensed under the [GNU General Public License v3.0](LICENSE).

When distributing this modified project or an APK based on it:

- Keep the original copyright, attribution, license and disclaimer notices.
- Clearly identify that this is a modified version and describe the changes.
- Provide the corresponding source code and the scripts or instructions needed to build the distributed version.
- Distribute covered derivative works under GPLv3 and do not add restrictions that conflict with the license.
- Include a copy of the GPLv3 license with the distribution.

Copyright in the original work remains with its original authors. Copyright in new contributions remains with their respective contributors.

## Disclaimer

This project is not affiliated with, funded, authorized, endorsed by, or associated with YouTube, Google LLC, Metrolist Group LLC, or their affiliates and subsidiaries.

All trademarks, service marks and other intellectual property referenced in this project belong to their respective owners.
