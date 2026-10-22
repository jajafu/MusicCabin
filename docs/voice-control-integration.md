# MusicCabin Voice Control — Integration Reference for Assistant Agents

This document tells a voice-assistant developer (or coding agent) exactly how to
call the MusicCabin music app. No screen, no login, no special permission needed.

## 1. Endpoint

- Broadcast action: `com.metrolist.music.action.VOICE_CONTROL`
- Receiver: `com.metrolist.music.voice.VoiceControlReceiver`
- The receiver is **exported with no caller restriction**. Any app can send to it.
- Always use `sendOrderedBroadcast()` — a normal (unordered) broadcast gets
  **no result back**.
- It is safest to pin the target package with `intent.setPackage(...)`.
  The base applicationId is `com.jajafu.musiccabin` (flavors may append
  suffixes such as `.gms` or `.debug`).
- The app does **not** need to be running. Connecting cold-starts playback.
- The whole call must finish within ~9 seconds; wait at most ~10 seconds
  for the result.

## 2. Request extras

| Extra     | Type   | Required | Meaning                                    |
|-----------|--------|----------|--------------------------------------------|
| `command` | String | Yes      | One of the commands in section 3           |
| `query`   | String | For `play_query` / `play_artist` / `volume_set` | Song name (artist may be included) for `play_query`; artist name only for `play_artist` (a trailing 的歌/歌 is stripped); target volume for `volume_set` (see section 3) |

## 3. Commands

| `command`       | Effect                                                                 |
|-----------------|------------------------------------------------------------------------|
| `play_query`    | Play by song name. Searches online first, then the local library.      |
| `play_artist`   | Play by artist name only. Keeps only songs whose artist matches (exact matches first) and reports which song was played. If nothing matches, plays the closest match and says so (`artist_matched=false`). |
| `play_liked`    | Play the favorites list from the newest liked song.                    |
| `play`          | Resume playback. If the queue is empty, plays the favorites list; if that is empty too, returns `no_song` asking for a song first. |
| `pause`         | Pause playback.                                                        |
| `stop`          | Stop playback (sound stops, queue is kept, `play` resumes).            |
| `now_playing`   | Report the current song plus artist.                                   |
| `next`          | Skip to next song and keep playing.                                    |
| `previous`      | Go back to previous song and keep playing.                             |
| `like`          | Like the currently playing song. Already liked returns `already_liked`. |
| `unlike`        | Remove the like from the currently playing song. Not liked returns `already_unliked`. |
| `repeat_toggle` | Cycle repeat mode: `off` → `all` → `one` → `off`.                      |
| `repeat_off`    | Set repeat mode to `off` directly.                                     |
| `repeat_all`    | Set repeat mode to `all` directly.                                     |
| `repeat_one`    | Set repeat mode to `one` directly.                                     |
| `repeat_state`  | Report the current repeat mode without changing it.                    |
| `volume_up`     | Raise the app's own music volume by 10% and report the new level.      |
| `volume_down`   | Lower the app's own music volume by 10% and report the new level.      |
| `volume_set`    | Set the app's own music volume to a target. `query` carries a whole number 0–100 (`80` → 80%) or a 0–1 decimal (`0.8` → 80%); `最大`/`max` means 100%, `靜音`/`mute` means 0%. Send whole numbers as `80`, not `80.0` — anything containing a decimal point is validated as a 0–1 fraction and rejected otherwise. Out-of-range or non-numeric values return `error` asking for 0–100. |
| `volume_get`    | Report the current app music volume without changing it.               |

The `volume_*` commands adjust the **music app's own volume** (the slider on
the player screen), not the device/system volume. Map spoken phrases like
「大聲一點」→ `volume_up`, 「小聲一點」→ `volume_down`, 「音量調到 80」→
`volume_set` with `query=80`. Volume changes do not unmute a muted player,
in a Listen Together room a follower's volume is overwritten by the
host's next sync, and while casting they only change the local volume,
not the cast target's volume.

## 4. Result contract (ordered broadcast only)

- `resultCode`: `Activity.RESULT_OK` on success, `Activity.RESULT_CANCELED` on failure.
- `getResultData()`: one human-readable sentence. Language follows the phone
  locale (Chinese or English). Do **not** parse it — parse the extras below.
  Messages naming a song use "title - artist" when the artist is known, so
  they can be spoken as-is (`title` itself stays the pure song title).
- `getResultExtras()` (Bundle, may be null if the app is missing/broken):
  - `status` (String): machine-readable outcome, see section 5.
  - `message` (String): same text as `getResultData()`.
  - `title` (String, optional): the song title only (never combined with artist).
  - `artist` (String, optional): the artist name when known.
  - `artist_matched` (Boolean, only for `play_artist`): `true` when the played
    song's artist matches the request, `false` when nothing matched and the
    closest match was played instead.
  - `is_playing` (Boolean, only for `now_playing`): whether playback is
    actually playing right now (`false` after pause/stop).
  - `mode` (String, only for `repeat_*`): `off`, `all`, or `one`.
  - `volume` (Float, only for `volume_*`): the applied volume as a 0–1
    fraction (0.8 means 80%). Values applied by `volume_set` / `volume_up` /
    `volume_down` are quantized to 1% precision (decimals rounded to whole
    percent); `volume_get` returns the queried value as-is.

## 5. Status values

| `status`     | Meaning                                              | `resultCode` |
|--------------|------------------------------------------------------|--------------|
| `played`     | Now playing `title` (for `play` with an empty queue this means the favorites fallback, same as `play_liked`) | OK           |
| `playing`    | Resumed, `title` is current song                     | OK           |
| `paused`     | Paused                                               | OK           |
| `stopped`    | Stopped (queue kept, `play` resumes)                 | OK           |
| `now_playing`| `title` plus `artist` is the current song            | OK           |
| `next`       | Skipped, `title` is the new song                     | OK           |
| `previous`   | Went back, `title` is the new song                   | OK           |
| `liked`      | `title` is now liked                                 | OK           |
| `already_liked` | `title` was already liked, nothing changed        | OK           |
| `unliked`    | Like removed from `title`                            | OK           |
| `already_unliked` | `title` was not liked, nothing changed        | OK           |
| `repeat`     | Repeat mode changed/reported, see `mode`             | OK           |
| `volume`     | App volume applied/reported, see `volume` (Float)    | OK           |
| `not_found`  | `play_query` matched nothing                         | CANCELED     |
| `empty`      | Favorites list is empty                              | CANCELED     |
| `no_network` | Offline (or online search failed) and nothing local  | CANCELED     |
| `denied`     | Blocked by the user's privacy switch (see section 6) | CANCELED     |
| `no_song`    | Nothing is playing / nothing to act on               | CANCELED     |
| `error`      | Unexpected failure, timeout, or invalid `volume_set` input | CANCELED     |

## 6. Privacy gates (user-controlled)

Settings → Privacy → Voice control:

| Switch | Default | Gates these commands                          |
|--------|---------|-----------------------------------------------|
| Allow external playback control | ON  | All except `like` / `unlike` |
| Allow external favorite edits   | OFF | `like`, `unlike`             |

If the matching switch is off, the call returns `denied` and nothing happens.
Before enabling favorite edits, the app warns the user that **any** app on
the phone will then be able to change likes.

## 7. Kotlin example

```kotlin
fun voiceCommand(context: Context, command: String, query: String? = null) {
    val intent = Intent("com.metrolist.music.action.VOICE_CONTROL").apply {
        setPackage("com.jajafu.musiccabin")
        putExtra("command", command)
        query?.let { putExtra("query", it) }
    }
    context.sendOrderedBroadcast(
        intent,
        /* receiverPermission = */ null,
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val ok = resultCode == Activity.RESULT_OK
                val extras = getResultExtras(false)
                val status = extras?.getString("status").orEmpty()
                val title = extras?.getString("title").orEmpty()
                val mode = extras?.getString("mode").orEmpty()
                val message = resultData.orEmpty()
                // Speak `message` to the user; branch logic on `status`.
            }
        },
        /* scheduler = */ null,
        Activity.RESULT_CANCELED,
        /* initialData = */ null,
        /* initialExtras = */ null,
    )
}

// Usage:
voiceCommand(context, "play_query", "夜曲 周杰倫")
voiceCommand(context, "play_artist", "周杰倫")
voiceCommand(context, "now_playing")
voiceCommand(context, "pause")
voiceCommand(context, "stop")
voiceCommand(context, "repeat_toggle")
voiceCommand(context, "volume_up")
voiceCommand(context, "volume_down")
voiceCommand(context, "volume_set", "80")
voiceCommand(context, "volume_get")
```

## 8. Quick test with adb

```bash
# Play by song name
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_query --es query "夜曲 周杰倫"

# Play by artist name only
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_artist --es query "周杰倫"

# Play favorites
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_liked

# Now playing / pause / stop / next / repeat
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command now_playing
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command pause
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command stop
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command next
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command repeat_toggle
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command repeat_state

# App music volume: louder / quieter / set to 80% / query
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command volume_up
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command volume_down
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command volume_set --es query "80"
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command volume_get
```

Note: `am broadcast` is unordered, so it triggers the action but prints no
result. To verify results, test from real code with `sendOrderedBroadcast`.
