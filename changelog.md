# MusicCabin 變更日誌 / Changelog

本檔案記錄 `MusicCabin` 專案自 `13.6.0` 起的客製功能、修正與建置變更。上游 Metrolist 的同步內容未在此重複列出。

This file records project-specific features, fixes, and build changes in `MusicCabin` from `13.6.0` onward. Upstream Metrolist synchronization changes are not repeated here.

## 13.7.3

### 中文

- 修正登入畫面頂部 header 過高：登入頁外層誤用了包含主畫面 AppBar 高度的 inset，導致 header 上方多出約 64dp 的黑色空白。現在頂部只保留系統狀態列高度，header 高度恢復正常。

### English

- Fix the oversized login screen header: the login page wrapper wrongly applied the inset containing the main app bar height, leaving about 64dp of extra black space above the header. The top now only reserves the system status bar height, restoring the normal header height.

## 13.7.2

### 中文

- 修正大量歌曲封面空白：YouTube 只對部分影片提供高解析縮圖，舊邏輯拿不到 `maxresdefault` 就直接空白。現在圖片載入失敗（404）時自動降級重試（maxres→sd→hq→mq→default），列表、播放器、通知與車機封面同步受惠。

### English

- Fix widespread blank song covers: YouTube only generates high-resolution thumbnails for some videos, and the old logic left a blank when `maxresdefault` was missing. Image loads that fail with 404 now automatically retry at lower resolutions (maxres → sd → hq → mq → default) across lists, player, notifications, and car surfaces.

## 13.7.1

### 中文

- 同步上游 13.7.0 的穩定性與效能修正：大量收藏不再一次載入全部藝人頁面快取，歷史紀錄改為資料庫端去重分頁，快取歌單改為資料庫驅動更新且不再每秒輪詢，音訊處理器重用緩衝區。
- 保留手動編輯的歌曲標題與歌手：專輯同步不再覆寫自訂內容，歌曲編輯支援多位歌手並保留播放佇列中的顯示。
- 自訂歌手名稱：重新命名歌手後，播放器、歌單、藝人頁與社群推薦同步顯示新名稱，並納入備份還原。
- 登入流程重寫：支援同一 Google 帳號下的 YouTube 頻道選擇與切換，自動重試網路恢復後的首頁資料，改善離線時帳號顯示。
- 下載與上傳強化：下載完成自動釋出播放器快取空間、上傳改為串流避免大檔記憶體不足、重複加入歌單改為批次檢查並在背景執行緒寫入。
- 播放錯誤畫面可一鍵複製完整診斷報告（含版本、裝置、串流客戶端與錯誤鏈），跨淡入淡出保留重複播放模式，通知列關閉後不再自動彈回，Listen Together 心跳干擾降低。
- 修復舊版資料庫升級（21→24 欄位補齊改為冪等），歌單追加改用實際最大位置避免位置衝突。

### English

- Sync upstream 13.7.0 stability and performance fixes: bulk screens no longer hydrate every cached artist page, History is deduplicated and paged in the database, the Cache playlist is database-driven instead of polling every second, and audio processors reuse buffers.
- Preserve manually edited song titles and artists: album sync no longer overwrites custom content, and the song editor supports multiple artists while refreshing the playing queue display.
- Custom artist names: renamed artists propagate to the player, lists, artist pages, and community recommendations, and are included in backup and restore.
- Reworked login: pick and switch between YouTube channels on the same Google account, auto-refresh home data after network recovery, and improved offline account display.
- Stronger downloads and uploads: finished downloads free player-cache space, uploads stream instead of buffering large files in memory, and duplicate playlist checks are batched with writes off the main thread.
- Playback errors offer one-tap copyable diagnostics (version, device, stream client, cause chain), repeat mode survives crossfade swaps, dismissed media controls stay dismissed, and Listen Together heartbeat interference is reduced.
- Repair legacy database upgrades (idempotent 21→24 column backfill) and append playlist songs after the actual max position to avoid position conflicts.

## 13.6.80

### 中文

- 修正播放器「⋯」選單遺漏的藝人訂閱操作，將原下載項目換成訂閱／已訂閱，顯示主要藝人名稱並同步更新圖示與訂閱狀態。Podcast 或缺少藝人識別碼的項目不顯示此操作；歌曲下載可從迷你播放器操作。

### English

- Fix the missing artist subscription action in the player's overflow menu by replacing download with Subscribe/Subscribed, showing the primary artist's name and updating the icon and subscription state. Hide this action for podcasts or items without an artist ID; song downloads remain available from the mini player.

## 13.6.79

### 中文

- 交換迷你播放器與歌曲選單的主要操作：迷你播放器現在提供歌曲下載，歌曲選單改提供加入喜愛的歌手；同步更新下載、離線與訂閱狀態圖示及無障礙描述。
- 播放與音訊設定中的「標準化音量」預設改為關閉，「自動下載喜歡的歌曲」預設改為開啟。

### English

- Swap the primary actions between the mini player and song menus: the mini player now offers song download, while song menus offer adding the artist to favorites. Update the download, offline, subscription-state icons, and accessibility descriptions accordingly.
- Change the Playback & Audio defaults so volume normalization is off by default and automatically downloading liked songs is on by default.

## 13.6.78

### 中文

- 帳號圖示開啟的帳號設定中，隱藏「點擊以顯示 Token」與「更多內容」項目；原有 Token 編輯、顯示與登入瀏覽設定邏輯保留在程式中。
- About MusicCabin 頁面移除非必要的「社區與資訊」區塊及「這個計畫支持巴勒斯坦」頁尾文字，保留維護者、合作者與贊助入口。

### English

- Hide the “tap to show token” and “More content” entries from the account dialog while keeping their existing token editor, reveal, and login-for-browse logic in the code.
- Remove the non-essential “Community & Info” section and “This project stands with Palestine” footer from About MusicCabin, keeping maintainer, collaborator, and sponsorship entries.

## 13.6.77

### 中文

- Android 套件識別碼由 com.jajafu.metrolist.androidcar 改為 com.jajafu.musiccabin；Debug 套件為 com.jajafu.musiccabin.debug。啟動器搜尋與音樂庫捷徑同步指向新套件，檔案分享、車機封面 Provider 與辨識 action 隨新套件產生。
- 這次會另行安裝為新的 App，無法覆蓋更新 13.6.76 或更早版本。本機設定、登入、下載與資料庫不會自動移轉；請重新登入、授予權限，並重新加入需要的小工具與捷徑，確認需保留的內容後再移除舊 App。
- 保留資料庫結構、固定 Release 簽章與 MusicCabin 更新來源；之後使用新套件識別碼與相同簽章的版本可直接覆蓋更新。同步修正 README 與 Release 的升級說明。

### English

- Change the Android package ID from com.jajafu.metrolist.androidcar to com.jajafu.musiccabin, with com.jajafu.musiccabin.debug for Debug. Point launcher search and library shortcuts to the new package; file-sharing and car-artwork provider authorities and the recognition action follow the new ID.
- This installs as a separate App and cannot update version 13.6.76 or earlier in place. Local settings, login, downloads, and database are not migrated automatically. Sign in again, grant permissions, and recreate needed widgets and shortcuts; check anything you want to keep before removing the old App.
- Keep the database schema, fixed release signing key, and MusicCabin update source. Future releases with the new package ID and the same key can update in place. Align both READMEs and release upgrade guidance.

## 13.6.76

### 中文

- 關於頁將 Mo Agamy 移到合作者，保留其 GitHub、贊助連結與頭像彩蛋；在 jajafu 維護者區加入 Buy me a coffee 按鈕，連結至 https://buymeacoffee.com/clifchi。
- 統一 App、啟動器、關於頁面、通知、桌面小工具、年度回顧、錯誤報告與匯出素材的 MusicCabin 品牌，並同步 README、商店圖示與專案連結。
- 更新器與播放器設定改用 jajafu/MusicCabin 的 GitHub 來源；新的 Release 與 APK 使用 MusicCabin 名稱，更新器仍可辨識舊版 APK。
- 保留 Android 套件識別碼 com.jajafu.metrolist.androidcar、資料庫結構與固定 Release 簽章設定；既有正式版可直接覆蓋更新，無須搬移資料。新匯出使用 MusicCabinExports 與 Pictures/MusicCabin，舊檔案保持原位置。
- 保留原始 Metrolist 作者署名及上游服務協定。品牌相關文字改用新的英文資源，避免既有翻譯覆蓋新名稱；其他翻譯不變。
- 修正啟動器搜尋與音樂庫捷徑的目標套件；依專案規則將播放器設定同步 workflow 改為僅手動執行。

### English

- Move Mo Agamy into the About collaborators list, retaining his GitHub, sponsorship link, and avatar Easter egg. Add a Buy me a coffee button below maintainer jajafu, linking to https://buymeacoffee.com/clifchi.
- Unify MusicCabin branding across the app, launcher, About screen, notifications, widgets, Wrapped, crash reports, and exports; align both READMEs, store icons, and project links.
- Point the updater and player configuration sources to jajafu/MusicCabin. Use MusicCabin release titles and APK names while retaining support for older APK filenames.
- Preserve com.jajafu.metrolist.androidcar, the database schema, and fixed release signing configuration for in-place upgrades without data migration. New exports use MusicCabinExports and Pictures/MusicCabin; existing files stay where they are.
- Retain original Metrolist credits and upstream service protocols. Brand-related text uses new English resources so inherited translations cannot restore the old name; other translations are unchanged.
- Correct the target package of launcher search and library shortcuts; make the player configuration sync workflow manual-only as required by the project rules.

## 13.6.75

### 中文

- 統一 App、啟動器、關於頁面、通知、桌面小工具、年度回顧、錯誤報告與匯出素材的 MusicCabin 品牌，並同步 README、商店圖示與專案連結。
- 更新器與播放器設定改用 jajafu/MusicCabin 的 GitHub 來源；新的 Release 與 APK 使用 MusicCabin 名稱，更新器仍可辨識舊版 APK。
- 保留 Android 套件識別碼 com.jajafu.metrolist.androidcar、資料庫結構與固定 Release 簽章設定；既有正式版可直接覆蓋更新，無須搬移資料。新匯出使用 MusicCabinExports 與 Pictures/MusicCabin，舊檔案保持原位置。
- 保留原始 Metrolist 作者署名及上游服務協定。品牌相關文字改用新的英文資源，避免既有翻譯覆蓋新名稱；其他翻譯不變。
- 修正啟動器搜尋與音樂庫捷徑的目標套件；依專案規則將播放器設定同步 workflow 改為僅手動執行。

### English

- Unify MusicCabin branding across the app, launcher, About screen, notifications, widgets, Wrapped, crash reports, and exports; align both READMEs, store icons, and project links.
- Point the updater and player configuration sources to jajafu/MusicCabin. Use MusicCabin release titles and APK names while retaining support for older APK filenames.
- Preserve com.jajafu.metrolist.androidcar, the database schema, and fixed release signing configuration for in-place upgrades without data migration. New exports use MusicCabinExports and Pictures/MusicCabin; existing files stay where they are.
- Retain original Metrolist credits and upstream service protocols. Brand-related text uses new English resources so inherited translations cannot restore the old name; other translations are unchanged.
- Correct the target package of launcher search and library shortcuts; make the player configuration sync workflow manual-only as required by the project rules.

## 13.6.73

### 中文

- 當車機已掛載 USB、但廠商 MediaStore 沒有建立照片索引時，空白 USB 頁面新增按需啟動的直接瀏覽備援，可逐層開啟目錄、勾選照片，或一次選取／取消整個目錄。
- 直接瀏覽不寫死 USB 名稱，只接受系統回報為已掛載的卸除式儲存空間；平時只讀取目前目錄，選取整個目錄時才遞迴掃描，並保存原始檔案路徑而不複製照片到 App。
- 加入路徑邊界與掛載狀態檢查，USB 拔除時會將照片標記為暫時無法讀取；重新連接並重新掃描後可繼續使用。

### English

- Add an on-demand direct-browser fallback to an empty mounted USB view when the vendor MediaStore has indexed no photos. Browse directories, select individual photos, or select/deselect a whole folder.
- Discover removable storage from mounted system volumes instead of hardcoding a USB name. Read only the open directory during browsing, recurse only for whole-folder selection, and persist original file paths without copying photos into the app.
- Validate mount state and canonical path boundaries. Photos become temporarily unavailable after USB removal and can be restored by reconnecting and rescanning.

## 13.6.72

### 中文

- 裝置照片瀏覽器新增按需開啟的儲存裝置診斷，可顯示 Android SDK、系統儲存名稱、MediaStore volume、掛載狀態／路徑／UUID 與索引照片數量，協助判斷車機 USB 名稱映射及媒體索引問題。
- 診斷只在使用者開啟時查詢系統資料庫，不遍歷 USB、不解碼縮圖，也不複製照片，因此不增加 App 啟動時的掃描或記憶體成本。

### English

- Add on-demand storage diagnostics to the device photo browser, showing Android SDK, system storage description, MediaStore volume, mount state/path/UUID, and indexed photo count to identify head-unit USB naming and media-index issues.
- Diagnostics query the system database only when opened. They do not traverse USB storage, decode thumbnails, copy photos, or add startup scanning and memory cost.

## 13.6.71

### 中文

- 重新整理數位相框控制層：第一列依序顯示時鐘、歌名與歌手，第二列集中音樂、照片、選擇、設定及退出按鈕；兩列在空間不足時都會自動換行。
- 將數位相框控制層文字與圖示放大為原本的兩倍，並保留適合車機操作的觸控範圍與按鈕間距。

### English

- Reorganize the Photo frame overlay into a clock/title/artist information row and a separate row for music, photo, selection, settings, and exit controls. Both rows wrap when space is limited.
- Double the Photo frame overlay text and icon sizes while retaining touch-friendly target sizes and spacing for head units.

## 13.6.70

### 中文

- 補齊 Browse device 照片瀏覽器的繁體中文介面，包含權限、儲存空間、資料夾選取、載入狀態與錯誤提示。
- 將輪播間隔改為 5／10／15／30／60 秒離散橫式拉桿；拖動時即時顯示秒數，放開後才儲存，且儲存時不再閃爍整個設定面板。

### English

- Complete the Traditional Chinese interface for the Browse device photo browser, including permissions, storage, folder selection, loading states, and errors.
- Replace the slideshow interval tap cycle with a discrete horizontal slider for 5/10/15/30/60 seconds. It previews the value while dragging, saves on release, and no longer flashes the settings panel while persisting.

## 13.6.69

### 中文

- 移除在部分車機上無法正常工作的系統檔案選擇器、SAF 資料夾選擇器及重新授權入口；數位相框統一透過 Browse device 選取 MediaStore 已索引的照片或整個目錄。
- 保留舊版本已保存照片與資料夾來源的讀取、重新掃描及移除相容性，不會複製原始照片。

### English

- Remove the system file picker, SAF folder picker, and reauthorization actions that fail on some head units. Photo frame now uses Browse device exclusively to select MediaStore-indexed photos or entire folders.
- Preserve reading, rescanning, and removal compatibility for photo and folder sources saved by earlier versions without copying original photos.

## 13.6.68

### 中文

- Browse device 新增 MediaStore 目錄篩選，可一次選取或取消目前目錄內的所有照片；目錄只讀取系統索引與照片 URI，不複製圖片或建立額外縮圖快取。

### English

- Add MediaStore folder filtering to Browse device, with one-tap selection or deselection of every photo in the current folder. Folder discovery reads only the system index and photo URIs without copying images or creating extra thumbnail caches.

## 13.6.67

### 中文

- 數位相框新增按需載入的 MediaStore 照片瀏覽器，可分開檢視系統已建立索引的內部儲存與 USB／SD 儲存卷，分頁載入縮圖並一次勾選多張照片；原始照片不會複製到 App。
- 照片權限只在開啟裝置瀏覽器時申請，未使用相框時不掃描儲存空間；縮圖不寫入共用快取，並保留原有系統檔案選擇器與資料夾選擇器作為相容備援。

### English

- Add an on-demand MediaStore browser to Photo frame. Browse indexed internal and USB/SD volumes separately, load thumbnails in pages, and select multiple photos without copying originals into the app.
- Request photo access only when the device browser is opened and never scan storage at app startup. Browser thumbnails bypass shared caches, while the existing system file and folder pickers remain available as compatibility fallbacks.

## 13.6.66

### 中文

- 修正車機選取照片後立即顯示需要重新授權的問題；在選擇器回傳暫時 URI 權限的 callback 期間立即保存存取權，再進行非同步掃描。

### English

- Fix head units immediately reporting that photo access must be renewed. Picker URI grants are now persisted during the activity-result callback before asynchronous scanning begins.

## 13.6.65

### 中文

- 修正數位相框直式螢幕的控制列排版；設定與選擇照片按鈕不再被推到畫面外，窄螢幕會自動換行顯示。

### English

- Fix Photo frame controls on portrait screens. Settings and photo-selection buttons are no longer pushed off-screen; controls wrap automatically on narrow displays.

## 13.6.64

### 中文

- 針對車機相容性，照片選擇優先使用標準文件選擇器，失敗時改用系統內容選擇器；無相容選擇器時顯示明確提示，不再誤報為照片讀取錯誤。

### English

- Improve head-unit compatibility by preferring the standard document picker for photos and falling back to the system content picker. Show a clear message when no compatible picker exists instead of reporting a misleading photo-read error.

## 13.6.63

### 中文

- 數位相框新增播放／暫停、上一張與下一張照片控制；手動換片後會繼續自動輪播，音樂上一首／下一首控制維持原功能。

### English

- Add Play/Pause and previous/next photo controls to Photo frame. Manual navigation continues the automatic slideshow, while the existing music previous/next controls remain unchanged.

## 13.6.62

### 中文

- 補齊數位相框的繁體中文介面，會跟隨系統語言顯示中文標題、按鈕、設定、錯誤提示與無障礙描述。

### English

- Complete the Traditional Chinese localization for Photo frame, including titles, buttons, settings, errors, and accessibility descriptions selected by the system language.

## 13.6.61

### 中文

- 延後 Listen Together 管理器與網路用戶端的建立，僅在開啟一起聽功能、整合設定或使用邀請連結時初始化，降低一般啟動時的記憶體與背景工作成本。
- 保留播放服務的房間狀態同步與既有一起聽功能；初始化流程改為一次性，避免重複建立偏好與事件觀察工作。

### English

- Defer creation of the Listen Together manager and network client until the feature, its integration settings, or an invitation link is opened, reducing startup memory and background work for normal playback.
- Preserve room-state handling in the playback service and existing Listen Together behavior while making initialization one-shot so preference and event observers cannot be duplicated.

## 13.6.60

### 中文

- 主選單以數位相框取代一起聽，直接開啟全螢幕照片頁；沒有照片時保持空白背景，音樂播放不中斷。
- 相框提供透明控制層，顯示時鐘與歌曲資訊，並可使用上一首、下一首、選擇照片、設定與退出。
- 支援多次加入單張照片、內部儲存或 USB 資料夾遞迴掃描、來源移除及清空選取；原始照片不會被複製或刪除。
- 提供隨機不重複輪播、交叉淡入淡出、5／10／15／30／60 秒間隔、填滿或完整顯示，以及時鐘與歌曲資訊開關。
- 相框採延遲初始化、限制圖片解碼尺寸並避免將照片留在共用快取；離開相框或進背景會停止載入與換片。失效來源可重新授權，USB 重接後可重新掃描。
- 一起聽仍保留於設定 → 整合及選用的頂部工具列捷徑；資料庫結構與既有音樂播放核心不變。請於停車時使用相框，本功能不提供 Android Auto 照片顯示。

### English

- Replace Listen Together in the main menu with Photo frame, opening a full-screen photo page with a blank background before selection while music keeps playing.
- Add transparent controls for the clock, song information, Previous/Next, photo selection, settings, and exit.
- Support repeated photo selections, recursive device/USB folder scans, source removal, and clearing selections without copying or deleting original photos.
- Add shuffled non-repeating playback, crossfades, 5/10/15/30/60-second intervals, fit/crop modes, and clock/song-information toggles.
- Initialize the frame on demand, bound image decoding size, and bypass the shared photo cache; leaving the frame or backgrounding the app stops image loading and slideshow timers. Reselect inaccessible sources or rescan after reconnecting a USB drive.
- Keep Listen Together under Settings → Integrations and its optional top-bar shortcut, without changing the database schema or existing music playback core. Use the frame while parked; photos are not displayed in Android Auto.

## 13.6.59

### 中文

- 移除歌詞羅馬化功能、相關設定與內建轉寫字典，縮減 APK 體積；一般原文歌詞與同步歌詞顯示維持不變。
- 清理羅馬化移除後的未使用資源與物件狀態，並修正 Discord 連線說明文字遺失問題。

### English

- Remove lyric romanization, its settings, and bundled transliteration dictionaries to reduce APK size; original and synced lyric display remain available.
- Clean up unused resources and object state left by the removal, and restore the missing Discord connection information text.

## 13.6.56

### 中文

- 在暫停狀態使用上一首或下一首後會自動開始播放，並統一套用於播放器按鈕、封面與迷你播放器滑動、桌面小工具、播放通知、鎖定畫面及車機媒體控制。

### English

- Automatically start playback after using Previous or Next while paused, consistently covering player buttons, artwork and mini-player swipes, home-screen widgets, playback notifications, the lock screen, and car media controls.

## 13.6.55

### 中文

- 更新通知、一起聆聽操作、音樂鬧鐘與歌曲辨識結果使用明確目的元件建立不可變 PendingIntent，防止通知或排程動作被其他應用程式攔截或重新導向，同時保留原有 action、資料、request code 與操作行為。

### English

- Use explicit destination components for immutable PendingIntents in update notifications, Listen Together actions, music alarms, and recognition results, preventing interception or redirection while preserving their existing actions, data, request codes, and behavior.

## 13.6.53

### 中文

- 首頁不再顯示 YouTube 推薦中的「再聽一次」與「翻唱與重混」區塊，並由後續的其他推薦補足最多 3 個區塊。

### English

- Hide YouTube's “Listen again” and “Covers & remixes” rows from Home, allowing later recommendations to fill the three-row limit.

## 13.6.52

### 中文

- 修正目前 YouTube 播放器版本 `ca042962` 尚未同步至本專案鏡像，導致所有歌曲顯示 `IO_UNSPECIFIED (2000)` 的問題；內建設定已與最新權威資料對齊。
- 播放器設定仍優先由本專案自行託管；偵測到目前播放器版本缺失時，會直接查詢 zemer-cipher 權威來源並保存有效結果，避免每日鏡像同步空窗再次中斷播放。
- 移除無法解決設定缺失、只會讓每首歌曲重試三次後跳到下一首的回復流程，恢復與上游一致的錯誤處理。
- Release 套件識別碼改為 `com.jajafu.metrolist.androidcar`，可與原始 Metrolist 同時安裝。這次轉換會被 Android 視為新 App，舊版的本機資料與登入不會自動移轉；後續版本仍可用固定簽章直接覆蓋更新。

### English

- Fix every song failing with `IO_UNSPECIFIED (2000)` because the current YouTube player `ca042962` had not reached this repository's mirror; the bundled table now matches the latest authoritative data.
- Continue preferring this project's self-hosted player configurations, but fetch and cache the authoritative zemer-cipher table when the current player remains missing so a mirror-sync window cannot interrupt playback again.
- Remove the ineffective recovery path that retried each song three times and advanced to the next track without repairing a missing configuration, restoring upstream-aligned error handling.
- Change the Release package ID to `com.jajafu.metrolist.androidcar` so it can coexist with the original Metrolist app. Android treats this one-time transition as a new App, so old local data and login state are not migrated automatically; later versions still update in place with the fixed signing key.

## 13.6.51

### 中文

- 修正 YouTube 在串流來源尚未辨識完成前回傳「Sign in to confirm you're not a bot」時，播放器會立即停止且顯示 `IO_UNSPECIFIED (2000)` 的問題。
- 未解析完成的串流錯誤現在會清除舊來源資訊並有限次數重新取得播放網址；既有的重試上限仍會防止持續失敗或無限迴圈。

### English

- Fix playback stopping immediately with `IO_UNSPECIFIED (2000)` when YouTube returns “Sign in to confirm you're not a bot” before a stream client has been identified.
- Unresolved stream failures now discard stale client information and retry URL resolution a bounded number of times, while the existing retry limit still prevents repeated failures or retry loops.

## 13.6.50

### 中文

- 藝術家延伸內容與 YouTube 瀏覽頁改用型別安全導航，移除容易混淆多個查詢參數的手動路由字串。
- 導航系統會安全保存含有斜線、問號、井號與 `&` 的 YouTube 參數，避免頁面開啟時參數遭截斷或遺失。

### English

- Use type-safe destinations for artist overflow content and YouTube browse pages, removing manually assembled routes that could confuse multiple query parameters.
- Preserve YouTube parameters containing slashes, question marks, hashes, and ampersands so navigation no longer truncates or loses them while opening a page.

## 13.6.49

### 中文

- 等化器、播放速度標題與 Parametric EQ 匯出使用明確地區格式，避免不同語系產生不一致或無法匯入的數字。
- 線上播放清單與 Podcast 標題改為只在捲動狀態跨越顯示門檻時重組；小工具預覽中的裝飾圖像明確排除無障礙朗讀。
- 移除 9 個可確認沒有程式或動態名稱引用的舊圖示、字型別名與啟動畫面資源。

### English

- Use explicit locale formatting for equalizer labels, playback-speed titles, and Parametric EQ exports so numbers remain appropriate for display and portable for file import.
- Recompose online playlist and podcast titles only when scrolling crosses the visibility threshold, and explicitly exclude decorative widget-preview images from accessibility announcements.
- Remove nine obsolete icons, a font alias, and launcher resources with no code or dynamic-name references.

## 13.6.48

### 中文

- 排行榜與探索頁改用明確的內容、載入與錯誤狀態；請求失敗時會停止載入動畫並顯示重試操作，成功載入的另一部分內容仍可保留顯示。
- 首頁載入與下拉重新整理加入單次執行及 `finally` 狀態復原，資料庫或網路處理發生例外時不再永久顯示旋轉指示。

### English

- Give Charts and Explore explicit content, loading, and error states so failed requests stop the loading animation, offer retry, and preserve any other content that loaded successfully.
- Make Home loading and pull-to-refresh single-flight operations restore their indicators in `finally`, preventing database or network exceptions from leaving a permanent spinner.

## 13.6.47

### 中文

- Android Auto 搜尋與語音播放改用 YouTube 搜尋摘要中的相關性歌曲排序，相同回應會穩定選擇相同的最佳歌曲。
- 線上結果會直接建立播放器項目，背景寫入 Room 不再立即查回，避免冷資料庫競態丟失歌曲；語音播放會建立可續頁的相關歌曲 Radio queue，沿用既有自動延伸與重試機制。

### English

- Use relevance-ranked songs from YouTube search summaries for Android Auto search and voice playback, making selection deterministic for the same response.
- Build playable items directly from online results instead of racing an asynchronous Room insert/read, and seed an extendable related-song radio queue that uses the existing continuation and retry system.

## 13.6.46

### 中文

- 連續使用「播放下一首」時改為依請求先後順序插入，不再讓較新的歌曲插到較舊請求前面。
- 開啟隨機播放時會將目前歌曲後的完整手動優先區塊排在自動歌曲前；切歌、重複播放、刪除、移動、換新或清空佇列後會同步縮減或重置追蹤狀態。

### English

- Keep repeated Play Next requests in first-in-first-out insertion order instead of placing newer requests before earlier ones.
- Keep the complete manual-priority block ahead of automatic songs during shuffle, reconciling or resetting its state after transitions, repeats, removals, moves, queue replacement, and clearing.

## 13.6.45

### 中文

- Coil 解碼或載入的封面會先複製成獨立、不可變的 ARGB_8888 軟體 Bitmap，再交給 Media3 使用。
- 來源圖片已回收或複製失敗時改用安全的小型替代圖，避免 Android 15 在通知、鎖定畫面或車機媒體控制縮放封面時崩潰。

### English

- Copy artwork decoded or loaded by Coil into an independently owned, immutable ARGB_8888 software bitmap before handing it to Media3.
- Return a small safe fallback when the source is recycled or copying fails, preventing Android 15 crashes while notifications, lock-screen metadata, or car controls scale artwork.

## 13.6.44

### 中文

- 遠端喜歡歌曲清單核對時，會保留尚未成功送出的最新本機按讚或取消按讚，避免離線操作被遠端舊狀態覆蓋。
- 沒有待處理本機操作時仍接受遠端取消按讚；裝置本機歌曲不會送往 YouTube，待處理按讚尚未清空時也不會把完整同步誤記為成功。

### English

- Preserve the latest pending local like or unlike while reconciling the remote liked-songs playlist so stale remote state cannot overwrite offline actions.
- Continue accepting remote unlikes when no local action is pending, never send device-local songs to YouTube, and do not mark a full sync successful while song-like updates remain pending.

## 13.6.43

### 中文

- 切換歌曲時會取消仍在等待的即時靜音跳轉、清除服務狀態並重置目前播放器的靜音偵測器。
- 延遲跳轉及每次連續跳轉前都會核對播放器、歌曲 ID 與佇列索引，避免上一首的靜音工作跳轉下一首。

### English

- Cancel pending instant-silence seek work, clear service state, and reset the active player's silence detector whenever the track changes.
- Verify the player, media ID, and queue index after the debounce and before every follow-up seek so stale work from the previous track cannot move the next one.

## 13.6.42

### 中文

- 遠端播放列表同步會先依 browse ID 去重，並在同一輪同步中記住新建立的本機項目，避免產生重複播放列表。
- 清理本機重複播放列表時改用輕量的播放列表資料；待處理編輯、正在修改與寬限期內項目不會被刪除，其他重複項目的已下載歌曲會先合併至保留項目。

### English

- Deduplicate remote playlists by browse ID and track newly inserted local records during the same sync pass to prevent duplicate playlist creation.
- Use lightweight playlist entities for local duplicate cleanup; preserve pending, actively modified, and grace-period records, and merge downloaded songs before removing other duplicates.

## 13.6.41

### 中文

- 歌曲批次讀取與播放列表重複歌曲檢查改為每 900 個 ID 分批查詢，避免大型播放列表超過 SQLite 綁定參數上限而崩潰。
- 空清單不再送入 Room 查詢；重複 ID 只回傳一次，跨批次結果會依輸入 ID 第一次出現的順序排列。

### English

- Split bulk song reads and playlist duplicate checks into queries of 900 IDs to prevent large playlists from exceeding SQLite bind-variable limits.
- Empty lists bypass Room queries, duplicate IDs produce one result, and combined results follow each input ID's first occurrence.

## 13.6.40

### 中文

- Podcast、UGC 與未知媒體型態不再略過 WEB_REMIX 串流驗證，驗證失敗時會繼續嘗試其他播放來源。
- WEB_REMIX 實際播放發生 `IO_UNSPECIFIED` 時，會在有限重試內排除該來源並重新解析；其他來源的相同錯誤不再無意義地重試同一網址。

### English

- Validate WEB_REMIX streams for podcast, UGC, and unknown media types, continuing through other playback sources when validation fails.
- When WEB_REMIX playback returns `IO_UNSPECIFIED`, exclude that source and resolve again within the existing retry limit; the same error from other clients no longer retries the same unsuitable URL.

## 13.6.39

### 中文

- 修正首頁快速存取歌曲在同步或刪除期間從資料庫消失時，畫面仍以非空值存取歌曲而造成的崩潰。
- 播放與歌曲選單會使用最新資料；資料列剛移除時則暫時使用畫面原有項目，直到首頁清單完成更新。

### English

- Fix a Home quick-access crash when a song disappears from the database during synchronization or deletion while the composed item still accesses it as non-null.
- Playback and song menus use the latest data, with the original displayed item as a temporary fallback until the Home list refreshes.

## 13.6.38

### 中文

- 音樂庫下拉重新整理現在會加入已排隊或執行中的完整同步，避免自動同步後立刻重複執行第二次完整同步。
- 重複下拉會共用同一個同步工作，旋轉指示會持續到實際工作完成；部分同步失敗時會顯示重試提示。

### English

- Make library pull-to-refresh join a queued or running full sync, preventing a second complete sync from running immediately after auto-sync.
- Repeated pulls now share one sync operation, keep the refresh indicator active until the actual work finishes, and show a retry message after partial failure.

## 13.6.37

### 中文

- 將車機首頁精簡為分類按鈕、12 個本機快速存取項目、帳號播放列表及最多 3 個 YouTube 官方推薦區塊。
- 停止載入重複且耗費資源的每日探索、社區歌單、自訂相似推薦、情境與類型、隨機首頁排序及無限分頁，並移除對應的內容設定選項。

### English

- Streamline the car home screen to category chips, 12 local quick-access items, account playlists, and at most three official YouTube recommendation sections.
- Stop loading duplicate and resource-intensive daily discovery, community playlist, custom similar recommendation, mood-and-genre, randomized home ordering, and infinite pagination sections, and remove the related content settings.

## 13.6.36

### 中文

- 將 App、播放器服務、一起聆聽、歌詞與 ViewModel 的 DataStore 設定讀取改為非同步讀取或記憶體快取，避免同步磁碟存取阻塞主執行緒。
- 播放佇列、電台、自動混音與 Podcast 播放位置的協程失敗現在會記錄具體操作來源與完整錯誤，不再靜默吞掉例外。

### English

- Move DataStore preference reads in the app, playback service, Listen Together, lyrics, and ViewModels to asynchronous reads or in-memory snapshots to prevent synchronous disk access from blocking the main thread.
- Record the specific operation and full error when queue, radio, automix, or podcast-position coroutines fail instead of silently swallowing exceptions.

## 13.6.35

### 中文

- Return YouTube Dislike 服務無法使用或回傳異常時，仍會保留 YouTube 已成功取得的歌曲媒體資訊，只有觀看、按讚與倒讚數暫時留空。

### English

- Preserve successfully retrieved YouTube media details when Return YouTube Dislike is unavailable or returns an invalid response; only view, like, and dislike counts remain unavailable.

## 13.6.34

### 中文

- 將 App 執行時的播放器設定與日期更新來源改為本專案的 GitHub 儲存庫，降低原始 Metrolist 遠端檔案遺失所造成的風險。

### English

- Point runtime player configuration and date updates to this project's GitHub repository, reducing reliance on the original Metrolist remote files.

## 13.6.33

### 中文

- 關閉串流網址驗證使用的 HTTP response，避免資源未釋放與連線累積。

### English

- Close HTTP responses used for stream URL validation to prevent leaked resources and accumulating connections.

## 13.6.32

### 中文

- 將備份檔案預覽與串流驗證移至背景執行緒，避免大型檔案操作阻塞介面。

### English

- Move backup file preview and streaming validation work off the main thread so large files do not block the UI.

## 13.6.31

### 中文

- 將喜歡歌曲的同步更新排入持久化、依序處理的佇列，避免快速操作時遺失或覆蓋更新。

### English

- Serialize durable liked-song synchronization updates so rapid actions do not lose or overwrite changes.

## 13.6.30

### 中文

- 服務重新啟動後恢復可延伸的 YouTube 播放佇列，讓自動載入更多歌曲可以繼續運作。

### English

- Restore extendable YouTube playback queues after service restarts so automatic loading can continue.

## 13.6.29

### 中文

- 修正隨機播放接近佇列尾端時的判斷，改為依照實際隨機播放順序載入後續歌曲。

### English

- Fix queue-end detection during shuffle playback by following the actual shuffled order when loading more songs.

## 13.6.28

### 中文

- 修正電台分頁結束後無法繼續產生推薦歌曲的問題。

### English

- Fix radio playback stopping when the current continuation page is exhausted.

## 13.6.27

### 中文

- 新增適合車機操作的播放清單格狀選擇器，放大項目與操作區域。
- CI 增加 lint 錯誤回歸檢查。
- 限制外部控制入口，避免未授權的控制路徑被使用。
- 修正下載網址快取的執行緒安全問題。
- 自動載入更多歌曲失敗時加入重試機制。
- 修正小型歌曲資料庫的播放處理、Android 版本相容性例外、備份替換復原，以及過期播放佇列請求。
- 修正音訊 ducking 後音量未恢復的問題。
- 修正同步完成狀態，使背景同步結果能被正確記錄。

### English

- Add a car-friendly grid playlist picker with larger items and touch targets.
- Add a lint-error regression gate to CI.
- Restrict external control entry points to prevent unauthorized control paths.
- Fix thread safety in the download URL cache.
- Retry failed automatic load-more requests.
- Fix playback for small song libraries, Android-version-specific service exceptions, recoverable backup replacement, and stale queue requests.
- Restore volume correctly after audio-focus ducking.
- Record background synchronization completion accurately.

## 13.6.26

### 中文

- CI 增加 lint 錯誤回歸檢查，防止新的編譯或靜態分析錯誤被忽略。

### English

- Add a lint-error regression gate to CI so new build or static-analysis errors are not missed.

## 13.6.25

### 中文

- 限制外部控制入口，改善應用程式的安全性。

### English

- Restrict external control entry points to improve application security.

## 13.6.24

### 中文

- 修正下載網址快取的執行緒安全問題，降低並行下載時的錯誤風險。

### English

- Fix thread safety in the download URL cache to reduce failures during concurrent downloads.

## 13.6.23

### 中文

- 自動載入更多歌曲失敗時加入重試機制，改善播放清單接近尾端時的連續播放。

### English

- Retry failed automatic load-more requests to improve continuous playback near the end of a playlist.

## 13.6.22

### 中文

- 修正小型歌曲資料庫的播放處理，避免歌曲數量較少時的錯誤。

### English

- Fix playback handling for small song libraries and avoid errors when only a few songs are available.

## 13.6.21

### 中文

- 增加 Android 特定版本服務例外的防護，改善背景播放穩定性。

### English

- Guard against Android-version-specific service exceptions to improve background playback stability.

## 13.6.20

### 中文

- 讓備份還原的檔案替換流程可復原，降低替換中斷造成資料無法使用的風險。

### English

- Make backup replacement during restore recoverable, reducing the risk of unusable data after an interrupted replacement.

## 13.6.19

### 中文

- 防止過期的播放佇列請求覆蓋較新的播放狀態。

### English

- Prevent stale queue requests from overwriting newer playback state.

## 13.6.18

### 中文

- 修正導航或其他音訊焦點事件暫時降低音量後，播放音量沒有恢復的問題。

### English

- Fix playback volume not being restored after navigation or other audio-focus ducking events.

## 13.6.17

### 中文

- 修正同步完成狀態的記錄，讓背景同步結果能被正確判定。

### English

- Fix synchronization completion tracking so background sync results are reported accurately.

## 13.6.16

### 中文

- 持久化播放清單歌曲移除操作，確保登出、重新登入或同步延遲後仍能套用刪除結果。

### English

- Persist playlist song removals so deletions are retained across logout, login, or delayed synchronization.

## 13.6.15

### 中文

- 持久化尚未送出的播放清單編輯，避免網路或背景同步中斷時遺失變更。

### English

- Persist pending playlist edits so changes are not lost when network or background synchronization is interrupted.

## 13.6.14

### 中文

- 改善 YouTube 播放清單同步可靠性，修正手機端與 YouTube 端更新延遲或不一致的情況。
- 更新專案文件與代理規範，要求 Release notes 依實際變更完整填寫。
- 移除已停用的 Download 收藏歌曲 JSON 備份說明與相關流程。
- 穩定自動電台佇列的延續播放。

### English

- Improve YouTube playlist synchronization reliability and fix delayed or inconsistent updates between the app and YouTube.
- Update project documentation and agent rules to require complete release notes based on actual changes.
- Remove the obsolete Download-folder liked-song JSON backup documentation and workflow.
- Stabilize automatic radio queue continuation.

## 13.6.13

### 中文

- 穩定自動電台佇列延續播放，改善播放清單播到尾端後沒有新歌曲的情況。

### English

- Stabilize automatic radio queue continuation when playback reaches the end of the current list.

## 13.6.12

### 中文

- 修正應用程式標籤與關於頁顯示不一致，確保品牌名稱顯示為 `Metrolist_AndroidCar`。

### English

- Fix inconsistent application labels and About-screen branding so the project name is shown as `Metrolist_AndroidCar`.

## 13.6.11

### 中文

- 將新的黑色專案標誌套用到應用程式、通知與待機播放控制等顯示位置。
- 修正收藏歌曲檔案重複建立的問題。

### English

- Apply the new black project logo across the app, notifications, and playback controls shown while idle.
- Prevent duplicate liked-song files from being created.

## 13.6.10

### 中文

- 修正收藏歌曲 JSON 檔案因同名檔案而不斷產生副本的問題。

### English

- Fix liked-song JSON exports repeatedly creating duplicate files when a file with the same name already exists.

## 13.6.9

### 中文

- 更新應用程式圖示與通知圖示，改用 AndroidCar 專案品牌圖案。

### English

- Update the application and notification icons with the AndroidCar project branding.

## 13.6.8

### 中文

- 在關於頁新增專案維護者 `jajafu` 與 GitHub 連結。
- 調整羅馬化、外觀，以及播放與音訊設定的預設值，使車機使用更簡潔。

### English

- Add project maintainer `jajafu` and a GitHub link to the About screen.
- Adjust default Romanization, appearance, and playback/audio settings for a simpler car-focused experience.

## 13.6.7

### 中文

- 調整預設設定：關閉羅馬化內容、頂部欄「一起聆聽」與不必要的自動播放清單選項；網格大小預設為大，並保留喜歡歌曲與已下載歌曲的自動播放清單。

### English

- Adjust defaults: disable Romanization content, top-bar Listen Together, and unnecessary automatic playlists; use a large grid by default while keeping liked and downloaded songs enabled.

## 13.6.6

### 中文

- 修正 YouTube 縮圖尺寸處理，恢復正常的圖片載入與顯示。

### English

- Fix YouTube thumbnail resizing and restore correct image loading and display.

## 13.6.5

### 中文

- 更新程式內更新檢查的版本比較方式，正確處理多位數版本號，避免錯誤判斷更新狀態。

### English

- Use semantic version comparison for update checks so multi-digit version numbers do not produce incorrect update states.

## 13.6.4

### 中文

- 新增使用固定 Android 簽章金鑰的 Foss Release 更新流程。
- 支援從 GitHub Release 取得並套用簽章一致的更新，改善重新安裝時的資料保留。

### English

- Add a signed Foss Release update flow using a persistent Android signing key.
- Support updates from GitHub Releases with consistent signing so reinstalling can preserve app data.

## 13.6.3

### 中文

- 將 Material 3 的棄用 `rememberModalBottomSheetState` 遷移至新版 bottom sheet API，保留隱藏與半展開行為。
- GitHub Actions 改為僅建置 Foss 版本，並維持手動執行流程。

### English

- Migrate the deprecated Material 3 `rememberModalBottomSheetState` calls to the new bottom-sheet API while preserving hidden and half-expanded behavior.
- Configure GitHub Actions to build only the Foss variant and remain manually triggered.

## 13.6.2

### 中文

- 更新 Room 破壞性遷移 fallback，明確使用 `dropAllTables` 參數以符合 Room 2.7 新版 API。

### English

- Update the Room destructive-migration fallback to use the explicit `dropAllTables` parameter required by the Room 2.7 API.

## 13.6.1

### 中文

- 修正 MusicService 與安全相關的 Kotlin 編譯警告，降低背景播放與外部控制的風險。

### English

- Resolve Kotlin compiler warnings in MusicService and security-related code, reducing risks around background playback and external control.

## 13.6.0

### 中文

- 建立 Metrolist AndroidCar 客製版本，加入車機導向的播放與橫向設定介面。
- 建立中英文雙語專案文件與客製化品牌資訊。
- 將 GitHub Actions 簡化為手動執行的 Foss APK 建置與 Release 流程。

### English

- Establish the customized Metrolist AndroidCar build with car-focused playback and landscape settings UI.
- Add bilingual project documentation and customized branding information.
- Simplify GitHub Actions to a manually triggered Foss APK build and release flow.
