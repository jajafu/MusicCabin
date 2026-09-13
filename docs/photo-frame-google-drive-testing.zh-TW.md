# 「數位相框 2」Google Drive 第 0 階段測試指南

本文件只涵蓋 [Google Drive 相框規畫](photo-frame-google-drive-plan.zh-TW.md) 第 0 階段的 OAuth／讀取、資料夾播放與相框 2 介面驗證。2026-09-12 使用者確認目前經修正後可在手機運作；13.7.11 將 GMS 改為可與 FOSS 並存的獨立套件，主選單／設定介面仍待重新檢查，車機尚未驗證，舊相框選取資料的自動遷移與獨立離線相簿／gallery 也尚未實作。

## Google Cloud 設定

依規畫文件第 1 節設定一個個人 Google Cloud 專案：

1. OAuth 同意畫面選 `External`，啟用 Google Drive API，加入本人為 test user，設定 `drive.readonly` 個人測試範圍。
2. 建立 Android OAuth client，填入實際安裝建置的 application ID 與簽署憑證 SHA-1。使用下列指令取得簽署資訊：

   ```powershell
   ./gradlew :app:signingReport
   ```

   GMS Debug 預設套件為 `com.jajafu.musiccabin.gms.debug`；個人簽署的 GMS Release 為 `com.jajafu.musiccabin.gms`。兩者都必須搭配該安裝版本的實際簽章 SHA-1。
3. Testing 狀態要測試同意後七日到期；長期自用可改為 `In production`，仍只供個人安裝。兩種狀態都不需要 web client secret，也不使用 YouTube Music cookie。

### 從 Testing 轉 In production（個人自用）

只改 Google Cloud，不用改 code、不用重編 APK、不用重裝。`In production` 是 OAuth 同意畫面的伺服器端狀態，APK 認的是 package name、SHA-1 與 scope。

1. 到 `console.cloud.google.com`，切到測試用的同一個專案，進入 `Google Auth Platform`（舊版叫 `APIs & Services → OAuth consent screen`）。
2. 若「目標對象」頁的「發布應用程式」是灰色不能按，先到左側「品牌」頁把必填補完再回來：應用程式名稱、使用者支援電子郵件、開發人員聯絡電子郵件，**外加應用程式首頁與隱私權政策連結**（新版 Console 切 External 正式版會要求這兩個 URL，缺了按鈕就是灰的）。使用者類型確認為「外部」。注意：這只是發佈的前置欄位，**個人自用不需要送品牌驗證或範圍驗證**，驗證中心的警告不用理；若不小心進了驗證流程（如品牌名稱與首頁內容不符被退件），直接取消即可。
3. 到左側「資料存取權」加入 `https://www.googleapis.com/auth/drive.readonly` 範圍並儲存（沒出現在清單就用手動新增）。品牌與範圍都齊後，「發布應用程式」才會啟用。
4. 回到「目標對象」按「發布應用程式」，狀態顯示為正式版（`In production`）即完成。發佈自用不需送驗證，授權時出現「未驗證應用程式」警告屬正常，未驗證的人數上限只影響上百人的公開用途，個人使用不受影響。
3. 不新增、不更換 scope（維持 `drive.readonly`）；不建立新的 Android OAuth client；不把任何 secret 放入 APK。
4. 轉完後在 App 內做一次「本機解除連接 → 連接 Google Drive」，選擇同一帳戶重新同意。Testing 期間核發的舊授權仍受七日到期限制，重連一次即取得正式效期的授權。
5. 只有下列情況才需要動 client 或重裝：安裝的 APK 變體改變（例如從 `com.jajafu.musiccabin.gms.debug` 換成 `com.jajafu.musiccabin.gms`）、簽署憑證更換、新增 scope。此時用 `./gradlew :app:signingReport` 核對實際套件與 SHA-1，補上對應的 Android OAuth client 後再安裝與授權。
6. 自用可不申請公開驗證，授權時出現「未驗證應用程式」警告屬正常，按繼續即可。此設定只是 OAuth 狀態，並非 Play 商店發行；也不要把同一個個人專案分享給公眾使用，公開用途需另案申請驗證。

### 公私分流：公開 Foss 與自用 GMS 簽名

公開發行沿用專案既有 release key 編 Foss（無 Drive 功能），自用 GMS 另建一把只屬於自己的私人 key，兩邊簽名不同就不會互相蹭到 OAuth 專案。私人 keystore 放在 repo 之外（例如家目錄 `.keystores`），不進版本控制；`app/build.gradle.kts` 已支援用環境變數指向它，不需改 code：

1. 本機建私人 keystore（密碼只打在自己的 shell，不要貼進對話或文件）；
2. 用 `keytool -list -v` 讀出該 keystore 的 SHA-1，只拿 SHA-1 去 Cloud 註冊；
3. 在 Cloud `Credentials` 新增一條 Android OAuth client：package `com.jajafu.musiccabin.gms`（無 `.debug` 後綴）＋私人 SHA-1。共用的 repo debug key（`com.jajafu.musiccabin.gms.debug`）不要綁進私人專案；
4. 自用編譯時帶環境變數編 GMS release：`METROLIST_RELEASE_KEYSTORE_PATH`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，裝上後在 App 內做一次「本機解除連接 → 連接 Google Drive」。若不想每個視窗重設，可用 `setx` 把四個變數寫入使用者環境變數（在自己的 shell 跑一次即可，密碼不要貼進對話或文件），之後新開視窗直接跑 `./gradlew :app:assembleGmsRelease` 就好。

別人 clone 原始碼自己編（debug 共用 key 或自己的簽名）時，簽名與私人專案綁定的 SHA-1 不同，授權會直接失敗，不會用到你的專案，也看不到你的資料。

### GMS Release／Debug 都可與 Foss 公開版並存

GMS Release 使用 `com.jajafu.musiccabin.gms`，GMS Debug 使用 `com.jajafu.musiccabin.gms.debug`，兩者都不會與公開 Foss Release 的 `com.jajafu.musiccabin` 衝突。7 天到期是 Testing 狀態的限制，與 debug／release 簽名無關，因此 debug 版只要專案已轉 `In production`，一樣沒有 7 天到期的問題，且不需自備 keystore。代價：repo 內建的共用 debug key 人人都有，若把它綁進私人專案，別人編 debug 就能蹭用你的專案（用量與警告都算你的）；debug 版另有效能較差、耗電較高的取捨，車機長期使用仍以自簽 release 為佳。自用 GMS 不啟用 GitHub updater，更新一律手動安裝 APK。

Foss 與 GMS 可並存不代表所有外部連結都能自動分流。兩版仍共用 YouTube／Listen Together 連結宣告與 `metrolistdiscord://oauth2/callback`；同時安裝時 Android 可能顯示 App 選擇器，Discord OAuth 若回到另一個 variant 會因 state 不符而失敗，請重新操作並選擇原先發起登入的 App。

不要把 OAuth client secret 放入 APK，也不要把 YouTube Music 登入帳戶或 Cookie 當成 Drive 授權。

## 建置與單元測試

開發檢查使用 GMS／Foss Kotlin 編譯與相框單元測試；APK 由使用者手動編譯及安裝。以下列出檢查與建置指令，實際開發檢查結果記錄於下方：

```powershell
./gradlew :app:compileGmsDebugKotlin :app:compileFossDebugKotlin :app:testGmsDebugUnitTest --tests 'com.metrolist.music.photo.v2.*' --tests 'com.metrolist.music.photo.*'

# 使用者手動建置含 Drive 功能的 APK
./gradlew :app:assembleGmsDebug
```

2026-09-11（13.7.8）最終開發驗證：GMS／Foss Debug Kotlin 編譯通過，88 項新舊相框單元測試全部通過（0 失敗、0 跳過），並通過 `git diff --check`。新增測試涵蓋新舊來源／顯示設定隔離、重新開啟保存的本地照片與 Drive 資料夾、權限失效、雲端照片歷史、齒輪暫停載入，以及主選單與返回。APK 仍由使用者手動編譯及安裝；新入口、橫直式介面、大字體、音樂操作與長時間車機播放待裝置驗證。

2026-09-12（13.7.10）最終開發驗證：GMS／Foss Debug Kotlin 編譯及 88 項相框單元測試通過（0 失敗、0 跳過），`git diff --check` 通過。導航測試確認主選單只有一個相框入口，並可進入保留的舊相框路由及正確返回。APK 繼續由使用者手動編譯；新設定外觀仍待手機複查，手機既有測試結果不代表車機驗收通過。

建置開關彼此獨立：`-PphotoFrameV2Enabled=false` 隱藏相框 2 入口；`-PphotoFrameDriveEnabled=false` 停用 Drive provider。停用 Drive 時 GMS 授權依賴仍會編入。若要完整移除 GMS OAuth，依規畫文件第 9 節改綁定回傳不可用的 provider、刪除 `GoogleDriveOAuth.kt`、移除 `gmsImplementation` 的 `libs.drive.authorization` 與 version catalog 對應項目；保留 Cast 仍需的依賴。

## 測試前提與結果記錄

使用者於 2026-09-12 確認目前版本在手機可運作。13.7.10 的新設定介面待手機複查，車機尚未驗證；另需於目標 Android 10 車機確認精簡 Google 服務、橫直式畫面、大字體與長時間播放的表現。

## 操作順序

1. 從四項主選單開啟「數位相框 2」，進入齒輪後選擇雲端來源並啟用 Drive，再按「連接 Google Drive」、選擇帳戶並完成同意畫面。
2. 瀏覽到要播放的資料夾，按「使用此資料夾播放」。App 讀完所有 `nextPageToken`（包括中間空清單頁面）後直接進入輪播，不需要逐張選片或下載；若測試本機來源，則從齒輪選本機並使用瀏覽器的 MediaStore 內部／USB／SD、direct USB 與多選流程。
3. 確認 Drive 自動隨機全螢幕播放：預設 10 秒，可選 5／10／15／30／60 秒；從齒輪測試雲端暫停／繼續，控制層測試歌曲上一首／播放暫停／下一首與照片上一張／下一張。第一張先顯示，之後最多預載接下來三張壓縮檔；本機來源則確認原有自動輪播，不套用雲端預載。
4. 播放與預載先查共用圖片快取，缺少時才單工下載；不預先下載整個資料夾。顯示解碼長邊不超過 1920px，預載檔案不解碼。暫存檔於載入後清理，已提交的圖片保留於共用快取。
5. 進背景或離開相框 2 頁面後會停止工作；再次進入時本機來源或仍有效的 Drive 授權會自動使用保存的來源並播放，Drive 授權過期則從齒輪手動重新連接。舊相框的來源與選取資料應保持獨立。
6. 重播快取照片時應避免再次下載。圖片容量與清除操作沿用「設定 → 儲存空間」；容量為 0 時不保留照片，也不預載。清除後，再次播放會重新下載。
7. 測試 token 更新；切換帳戶前先本機解除連接。輪播發生網路／授權等整體錯誤時停止新下載，繼續尋找工作階段內的快取照片；從齒輪的雲端暫停／繼續可重試下載，需要重新授權則手動重新連接。

| 裝置／車機型號 | Android／GMS 版本 | 套件 | SHA-1 | 測試帳戶 | 結果（不要填 token） |
| --- | --- | --- | --- | --- | --- |
| 待填 | 待填 | 待填 | 待填 | 待填 | 待測 |

## 情境清單

每項記錄畫面結果與去敏錯誤碼；不要記錄 access token、Cookie 或 client secret。

| 情境 | 預期檢查 |
| --- | --- |
| 主選單與舊資料 | GMS 主選單顯示四項且只有相框 2；原相框路由仍可用，來源和設定未被清除。不提供 v2 的建置保留原相框入口。 |
| 設定排版 | 本地／雲端來源卡片等寬、相簿列表對齊；捲動時標題與完成按鈕保持可見。測試深淺色、橫直式、大字體及說明展開／收合，開關與選片作用不變。 |
| 首次同意 | 可選帳戶、完成授權、瀏覽 My Drive 資料夾；按「使用此資料夾播放」後自動播放。 |
| 取消／拒絕 | 回到可操作的設定頁，不自動重開授權，不影響音樂登入。 |
| 等待逾時 | Drive SDK 網路呼叫使用 20 秒 timeout；同意 UI 不強制套用 20 秒 timeout。可取消 SDK 等待並顯示重試狀態，不留下可用的半成品。 |
| 網路錯誤 | 顯示錯誤並可重試；不把 YouTube Music 登出。 |
| 大型資料夾 | 消耗所有 `nextPageToken`，包括中間空清單頁面，完成直屬照片 metadata 清單計數；不可批次下載圖片二進位資料。 |
| 不可讀照片 | 略過或顯示該檔案錯誤，不誤判整個帳戶失效。 |
| 大檔與格式 | 超過 20 MiB 或非 JPEG／PNG／WebP 時略過；顯示解碼長邊不超過 1920px。 |
| 隨機與控制 | 每輪不重複；多張時跨輪不連續顯示同一張。空資料夾有提示；單張不反覆下載。暫停、下一張、間隔、點擊隱藏控制列與返回均可操作。 |
| 快取 | 重播不再次下載圖片；不同帳戶、檔案版本分開。快取清除／淘汰後按需下載，損壞與取消不提交半檔。 |
| 慢速與斷線 | 下一張尚未就緒時保留目前照片；整體下載錯誤後只讀剩餘快取，不對每個未快取檔案反覆發出網路請求。 |
| token 更新 | 401 後只重取一次並重試；需要互動時提供重新授權入口。 |
| 本機解除連接 | 清除 Drive 本機狀態，不清 YouTube cookie、音樂帳戶或原相框資料。 |
| 切換帳戶 | 舊帳戶請求失效，結果不能寫回新帳戶；各帳戶資料隔離。 |
| 停用後重開 App | 不自動啟用、不發 Drive 請求，入口依建置／設定狀態隱藏或停用。 |
| 進背景／返回 | 關閉播放並釋放解碼圖片與下載工作，保留來源選擇與快取；返回後本機或有效 Drive 來源自動恢復，過期授權才需從齒輪手動重新連接。 |
| 舊相框與音樂 | 原數位相框、本機／USB 選取、YouTube 登入與播放仍可使用。 |

測試完成前，不能宣稱第 0 階段通過，也不能進入規畫文件第 8 節的舊相框移除階段。
