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
| `query` | `play_query`、`play_artist` 需要：前者是歌名（可含歌手），後者只寫歌手名（後面多出來的「的歌」會自動去掉）。`volume_set` 需要：目標音量（見動作一覽） |

## 動作一覽

| `command` | 效果 |
| --- | --- |
| `play_query` | 照歌名播放。先找線上，再找本地收藏，都找不到就回沒找到 |
| `play_artist` | 照歌手播放。只講歌手時用，只留歌手對得上的歌（完全相同的排前面），並回真的播了哪首；一首都對不上就播最相關的一首，並照實說沒對上 |
| `play_liked` | 播放喜愛清單，會回第一首歌名。清單是空的就明確回空的 |
| `play` | 繼續播，會回目前歌名。沒東西可播就先播喜愛清單（這時回的狀態跟播喜愛清單一樣），連喜愛都是空的才回請先點歌 |
| `pause` | 暫停。沒東西可播就回目前沒歌 |
| `stop` | 停止（聲音停住，清單留著，之後用 `play` 還能繼續）。沒東西可播就回目前沒歌 |
| `now_playing` | 查現在播什麼，會回歌名加歌手。沒播就回目前沒歌 |
| `next` | 下一首，會回切過去的歌名。沒有下一首就明確回覆 |
| `previous` | 上一首，會回切過去的歌名。沒有上一首就明確回覆 |
| `like` | 收藏目前這首（要先開第二個開關）。已經收藏過就直接回已經收藏過了 |
| `unlike` | 取消收藏目前這首（要先開第二個開關）。本來沒收藏就直接回本來就沒收藏 |
| `repeat_toggle` | 循環模式開關：關 → 全部循環 → 單曲循環 → 關 |
| `repeat_off` | 直接關循環 |
| `repeat_all` | 直接設成全部循環 |
| `repeat_one` | 直接設成單曲循環 |
| `repeat_state` | 查目前是哪一種循環，不會切換 |
| `volume_up` | App 內音量大聲一點（一次 +10%），會回套用後的音量 |
| `volume_down` | App 內音量小聲一點（一次 −10%），會回套用後的音量 |
| `volume_set` | 直接把 App 內音量設成指定值。`query` 放 0～100 的整數（`80` → 80%）或 0～1 的小數（`0.8` → 80%），也收 `最大`／`max`（100%）和 `靜音`／`mute`（0%）；整數請直接寫 `80`，不要寫成 `80.0`（帶小數點的輸入一律照 0～1 小數檢查，範圍外就拒絕），超出範圍或不是數字會回 `error`，並請對方說 0 到 100 |
| `volume_get` | 查目前 App 內音量，不會改變 |

`volume_*` 四個指令調整的是音樂 App 自己的音量（就是播放畫面那條音量滑桿），不會動到系統音量。調音量不會解除靜音；「一起聽」房間裡跟隨端的音量會被房主下次同步蓋回；投放（Cast）中語音只調本機音量，不影響投放端音量。

沒啟動也能用：入口連上播放服務時會自動冷啟動，不用先手動播一首。

## 回傳結果（有序廣播才有）

- `resultCode`：成功 `OK`，失敗 `CANCELED`
- `resultData`：一句話說明（會跟著手機語系走，中文或英文）。提到歌名的訊息在知道歌手時會是「歌名 - 歌手」，直接念出來即可
- 結果附帶：
  - `status`：`played` / `playing` / `paused` / `stopped` / `now_playing` / `next` / `previous` / `liked` / `already_liked` / `unliked` / `already_unliked` / `repeat` / `volume` / `not_found` / `empty` / `no_network` / `denied` / `no_song` / `error`
  - `title`：歌名（只有歌名，不含歌手）
  - `artist`：歌手名（有的話）
  - `artist_matched`：只有 `play_artist` 才有。`true` 表示播的歌歌手有對上，`false` 表示沒對上、播的是最相關的一首
  - `is_playing`：只有 `now_playing` 才有。當下有沒有真的在播（暫停或停止後查歌會是 `false`）
  - `mode`：循環模式結果，`off` / `all` / `one`（`repeat_*` 才有）
  - `volume`：套用後的音量，0～1 的小數（`volume_*` 才有；例如 0.8 代表 80%）。`volume_set`／`volume_up`／`volume_down` 的套用值以 1% 為精度（小數四捨五入到百分比）；`volume_get` 回查詢當下的原值，不做精度調整
  - `message`：和 `resultData` 相同的說明文字

## 隱私開關

| 開關 | 預設 | 管哪些動作 |
| --- | --- | --- |
| 允許外部播放控制 | 開 | 播放、暫停、停止、查歌、切歌、點歌、喜愛清單、循環模式、音量 |
| 允許外部改收藏 | 關 | 收藏、取消收藏。打開前會跳提醒：手機上任何 App 都能改收藏 |

## 範例（adb）

```bash
# 傳歌名播放
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_query --es query "夜曲 周杰倫"

# 只講歌手時照歌手播放
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_artist --es query "周杰倫"

# 播喜愛清單
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command play_liked

# 查現在播什麼
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command now_playing

# 暫停 / 停止 / 下一首
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command pause
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command stop
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command next

# 循環直接指定 / 查目前是哪一種
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command repeat_one
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command repeat_state

# App 內音量大聲一點 / 小聲一點
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command volume_up
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command volume_down

# 音量直接指定（80%）、查目前音量
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command volume_set --es query "80"
adb shell am broadcast -a com.metrolist.music.action.VOICE_CONTROL \
  --es command volume_get
```
