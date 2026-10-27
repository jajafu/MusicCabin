[English](README.md) | [繁體中文](README.zh-TW.md)

# MusicCabin

MusicCabin 是 [Metrolist](https://github.com/MetrolistGroup/Metrolist) 的 Android 車機與TV導向客製分支。Metrolist 是一個開源的 Android YouTube Music 用戶端。


## 主要特色

以下是在 Metrolist 之上新增的客製功能。完整版本歷史請見 [`changelog.md`](changelog.md)；本頁只描述目前功能。

- **相框KTV**：
   - 全螢幕本機／USB 照片輪播，含時鐘、歌曲資訊、音樂與照片控制及相框內設定。
   - 播放間隔拉桿（5／10／15／30／60 秒）
   - 完整顯示／裁切填滿
   - 方向不合的照片會在同一間隔併排顯示
   - 歌詞顯示開關、歌詞簡轉繁開關
   - 歌詞於畫面下方，同時顯示前一句、目前句與後一句，依播放方向滑動，逐字掃光
   - 控制列可一鍵更換歌詞來源
   - 控制列均有歌詞偏移 −／＋按鈕，每次 50ms
   - 車機顯示 QR code，手機掃描後開啟網址(可走手機熱點同區域網路下)，手機選擇照片後先壓縮再直接傳入相框，不需USB裝置，同一手機網頁還有「點歌」分頁：搜尋歌曲、插播、加到待播清單最後、調整待播順序或移除待播歌曲，並每 10 秒自動更新目前播放與待播清單
   - GMS可自行編譯設定google Oauth，直接存取個人雲端硬碟照片，請參閱 [Google Drive 測試指南](docs/photo-frame-google-drive-testing.zh-TW.md)。

- **TV 遙控器體驗**（Google TV／Android TV）：
  - 獨立 TV 操作介面
  - 歌曲列可用遙控器按愛心與加入歌單
  - 側欄「收藏」集中喜愛歌曲與收藏歌單
  - 歌單詳情可全部播放、收藏及編輯本地歌單
  - 關閉螢幕持續播放，「停止並退出」會結束播放與服務，一般返回則保留背景播放
  - TV 相框可用遙控器操作輪播、設定與換歌詞
  - 側欄「帳號」頁在未登入時顯示 QR code、電視網址與 6 位數配對碼；同一 Wi-Fi 下用手機帳號選單授權，即可登入
  - 登入後可同步推送 YouTube 播放清單到電視並同步收藏。

- **車載播放**：獨立音樂音量，可降低音量避免音樂超過導航音量。
- **車載介面**：
  - 播放島與相框文字以手機大小為 1 倍基準，依螢幕短邊放大至自動縮放上限（預設 2 倍、最多 3 倍；設定 → 外觀，或 TV 相框顯示設定）。
  - 首頁為分類按鈕、12 個快速存取、帳號歌單

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
