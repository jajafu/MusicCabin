# 語音控制公開入口（給任何語音助手用）

> 給語音助手的開發 agent 看的英文對接文件：[`voice-control-integration.md`](./voice-control-integration.md)（含完整參數、回傳格式與程式範例）。

MusicCabin 提供一組公開廣播入口，任何語音助手或 App 不用開畫面也能點歌和控制。
刻意不限定呼叫者是誰，開關由使用者在「設定 → 隱私 → 語音控制」裡決定。

## 基本資料

- 廣播動作：`com.metrolist.music.action.VOICE_CONTROL`
- 接收器：`com.metrolist.music.voice.VoiceControlReceiver`（公開，不設呼叫者限制）
- 建議用 `sendOrderedBroadcast()` 發送，才能收到明確結果

## 參數

| 參數 | 說明 |
| --- | --- |
| `command` | 下表其中一個動作名字 |
| `query` | 只有 `play_query` 需要，歌名（可含歌手） |

## 動作一覽

| `command` | 效果 |
| --- | --- |
| `play_query` | 照歌名播放。先找本地收藏，再找線上，都找不到就回沒找到 |
| `play_liked` | 播放喜愛清單，會回第一首歌名。清單是空的就明確回空的 |
| `play` | 繼續播，會回目前歌名。沒東西可播就回目前沒歌 |
| `pause` | 暫停。沒東西可播就回目前沒歌 |
| `next` | 下一首，會回切過去的歌名。沒有下一首就明確回覆 |
| `previous` | 上一首，會回切過去的歌名。沒有上一首就明確回覆 |
| `like` | 收藏目前這首（要先開第二個開關） |
| `unlike` | 取消收藏目前這首（要先開第二個開關） |
| `repeat_toggle` | 循環模式開關：關 → 全部循環 → 單曲循環 → 關 |

沒啟動也能用：入口連上播放服務時會自動冷啟動，不用先手動播一首。

## 回傳結果（有序廣播才有）

- `resultCode`：成功 `OK`，失敗 `CANCELED`
- `resultData`：一句話說明（會跟著手機語系走，中文或英文）
- 結果附帶：
  - `status`：`played` / `playing` / `paused` / `next` / `previous` / `liked` / `unliked` / `repeat` / `not_found` / `empty` / `no_network` / `denied` / `no_song` / `error`
  - `title`：播了哪首、收藏了哪首（有的話）
  - `mode`：循環模式結果，`off` / `all` / `one`
  - `message`：和 `resultData` 相同的說明文字

## 隱私開關

| 開關 | 預設 | 管哪些動作 |
| --- | --- | --- |
| 允許外部播放控制 | 開 | 播放、暫停、切歌、點歌、喜愛清單、循環模式 |
| 允許外部改收藏 | 關 | 收藏、取消收藏。打開前會跳提醒：手機上任何 App 都能改收藏 |

## 範例（adb）

```bash
# 傳歌名播放
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_query --es query "夜曲 周杰倫"

# 播喜愛清單
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_liked

# 暫停 / 下一首
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command pause
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command next
```
