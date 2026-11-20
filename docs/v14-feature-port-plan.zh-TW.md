# MusicCabin 功能移植規劃書：Metrolist v14 五項功能

> 狀態：實作中（功能一、二、三、四已完成；目前進行功能五，先做第 0 步裝置端技術驗證）
> 建立日期：2026-11-17
> 對應上游：Metrolist v14.0.0（2026-11-14 發佈）
> 適用版本：MusicCabin 13.7.96（versionCode 326）之後

---

## 1. 背景與範圍

上游 Metrolist v14.0.0 是把整個專案重寫成跨平台版本（Windows/macOS/Linux/iOS）的里程碑，Android 原始碼架構已完全不同，**無法直接合併原始碼**。本規劃書的內容是：把 v14 中選定的五項功能，在 MusicCabin 現有的 Android 專案上重新實作。

五項功能（依評估結果排序；YouTube 直播經評估後排除——工程量大、非使用重點，詳見附錄 B）：

| # | 功能 | 預估工作量 | 分類 |
|---|---|---|---|
| 1 | 車機離線瀏覽已存歌單 | 小 | 車用 |
| 2 | 車機釘選歌單＋佇列控制 | 中 | 車用 |
| 3 | 封鎖 AI 生成歌手／隱藏播客 | 中 | 內容過濾 |
| 4 | 歌詞逐字時間軸編輯器 | 中大 | 歌詞／KTV |
| 5 | 音樂錄影帶（MV）播放 | 大 | 播放（手機/平板） |

### 目前進度

| 版本 | 功能 | 狀態 | 提交 |
|---|---|---|---|
| 13.8.1 | 車機離線瀏覽已存歌單 | 已完成並推送 | `cb18e722` |
| 13.8.2 | 車機釘選歌單＋佇列控制 | 已完成並推送 | `dfeff635` |
| 13.8.3 | 封鎖 AI 生成歌手／隱藏播客 | 已完成並推送 | `38334251` |
| 13.8.4 | 封鎖 AI 生成歌手／隱藏播客修正版 | 已完成並推送 | `bff9d6cd` |
| 13.8.5 | 歌詞逐字時間軸編輯器 | 已完成並推送 | `40cff300` |
| 13.8.6 | 音樂錄影帶（MV）播放 | 已完成（手機／平板與 Android TV 模擬器驗收通過；TV 維持純音訊） | — |
| 13.8.7 | MV 高畫質／全螢幕（可選） | 尚未開始 | — |

13.8.3 目前實際範圍是首頁的線上推薦區塊、首頁帳號歌單／播客專區，以及線上搜尋結果：提供名稱關鍵字的疑似 AI 歌手偵測、播客過濾與手動隱藏歌手名單。解析器徽章偵測、車機、TV、語音、本機資料庫與其他頁面的完整套用，留待後續版本；因此本版不宣稱這些入口已同步過濾。

### 非目標（本規劃明確不做）

- 不遷移到 v14 跨平台（KMP）架構；MusicCabin 的相框KTV、TV 相框、車機修正都綁在現有 Android 架構上。
- 不做 Material 3 Expressive 全面改版、區網裝置互控、DLNA/FCast 投放、ListenBrainz、其他服務歌單匯入、備份匯入、一起聽（已於 13.7.90 徹底停用）。
- 不做影片的離線下載：MV 播放是**線上串流觀看影片**（撥放時有畫面），本功能不把影片檔存進裝置供離線觀看；現有的離線下載功能維持現狀，不因本功能改變。
- 不在 TV 端做歌詞編輯器（遙控器輸入不切實際；TV 相框的逐字顯示會自動受益於編輯結果）。

### 共通原則

- 字串一律加在 `app/src/main/res/values/metrolist_strings.xml`（專案規則）。
- 每完成一項功能即獨立遞增版本（13.8.1 起）、更新 `changelog.md` 與 `README.md`/`README.zh-TW.md`。
- 資料庫結構原則上不動；兩處需要新表／新欄位的方案已在各節標明「需要遷移」，實作前另行確認。
- 每項功能驗證：`./gradlew :app:assembleFossDebug` + 相關單元測試 + 對應手動測試。

---

## 2. 功能一：車機離線瀏覽已存歌單

### 目標

車機（Android Auto）在沒有網路時（隧道、地下停車場、山區），仍能瀏覽並播放已收藏歌單的內容。

### 現況調查結論

車機瀏覽樹有兩個與歌單有關的節點，離線表現不同：

- **「播放清單」節點**（`playlist/<id>`）：其實**已經是離線可用**。已存的 YouTube 歌單會由 `SyncUtils.executeSyncPlaylist()`（`utils/SyncUtils.kt` 2092–2168 行）把歌曲快取進 Room 資料庫，車機瀏覽純讀資料庫（`AndroidAutoLibrary.songIds`），不碰網路。
- **真正缺口一：從未同步成功的歌單**。剛收藏、同步失敗、或剛登入的帳號，歌單在車機上顯示 0 首歌，且沒有任何補救路徑。
- **真正缺口二：「YouTube 建議歌單（Mixes）」節點**（`youtube_playlist/<id>`，`MediaLibrarySessionCallback.kt` 403–445、460–490 行）：清單與內容**全部靠即時網路請求**（`YouTube.home()`、`YouTube.playlist()`），離線時整個節點空白、點播無聲無息失敗。
- 附帶：封面走 `CarArtworkProvider` 需網路下載，離線時封面空白（僅外觀問題，已有 96 張 PNG 快取）。

### 實作方案

**步驟 1 — 未同步歌單的線上補救（播放清單節點）**
- `MediaLibrarySessionCallback` 的歌單子項分支（454–458 行）：當 `carLibrary.songIds` 回傳空、且該歌單有 `browseId` 時，嘗試 `YouTube.playlist(browseId)` 抓一次內容填充；失敗（含離線）則維持現狀並回傳「歌曲尚未同步」提示項。
- 車機服務已有 `MusicService.isNetworkConnected` StateFlow（351 行）可用於先行判斷，避免離線時白等逾時。

**步驟 2 — Mixes 節點快取**
- 把最後一次成功的 `YouTube.home()` 歌單清單（每項含 browseId、標題、封面 URL、歌曲數）以 JSON 存入 DataStore（免資料庫遷移；資料量約數 KB）。
- 離線時：節點清單改由快取提供；單一建議歌單的內容離線時顯示「需連網」提示項（不快取整份歌曲內容，控制資料量）。
- 連線時：由現有請求結果順手刷新快取（寫在 callback 取得清單成功之處）。

**步驟 3 — 細節**
- 播放路徑（`onSetMediaItems` 的 `YOUTUBE_PLAYLIST` 分支，649–676 行）離線時改回傳明確的錯誤提示而非靜默失敗。
- 封面：維持現有快取行為，不做額外離線封面下載（列入未來加強）。

### 風險與限制

- 線上補救在車機執行緒上需留意逾時（沿用 `runCatching` + 逾時上限，比照現有 Mixes 路徑）。
- Mixes 快取可能過期（建議歌單會輪替）：離線顯示的清單標注「上次更新」時間即可接受。

### 驗證

- 開啟飛航模式後：已同步歌單可瀏覽播放（回歸確認）、未同步歌單出現提示、Mixes 顯示快取清單。
- 單元測試擴充：`AndroidAutoLibraryTest.kt`。

### 工作量：小

---

## 3. 功能二：車機釘選歌單＋佇列控制

### 目標

- 車機上把最常用的歌單釘選在最上層，一鍵直達。
- 車機上可對歌曲執行「下一首播放」「加入佇列」。

### 現況調查結論

- 車機根節點區塊由 `AndroidAutoSection` 列舉驅動（`AndroidAutoSettings.kt` 62–68 行），支援排序與開關，加新區塊是現成模式。
- **釘選資料源現成**：專案已有「速度撥號」表 `speed_dial_item`（`db/entities/SpeedDialItem.kt`，含 SONG/ALBUM/ARTIST/PLAYLIST/LOCAL_PLAYLIST 類型），歌單長按選單已有釘選入口（`ui/menu/PlaylistMenu.kt` 497–529、`YouTubePlaylistMenu.kt` 579–581），**不需要資料庫遷移**。
- **佇列控制缺失的根因**：`MediaLibrarySessionCallback` 只覆寫了 `onSetMediaItems`，沒有覆寫 `onAddMediaItems`；Media3 預設實作回傳 `ERROR_NOT_SUPPORTED`，所以 Android Auto 項目選單裡的「下一首播放／加入佇列」必然失敗。
- 服務端 `playNext`/`addToQueue` 已存在（`MusicService.kt` 2390–2461 行，含去重與 `ManualQueueTracker` 插入順序邏輯），只差車機接線。

### 實作方案

**步驟 A — 釘選歌單區塊**
1. `AndroidAutoSection` 新增 `PINNED` 值；`serializeSections`/`deserializeSections`（79–91 行）同步支援。
2. `onGetChildren` 根分支（276–336 行）新增釘選區塊：讀 `speed_dial_item` 中 `PLAYLIST` 與 `LOCAL_PLAYLIST` 類型項目；`LOCAL_PLAYLIST` 對應 `playlist/<id>`，遠端 `PLAYLIST`（browseId）若已同步進 Room 走本機路徑，否則走 `youtube_playlist/<browseId>` 線上路徑。
3. 子項完全複用現有 `playlist/<id>` 路徑（Room 歌曲、隨機播放偽項目、分頁全部現成）。
4. `AndroidAutoSettings` 區塊清單自動納入新區塊（排序/開關沿用）。
5. 釘選數量上限 10（超過略過，避免車機清單過長）。

**步驟 B — 佇列控制**
1. **覆寫 `onAddMediaItems`**（`MediaLibrarySessionCallback`）：解析傳入項目的 mediaId 路徑 → 重用 `onSetMediaItems` 的解析邏輯（634–676 行模式）產生可播放 `MediaItem`。這一步就會讓 Android Auto 原生的「下一首播放／加入佇列」選單項直接生效（插入位置由 Media3 依 `addMediaItems(index, …)` 決定；不保證套用 App 內服務層的去重與隨機手動插入規則）。
2. **補充自訂指令**（比照 `CommandToggleLike` 模式）：`MediaSessionConstants` 加 `ACTION_PLAY_NEXT`/`ACTION_ADD_TO_QUEUE`（Bundle 帶 mediaId，比照 `ACTION_SET_VOLUME` 帶參數的先例）；`onConnect` 註冊、`onCustomCommand` 派發（兩處都要過 `MediaControllerCommandPolicy.canUseCustomCommands` 檢查），呼叫 `service.playNext(...)`/`service.addToQueue(...)`。
3. `MusicService.updateNotification` 的 `setCustomLayout`（1702–1760 行）視需要加「下一首播放」按鈕到車機現在播放畫面。

### 風險與限制

- 分頁上限 `CAR_PAGE_SIZE = 100` 與 `AndroidAutoLibrary.songs` 的 `require` 檢查：新區塊必須走既有 `CarPage`/`slicePage` 分頁機制。
- 自訂指令若未同時在 `onConnect` 註冊會回 `ERROR_PERMISSION_DENIED`；舊款車機走相容路徑（legacy `MediaBrowserService`）時自訂指令行為可能不同，以 `onAddMediaItems` 為主要機制可避開。
- 「下一首播放」在隨機模式下依賴 `ManualQueueTracker` 的插入邏輯，直接呼叫服務函式即可繼承，不重寫。

### 驗證

- 單元測試：`AndroidAutoLibraryTest.kt`、`MediaControllerCommandPolicyTest.kt` 擴充。
- 手動：Android Auto 模擬器（DHU）驗證釘選區塊、項目長按選單、插入順序（含隨機模式）。

### 工作量：中

---

## 4. 功能三：封鎖 AI 生成歌手／隱藏播客

### 目標

- 搜尋與首頁不再出現 AI 生成的歌手／歌曲內容。
- 可隱藏播客內容（首頁、搜尋）。
- 可手動把特定歌手加入隱藏名單。

### 現況調查結論

- 專案已有三個內容過濾器（`filterExplicit`、`filterVideoSongs`、`filterYoutubeShorts`，`innertube/.../models/YTItem.kt` 163–182 行），在約 15 個呼叫點鏈接（首頁、搜尋、歌手頁、資料庫、車機、TV、語音），**新增過濾器就是複製這個模式**。
- 首頁「整個區塊隱藏」有現成先例：`HomeSectionPolicy.shouldHideHomeSection()`（`viewmodels/HomeSectionPolicy.kt`，含單元測試）。
- **AI 偵測的現實**：目前 innertube 解析器沒有讀取任何「AI 生成」標記；YouTube 若在回應中附帶 AI 徽章，會在 `MUSIC_EXPLICIT_BADGE` 檢查點被當成未知類型忽略。偵測需採「徽章偵測＋名稱關鍵字」混合，準確率有限，須搭配手動隱藏名單補足。
- 播客項目有明確型別（`PodcastItem`、`EpisodeItem`、`SongItem.isEpisode`），隱藏播客是精確過濾。
- 設定 UI 模式現成（`ContentSettings.kt` 645–729 行的 `Material3SettingsItem` + `Switch`）。

### 實作方案

**步驟 1 — 設定鍵**（`constants/PreferenceKeys.kt`，97–99 行旁）
- `BlockAiArtistsKey`（封鎖 AI 生成內容，預設關）
- `HidePodcastsKey`（隱藏播客，預設關）
- `HiddenArtistIdsKey`（`stringSetPreferencesKey`，隱藏歌手名單；比照 `ManualLyricsKey` 模式，免資料庫遷移）

**步驟 2 — 過濾器**（`innertube` 模組）
- `YTItem.kt` 新增：
  - `filterPodcasts(enabled)`：剔除 `PodcastItem`、`EpisodeItem`、`SongItem.isEpisode`。
  - `filterHiddenArtists(hidden: Set<String>)`：剔除歌手項目（id 比對），以及歌曲/專輯/歌單中歌手 id 相符者。
  - `filterAiArtists(enabled)`：名稱關鍵字比對（中英文「AI」「AI 生成」「AI Generated」等）+ 解析器端徽章偵測。
- `SearchSummaryPage.kt`（32–47 行旁）、`HomePage.kt`（359–372 行旁）加頁面級版本（區塊全空則整塊移除）。
- 解析器徽章偵測：在 `SearchSummaryPage.kt`/`HomePage.kt` 現有的 explicit 徽章檢查點（如 `MUSIC_EXPLICIT_BADGE` 判斷處）加對 AI 相關 `iconType` 的辨識；實際值以抓包確認後寫死對照表。

**步驟 3 — 套用點**
- 與現有三個過濾器同處鏈接：`HomeViewModel`（456–464、504 行等）、`OnlineSearchViewModel`（59–62、100–106、130–133 行）、`ArtistViewModel`、`LibraryViewModels`、`MusicService` 車機路徑（採 1243–1246 行的快取偏好模式）、`TvScreen`、`VoiceControlReceiver`。
- 建議抽一個共用輔助函式（收所有過濾旗標一次套用），避免 4 連鎖在 15 個點重複。

**步驟 4 — 播客首頁區塊隱藏**
- `HomeSectionPolicy` 加入播客區塊規則（比照既有 history/covers 的 browseId＋標題比對），並附單元測試。

**步驟 5 — 手動隱藏歌手**
- `ui/menu/YouTubeArtistMenu.kt` 加「隱藏此歌手」動作（該檔已有資料庫與協程存取）。
- `ContentSettings.kt` 加隱藏名單管理入口（清單檢視＋逐項移除）。

**步驟 6 — 設定 UI**
- `ContentSettings.kt` General 區塊加兩個開關（複製 668–727 行模式）。

### 風險與限制

- **AI 偵測誤判**：名稱含 "AI" 的正常歌手可能被誤殺（關鍵字要夠精確，優先靠徽章）。設定說明文字要寫明「偵測為近似，可用隱藏名單補充」。
- 過濾點多達十餘處，需逐點回歸測試避免漏網或重複過濾。

### 驗證

- 單元測試：過濾器（仿 `HomeSectionPolicyTest.kt`）、`SearchSummaryPage`/`HomePage` 過濾。
- 手動：首頁／搜尋／歌手頁／車機／TV 各點一次開關。

### 工作量：中

---

## 5. 功能四：歌詞逐字時間軸編輯器

### 目標

在手機上校正逐字歌詞（KTV 式）的文字與每個字的时间軸，編輯結果同步用於播放頁逐字顯示與三個相框KTV。

### 現況調查結論

- 逐字資料結構現成：`LyricsEntry.words: List<WordTimestamp>`（`lyrics/LyricsEntry.kt`，秒為單位的 start/end）。
- 逐字歌詞支援兩種序列化格式且解析器可往返：增強型 LRC（`[mm:ss.cc]<mm:ss.cc>字 ...`）與 LyricsPlus 字塊（`<字:起:終|...>`）（`lyrics/LyricsUtils.kt` 145–290、344–365 行）。
- 歌詞存在 Room `lyrics` 表的 `lyrics` 文字欄（`db/entities/LyricsEntity.kt`），**逐字時間軸包在字串裡 → 編輯器把結果序列化回字串即可，完全免資料庫遷移**。
- 手動歌詞保護機制現成：`markManualLyrics()`（DataStore）＋相框覆寫檢查（`FrameLyricsOverlay.replaceable()`）。
- KTV 渲染引擎可重用做即時預覽：`ui/component/LyricsLine.kt` 的 `WordLevelLyrics`（309–782 行）。
- 現有編輯入口：`ui/menu/LyricsMenu.kt` 的「編輯歌詞」（442–459 行，純文字）——新編輯器掛在同一選單。

### 實作方案

**步驟 1 — 入口**
- `LyricsMenu.kt` 加「編輯逐字時間軸」動作，開全螢幕對話框（`bottomSheetPageState.show` 模式，比照 `ShowOffsetDialog`）。
- 另註冊正式路由 `Screens.LyricsEditor`（`ui/screens/NavigationBuilder.kt` 模式）作為備選，若對話框承載不了完整編輯器再改走路由。

**步驟 2 — 編輯器畫面（單頁三區）**
1. **歌詞行列表**：各行顯示時間與文字，當前播放行高亮（`findActiveLineIndices`）；拖曳調整行起始時間（±步進＋長按連續）。
2. **逐字區**：選中一行後展開字的時間軸；「跟唱打點」模式——播放到哪點到哪個字即設定該字開始時間（前一個字自動結束），這是校正 KTV 歌詞最快的方式。
3. **即時預覽**：直接嵌入 `WordLevelLyrics` 顯示編輯中的行，隨播放位置即時著色。
- 播放控制：播放/暫停/3 秒回退（校正需要反覆聽同一段）。
- 時間基準含 `song.lyricsOffset`（與現有偏移設定一致）。

**步驟 3 — 由行級歌詞產生逐字骨架**
- 若現有歌詞只有行級時間，提供「產生逐字骨架」：行時間內每字平均分配（含標點），再手動微調。這讓任何歌都能快速變成可跟唱的 KTV 歌詞。

**步驟 4 — 儲存**
- 序列化回增強型 LRC 格式（`parseRichSyncWords` 已支援往返）。
- `markManualLyrics(mediaId)` ＋ `upsert(LyricsEntity)`（copy 既有列以保留翻譯欄位，`provider = "Manual"`）。
- 儲存後 `LyricsResyncHelper.triggerResync()` 讓所有顯示面（播放頁、相框）立即重整。

### 風險與限制

- 格式往返一致性（parse→edit→serialize→parse）需單元測試確保不失真（含 `{agent:v1}`、`{bg}` 標記保留）。
- 編輯器 UI（打點手勢＋預覽同步）是主要工程量所在。
- DataStore `ManualLyricsKey` 有 500 筆上限（滿了會重置清單），維持現狀即可但要在程式碼註解標明。

### 驗證

- 單元測試：序列化往返、骨架分配演算法。
- 手動：編輯後在播放頁、相框KTV、相框KTV2、TV 相框確認逐字同步；重啟 App 確認持久化。

### 工作量：中大（UI 為主，資料層零遷移）

---

## 6. 功能五：音樂錄影帶（MV）播放

### 目標

在手機、平板，以及**直接安裝在車機主機上執行時**，播放歌曲的官方音樂錄影帶（畫面＋聲音）。TV 目前沒有播放頁，13.8.6 維持純音訊；TV 影片頁另行評估。另一個維持純音訊的場景是 **Android Auto 投射連線**——那是平台限制：投射畫面（媒體模板）本身沒有影片顯示區，不是本專案的取捨（說明見風險與限制）。

關於「判斷車機」：App 不判斷、也無法判斷裝置是不是車機。App 直接裝在車機主機（Android 平板）上執行時，行為與平板完全相同、開放影片；只有裝置透過 Android Auto 投射協定把畫面交給車機時，才會進入純音訊模式（此時畫面由 Android Auto 繪製，App 提供不了影片）。副駕駛等乘客的觀看需求由「主機畫面直接開 App」滿足。

### 串流可行性實測（2026-11-17，動手驗證而非只讀程式碼）

以與 App 完全相同的請求方式（innertube player 端點、相同用戶端標頭與內容、相同的訪客憑證流程）實際呼叫 YouTube，並用分段下載驗證串流真實可用。**結論先講：原本規劃書以為「影片格式已被解析、改挑 muxed 格式即可」的想法是錯的——現有管線拿不到 MV 串流，必須換請求路線。**

**結論一：MV 串流無法從現有管線取得（這也是上游 v14 播不了 MV 的根本原因）**

- App 的串流一律向 YT Music API（music.youtube.com）請求，備援用戶端鏈為 VISIONOS → ANDROID_VR → WEB_REMIX → TVHTML5（見 `ContentAwareFallbackStrategy.defaultClients`）。
- 實測三支官方 MV（Gangnam Style、Despacito、Roar）在這些用戶端**全部被機器人驗證攔截**（「登入帳戶以確認你不是機器人」／「必須重新載入頁面」），`streamingData` 整個不回傳。
- 同一環境的**對照組**：普通歌曲（ATV）用 ANDROID_VR 正常取得串流——證明攔截是針對「音樂 API 上的影片內容」，不是 IP 被全面封鎖。
- 使用者在 MuMu 模擬器安裝上游 Metrolist v14 也實際確認影片無法播放，與此結論吻合。

**結論二：改走一般 YouTube API（www.youtube.com）可取得影片串流，且已驗證可真實下載**

| 路線 | 取得內容 | 畫質上限 | 需要 poToken | 下載驗證 |
|---|---|---|---|---|
| www.youtube.com + ANDROID 用戶端 | 影音合一單檔（itag 18，H.264+AAC） | 360p | 免 | HTTP 206、video/mp4、MP4 檔頭正確 ✓ |
| www.youtube.com + IOS 用戶端 | 純影片軌 144p～1080p（20 條全附純網址）＋純音訊軌（Opus 約 63～148kbps，5 條） | 1080p | 免 | 1080p MP4（111MB）與音訊 webm 皆下載成功 ✓ |

- 即使從機房 IP（信譽較差）測試兩條路線都通過；一般家用網路只會更寬鬆。
- 純影片軌與純音訊軌分開取，正好可用 Media3 的 `MergingMediaSource` 合併播放高畫質（函式庫已內建，**不需新增依賴**）。
- **名詞澄清**：表中的「IOS 用戶端」是 YouTube API 內部的用戶端代號（請求標頭 `X-YouTube-Client-Name: 5` 那組參數），只是向 YouTube 表明身分的標籤，**與蘋果 iOS 平台、iOS 開發完全無關**——在安卓裝置上就是一個 HTTP 請求參數，已實測可直接取得影片串流。
- 無 poToken 時 1080p 以上畫質取不到網址；App 目標裝置為手機／平板／TV，1080p 已足夠。

### 實作方案（第 0 步技術驗證 → 兩階段）

**第 0 步 — 裝置端技術驗證 spike（必做，先於一切 UI 工程）**

- 以隱藏的除錯開關（僅測試版）實作最小播放路徑：MV 項目改向 www.youtube.com 請求 → ANDROID 用戶端取 360p 單檔 → 交給現有 ExoPlayer 播放（無 UI 工程，先證明「播得出來」）。
- 在 MuMu 模擬器與至少一台實機驗證；模擬器若因編解碼器問題失敗，以實機結果為準並記錄差異。
- spike 同時驗證行為面：MV 進佇列後自動換下一首、影片與音訊歌曲混合播放、播完進入下一首。

**第一階段 — 360p 影音合一播放（ANDROID 單檔路線）**

1. **設定**：`PlayerSettings` 加「音樂錄影帶播放」開關（`MusicVideoPlaybackKey`，預設關）。開啟時佇列過濾放行帶影片的歌曲（`musicVideoType` 非 ATV，含官方 MV 與 UGC 影片）；App 內搜尋、首頁與 YouTube 瀏覽頁也連動放行（2026-11-20 修正：原行為會讓使用者開了 MV 播放卻找不到影片項目）；Android Auto 與語音入口仍依「隱藏帶影片的音樂」設定過濾。
2. **串流解析**：`YTPlayerUtils` 新增影片專用解析函式——影片類型歌曲改向 www.youtube.com 的 ANDROID 用戶端請求、挑 itag 18；與現有音訊管線並存、互不干擾。
3. **快取鍵隔離**：影片項目跳過下載快取、快取鍵加後綴（`mediaId#video`），避免把離線音訊快取誤當影片播放。
4. **播放頁影片區**：`ui/player/Thumbnail.kt` 的 `ThumbnailItem` 以 `AndroidView { TextureView }` 取代封面；`DisposableEffect` 掛接 player surface；該分支移除 `Offscreen` 合成策略（硬體層無法容納 TextureView）。選 TextureView 以相容現有 Compose 轉場動畫。
5. **TV 影片區（本版不做）**：`tv/TvScreen.kt` 沒有大圖播放頁，唯一常駐封面是底部播放列的 64dp 小圖。13.8.6 **不在播放列放影片**（小畫面沒有意義），TV 維持純音訊、播放列只顯示封面。TV 若要播影片，應另做獨立的全螢幕影片頁，並處理進出相框時的畫面切換，留待後續版本確認。
6. **僅音訊切換**：播放頁加切換鈕，關閉影片時釋放 surface 回復封面。
7. **音訊焦點**：影片項目焦點屬性改 `C.AUDIO_CONTENT_TYPE_MOVIE`；確認 `setOffloadEnabled` 對影片項目停用。

**第二階段 — 高畫質與全螢幕（另行確認後實施）**

8. **高畫質**：以 YouTube API 的「IOS 標識用戶端」（僅是請求標頭裡的用戶端代號，**與蘋果 iOS 裝置無關**，安卓裝置上就是一個 HTTP 參數，已實測直接可用）取 720p/1080p 純影片軌＋音訊軌，以 `MergingMediaSource` 合併播放（兩個 DataSource，工程較大）。這是 720p 以上畫質的唯一已驗證路徑，不需要任何 iOS 開發。
9. **全螢幕**：延伸現有 `isFullScreen` 切換 → 鎖橫屏＋隱藏系統列＋影片依寬高比填滿。
10. ~~子母畫面~~：已評估後移除（2026-11-17）。

### 風險與限制

- **YouTube 策略可能變動**：用戶端行為非官方保證。對策——本節的實測方法（同樣的請求與下載驗證）可直接重跑，每次上游同步後重驗一次；若 ANDROID/IOS 路線同時失效，功能降級回純音訊並暫停。
- 第一階段畫質 360p；720p 以上需第二階段的音視訊合併。
- 影片模式音訊上限約 148kbps（Opus）；一般純音訊播放不受影響（維持現有高音質路徑）。
- 模擬器編解碼器差異：以實機結果為準。
- 電池與流量：影片解碼耗電，設定說明寫明；行動網路下可考慮提示（不強制）。
- **Android Auto 投射維持純音訊（平台限制，非取捨）**：投射時畫面由 Android Auto 的媒體模板繪製，該模板沒有影片顯示區；要在投射畫面放影片必須改用 Google 的車用影片合作計畫（另套 SDK 與審核），不列入範圍。
- **直接安裝在車機主機＝平板，開放影片**：App 無法也不嘗試偵測「這是不是車機」，主機畫面上直接開 App 即與平板相同；副駕駛等乘客觀看情境由這條路滿足。行進間是否限制影片（駐車訊號鎖定）屬車機硬體／韌體的職責，App 不介入。
- **TV 影片頁尚未實作**：TV 只有列表與底部播放列，沒有播放頁；13.8.6 維持純音訊、播放列只顯示封面。若要做，應是獨立的全螢幕影片頁，而不是把影片塞進播放列。
- 離線下載行為不變：本功能不做影片檔下載；MV 影片播放需要網路，已下載的 MV 歌曲在離線時播放音訊。

### 驗證

- 第 0 步：MuMu 模擬器＋實機播放 360p（**不通過則本功能暫停，不繼續投入**）。
- 手動：手機（直／橫屏）與平板播放 OMV、僅音訊切換、切到背景、音訊焦點（來電）、藍牙輸出；TV 本版維持純音訊（播放列只顯示封面，尚無影片頁）。
- 回歸：Android Auto 投射連線時 MV 歌曲仍為音訊（投射平台無影片區）；已下載的 MV 歌曲離線播放正常（離線仍為音訊，影片播放需網路）；普通歌曲播放路徑無回歸。
- 單元測試：影片專用格式選擇與解析函式；快取鍵隔離由 `#video` 專用鍵在播放管線中保證，尚未抽出獨立純函式測試。
- 2026-11-19 手機模擬器結果（MuMu，Android 手機模式）：MV 顯示畫面、隱藏影片畫面後音訊不中斷、切回影片、關閉設定立即回純音訊、一般歌曲仍走原音訊管線、連續跳播多首 MV 無崩潰。另驗證「隱藏帶影片的音樂」與 MV 播放同時開啟時，App 佇列與佇列還原仍可播放 MV，搜尋清單仍隱藏 MV。離線退回音訊尚未實機驗收。
- 2026-11-20 修正與驗收（MuMu）：（1）「音樂錄影帶播放」開啟時，App 內搜尋、首頁與 YouTube 瀏覽頁連動放行影片歌曲，解決「開 MV 播放卻找不到影片項目」的矛盾；搜尋「影片」分類不再被隱藏設定自動清空。（2）影片畫面由裁切填滿改為等比縮放留黑邊（letterbox），影片實際渲染後才鋪黑底，退回音訊時仍顯示封面；已實機驗證 16:9 影片完整顯示。（3）將 App 內影片過濾判斷抽至 `DataStore.shouldHideVideoSongsInApp()`，修正搜尋／首頁相關程式碼縮排與重複狀態寫入；編譯、Debug 建置、安裝與前景 App smoke test 通過。
- 2026-11-19 Android TV 模擬器結果（Android Studio 的 AndroidTV AVD，具 leanback）：TV 介面正常開啟；搜尋並播放影片歌曲（NEON）時日誌只有一般 `FETCHING STREAM`、沒有 MV 串流請求，底部播放列只顯示封面；相框 KTV 可正常進入播放歌詞畫面、按返回回到列表後音樂不中斷，兩者不衝突、無當機。

### 工作量：大（第 0 步 spike 為門檻；第一階段為主體）

---

## 7. 實作順序與版本規劃

### 建議順序

```
第 1 批（車用優先，彼此獨立，風險低）
  1. 功能一：車機離線瀏覽已存歌單        → 13.8.1
  2. 功能二：車機釘選歌單＋佇列控制      → 13.8.2
  3. 功能三：封鎖 AI 歌手／隱藏播客      → 13.8.3

第 2 批（歌詞／KTV）
  4. 功能四：歌詞逐字時間軸編輯器        → 13.8.5

第 3 批（播放核心，技術風險較高）
  0. 功能五：MV 裝置端技術驗證 spike     → 除錯版驗證，不通過則暫停本功能
  5. 功能五：MV 播放（第一階段 360p）    → 13.8.6
     └ 第二階段（高畫質／全螢幕）        → 13.8.7（另行確認後實施）
```

- 每項功能完成後：遞增版本與 versionCode、更新 `changelog.md`（中英）、`README.md`＋`README.zh-TW.md`（功能級變更）。
- 功能五動播放核心：第 0 步裝置端 spike 通過後才進入 UI 工程；上游同步（播放器設定更新）之後需重跑 MV 串流驗證（方法見功能五章節，可直接重測）。
- 每批之間保留回歸觀察期，車用功能（第 1 批）建議實車驗證後再進下一批。

### 需要使用者確認的事項

| 事項 | 影響 | 出現位置 |
|---|---|---|
| Mixes 快取若改存 Room（而非 DataStore） | 資料庫 39→40 遷移 | 功能一（目前方案免遷移） |
| 釘選來源採 speed_dial（免遷移）或新欄位（需遷移） | 資料庫 39→40 遷移 | 功能二（目前方案免遷移） |
| UGC 影片是否納入 MV 播放 | 內容品質 | 功能五（目前限官方 OMV） |
| 第一階段 360p 畫質是否可接受 | 產品決策 | 功能五（720p+ 需第二階段合併播放） |
| MV 開關預設值 | 使用者體驗 | 功能五（規劃預設關） |

---

## 附錄 A：關鍵檔案索引

| 領域 | 檔案 |
|---|---|
| 車機瀏覽樹 | `app/src/main/kotlin/com/metrolist/music/playback/MediaLibrarySessionCallback.kt`、`AndroidAutoLibrary.kt`、`AndroidAutoDao.kt`、`CarMediaItem.kt`、`CarArtworkProvider.kt` |
| 車機設定 | `ui/screens/settings/AndroidAutoSettings.kt`、`constants/PreferenceKeys.kt`（`AndroidAuto*Key`） |
| 內容過濾 | `innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt`、`pages/SearchSummaryPage.kt`、`pages/HomePage.kt`、`viewmodels/HomeSectionPolicy.kt`、`ui/screens/settings/ContentSettings.kt` |
| 歌詞 | `lyrics/LyricsEntry.kt`、`LyricsUtils.kt`、`ManualLyrics.kt`、`ui/menu/LyricsMenu.kt`、`ui/component/LyricsLine.kt`、`photo/FrameLyricsOverlay.kt`、`db/entities/LyricsEntity.kt` |
| 串流 | `innertube/.../models/response/PlayerResponse.kt`、`utils/YTPlayerUtils.kt`、`playback/MusicService.kt`（DataSource 工廠）、`playback/PlayerConnection.kt`、`gradle/libs.versions.toml` |
| 播放 UI | `ui/player/Player.kt`、`ui/player/Thumbnail.kt`、`extensions/MediaItemExt.kt` |
| 佇列 | `playback/queues/Queue.kt`、`playback/ManualQueueTracker.kt`、`constants/MediaSessionConstants.kt` |

## 附錄 B：上游 v14 對應說明

- 本規劃的功能對應上游 v14.0.0 發行說明中的：Android Auto pinned playlists and song queue actions、Restored offline browsing of saved YouTube playlists in Android Auto（修復項）、optional blocking of AI-generated artists and local hiding of artists and podcasts、editor for lyrics and word timings、music-video playback。
- YouTube 直播（live audio and video）經評估後排除：需新增 HLS 模組與直播專用錯誤處理、工程量大且非車用／相框重點；日後有需求再重啟評估。
- MV 播放與上游做法不同：上游在 KMP 架構上實作且實測無法播放（見功能五章節的驗證結論一）；本規劃改走一般 YouTube API 路線並以實測與裝置端 spike 把關。
- 上游在 KMP 架構上同時移除了音樂鬧鐘與 LyricsPlus 歌詞來源——MusicCabin 兩者皆保留，這是不遷移架構的額外理由。
