[English](README.md) | [繁體中文](README.zh-TW.md)

# MusicCabin

MusicCabin is a customized Android Car-oriented fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist), an open-source YouTube Music client for Android.

This fork is maintained by [jajafu](https://github.com/jajafu) and focuses on a more readable and practical in-car playback experience.

## Current customizations

- Photo frame replaces Listen Together in the main menu. It opens a full-screen local-photo slideshow (or a blank background before selection) with clock, song information, music playback controls, Previous/Next photo controls, and in-frame settings. The enlarged overlay uses one wrapping information row for clock, song title, and artist, followed by a separate wrapping row for all controls, keeping them readable in both landscape and portrait. Its on-demand MediaStore browser pages through indexed internal and USB/SD photos without startup scanning, can select or deselect an entire indexed folder at once, and follows the system language with complete Traditional Chinese text. On Android 10 head units whose vendor MediaStore detects a mounted USB drive but indexes no images, the empty USB view offers a lightweight direct browser: it reads only the open directory, scans recursively only when selecting that whole folder, and remembers file paths without copying photos into App storage. An on-demand storage diagnostics dialog reports Android SDK, system storage description, MediaStore volume name, mount state/path/UUID when available, and indexed photo count. The unreliable system file and SAF folder picker actions have been removed; sources saved by earlier versions remain readable. Choose 5/10/15/30/60-second intervals with a horizontal slider, choose fit/crop, and rescan after reconnecting a drive. Original files are never copied or deleted. Browser thumbnails bypass shared caches, and image loading only runs while the frame or browser is visible. Use while parked; photos are not shown in Android Auto. Listen Together remains available under Settings → Integrations and through its optional top-bar shortcut.
- Enlarged the playback island by 2× for better visibility.
- Fixed scrolling for the Theme and Color settings in landscape orientation.
- Increased dark-mode contrast by changing adjustment-button outlines to pure white.
- Increased the cached playback queue to three tracks.
- Removed the sleep button from the playback cover and enlarged the other buttons.
- The mini-player's add-to-playlist picker uses large adaptive playlist cards matching the library grid for easier in-car operation.
- The home screen is reduced to category chips, 12 local quick-access items, account playlists, and at most three official YouTube recommendation sections, excluding “Listen again” and “Covers & remixes.” Expensive duplicate discovery, community, similar-content, mood-and-genre, random-order, and infinite-pagination sections are not loaded, and quick-access items remain safe if a song is removed during synchronization.
- Charts and Explore show a retry action instead of an endless loading animation after a failed request, while Home always releases its loading and pull-to-refresh indicators after errors.
- Uses the `MusicCabin` name and black music-car logo across the launcher, About screen, playback notifications, and store artwork. About lists jajafu as project maintainer with a [Buy me a coffee](https://buymeacoffee.com/clifchi) button and Mo Agamy among the collaborators, focusing the page on project credits without the community/info or Palestine footer.
- Uses the dedicated Android package ID `com.jajafu.musiccabin`, allowing this fork and the original Metrolist app to be installed at the same time.
- New playlists default to YouTube Music sync when the account and sync setting are active. Failed playlist creation, song additions, and song removals are stored outside the App database, retried automatically, and shown as pending in the playlist library; duplicate-song removals preserve the exact YouTube occurrence, new remote playlists receive a reconciliation grace period, duplicate remote playlists reconcile to one protected local record without losing downloaded songs, and large song-ID operations are split below SQLite limits.
- Song like and unlike actions use one ordered, durable synchronization queue. Rapid opposite actions keep the latest state, failed YouTube updates remain pending across App restarts, and duplicate network requests are avoided. Remote reconciliation preserves the latest pending local choice while respecting remote changes when no local action remains; likes for device-local files never reach YouTube.
- Automatic full sync starts its cooldown only after every required component and pending song-like or playlist edit succeeds. Partial failures remain visible and can be retried immediately.
- Library pull-to-refresh joins an already queued or running full sync, ignores repeated pull gestures, keeps the indicator active until the shared operation finishes, and reports partial failures instead of running duplicate back-to-back syncs.
- Rapid queue or radio selections keep only the latest request, preventing slower network responses from replacing the active playback queue.
- Repeated Play Next requests remain first-in-first-out. Their full manual-priority block stays ahead of the automatic queue when shuffle is enabled and is safely updated after transitions, repeats, removals, queue replacement, clearing, or service restoration.
- Using Previous or Next while paused automatically starts playback across player buttons, swipe gestures, widgets, notifications, the lock screen, and car media controls.
- Podcast, UGC, and unknown media types validate WEB_REMIX streams before playback. Player configuration refreshes use this repository's mirror first and consult the authoritative zemer-cipher source only when the current YouTube player is still missing, avoiding playback outages during mirror lag.
- Backup, restore, CSV preview, and M3U import file work runs in the background. File previews stream or cap input to avoid loading large documents into memory, and newer selections cancel stale preview work. Restores validate staged database and settings files before replacing live data, roll back all replacements on failure, and restart into a usable database state when required.
- Foreground-service startup handling and widget themes are compatible with Android 8.0 (API 26) through current Android releases.
- Wrapped safely supports listening histories with fewer than five eligible top songs.
- Automatic queue continuation retries temporary failures with bounded backoff, waits for network recovery without duplicate requests, and offers a manual retry after the final failure. When a YouTube continuation ends, enabled similar content starts a new radio from the current tail song so playback can keep extending beyond the first 99 tracks without duplicating queued songs. End-of-queue checks follow the actual shuffled playback order, and YouTube continuation state survives an App service restart.
- Instant silence skipping resets its detector and cancels delayed seek work whenever the track changes, preventing silence from one song from skipping into the next.
- Artwork supplied to notifications, lock-screen metadata, and car media controls is copied into an independently owned software bitmap, preventing image-cache recycling from crashing Media3 during rapid track changes.
- Parallel downloads share a thread-safe URL cache, preventing duplicate resolution and cache races.
- Security hardening limits cleartext Listen Together connections to local-network servers, isolates private widget actions from exported update receivers, and grants custom media commands only to trusted controllers.
- Android Auto browses large libraries in pages of at most 100 items, with a More content folder for clients that request the entire list. Local search filters in SQL and loads at most 100 matches; full playlist playback preserves song order and repeated entries while loading database relationships in batches. Browse artwork uses on-demand local image URIs with bounded cover size and disk caching instead of preloading every cover or embedding image files, reducing memory pressure and out-of-memory crash risk.
- Login supports Google accounts with multiple YouTube channels: pick the channel during sign-in and switch channels later from account settings. The Cache playlist lists streamed tracks only; finished downloads live in the download library and no longer occupy player-cache space.

## Features

- Independent volume control. Unlike standard YouTube Music which only follows system volume, this app allows separate music volume adjustment to reduce interference with navigation guidance. Music volume now reliably returns to its configured level after navigation guidance ducks or temporarily pauses playback.
- Stream music from YouTube Music.
- Background playback and offline downloads. Download songs from the mini player; use the player's overflow menu to subscribe or unsubscribe to the primary artist.
- Skip silence, sleep timer, audio normalization, tempo and pitch control.
- Synced lyrics and lyrics translation.
- Search for songs, albums, artists and playlists.
- Library, local playlist and account synchronization.
- Listen together with other users.
- Material 3 interface with light, dark, black, dynamic and preset color themes.
- Android Auto-focused layout and playback controls. Voice playback uses YouTube's relevance-ranked song results directly, so a cold local database cannot drop the best match, and continues through an extendable related-song radio instead of stopping after a finite search list.
- High-resolution image URL handling for YouTube's current image CDN formats.
- Car-focused defaults with large grids and a minimal set of generated playlists.

## Build and updates

Build the FOSS release variant locally with:

```bash
./gradlew :app:assembleFossRelease
```

GitHub Actions workflows run manually. The release workflow builds only the FOSS Release APK and publishes `MusicCabin-v<version>-car.apk` to this repository's GitHub Releases. Release notes use the matching version entry in `changelog.md` and are refreshed when an existing release is rerun. The workflow requires the fixed Android signing secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`; do not commit the keystore or passwords. Player configuration synchronization also runs manually.

The in-app updater checks [this repository's releases](https://github.com/jajafu/MusicCabin/releases) and opens the matching APK download for confirmation. Android still requires the user to approve installation.

Release names may include the `-car` suffix; the updater compares their numeric version components with the installed app version, so the current release is not reported as a new update.

Starting with version `13.6.77`, MusicCabin uses the package ID `com.jajafu.musiccabin` (Debug: `com.jajafu.musiccabin.debug`). Versions `13.6.52`–`13.6.76` used `com.jajafu.metrolist.androidcar`. Android installs the new package as a separate App even with the same signing key; it cannot update the old package in place. Local settings, login, downloads, database, permissions, and pinned widgets/shortcuts do not migrate automatically. Sign in and configure the new installation, grant permissions, and recreate widgets/shortcuts as needed; remove the old App only after checking anything you want to keep. The database schema remains unchanged. Future releases with the new package ID and the same fixed signing key can update in place.

Version `13.6.76` unified the visible MusicCabin branding; `13.6.77` also renames the package ID as described above. The updater accepts both MusicCabin and older APK filenames. New playlist exports and saved images use `MusicCabinExports` and `Pictures/MusicCabin`; existing files remain in their original locations. Brand-related text uses new English resources to prevent inherited translations from restoring the old name.

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
