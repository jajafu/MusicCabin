[English](README.md) | [繁體中文](README.zh-TW.md)

# MusicCabin

MusicCabin is a customized Android Car-oriented fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist), an open-source YouTube Music client for Android.

This fork is maintained by [jajafu](https://github.com/jajafu) and focuses on a more readable and practical in-car playback experience.

## Highlights

What this fork adds on top of Metrolist. Full version history lives in [`changelog.md`](changelog.md); this page describes current behavior only.

- **Photo frame KTV** (phone/head-unit main menu, replaces Listen Together; the Settings → Integrations entries — Listen Together, Discord, LastFM — are hidden): full-screen local/USB photo slideshow with clock, song info, music and photo controls, and in-frame settings. Interval slider (5/10/15/30/60 s), fit/crop, rescan after reconnecting a drive; mismatched-orientation photos pair up on the same interval, falling back to a single photo. Originals are never copied or deleted. Use while parked; photos are not shown in Android Auto.
- **On-demand photo browser**: paged MediaStore picker for indexed internal and USB/SD photos with whole-folder select/deselect and full Traditional Chinese UI. No startup scan; thumbnails bypass shared caches and loading runs only while the frame or browser is visible. On head units whose MediaStore detects a mounted USB drive but indexes nothing, the empty USB view offers a lightweight direct browser plus a storage diagnostics dialog. Legacy saved sources remain readable.
- **KTV lyrics overlay** (shared by phone, TV, and frame 2): previous/current/next synced lines at the bottom with directional slide, word-level KTV sweep, and voice-agent placement (`v1` left, `v2` right, `v1000`/background centered). Includes an offline Simplified-to-Taiwan-Traditional converter and a one-tap lyrics-source switcher in the control row.
- **Photo frame KTV 2** (GMS phone/head-unit main menu; the original frame and its data stay preserved): opens directly into the saved local source or Drive folder. Drive validates a few randomized cached photos for immediate offline playback, expands to the full intact cache in the background, then refreshes OAuth and cloud metadata concurrently; cached photos keep rotating through auth/network failures with a stable error code, and cloud playback can pause/resume. See the [Google Drive test guide](docs/photo-frame-google-drive-testing.zh-TW.md).
- **Send photos over the local network**: phone/head-unit frame settings show a QR code and address (hotspot-friendly); the phone compresses photos and sends them straight to the frame with no USB drive, pairing code, or cloud relay. The receiver runs only while that settings section is open in the foreground. TV serves a phone picker page (up to 1920 px on the longest edge, no TV Google OAuth). Received copies accumulate separately and clear independently of local/USB originals.
- **TV remote experience** (Google TV/Android TV): D-pad interface with quick picks and online songs grouped under their original Home section names, cover art in Home/search/queue, remote like and add-to-playlist on song rows, a sidebar Library (liked songs plus saved playlists), and playlist detail with Play all/Save plus local edit actions. High-contrast section headings; Stop and exit ends playback and the service while Back keeps background music. The TV photo frame offers remote slideshow, settings, and lyrics switching; fullscreen icon or Menu hides the controls, any D-pad key brings them back.
- **TV login and updates**: the sidebar Account page shows a QR code, TV address, and 6-digit code while logged out; authorize from the phone account menu on the same Wi-Fi to push the login over and sync the library, no TV keyboard typing. FOSS TV builds include a remote-operated Update page (check, download with progress, install); GMS builds update manually.
- **Lyrics**: provider priority Paxsenix, LyricsPlus, KuGou, Better Lyrics, LrcLib, with YouTube Subtitles and YouTube Music as fallbacks. "Switch lyrics source" searches all enabled providers for the current track; manual picks are kept and never overwritten by auto-refetch. Includes synced lyrics, translation, and the KTV whole-line fallback for sources without word timings.
- **Car playback**: independent music volume that no longer fights navigation guidance and reliably restores after ducking; skip silence, sleep timer, audio normalization, tempo/pitch; voice search uses relevance-ranked YouTube results and continues through an extendable related-song radio.
- **Car UI**: MiniPlayer and frame text scale from the phone-size 1× baseline with the screen short edge up to the auto-scale cap (default 2×, up to 3×; Settings → Appearance, or TV frame Display settings). Simplified Home (category chips, 12 quick-access items, account playlists, at most 3 official sections); large adaptive grid playlist picker; landscape/portrait share one MiniPlayer size. Android Auto pages large libraries (100 per page) with SQL local search and on-demand artwork to reduce memory pressure.
- **Reliable sync**: Google login with multi-channel pick and later switching; the Cache playlist lists streamed tracks only. Likes use one ordered durable queue and playlist creates/adds/removes persist outside the database with auto-retry and pending display; full-sync cooldown starts only after pending work succeeds and pull-to-refresh joins a running sync instead of duplicating it. Repeated Play Next stays first-in-first-out and shuffle-safe; Previous/Next while paused starts playback everywhere.
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
