# MusicCabin 變更日誌 / Changelog

本檔案記錄 `MusicCabin` 專案自 `13.6.0` 起的客製功能、修正與建置變更。上游 Metrolist 的同步內容未在此重複列出。

This file records project-specific features, fixes, and build changes in `MusicCabin` from `13.6.0` onward. Upstream Metrolist synchronization changes are not repeated here.

## 13.7.62

### 中文

- 隱藏設定中的「串流來源」入口；各來源維持預設全部啟用，播放解析行為不變。曾手動關閉來源的舊設定會保留（不動資料），只是無法再從 UI 調整。
- 版本 code 292；無資料庫 schema 變更，升級不需遷移資料。

### English

- Hide the Stream sources entry in Settings; all sources stay enabled by default with no change to stream-resolution behavior. Previously saved per-source toggles are preserved (no data change) but can no longer be adjusted from the UI.
- Version code 292; no database schema change or data migration.

## 13.7.61

### 中文

- 隱藏設定 → 帳號下的「整合」入口（一起聽、Discord、LastFM），並移除頂部列一起聽捷徑、播放器選單的一起聽項目及邀請連結自動加入房間；相關程式保留但不再被觸發，不會連接第三方伺服器。已登入 LastFM 或已啟用 Discord 的舊設定會維持原狀（不動資料），只是無法再從 UI 登出或停用。
- 版本 code 291；無資料庫 schema 變更，升級不需遷移資料。

### English

- Hide the Settings → Account → Integrations entry (Listen Together, Discord, LastFM), and remove the top-bar Listen Together shortcut, the player-menu Listen Together item, and invite-link auto-join. The underlying code is retained but unreachable, so the app no longer contacts third-party servers. Previously saved LastFM logins or Discord enablement are left untouched (only the UI to sign out or disable is gone).
- Version code 291; no database schema change or data migration.

## 13.7.60

### 中文

- 相框 KTV 頂部控制層與底部歌詞的外層留白減半，讓照片顯示面積更大。
- 版本 code 290；無資料庫 schema 變更，升級不需遷移資料。

### English

- Halved the outer top padding of the photo frame KTV control overlay and the outer bottom padding of the lyrics overlay to show more of the photo.
- Version code 290; no database schema change or data migration.

## 13.7.59

### 中文

- 重新開啟 LrcLib 歌詞來源，並將歌詞提供者預設優先順序改為 Paxsenix、LyricsPlus、KuGou、Better Lyrics、LrcLib；YouTube 字幕與 YouTube Music 維持在可調整來源之後作為回退來源。
- 修正 LyricsPlus 查詢歌曲時長的單位，避免把已是秒數的 YouTube Music 時長再次除以 1000。
- 手動切換歌詞來源時，所有提供者單次最多查詢 8 秒，整體最多 25 秒；LyricsPlus 不再因 KuGou 等其他來源回傳超過兩筆候選而被跳過。
- 版本 code 289；無資料庫 schema 變更，升級不需遷移資料。

### English

- Re-enabled LrcLib and changed the default lyrics-provider priority to Paxsenix, LyricsPlus, KuGou, Better Lyrics, and LrcLib; YouTube Subtitles and YouTube Music remain after the configurable providers as fallbacks.
- Fixed the LyricsPlus duration parameter so the already-second-based YouTube Music duration is not divided by 1000 again.
- Manual lyrics-source searches now allow up to 8 seconds per provider and 25 seconds overall; LyricsPlus is no longer skipped when KuGou or other providers return more than two candidates.
- Version code 289; no database schema change or data migration.

## 13.7.58

### 中文

- 統一相框、相框 KTV2、TV 相框與照片瀏覽器的主要操作：重新掃描、取消掃描、關閉及重試改用實體外框按鈕；清除操作使用錯誤色按鈕，確認清除使用醒目的錯誤色實體按鈕，提升觸控與遙控器操作的辨識度。
- 版本 code 288；無資料庫 schema 變更，升級不需遷移資料。

### English

- Unified the primary actions in the photo frame, Photo frame KTV 2, TV photo frame, and photo browser: rescan, cancel scan, close, and retry now use visible outlined buttons; clear actions use error-colored buttons, with destructive confirmation shown as a prominent error-colored filled button for clearer touch and remote-control interaction.
- Version code 288; no database schema change or data migration.

## 13.7.57

### 中文

- 簡化數位相框設定中的照片清單：每筆選取項目只保留檔案名稱與刪除按鈕，移除多餘的「照片／資料夾」標籤及照片數量／狀態摘要；頁面上方的照片總數仍保留。
- 清理同步移除的多語言字串；版本 code 287，無資料庫 schema 變更，升級不需遷移資料。

### English

- Simplified the photo list in Photo frame settings: each selected item now keeps only its file name and remove button, removing the redundant Photo/Folder label and photo-count/status summary while retaining the total count at the top.
- Cleaned up the removed localized strings; version code 287, with no database schema change or data migration.

## 13.7.56

### 中文

- 數位相框固定啟用歌詞聲部定位：`v1` 歌詞靠左、`v2` 歌詞靠右、`v1000` 與背景聲部置中；前一句、目前句及後一句都依各自聲部排列，且不受歌詞頁設定開關影響。
- 將原「數位相框」重新命名為「相框KTV」，GMS 的 Drive 相框 2 命名為「相框KTV2」，英文名稱同步改為 Photo frame KTV／Photo frame KTV 2。
- 相框設定移除 KTV 模式開關並固定使用 KTV 歌詞來源與渲染；「顯示歌詞」及「歌詞簡轉繁」的新預設值改為開啟，並移除這兩個選項下方的說明文字。既有使用者保存的顯示歌詞與簡轉繁選擇不被覆蓋。
- 版本 code 286；無資料庫 schema 變更，升級不需遷移資料。

### English

- Photo frame lyrics now always respect voice-agent positioning: `v1` lines align left, `v2` lines align right, and `v1000` plus background vocals stay centered. The previous, current, and next lines each use their own voice placement, independent of the lyrics-page setting toggle.
- Version code 286; no database schema change or data migration.

## 13.7.55

### 中文

- 修正 TV 更新日誌文字看不到的問題：更新日誌與下載進度文字未指定顏色，預設黑色在深色背景上無法閱讀；改為白色文字。
- 收緊數位相框操作列：圖示間距由 8dp 縮為 3dp，按鈕由 48dp 縮為 40dp（圖示本體維持 32dp），三種相框（手機／車機、TV、Drive）共用同一組基準；2X 縮放下按鈕仍有 80dp，觸控不受影響。
- 補上外觀設定「自動縮放上限」的中文：標題、說明與自動摘要在中文語系直接顯示本地中文字串，不需等待 Crowdin 翻譯；TV 相框設定同步支援中文。
- 版本 code 285；無資料庫 schema 變更，升級不需遷移資料。

### English

- Fixed unreadable TV changelog text: changelog and download progress text used the default black color on the dark background; now shown in white.
- Tightened the photo frame control row: icon spacing reduced from 8dp to 3dp and buttons from 48dp to 40dp (glyphs stay 32dp), shared by all three frame variants (phone/head-unit, TV, Drive); buttons are still 80dp at 2x uiScale so touch is unaffected.
- Added Chinese for the appearance Auto scale limit: title, description and auto summary now show local Chinese strings in Chinese locales without waiting for Crowdin; the TV frame settings show Chinese too.
- Version code 285; no database schema change or data migration.

## 13.7.54

### 中文

- 修正 TV 授權登入後只同步到喜愛歌曲、歌單沒出現的問題：電視收到手機登入時立即把會話寫入記憶體的 YouTube 客戶端，再開始完整同步，不再因 DataStore 觀察者尚未更新而用舊授權去抓收藏歌單；登入後喜愛歌曲與收藏歌單都會同步。
- 修正中文語系播放／暫停按鈕顯示英文 Pause：上游 `R.string.pause` 只有英文、所有語言都缺翻譯，手機播放器與 TV 底部列都會中招；新增本地播放／暫停中文字串，中文直接顯示「播放」／「暫停」，其他語言維持上游行為，不需等待 Crowdin 翻譯。
- 版本 code 284；無資料庫 schema 變更，升級不需遷移資料。

### English

- Fixed TV authorized login syncing liked songs but not playlists: the TV now applies the pushed session to the in-memory YouTube client before starting the full sync instead of racing the DataStore observers with stale auth; both liked songs and saved playlists sync after login.
- Fixed Play/Pause showing English Pause in Chinese locales: upstream `R.string.pause` is English-only across all locales, affecting both the phone player and the TV bottom bar. Local Play/Pause strings now show 播放/暫停 for Chinese while other locales keep upstream behavior without waiting for Crowdin.
- Version code 284; no database schema change or data migration.

## 13.7.53

### 中文

- 更新器下載支援斷點續傳：下載中斷（例如鎖屏後斷線、網速慢逾時）會保留已下載的部分，下次點下載自動從中斷處接續並重試，不需重頭開始；按鈕會顯示「繼續下載」並提示可接續。
- 補上更新頁缺失的繁中／簡中翻譯：下載更新、繼續下載、下載進度、下载完成提示、安裝更新、允許安裝未知應用說明與按鈕、下載失敗訊息；TV 端下載按鈕同步支援「繼續下載」。
- 版本 code 283；無資料庫 schema 變更，升級不需遷移資料。

### English

- Updater downloads now resume after interruption: an interrupted download (e.g. connection drop after screen lock or slow-network timeout) keeps the downloaded part, and the next tap resumes from where it stopped with retries instead of restarting. The button switches to Resume download with a hint that progress is kept.
- Fill in the missing Traditional/Simplified Chinese translations on the update screen: download, resume, progress, finished, install, allow-installs guidance and button, and the download-failure message; the TV download button also supports resume.
- Version code 283; no database schema change or data migration.

## 13.7.52

### 中文

- 修正 Release 版數位相框「歌詞簡轉繁」沒有轉換的問題：保留 opencc4j 反射建立的轉換器類別，避免 R8 壓縮造成轉換器初始化失敗；未使用的 Jieba 選用依賴也加入建置規則。Debug 與 Release 現在都能正常將簡體歌詞轉為台灣繁體中文。
- 版本 code 282；無資料庫 schema 變更，升級不需遷移資料。

### English

- Fixed Release builds where the photo frame Traditional Chinese lyrics option did not convert Simplified lyrics: opencc4j classes created through reflection are now kept from R8 optimization so converter initialization succeeds, and its unused optional Jieba reference is handled in the build rules. Simplified lyrics now convert to Taiwan Traditional Chinese in both Debug and Release builds.
- Version code 282; no database schema change or data migration.

## 13.7.51

### 中文

- 手機版更新器支援 App 內下載安裝（FOSS 版）：設定 → 更新器的新版本區新增下載按鈕與進度條，下載完成可直接安裝，未允許安裝未知應用時導向系統設定；設定頁、帳號選單與更新通知的入口都改為開啟 App 內更新器，不再跳轉瀏覽器。
- 版本 code 281；無資料庫 schema 變更，升級不需遷移資料。

### English

- Phone updater now downloads and installs in-app (FOSS builds): the updater screen gains a download button with a progress bar, installs directly after download, and guides to system settings when installs from this app are not allowed yet. The settings, account menu, and notification entries now open the in-app updater instead of a browser.
- Version code 281; no database schema change or data migration.

## 13.7.50

### 中文

- TV 更新頁遙控器焦點修正：檢查／下載按鈕在任務執行中保持可聚焦，不再把焦點丟到側欄；檢查出新版本會自動把焦點移到下載按鈕，下載完成自動移到安裝（或開啟設定）按鈕，下載中焦點停在原按鈕顯示進度等待。
- 版本 code 280；無資料庫 schema 變更，升級不需遷移資料。

### English

- Fixed TV update page remote focus: check/download buttons stay focusable while their operation runs instead of stranding focus onto the sidebar; focus now auto-moves to download when an update is found, to install (or open-settings) when the download finishes, and stays on the download button with progress while downloading.
- Version code 280; no database schema change or data migration.

## 13.7.49

### 中文

- TV 首頁、搜尋與歌單詳情頁的載入改為正確傳遞協程取消：快速連續搜尋或切換歌單時，被取代的舊請求不再回報假錯誤，也不再用過期的載入狀態覆蓋新請求；只有最新請求能更新畫面。
- 本機數位相框播放歷史改為最多保留 200 步（與 Drive 輪播一致）：長時間開啟相框不再無上限累積上一張／下一張紀錄，超過時丟棄最舊紀錄。
- 版本 code 279；無資料庫 schema 變更，升級不需遷移資料。

### English

- TV home, search, and playlist detail loading now propagate coroutine cancellation correctly: when searches or playlists are switched in quick succession, a superseded request no longer reports a stale failure or overwrites the replacement's loading state; only the latest request updates the UI.
- Local photo frame playback history is now capped at 200 steps (matching the Drive slideshow): leaving the frame open no longer accumulates previous/next records without bound, with the oldest records dropped past the cap.
- Version code 279; no database schema change or data migration.

## 13.7.48

### 中文

- 修正數位相框歌詞滑動時新舊動畫窗口共用最新當前句，導致焦點歌詞像固定不動的問題；改為各窗口使用自己的歌詞索引與內容，依顯示行數移動一行，並降低淡化速度、延長滑動時間。正常播放向上滾動，倒退時反向。
- 版本 code 278；無資料庫 schema 變更，升級不需遷移資料。

### English

- Fixed photo frame lyric scrolling where both animation windows used the latest current line, making the focused lyric appear stationary. Each window now renders its own indexed lyric content, moves by one visible line, fades less quickly, and scrolls longer. Playback scrolls upward; backward seeking reverses the direction.
- Renamed the original Photo frame to Photo frame KTV and the GMS Drive frame 2 to Photo frame KTV 2; Traditional Chinese labels are now 相框KTV and 相框KTV2.
- Removed the KTV mode switch from frame settings and always use the KTV lyric source and renderer. New defaults for Show lyrics and Convert lyrics to Traditional Chinese are enabled, and their supporting descriptions have been removed. Existing saved Show lyrics and conversion choices are preserved.
- Version code 278; no database schema change or data migration.

## 13.7.47

### 中文

- 數位相框底部歌詞換行改為輕量滑動過渡（手機、TV、相框 2 共用）：正常播放時新詞由下方滑入、舊詞向上滑出並保留原本淡入淡出，回播倒退時方向相反；仍維持前一句／當前句／後一句三行顯示與 KTV 逐字效果。
- 版本 code 277；無資料庫 schema 變更，升級不需遷移資料。

### English

- Photo frame bottom lyrics now use a lightweight slide transition (shared by phone, TV, and frame 2): new lines slide in from below and old lines slide out upward during normal playback, reversed when seeking backward, while keeping the existing fade and the previous/current/next three-line layout with the KTV word-by-word effect.
- Version code 277; no database schema change or data migration.

## 13.7.46

### 中文

- 手機版數位相框設定的底部「完成」改為相框 2 同款的顯眼填滿按鈕並固定在底部：設定本來就是即時儲存，按鈕只負責關閉，改後不用滑到底也看得到。TV 版維持右上關閉鈕，不另加底部按鈕。
- 版本 code 276；無資料庫 schema 變更，升級不需遷移資料。

### English

- The phone photo frame settings Done button now uses the same prominent filled style as Frame 2 and stays pinned at the bottom: settings already save instantly and the button only closes the sheet, so it is visible without scrolling to the end. The TV version keeps its top-right close button with no bottom button added.
- Version code 276; no database schema change or data migration.

## 13.7.45

### 中文

- 數位相框「顯示」設定新增「歌詞簡轉繁」（手機、TV、相框 2 共用）：開啟後以離線 opencc4j 字典將簡體中文相框歌詞轉為台灣繁體中文，不需網路與 API key；歌詞載入完成（含重新選擇歌詞）後，有至少 2 個簡體特徵字且多於繁體特徵字才轉換，其他語言保持不變。只替換顯示文字並保留時間軸與逐字時間，KTV 逐字掃光不受影響，原始歌詞仍存於資料庫，關閉即還原。
- 版本 code 275；無資料庫 schema 變更，升級不需遷移資料。

### English

- Photo frame Display settings gain a Traditional Chinese lyrics option (shared by phone, TV, and frame 2): when enabled, Simplified Chinese frame lyrics are converted offline to Taiwan Traditional Chinese via the opencc4j dictionary with no network or API key needed. After lyrics finish loading (including reselected lyrics), conversion only runs when at least 2 Simplified-specific characters are found and they outnumber Traditional-specific ones; other languages are untouched. Only display text is replaced while timestamps and word timings are preserved, so the KTV sweep keeps working; the original lyrics stay in the database and turning the option off restores them.
- Version code 275; no database schema change or data migration.

## 13.7.44

### 中文

- TV 登入 QR 改為中英雙語說明頁並附「用 App 繼續」按鈕：掃碼後點按鈕即跳回手機 App 並自動填入電視網址，只需再輸入 6 位數配對碼；手機未登入時授權頁直接提供前往登入按鈕。
- 版本 code 274；無資料庫 schema 變更，升級不需遷移資料。

### English

- The TV login QR now opens a bilingual page with a continue-in-app button: tapping it jumps back to the phone app with the TV address prefilled so only the 6-digit code needs typing. The authorize screen offers a go-to-login button when the phone is not logged in.
- Version code 274; no database schema change or data migration.

## 13.7.43

### 中文

- 修正歌詞選單無網路提示的 Compose lint 錯誤（改用 `stringResource` 讀取字串），Foss Release CI 的 `lintFossRelease` 可重新通過；無功能與顯示變更。
- 版本 code 273；無資料庫 schema 變更，升級不需遷移資料。

### English

- Fix the Compose lint error for the lyrics menu offline toast (read the string via `stringResource`), so Foss Release CI `lintFossRelease` passes again; no functional or UI change.
- Version code 273; no database schema change or data migration.

## 13.7.42

### 中文

- Paxsenix 歌詞來源改回預設開啟；LyricsPlus 的程式預設與設定頁不一致（設定顯示開、實際抓取為關）一併修正為預設開啟。LrcLib 維持預設關閉；曾手動關閉者不受影響。
- 數位相框「顯示」設定新增 KTV 模式（手機、TV、相框 2 共用）：開啟後相框歌詞只向 LyricsPlus 與 Paxsenix 抓取逐字歌詞（忽略啟用開關），庫存的行級歌詞會強制重抓，都抓不到才退回一般順序的行級歌詞；手動挑選（含手機歌詞選單與文字編輯）的歌詞會做標記，自動重抓不再覆寫。
- 歌詞一律走 KTV 引擎：無逐字時間的來源改為整句點亮（不再用假逐字動畫），相框當句同步改用同一引擎，未唱部分以 45% 顯示以突顯掃描效果，前後句維持 40% 透明度的三句顯示。
- 新增的 KTV 開關與 TV 授權相關字串同步附上繁中對照，中文語系直接顯示中文，不需等待 Crowdin 翻譯。
- 版本 code 272；無資料庫 schema 變更，升級不需遷移資料。

### English

- Paxsenix lyrics is enabled by default again; LyricsPlus had mismatched defaults (settings showed on while fetching treated it as off) and is now default-on in both. LrcLib stays default-off; users who explicitly disabled a provider are unaffected.
- Photo frame Display settings gain a KTV mode (shared by phone, TV, and frame 2): frame lyrics are fetched only from LyricsPlus and Paxsenix for word-synced lyrics (enable switches ignored); cached line-level lyrics are refetched, falling back to the regular order line-level lyrics when neither has word timing. Manually picked lyrics (frame picker, phone menu, and text edits) are marked so auto-refetch never overwrites them.
- Lyrics always render with the KTV engine: sources without word timings light the whole line at once instead of guessed per-word animation, and the frame's current line uses the same engine with the unsung part dimmed to 45% so the sweep stays visible, while the surrounding lines keep the 40% dimmed three-line layout.
- New KTV and TV authorization strings ship with Traditional Chinese alongside English so Chinese locales show translated text without waiting for Crowdin.
- Version code 272; no database schema change or data migration.

## 13.7.41

### 中文

- Google TV 新增側欄「帳號」頁：未登入時顯示 QR code、電視網址與 6 位數配對碼，手機在同一 Wi-Fi 下開啟帳號選單 →「授權 TV 登入」，輸入網址與配對碼即可把手機登入推送到電視，不需在電視上輸入 email 與密碼；登入成功後自動同步 YouTube 收藏與歌單，已登入時可手動立即同步或登出。FOSS 與 GMS 皆可使用，不需 Google Play 服務。
- 區網授權接收服務只在 TV 帳號頁開啟時運作，配對碼單次有效、錯誤太多次即鎖定，登入資料只寫入本機 DataStore，不上傳雲端中轉。
- 版本 code 271；無資料庫 schema 變更，升級不需遷移資料。

### English

- Google TV gains an Account page in the sidebar: while logged out it shows a QR code, the TV address, and a 6-digit pairing code. On the same Wi-Fi, open the phone account menu → Authorize TV login, enter the address and code, and the phone pushes its login to the TV — no email/password typing on the TV. The library syncs from YouTube automatically after authorization; manual sync and logout are available once logged in. Works on both FOSS and GMS with no Google Play services required.
- The LAN authorization receiver only runs while the TV Account page is open; each pairing code is single-use and the receiver locks after too many wrong attempts. Credentials are only written to the local DataStore with no cloud relay.
- Version code 271; no database schema change or data migration.

## 13.7.40

### 中文

- 歌詞選單的搜尋改為「更換歌詞來源」：點一下就用目前歌曲直接查全部已啟用的來源並列出候選，選取即覆寫；原輸入歌名／歌手保留為結果清單內的實心「調整搜尋關鍵字」按鈕，髒標題時可手動修正再查。
- 數位相框資訊列的時鐘、歌名與歌手改為單行垂直置中（歌名真的放不下被省略時才隱藏歌手，優先顯示完整歌名），並去除字體內距與固定行高，混合字級視覺重心齊平。
- 手機數位相框、TV 數位相框與數位相框 2 的操作列新增歌詞圖示，可直接開啟更換歌詞來源（TV 可用遙控器操作）；選取的歌詞會存檔，之後播放同一首歌維持使用。
- 版本 code 270；無資料庫 schema 變更，升級不需遷移資料。

### English

- The lyrics menu search is now "Switch lyrics source": one tap searches all enabled providers with the current track and lists the candidates; picking one overwrites the saved lyrics. The title/artist inputs remain as "Adjust search keywords" inside the results for dirty YouTube titles.
- Vertically center the single-line photo frame info row (clock, song title, artist; the artist is hidden only when the title itself is ellipsized so the full title shows first) and trim font padding and fixed line heights so mixed sizes share one visual center.
- Add a lyrics icon to the control rows of the phone photo frame, the TV photo frame, and photo frame 2 for quick lyrics source switching (remote-friendly on TV with a visible focus ring); the picked lyrics are saved so later playback of the same track keeps using them.
- Version code 270; no database schema change or data migration.

## 13.7.39

### 中文

- 數位相框的歌詞改為像歌詞頁一樣同時顯示前一句、目前句及後一句，前後句以 40% 透明度顯示，行距收緊更緊湊，避免遮住照片。
- 版本 code 269；無資料庫 schema 變更，升級不需遷移資料。

### English

- Show the previous, current, and next lyric lines together in the photo frame like the lyrics page, with the surrounding lines at 40% opacity and tighter line spacing to keep the photo visible.
- Version code 269; no database schema change or data migration.

## 13.7.38

### 中文

- TV 首頁現在會顯示歌單區塊（例如 Featured playlists for you），不再只收歌曲；點歌單可開啟詳情頁，查看歌曲、全部播放或收藏。
- TV 新增側欄「收藏」頁：上半為喜愛歌曲（可取消愛心、全部播放），下半為收藏的歌單（可取消收藏、點播）。
- TV 歌曲列新增愛心與加入歌單按鈕；加入歌單可在既有可編輯歌單中選擇，也可直接新增本地歌單並加入。
- TV 歌單詳情頁提供全部播放與收藏；可編輯的本地歌單可移除歌曲與刪除歌單（同步時一併處理 YouTube 端）。
- TV 數位相框播放時保持螢幕開啟，長時間輪播不再被 Google TV 螢幕保護程式打斷；離開相框後恢復原本的旗標狀態。
- TV 數位相框設定「顯示」頁新增自動縮放上限（1x–3x，預設 2x），與手機版設定 → 外觀共用同一設定，遙控器可直接切換，即時生效。
- 版本 code 268；無資料庫 schema 變更，升級不需遷移資料。

### English

- The TV Home now shows playlist sections (e.g. Featured playlists for you) instead of songs only; opening a playlist shows its songs with Play all and Save.
- The TV sidebar gains a Library page: liked songs on top (unlike, play all) and saved playlists below (unsave, open and play).
- TV song rows gain like and add-to-playlist buttons; the picker lists editable playlists and can create a new local playlist inline.
- The TV playlist detail page offers Play all and Save; editable local playlists support removing songs and deleting the playlist (including the YouTube side when synced).
- Keep the screen on while the TV photo frame is playing so long slideshows are no longer interrupted by the Google TV screensaver; the previous flag state is restored on exit.
- Add the auto scale limit (1x–3x, default 2x) to the TV photo frame Display settings, sharing the same setting as Appearance on phones and adjustable with the remote; changes apply immediately.
- Version code 268; no database schema change or data migration.

## 13.7.34

### 中文

- 歌詞提供者選擇中，第一項 LrcLib 與第四項 Paxsenix 預設改為關閉；已儲存的開關不受影響，新安裝或未設定過的使用者才會看到關閉狀態。
- 版本 code 264；無資料庫 schema 變更，升級不需遷移資料。

### English

- Change the defaults in lyrics provider selection so the first item LrcLib and the fourth item Paxsenix start disabled; saved toggles are unaffected and only fresh installs or never-configured users see the off state.
- Version code 264; no database schema change or data migration.

## 13.7.33

### 中文

- TV 左側選單與相框設定標題的「數位相框 1」改為「數位相框」，不再標示編號。功能與路由不變。
- 移除 TV 相框操作列中重複的選擇圖片圖示，選圖功能保留在齒輪設定的「本地照片」頁。
- 版本 code 263；無資料庫 schema 變更，升級不需遷移資料。

### English

- Drop the number from the TV sidebar entry and photo frame settings title, which now read "Photo frame". The feature and route are unchanged.
- Remove the duplicate photo picker icon from the TV frame control row; photo selection remains on the Local photos page in settings.
- Version code 263; no database schema change or data migration.

## 13.7.32

### 中文

- 修正車機短邊約 720dp 時自動縮放停在 2x，導致選擇 2.5x／3x 沒有尺寸變化；改為從 360dp 的 1x 漸進縮放，至 720dp 達到所選上限。上限恢復為 3x，預設 2x；舊設定若超過新上限，會回到 2x。
- 明確分組相框資訊列與操作列，將兩列間距縮至原本約三分之一，並把操作圖示上移以消除觸控按鈕內造成的視覺空隙；48dp 觸控區不變。
- 版本 code 262；無資料庫 schema 變更，升級不需遷移資料。

### English

- Fix auto scaling stopping at 2x on a roughly 720dp-short-edge head unit, where selecting 2.5x or 3x made no size difference. Scale now interpolates from 1x at 360dp to the selected limit at 720dp; the limit is restored to 3x and defaults to 2x. Saved values above the restored limit fall back to 2x.
- Explicitly group the photo-frame information and control rows, reduce their gap to about one third, and lift the control icons to remove the apparent whitespace inside the touch buttons. The 48dp touch targets are unchanged.
- Version code 262; no database schema change or data migration.

## 13.7.31

### 中文

- 修正 TV 版啟動即崩潰：更新流程把含格式佔位符的字串（`failed_to_check_updates`、`tv_update_downloading`、`tv_update_failed`）當成純文字讀取，Android 會因此拋出 `MissingFormatArgumentException`。改為顯示訊息時才帶參數格式化。
- 新增格式化字串的回歸測試，避免同類問題再次發生。
- 版本 code 261；無資料庫 schema 變更，升級不需遷移資料。

### English

- Fix a crash on every TV launch: the update flow read the format strings `failed_to_check_updates`, `tv_update_downloading` and `tv_update_failed` without their arguments, which throws MissingFormatArgumentException on Android. Those messages are now formatted with their arguments when they are shown.
- Add a regression test that formats those strings with arguments.
- Version code 261; no database schema change or data migration.

## 13.7.30

### 中文

- 自動縮放上限由 1x-3x 改為 1x-5x（預設 5x），大螢幕可進一步放大迷你播放器與相框文字。
- 縮減數位相框資訊列與控制列之間的行距，讓兩行讀起來是一個整體控制層。
- 版本 code 260；無資料庫 schema 變更，升級不需遷移資料。

### English

- Raise the auto scale limit from 1x-3x to 1x-5x (default 5x) so MiniPlayer and photo frame text can grow further on large displays.
- Reduce the gap between the photo frame information row and the control row so both read as one overlay.
- Version code 260; no database schema change or data migration.

## 13.7.29

### 中文

- TV 新增完整 App 內更新功能（僅 FOSS 建置）：啟動時自動檢查 GitHub Releases，有新版本時首頁顯示橫幅；側欄「更新」頁可用遙控器檢查、手動下載 APK（附進度條）、開啟系統安裝器安裝，並在缺少安裝未知應用權限時引導至系統設定開啟，介面含繁體中文。手機版維持原有瀏覽器下載流程。
- 版本 code 259；無資料庫 schema 變更，升級不需遷移資料。

### English

- Add full in-app updating on TV (FOSS builds only): automatic GitHub Releases check on launch with a HOME banner when an update is available; the sidebar Update page checks, downloads the APK with progress, and opens the system installer from the remote, guiding the user to the install-unknown-apps setting when the permission is missing; the interface includes Traditional Chinese. The phone flow still downloads via browser.
- Version code 259; no database schema change or data migration.

## 13.7.28

### 中文

- 修正 1 倍基準定義：相框時鐘／歌名／歌手／歌詞改以 Material 標準字級為 1 倍（手機時鐘 32sp、歌名 22sp、歌手 16sp、歌詞 22sp），先前為標準字級的 2 倍，手機上偏大；車機 1280×720 仍依短邊得 2 倍。
- 移除播放島橫向額外的 2 倍方位基準，橫向與直向同尺寸，1 倍即為上游的 64dp 版面。
- 版本 code 258；無資料庫 schema 變更，升級不需遷移資料。

### English

- Fix the 1x baseline definition: photo frame clock, song title, artist and lyric text now use the stock Material typography as 1x (phone clock 32sp, title 22sp, artist 16sp, lyric 22sp) instead of twice that size, which was too large on phones; a 1280x720 head unit still reports 2x from its short edge.
- Remove the MiniPlayer's separate 2x landscape baseline so landscape and portrait share one size and 1x matches the upstream 64dp layout.
- Version code 258; no database schema change or data migration.

## 13.7.27

### 中文

- 外觀設定新增自動縮放上限（1x–3x 可調，預設 3x），迷你播放器與數位相框文字依螢幕短邊在手機基準與上限之間縮放，即時生效免重啟。
- 版本 code 257；無資料庫 schema 變更，升級不需遷移資料。

### English

- Add an adjustable auto scale limit (1x–3x, default 3x) under Appearance; MiniPlayer and photo frame text scale with the screen short edge between the phone baseline and that cap. Changes apply immediately without restart.
- Version code 257; no database schema change or data migration.

## 13.7.26

### 中文

- 迷你播放器與數位相框（時鐘／歌名／歌手／歌詞）以 Material 標準字級為 1 倍基準，依螢幕短邊最多放大至 3 倍；迷你播放器橫向與直向同尺寸，不再另加 2 倍方位基準。大螢幕長文字可縮回手機基準大小，仍放不下才以省略號結尾，不會縮小於手機基準。
- 修正相框文字放大後行高未跟著調整，導致字被上下裁切或歌詞兩行重疊。
- 版本 code 256；無資料庫 schema 變更，升級不需遷移資料。

### English

- Scale MiniPlayer and photo frame text (clock/song/artist/lyrics) from the stock Material typography size as a 1x baseline to at most 3x by the screen's short edge. The MiniPlayer no longer adds a separate 2x landscape baseline, so landscape and portrait share one size. On large screens, overlong text may shrink back to the phone baseline before ellipsizing, but never below it.
- Fix scaled photo frame text keeping its original line height, which clipped single lines and overlapped the two-line lyric.
- Version code 256; no database schema change or data migration.

## 13.7.25

### 中文

- 數位相框新增方向適配拼圖：橫式螢幕遇到直式照片時左右並排兩張，直式螢幕遇到橫式照片時上下堆疊兩張；兩張共用同一輪播間隔一起切換。湊不到同方向的第二張時退回單張顯示，並沿用完整顯示／裁切填滿設定。適用相框 1（手機／車機／TV）與相框 2 本機來源；Drive 雲端輪播維持單張。
- 版本 code 255；無資料庫 schema 變更，升級不需遷移資料。

### English

- Add orientation-aware pairing to Photo frame: landscape screens show two portrait photos side by side, and portrait screens stack two landscape photos, with each pair sharing one slideshow interval. When no same-orientation partner is available, it falls back to a single photo following the fit/crop setting. Applies to Photo frame 1 (phone/head-unit/TV) and Photo frame 2 local sources; Drive cloud playback stays single-photo.
- Version code 255; no database schema change or data migration.

## 13.7.24

### 中文

- TV 數位相框 1 設定的本地照片、手機傳送與顯示選項改為只切換設定內容；按播放按鈕才切換輪播來源並關閉設定。移除與側邊選項或操作按鈕重複的標題。
- 手機／車機相框 1 將本機與 USB 照片清單和手機傳入副本分開顯示，並明確區分「清空相框全部照片」與「清空手機傳入照片」。
- 版本 code 254；無資料庫 schema 變更，升級不需遷移資料。

### English

- Make TV Photo frame 1 settings navigation show each control page without changing the slideshow source. The Play buttons now select their respective source and close settings. Remove headings repeated by navigation items or action buttons.
- Separate local/USB selections from received-photo counts in phone and head-unit Photo frame 1 settings, with distinct Clear all frame photos and Clear photos sent from phone actions.
- Version code 254; no database schema change or data migration.

## 13.7.23

### 中文

- 數位相框 1（手機／車機）、TV 版相框 1 與數位相框 2 新增歌詞圖層。開啟相框設定中的「顯示歌詞」後，畫面最下方會跟隨目前播放位置的歌詞行，最多一至兩行，超過以省略號截斷，切歌時淡入淡出。歌詞無時間軸時不顯示，以免出現不會變動的文字。
- 歌詞圖層僅在相框可見時運作，並沿用歌曲的歌詞時間偏移；歌詞尚未下載時會自動向已啟用的來源取得一次並快取。
- 版本 code 253；無資料庫 schema 變更，升級不需遷移資料。歌詞預設關閉，既有使用者不需變更設定。

### English

- Add a lyric layer to phone and head-unit Photo frame 1, the TV Photo frame 1, and Photo frame 2. Turn on Show lyrics in the frame settings to follow the current lyric line along the bottom edge, kept to one or two lines with an ellipsis for longer text and a fade between lines as the track changes. Lyrics without time tags are not drawn, so the layer never shows a line that will not move.
- The lyric layer runs only while the frame is visible, honours the song's lyric offset, and fetches lyrics once through the enabled providers when the current track has none cached.
- Version code 253; no database schema change or data migration. Lyrics are off by default, so existing users need no setting changes.

## 13.7.22

### 中文

- 手機與車機的數位相框 1 設定新增 QR code 與區網傳照；車機可連上傳送手機的熱點，不需 USB、配對碼或雲端中轉。手機先壓縮照片，接收副本會累加並可獨立清除，不影響本機、USB 或手機原檔；接收服務僅在傳送設定開啟且 App 位於前景時運作。
- 版本 code 252；無資料庫 schema 變更，升級不需遷移資料。TV 相框 1 與手機／車機相框 2 的原有來源及 Google Drive 授權流程維持不變。

### English

- Add QR code and direct local-network photo transfer to phone and head-unit Photo frame 1 settings. A head unit may join the sending phone's hotspot; no USB drive, pairing code, or cloud relay is needed. The phone compresses photos before transfer. Received copies accumulate and can be cleared separately without changing local, USB, or phone originals. The receiver runs only while transfer settings are open and the app is in the foreground.
- Version code 252; no database schema change or data migration. Existing TV Photo frame 1 sources and phone/head-unit Photo frame 2 Google Drive authorization remain unchanged.

## 13.7.21

### 中文

- TV 選單改用「數位相框 1」，包含遙控器操作、本地／USB 照片、固定設定視窗與同 Wi-Fi 手機傳照。FOSS 與 GMS TV 均可使用，不需要 TV Google OAuth。
- TV 首次開啟相框 1 時，會將先前 TV 相框 2 已選的本地照片與手機傳入副本匯入相框 1；來源與照片原檔保留。相框 2 恢復為手機／車機原有的本地與 Google Drive OAuth 流程。
- 版本 code 251；無資料庫 schema 變更。升級後如曾在 TV 相框 2 選照，請開啟 TV 相框 1 完成一次匯入；FOSS 與 GMS 仍是分開安裝的 App，資料不跨版本複製。

### English

- Move the TV frame to Photo frame 1, including remote controls, local/USB photos, a fixed settings dialog, and direct same-Wi-Fi transfer from a phone. Both FOSS and GMS TV builds include it without TV Google OAuth.
- On first launch of TV Photo frame 1, import previously selected local photos and phone-sent copies from the TV's Photo frame 2 catalog while preserving original sources and files. Restore Photo frame 2 to its phone/head-unit local and Google Drive OAuth flow.
- Version code 251; no database schema change. Open TV Photo frame 1 once after upgrading to import prior TV selections. FOSS and GMS remain separate installations and do not transfer data between variants.

## 13.7.20

### 中文

- TV 本地照片頁在內部儲存空間沒有可讀取的已索引照片時，清楚說明儲存空間已偵測到，並提示檢查完整照片授權或等候媒體掃描；不再錯誤要求重插 USB。
- 版本 code 250；無資料庫 schema 變更，升級不需遷移資料或設定。

### English

- Clarify that the TV's internal storage is detected when its local photo gallery has no accessible indexed images; suggest checking full photo access or waiting for media scanning instead of incorrectly asking to reconnect a USB drive.
- Version code 250; no database schema change or upgrade migration is required.

## 13.7.19

### 中文

- TV 左側選單將「停止並退出」固定在底部，避免小尺寸電視上被壓縮到看不見。
- TV 數位相框傳送設定在網址上方顯示可掃描的 QR code；手機網頁不再要求六位數配對碼。接收服務仍只在設定開啟時監聽區域網路，並限制同源請求與照片大小。
- TV 相框設定改用位置固定的視窗，來源選單與可捲動內容分開，避免遙控器操作時整個浮窗上下移動；手機與車機的底部設定頁維持原樣。
- 版本 code 249；無資料庫 schema 變更，升級不需遷移資料或設定。

### English

- Keep Stop and exit fully visible at the bottom of the TV sidebar on shorter displays.
- Show a scannable QR code above the TV photo sender address and remove the six-digit code from the phone page. The receiver still runs only while the settings are open and restricts requests to the same origin and small images.
- Use a fixed TV photo settings dialog with separate source navigation and scrollable content so remote navigation does not move the whole panel. Phone and head-unit bottom sheets are unchanged.
- Version code 249; no database schema change or upgrade migration is required.

## 13.7.18

### 中文

- TV 數位相框改由電視在區域網路提供選檔網頁；手機開啟電視網址、輸入六位數配對碼，從本機或系統檔案選擇器可見的雲端來源選照片，縮至最長邊 1920 像素並壓縮後直接傳到電視。照片會累加，TV 可播放或清空手機傳入的副本，原檔不受影響。
- TV 配對接收服務僅在相框配對設定開啟時運作，不需 TV Google OAuth、外部網站或照片雲端中轉；原有手機與車機的 Google Drive 資料夾授權和輪播維持原流程。
- TV 相框操作列的隱藏控制改為與其他操作一致的全螢幕圖示按鈕，保留遙控器焦點外框與方向鍵叫回控制列的操作。
- 版本 code 248；無資料庫 schema 變更，升級不需遷移資料或設定。

### English

- The TV photo frame now serves a local picker page. A phone opens the TV address, enters a six-digit pairing code, and selects photos from local storage or a cloud provider available in the system file picker. It resizes them to at most 1920 pixels on the longest edge and compresses them before direct transfer. Photos accumulate; the TV can play or clear received copies without changing originals.
- The TV receiver runs only while pairing settings are open and needs no TV Google OAuth, external website, or photo cloud relay. Existing phone and head-unit Drive folder authorization and slideshow remain unchanged.
- The TV frame's Hide controls action now uses a fullscreen icon button consistent with the other controls, retaining remote focus styling and D-pad reveal behavior.
- Version code 248; no database schema change or data/settings migration is required.

## 13.7.17

### 中文

- 修正 Google TV／Android TV 本地照片瀏覽器在尚未授權時沒有初始遙控器焦點、無法明確選擇「允許存取」的問題；授權後焦點會移到瀏覽器工具列。
- 相框設定與本地照片瀏覽器的授權、導覽、相簿與照片相關按鈕增加清楚的 TV 焦點外框；手機與車機觸控外觀維持不變。
- 版本 code 247；無資料庫 schema 變更，升級不需遷移資料或設定。

### English

- Fix missing initial remote focus on Allow access in the Google TV and Android TV local photo browser; move focus to the browser toolbar after permission is granted.
- Add clear TV focus outlines to frame settings, permission, navigation, album, and photo browser controls while leaving phone and head-unit touch styling unchanged.
- Version code 247; no database schema change or data/settings migration is required.

## 13.7.16

### 中文

- 修正 Google TV／Android TV 首頁分類標題在部分電視的動態色盤上與背景近乎同色的問題，改用 TV 專用高對比深色主題。
- TV 左側導覽改為等寬、靠左對齊的選項，提供一致的圖示、選取與遙控器焦點狀態，退出選項置於底部。
- 首頁歌曲快選與線上推薦每類顯示最多 6 首，線上最多呈現 4 個分類，讓分類標題更快出現；標題缺漏時顯示「推薦歌曲」。
- 版本 code 246；無資料庫 schema 變更，升級不需遷移資料或設定。

### English

- Fix TV Home section headings blending into the background on some TVs by using a dedicated high contrast dark theme for Google TV and Android TV.
- Replace the TV side menu with equal width, left aligned rows and consistent icons, selection, and remote focus states; place Exit at the bottom.
- Show up to six quick picks and six songs per online section, with up to four online sections, so category headings appear sooner; use Recommended songs when a section has no title.
- Version code 246; no database schema change or data/settings migration is required.

## 13.7.15

### 中文

- 修正 Android TV 首次從一般啟動圖示進入時可能開啟觸控版的問題；僅在 TV 裝置導向專用畫面，手機與 Android 車機的觸控流程維持不變。
- 修正 TV 播放佇列使用不支援儲存的列表鍵而造成的崩潰。
- GMS TV 左側選單新增「數位相框 2」，沿用既有本機照片、Google Drive 來源與快取，並提供遙控器焦點提示以操作相框控制、來源及設定。
- TV 相框保留半透明時鐘、歌曲資訊與控制列；可用遙控器隱藏及叫回控制列，返回鍵依序關閉設定、恢復控制列與返回首頁。
- TV 首頁依 YouTube Music 原本的區塊標題分類顯示線上歌曲，歌曲快選標示資料來源；首頁、搜尋、播放佇列與底部目前播放資訊增加封面。
- TV 左側選單新增「停止播放並退出」，會停止本機與投放播放並結束播放服務；一般返回仍可保留背景播放。
- 版本 code 245；沒有資料庫 schema 變更，升級不需遷移資料或設定。

### English

- Route the standard launcher entry to the dedicated interface on Android TV from the first launch. The phone and Android head-unit touch flow stays unchanged.
- Fix the TV queue crash caused by a list key that Compose cannot save.
- Add Photo frame 2 to the GMS TV menu, reusing saved local photos, Google Drive sources, and cache, with remote focus indicators for frame controls, sources, and settings.
- Retain the translucent clock, song information, and controls in the TV frame; use the remote to hide or reveal controls, and Back to close settings, reveal controls, then return Home.
- Group online TV Home songs by their original YouTube Music section titles and label the quick picks source; add cover art to Home, search, queue, and the bottom now-playing area.
- Add Stop and exit to the TV menu to end local or Cast playback and stop the playback service; ordinary Back still allows background playback.
- Version code 245; no database schema change or data/settings migration is required.

## 13.7.14

### 中文

- 新增 Android TV 專用啟動入口與遙控器介面，可用方向鍵、確認鍵與返回鍵瀏覽首頁歌曲、搜尋歌曲、控制上一首／播放暫停／下一首，以及查看與選播佇列。電視版會沿用既有播放服務與資料，初版不包含設定頁面。
- 原有 MainActivity 與 Android 車機觸控流程維持不變。版本 code 244；沒有資料庫 schema 變更，升級不需遷移資料或設定。

### English

- Add a dedicated Android TV launcher and remote interface for browsing Home songs, searching songs, controlling previous/play-pause/next, and viewing or playing the queue with the D-pad, Select, and Back. The TV interface shares the existing playback service and data; settings are outside this first version.
- Keep the existing MainActivity and Android head-unit touch flow unchanged. Version code 244; there is no database schema change or data/settings migration.

## 13.7.13

### 中文

- 修正歌單播到盡頭後推薦佇列可能重複舊歌曲與順序的問題：隨機挑選尚未使用的 YouTube 歌曲作為新電台來源，最多嘗試三個來源，並排除既有佇列、不同影片 ID 的同名同歌手歌曲，以及同一批回應中的重複歌曲；新推薦歌曲會打散順序。原歌單分頁維持既有順序。
- 歌單畫面與播放器遇到重複的續頁代碼時會停止該分頁，避免反覆載入同一頁。找不到新推薦時不會接上舊清單，可在播放佇列手動重試。版本 code 243，無資料庫 schema 變更；升級後原有歌單與設定不需轉換。

### English

- Fix repeated songs and ordering after a playlist ends: choose unused YouTube song seeds at random, try up to three radio seeds, and exclude songs already queued, matching title and artist under a different video ID, and duplicates within one response. Shuffle fresh radio additions while preserving the original playlist page order.
- Stop repeated continuation tokens on the playlist screen and in playback so the same page is not fetched indefinitely. When no fresh recommendations are available, the old list is not appended; manual retry remains available in the queue. Version code 243; no database schema change or migration of existing playlists or settings is needed.

## 13.7.12

### 中文

- GMS「數位相框 2」進入 Drive 來源時，先由持久化快取索引隨機檢查最多 5 張仍存在且可解碼的圖片並立即播放，再於背景擴充為全部有效快取，同時進行 OAuth、帳戶確認與雲端資料夾清單更新；有已建立索引的快取時，不再因地下室斷網或驗證等待而只顯示黑畫面。
- 雲端更新成功時保留目前畫面並無縫換成最新清單，先準備目前照片與接下來 4 張隨機照片，所有讀取都先查共用磁碟快取，只下載缺少或版本已變更的圖片。OAuth、授權或網路失敗會在操作列下方顯示穩定錯誤碼，離線隨機輪播仍持續涵蓋全部有效快取；快取容量為 0 或尚無快取時才等待網路。版本 code 242，無資料庫 schema 變更。
- 從舊版升級後，既有圖片二進位快取因舊格式沒有可離線反查的檔案 metadata，需至少成功更新一次保存的 Drive 資料夾；更新會在背景比對最新 metadata、建立離線索引且不重複下載已有圖片。此後才能在完全離線啟動時列出這些舊快取。

### English

- When Photo frame 2 opens a saved Drive source, it validates up to five randomized images from a persistent cache index and starts them immediately, expands to the complete intact cache in the background, and runs OAuth, account checks, and cloud-folder metadata refresh concurrently. With indexed cached photos available, entering a basement or waiting on authorization no longer leaves only a black frame.
- A successful cloud refresh preserves the visible frame while adopting the latest list, seeds the current photo plus four randomized upcoming photos, and checks the shared disk cache before downloading only missing or changed images. OAuth, authorization, and network failures show a stable error code below the controls while the complete valid cache keeps rotating offline; only a disabled or empty cache waits for the network. Version code 242; no database schema change.
- After upgrading, existing image binaries from older versions require one successful refresh of the saved Drive folder because the old cache format has no file metadata that can be enumerated offline. The refresh matches current metadata and builds the offline index in the background without redownloading existing images; those older entries can then be listed on a fully offline launch.

## 13.7.11

### 中文

- FOSS 維持套件識別碼 `com.jajafu.musiccabin`，GMS 改為 `com.jajafu.musiccabin.gms` 並顯示為「MusicCabin GMS」，使兩個 Release 版可同時安裝；Debug 分別使用 `.debug` 與 `.gms.debug`。靜態搜尋／音樂庫捷徑及依 application ID 產生的 Provider、辨識 action 會正確指向各自 variant。
- GMS 關閉只提供 FOSS APK 的 GitHub updater，維持本機手動安裝；GitHub Foss workflow 仍為手動觸發且建置流程不變。既有 GMS 安裝不會自動移轉設定、登入、下載或資料庫；Google Drive 必須以新 GMS package 與實際簽章 SHA-1 另建 Android OAuth client。兩版共用的網頁連結及 Discord callback 仍可能顯示 App 選擇器。版本 code 241，無資料庫 schema 變更。

### English

- FOSS keeps the `com.jajafu.musiccabin` package ID, while GMS moves to `com.jajafu.musiccabin.gms` and is labeled `MusicCabin GMS`, allowing both Release variants to be installed together; Debug uses `.debug` and `.gms.debug`, respectively. Static search/library shortcuts and application-ID-derived providers and recognition actions now target the correct variant.
- GMS disables the GitHub updater because the repository publishes only a FOSS APK and remains manually installed; the manually triggered GitHub Foss workflow is unchanged. Existing GMS settings, login, downloads, and database do not migrate automatically, and Google Drive requires a separate Android OAuth client for the new GMS package plus its actual signing SHA-1. Shared web links and the Discord callback may still show an app chooser. Version code 241; no database schema change.

## 13.7.10

### 中文

- 主選單在相框 2 可用時僅顯示相框 2，從五項回到四項；相框 1 的畫面、路由、設定與照片資料完整保留。不提供相框 2 的建置仍顯示原相框。
- 整理相框 2 齒輪設定：等寬照片來源卡片、統一圓角與間距、固定標題／完成按鈕、整齊的相簿及已選照片列表、附圖示的顯示開關及醒目的間隔數值。連線權限與圖片格式／快取說明可展開閱讀，首次連接前仍顯示唯讀授權範圍；照片與音樂播放流程維持不變。版本 code 240。
- 本機 Release 簽章可改由已忽略版控的根目錄 `keystore.properties` 提供 keystore 路徑、store 密碼、key alias 與 key 密碼；CI 環境變數仍具優先權，GitHub Foss workflow 的簽章方式不變。

### English

- When Photo frame 2 is available, the main menu shows only that frame, returning from five items to four. Photo frame 1 retains its screen, route, settings, and photo data; builds without Photo frame 2 keep the original menu entry.
- Refine the Photo frame 2 gear panel with equal-width source cards, consistent rounded groups and spacing, a fixed header and Done button, aligned folder and selected-photo lists, display icons, and a highlighted interval value. Connection permissions and image format/cache details can expand on demand, while the read-only scope remains visible before connecting. Photo and music playback behavior is unchanged. Version code 240.
- Local Release signing can now read the keystore path, store password, key alias, and key password from a root `keystore.properties` file that is ignored by version control. CI environment variables still take precedence, so the GitHub Foss workflow signing path is unchanged.

## 13.7.9

### 中文

- 修復通知欄與其他以 `Uri` 載入封面的位置仍顯示空白：縮圖降級重試同時支援字串與 `Uri` 請求，404 時自動逐級改用低解析度縮圖。

### English

- Fix blank covers on the notification shade and other surfaces that load artwork as a `Uri`: the thumbnail downgrade retry now handles both string and `Uri` requests, stepping down resolutions on 404.

## 13.7.8

### 中文

- 「數位相框 2」入口移至主選單原有相框旁；進入後立即以最後選取的本機照片或 Google Drive 資料夾開始全螢幕隨機輪播。有效的保存 Drive 授權會靜默使用，過期授權改由齒輪手動重新連接，不自動重複同意流程；來源選擇只在齒輪內提供。
- 新頁面沿用原相框的時間／歌曲／歌手半透明第一列，以及音樂上一首／播放／下一首與照片上一張／下一張、齒輪、退出第二列；支援 5／10／15／30／60 秒（預設 10 秒）、第一張立即顯示及目前輪次接下來最多三張壓縮照片預載。使用獨立 v2 preference keys、index 與 ViewModel，本機 MediaStore／USB／SD／direct USB／多選行為沿用既有程式，但不遷移舊選取。
- 本機與 Drive 照片輪播在齒輪及背景時暫停，Drive 快取與預載仍沿用共用 Coil DiskCache；獨立離線相簿／索引、舊資料承接與完整 v2 仍未完成。13.7.7 的資料夾列出與播放已確認，13.7.8 介面／車機驗證待完成；版本 code 238，無資料庫 schema 變更。
- 「數位相框 2」旋轉螢幕不再重新載入：Drive 沿用已連接的工作階段與目前照片、不重列清單；本機沿用播放順序與目前照片位置，只重排版並將新尺寸套用到之後的照片。

### English

- Photo frame 2 (test) now sits beside the original frame in the main menu and opens directly into a full-screen randomized slideshow using the last selected local-photo source or Google Drive folder. A valid saved Drive authorization is reused silently; expired authorization requires an explicit reconnect from the gear without an automatic consent loop, and source selection is available only from the gear.
- The new screen keeps the original frame’s semitransparent time/song/artist first row and separate music previous/play-pause/next, photo previous/next, gear, and exit controls in the second row. It supports 5/10/15/30/60-second intervals (10 seconds by default), immediate first-image display, and prefetching up to three compressed photos ahead in the current randomized round. Independent v2 preference keys, index, and ViewModel are used; existing MediaStore, USB/SD, direct USB, and multiselect behavior is reused without migrating old selections.
- Local and Drive photo playback suspends from the gear and in the background, while Drive cache and prefetch continue using the shared Coil DiskCache. Standalone offline gallery/catalog, legacy selection migration, and the complete v2 scope remain unfinished. Version 13.7.7 folder listing and playback are confirmed; 13.7.8 UI and head-unit validation are pending. Version code 238; no database schema change.
- Photo frame 2 no longer reloads on rotation: Drive keeps its connected session and current photo without re-listing, while local playback keeps its order and position with only a layout pass and the new size applied to later photos.

## 13.7.7

### 中文

- 「數位相框 2」的 Google Drive 流程改為選取資料夾後按「使用此資料夾播放」：完整讀取所有直屬照片 metadata 分頁，再開始自動隨機全螢幕輪播。第一張先顯示，再預載最多三張壓縮照片，預載不解碼，顯示解碼長邊不超過 1920px；雲端可從齒輪暫停／繼續。歌曲上一首／播放暫停／下一首與照片上一張／下一張分開控制；本機來源保留原有自動輪播，不使用雲端預載。
- 使用共用 Coil DiskCache 與既有容量／清除設定，key 包含帳戶、檔案 ID、版本／modifiedTime／size；命中不下載，新增、缺失或變更才下載。損壞／不可用檔案略過，下一張載入時保留目前畫面；網路失敗時，工作階段內已有快取的照片仍可播放。容量為 0 時只用單一暫存檔、不預載且不保留；進背景會釋放播放資源但保留選取與快取，返回須手動重新連接。
- 本次只涵蓋 Drive 播放增量，未完成獨立離線相簿／gallery、本機資料承接或整體 v2 規畫；將原先「開始前先準備三張」調整為第一張先顯示、再向前預載最多三張以縮短首次等待。版本 code 237。實際目標車機驗證尚未完成，無資料庫 schema 變更。

### English

- Photo frame 2 now offers Use this folder to play after selecting a Google Drive folder: it consumes all direct-child photo metadata pages before starting automatic randomized full-screen playback. The first image displays first, followed by prefetching up to three compressed photos without decoding them; display decoding has a 1920px maximum long edge, and cloud pause/resume is available from the gear. Song previous/play-pause/next and photo previous/next controls are separate; local sources retain their original automatic rotation without cloud prefetch.
- The shared Coil DiskCache and existing capacity/clear settings are used, with keys containing account, file ID, and version/modifiedTime/size. Cache hits avoid downloads; new, missing, or changed versions download on demand. Corrupt or unavailable files are skipped while the current frame remains during the next load; session-cached photos can continue during network failure. Capacity 0 uses one temporary file with no prefetch or retention; background closes playback and releases resources while retaining selection and cache, and return requires an explicit reconnect.
- This increment covers Drive playback only. Standalone offline gallery, local migration, and the complete v2 plan remain future work; the original prepare-three-before-start goal is adjusted to first-image-first plus up to three ahead for faster start. Version code 237. Actual target head-unit validation is still pending, with no database schema change.

## 13.7.6

### 中文

- GMS 建置新增選用的「設定 → 相框 2（測試）」入口，進行第 0 階段 Google Drive 唯讀 OAuth 驗證；Drive 帳戶與 YouTube Music 登入分離，會讀完資料夾所有分頁後顯示完整的直屬照片 metadata 清單，只有明確預覽單張不超過 20 MiB 的 JPEG、PNG、WebP 照片時才下載，解碼長邊上限 1920px。token SDK 呼叫集中於 Drive provider；帳戶檢查、取消、停用與本機解除連接則由 Drive 專用模組處理，不接觸音樂登入。Foss／Izzy 不顯示入口。
- 圖片只使用暫存檔，載入完成後清理，不提供離線快取；三張預載、共用圖片快取、v2 本機資料承接與離線相簿仍屬後續規畫。建置開關 `-PphotoFrameV2Enabled=false` 與 `-PphotoFrameDriveEnabled=false` 可分別隱藏入口及停用 Drive；停用 Drive 時 GMS 授權依賴仍會編入。無資料庫 schema 變更，也不進行舊相框資料遷移升級。原有數位相框、本機／USB 來源、音樂登入與播放維持不變。第 0 階段實際車機 OAuth 測試尚未完成。

### English

- GMS builds add an opt-in Settings → Photo frame 2 (test) entry for phase 0 Google Drive read-only OAuth validation. Drive accounts are separate from YouTube Music login; the app consumes every folder page before showing the complete direct-photo metadata list, and downloads an individual JPEG, PNG, or WebP only for explicit preview, up to 20 MiB with a 1920px maximum long edge. Token SDK calls are confined to the Drive provider; account verification, cancellation, disabling, and local disconnect are handled by the Drive-only module without touching music login. Foss and Izzy do not expose the entry.
- Image binaries use temporary files that are cleaned after loading, with no offline cache. Three-image preloading, shared image cache, v2 local migration, and offline gallery remain planned. Build flags `-PphotoFrameV2Enabled=false` and `-PphotoFrameDriveEnabled=false` independently hide the entry and disable Drive; the GMS authorization dependency remains compiled when Drive is disabled. There is no database schema change or legacy photo-frame data migration upgrade. The original photo frame, local/USB sources, music login, and playback are unchanged. The actual phase 0 head-unit OAuth test is still pending.

## 13.7.5

### 中文

- 修正快取清理與下載完成的競態，僅清除仍符合條件的快取日期，保留最新的下載、喜愛與歌曲資料；歌曲選單訂閱藝人不再清空藝人頁面快取。
- 還原前先驗證歌手別名資料，避免格式損壞時已替換資料庫；還原沒有別名的舊備份時清除舊別名。
- 修改歌曲標題不再覆蓋最新的下載與喜愛狀態，僅修改標題時保留原歌手識別與含逗號的姓名；上傳使用目前登入的 Google 帳號索引。
- 修正播放診斷報告的錯誤時間換算與 MusicCabin 名稱。無資料庫結構變更。

### English

- Fix cache-cleanup races with completed downloads by clearing only eligible cache dates while preserving current download, like, and song data. Subscribing through the song menu no longer clears cached artist pages.
- Validate artist aliases before replacing restored database files; clear existing aliases when restoring an older backup without aliases.
- Preserve current download and like state when editing a song title, along with artist identities and names containing commas in title-only edits. Upload using the currently signed-in Google account index.
- Correct playback diagnostic timestamps and MusicCabin branding. No database schema changes.

## 13.7.4

### 中文

- 修正登入流程的字串讀取方式：錯誤訊息改在 composition 期間以 `stringResource` 解析，解決 Foss Release 建置的 6 個 lint 錯誤。

### English

- Fix how the login flow resolves strings: error messages are now resolved with `stringResource` during composition, clearing the 6 lint errors that blocked the Foss Release build.

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

- 同步上游 13.7.0 的穩定性與效能修正：大量收藏不再一次載入全部藝人頁面快取，歷史紀錄改為先在資料庫端依顯示區段去重，快取歌單改為資料庫驅動更新且不再每秒輪詢，音訊處理器重用緩衝區。
- 保留手動編輯的歌曲標題與歌手：專輯同步不再覆寫自訂內容，歌曲編輯支援多位歌手、僅調整該歌曲的歌手關聯，並保留播放佇列中的顯示。
- 登入流程重寫：支援同一 Google 帳號下的 YouTube 頻道選擇與切換，自動重試網路恢復後的首頁資料，改善離線時帳號顯示。
- 下載與上傳強化：下載完成自動釋出播放器快取空間、上傳改為串流避免大檔記憶體不足、重複加入歌單改為批次檢查並在背景執行緒寫入。
- 播放錯誤畫面可一鍵複製完整診斷報告（含版本、裝置、串流客戶端與錯誤鏈），跨淡入淡出保留重複播放模式，通知列關閉後不再自動彈回，Listen Together 心跳干擾降低。
- 修復舊版資料庫升級（21→24 欄位補齊改為冪等），歌單追加改用實際最大位置避免位置衝突。

### English

- Sync upstream 13.7.0 stability and performance fixes: bulk screens no longer hydrate every cached artist page, History is deduplicated by display section in the database, the Cache playlist is database-driven instead of polling every second, and audio processors reuse buffers.
- Preserve manually edited song titles and artists: album sync no longer overwrites custom content, and the song editor supports multiple artists by changing only that song's artist relations while refreshing the playing queue display.
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
