[English](README.md) | [繁體中文](README.zh-TW.md)

# MusicCabin

MusicCabin 是 [Metrolist](https://github.com/MetrolistGroup/Metrolist) 的 Android 車機導向客製分支。Metrolist 是一個開源的 Android YouTube Music 用戶端。

本分支由 [jajafu](https://github.com/jajafu) 維護，主要改善車載使用時的播放介面、可讀性與操作體驗。

## 主要特色

以下是在 Metrolist 之上新增的客製功能。完整版本歷史請見 [`changelog.md`](changelog.md)；本頁只描述目前行為。

- **相框KTV**（手機／車機主選單，取代一起聽；設定 → 整合下的一起聽、Discord、LastFM 入口已隱藏）：全螢幕本機／USB 照片輪播，含時鐘、歌曲資訊、音樂與照片控制及相框內設定。間隔拉桿（5／10／15／30／60 秒）、完整顯示／裁切填滿、重接隨身碟後可重新掃描；方向不合的照片會在同一間隔併排顯示，湊不到則退回單張。原始檔案不會被複製或刪除。請於停車時使用，不在 Android Auto 顯示照片。
- **按需照片瀏覽器**：分頁載入系統已索引的內部儲存與 USB／SD 照片，可依目錄一次選取或取消整個目錄，介面完整繁體中文化。App 啟動時不掃描；縮圖不寫入共用快取，只在相框或瀏覽器可見時載入。若車機 MediaStore 已偵測到 USB 卻沒有建立索引，空白 USB 頁面提供輕量直接瀏覽與儲存裝置診斷。舊版本已保存的來源仍可讀取。
- **KTV 歌詞圖層**（手機、TV、相框KTV2 共用）：畫面下方同時顯示前一句、目前句與後一句，依播放方向滑動，逐字掃光，並依聲部定位（`v1` 靠左、`v2` 靠右、`v1000`／背景置中）。內建離線簡轉繁（台灣繁體），控制列可一鍵更換歌詞來源。
- **相框KTV2**（GMS 手機／車機主選單；原相框功能與資料完整保留）：開啟後直接使用保存的本機來源或 Drive 資料夾。Drive 先隨機驗證幾張快取照片立即離線播放，再於背景擴充為全部有效快取，同時更新 OAuth 與雲端清單；授權或網路失敗時快取照片仍持續輪播並顯示穩定錯誤碼，雲端可暫停／繼續。請參閱 [Google Drive 測試指南](docs/photo-frame-google-drive-testing.zh-TW.md)。
- **區域網路傳送照片**：手機／車機相框設定顯示 QR code 與網址（可走手機熱點）；手機先壓縮再直接傳入相框，不需隨身碟、配對碼或雲端中轉。接收服務只在該設定區塊開啟且 App 位於前景時運作。TV 提供手機選檔網頁（最長邊 1920 像素，不需 TV Google OAuth）。接收副本會累加，可與本機／USB 原檔分開清除。
- **TV 遙控器體驗**（Google TV／Android TV）：方向鍵介面，歌曲快選與線上歌曲依原始首頁區塊分類，首頁、搜尋與佇列皆有封面；歌曲列可用遙控器按愛心與加入歌單，側欄「收藏」集中喜愛歌曲與收藏歌單，歌單詳情可全部播放、收藏及編輯本地歌單。高對比分類標題；「停止並退出」會結束播放與服務，一般返回則保留背景播放。TV 相框可用遙控器操作輪播、設定與換歌詞；全螢幕圖示或選單鍵隱藏控制列，任一方向鍵叫回。
- **TV 登入與更新**：側欄「帳號」頁在未登入時顯示 QR code、電視網址與 6 位數配對碼；同一 Wi-Fi 下用手機帳號選單授權，即可把登入推送到電視並同步收藏，不需在電視輸入帳號密碼。FOSS TV 版另有遙控器操作的「更新」頁（檢查、下載進度、安裝）；GMS 版手動更新。
- **歌詞**：提供者優先順序為 Paxsenix、LyricsPlus、KuGou、Better Lyrics、LrcLib，YouTube 字幕與 YouTube Music 作為回退。「更換歌詞來源」會用目前歌曲查詢全部已啟用來源並列出候選；手動挑選的歌詞會保留，不被自動重抓覆寫。包含同步歌詞、翻譯，以及無逐字時間來源的整句點亮回退。
- **車載播放**：獨立音樂音量，不與導航語音打架，導航壓低或短暫暫停後可靠恢復；跳過靜音、睡眠計時、音量正常化、速度與音調；語音播放直接用 YouTube 相關性排序結果，並以可延伸的相關歌曲電台接續。
- **車載介面**：播放島與相框文字以手機大小為 1 倍基準，依螢幕短邊放大至自動縮放上限（預設 2 倍、最多 3 倍；設定 → 外觀，或 TV 相框顯示設定）。首頁精簡為分類按鈕、12 個快速存取、帳號歌單及最多 3 個官方推薦；大型自適應格狀歌單選擇器；橫直向共用同一播放島尺寸。Android Auto 分頁瀏覽大型媒體庫（每頁 100 筆），本機搜尋走 SQL，封面按需載入以降低記憶體壓力。
- **可靠的同步**：Google 登入支援多頻道選擇與事後切換；快取歌單只列串流暫存。按讚使用單一有序持久佇列，歌單建立／加入／移除保存在資料庫之外並自動重試，在歌單庫顯示待同步；自動完整同步只在待處理工作全數成功後才冷卻，下拉重新整理會加入執行中的同步而不重複執行。連續「播放下一首」維持先後順序且隨機播放安全；暫停中按上一首／下一首會在各入口自動開始播放。
- **品牌與版本**：`MusicCabin` 名稱搭配黑色音樂車 Logo，統一用於啟動器、關於頁、通知與商店素材。FOSS（`com.jajafu.musiccabin`）與 GMS（`com.jajafu.musiccabin.gms`）可同時安裝、資料各自獨立；FOSS 在 App 內從 GitHub Releases 更新，GMS 手動更新。

## 核心音樂功能

上游繼承的基本音樂功能：

- 播放 YouTube Music 音樂。
- 背景播放與離線下載。
- 跳過靜音、睡眠計時、音量正常化、速度與音調調整。
- 同步歌詞與歌詞翻譯，可在歌詞選單「更換歌詞來源」。
- 搜尋歌曲、專輯、藝人與播放列表。
- 音樂庫、本機播放列表與帳號同步。
- Material 3 介面，支援亮色、暗色、全黑、動態與預設配色主題。
- 針對 Android Auto 調整版面與播放控制。

## 建置與更新

在本機建置 FOSS Release 版本：

```bash
./gradlew :app:assembleFossRelease
```

本機可用 `./gradlew :app:assembleGmsRelease` 建置能與 FOSS 並存的 GMS Release；其啟動器名稱為「MusicCabin GMS」。

GitHub Actions workflow 均為手動執行。發版 workflow 只會建置 FOSS Release APK，並將 `MusicCabin-v<version>-car.apk` 發布到本專案的 GitHub Releases。Release notes 使用 `changelog.md` 的對應版本內容。

只有 FOSS 建置啟用 App 內更新器，會檢查[本專案的 Releases](https://github.com/jajafu/MusicCabin/releases)，在 App 內直接下載符合版本的 APK（顯示進度）並開啟系統安裝器；Android 仍會要求使用者核准安裝。僅 FOSS 建置為此流程宣告 `REQUEST_INSTALL_PACKAGES` 權限，其他 variant 不會。自用 GMS 建置一律手動更新。

FOSS 使用 `com.jajafu.musiccabin`，GMS 改用 `com.jajafu.musiccabin.gms`（Debug 分別附加 `.debug`／`.gms.debug`），兩個 Release 版可同時安裝，設定、登入、下載、資料庫與權限完全分開。舊套件識別碼的 GMS 安裝不會原地升級或自動移轉資料；請以新套件識別碼及實際簽章 SHA-1 另建 Android OAuth client 後再連接 Drive。資料庫結構維持不變；後續只要同一 variant 沿用相同套件識別碼與簽章金鑰，即可直接覆蓋更新。

## 原始專案與致謝

本專案是 [Metrolist](https://github.com/MetrolistGroup/Metrolist) 的修改版本。原作者、貢獻者與版權聲明仍保留於程式碼與 [`LICENSE`](LICENSE) 中。

Metrolist 也使用了 [InnerTune](https://github.com/z-huang/InnerTune)、[OuterTune](https://github.com/DD3Boh/OuterTune)、[Better Lyrics](https://better-lyrics.boidu.dev)、[metroserver](https://github.com/MetrolistGroup/metroserver)、[MusicRecognizer](https://github.com/aleksey-saenko/MusicRecognizer) 及 [zemer-cipher](https://github.com/ZemerTeam/zemer-cipher) 等開源專案的成果。

## GPLv3 修改發布要求

本專案採用 [GNU General Public License v3.0](LICENSE) 授權。

發布修改後的程式或基於本專案產生的 APK 時，請遵守以下要求：

- 保留原作者、版權、來源、授權與免責聲明。
- 清楚標示這是修改版本，並說明修改內容。
- 提供對應原始碼，以及建置該發布版本所需的腳本或指示。
- 衍生作品必須依 GPLv3 授權發布，不得加入與授權條款衝突的限制。
- 隨發布內容提供 GPLv3 授權全文。

原始程式的版權仍屬於原作者；新增程式碼的版權則由各貢獻者保有。

## 免責聲明

本專案與 YouTube、Google LLC、Metrolist Group LLC 或其關係企業沒有任何隸屬、出資、授權、背書或其他關聯關係。

本專案中提及的商標、服務標章及其他智慧財產權均屬於各自權利人所有。
