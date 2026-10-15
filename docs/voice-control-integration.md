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
| `query`   | String | For `play_query` | Song name, artist name may be included |

## 3. Commands

| `command`       | Effect                                                                 |
|-----------------|------------------------------------------------------------------------|
| `play_query`    | Play by song name. Searches the local library first, then online.      |
| `play_liked`    | Play the favorites list from the newest liked song.                    |
| `play`          | Resume playback.                                                       |
| `pause`         | Pause playback.                                                        |
| `next`          | Skip to next song and keep playing.                                    |
| `previous`      | Go back to previous song and keep playing.                             |
| `like`          | Like the currently playing song.                                       |
| `unlike`        | Remove the like from the currently playing song.                       |
| `repeat_toggle` | Cycle repeat mode: `off` → `all` → `one` → `off`.                      |

## 4. Result contract (ordered broadcast only)

- `resultCode`: `Activity.RESULT_OK` on success, `Activity.RESULT_CANCELED` on failure.
- `getResultData()`: one human-readable sentence. Language follows the phone
  locale (Chinese or English). Do **not** parse it — parse the extras below.
- `getResultExtras()` (Bundle, may be null if the app is missing/broken):
  - `status` (String): machine-readable outcome, see section 5.
  - `message` (String): same text as `getResultData()`.
  - `title` (String, optional): the song the action refers to
    (`play_query`, `play_liked` first song, `play`, `like`, `unlike`,
    `next`, `previous`).
  - `mode` (String, only for `repeat_toggle`): `off`, `all`, or `one`.

## 5. Status values

| `status`     | Meaning                                              | `resultCode` |
|--------------|------------------------------------------------------|--------------|
| `played`     | Now playing `title`                                  | OK           |
| `playing`    | Resumed, `title` is current song                     | OK           |
| `paused`     | Paused                                               | OK           |
| `next`       | Skipped, `title` is the new song                     | OK           |
| `previous`   | Went back, `title` is the new song                   | OK           |
| `liked`      | `title` is now liked                                 | OK           |
| `unliked`    | Like removed from `title`                            | OK           |
| `repeat`     | Repeat mode changed, see `mode`                      | OK           |
| `not_found`  | `play_query` matched nothing                         | CANCELED     |
| `empty`      | Favorites list is empty                              | CANCELED     |
| `no_network` | Offline (or online search failed) and nothing local  | CANCELED     |
| `denied`     | Blocked by the user's privacy switch (see section 6) | CANCELED     |
| `no_song`    | Nothing is playing / nothing to act on               | CANCELED     |
| `error`      | Unexpected failure or timeout                        | CANCELED     |

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
voiceCommand(context, "pause")
voiceCommand(context, "repeat_toggle")
```

## 8. Quick test with adb

```bash
# Play by song name
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_query --es query "夜曲 周杰倫"

# Play favorites
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_liked

# Pause / next / repeat
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command pause
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command next
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL --es command repeat_toggle
```

Note: `am broadcast` is unordered, so it triggers the action but prints no
result. To verify results, test from real code with `sendOrderedBroadcast`.
