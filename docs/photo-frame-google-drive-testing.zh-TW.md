# 「相框KTV2」Google Drive 自行編譯與 OAuth 設定指南

本文件說明如何自行編譯含 Drive 功能的 GMS 版本，並設定個人 Google Cloud OAuth。

## 1. Google Cloud 設定

建立一個個人 Google Cloud 專案：

1. OAuth 同意畫面選 `External`，啟用 Google Drive API，把自己加入 test user，範圍只用 `drive.readonly`。
2. 建立 Android OAuth client，填入實際安裝建置的 application ID 與簽署憑證 SHA-1。用下列指令查詢：

   ```powershell
   ./gradlew :app:signingReport
   ```

   GMS Debug 預設套件為 `com.jajafu.musiccabin.gms.debug`；個人簽署的 GMS Release 為 `com.jajafu.musiccabin.gms`。兩者都要搭配該安裝版本的實際 SHA-1 註冊。
3. 不要建立 web client，也不要把任何 client secret 放入 APK；Drive 授權與 YouTube Music 登入帳戶分開，不要混用 Cookie。

## 2. Testing 轉 In production（個人自用）

Testing 狀態的授權七日到期；個人長期自用可轉為 `In production`，只改雲端設定，不用改 code、重編或重裝：

1. 到 `console.cloud.google.com` 切到同一個專案，進入 `Google Auth Platform`（舊版叫 `APIs & Services → OAuth consent screen`）。
2. 若「發布應用程式」不能按，先到「品牌」頁補完必填：應用程式名稱、使用者支援電子郵件、開發人員聯絡電子郵件、應用程式首頁與隱私權政策連結。個人自用不需要送品牌或範圍驗證。
3. 到「資料存取權」加入 `https://www.googleapis.com/auth/drive.readonly` 並儲存。
4. 回到「目標對象」按「發布應用程式」，狀態顯示正式版即完成。授權時出現「未驗證應用程式」警告屬正常，按繼續即可。
5. 轉完後在 App 內做一次「本機解除連接 → 連接 Google Drive」，用同一帳戶重新同意，舊的七日授權即被取代。

只有 APK 變體改變（如 debug 換 release）、簽署憑證更換或新增 scope 時，才需要補 Android OAuth client 並重裝。

## 3. 私人簽名（建議）

公開 Foss 版沿用專案 release key（無 Drive 功能）；自用 GMS 建議另建一把私人 key，兩邊簽名不同就不會互相用到對方的 OAuth 專案。私人 keystore 放在 repo 之外，不進版本控制；`app/build.gradle.kts` 已支援用環境變數指向它：

```powershell
$env:METROLIST_RELEASE_KEYSTORE_PATH="C:\path\to\private.keystore"
$env:STORE_PASSWORD="..."
$env:KEY_ALIAS="..."
$env:KEY_PASSWORD="..."
./gradlew :app:assembleGmsRelease
```

用 `keytool -list -v` 讀出私人 keystore 的 SHA-1，到 Cloud `Credentials` 新增一條 Android OAuth client（package `com.jajafu.musiccabin.gms`＋私人 SHA-1）。不要把共用 repo debug key 綁進私人專案，否則別人編 debug 也能用你的專案額度。

GMS Release（`com.jajafu.musiccabin.gms`）與 GMS Debug（`com.jajafu.musiccabin.gms.debug`）都不會與公開 Foss（`com.jajafu.musiccabin`）衝突，可並存。自用 GMS 沒有 App 內更新器，更新一律手動安裝 APK。

## 4. 編譯

```powershell
# 含 Drive 功能的 APK（使用者手動編譯安裝）
./gradlew :app:assembleGmsDebug
./gradlew :app:assembleGmsRelease

# 開發檢查：Kotlin 編譯與相框單元測試
./gradlew :app:compileGmsDebugKotlin :app:compileFossDebugKotlin :app:testGmsDebugUnitTest --tests 'com.metrolist.music.photo.v2.*' --tests 'com.metrolist.music.photo.*'
```

建置開關彼此獨立：`-PphotoFrameV2Enabled=false` 隱藏相框 2 入口；`-PphotoFrameDriveEnabled=false` 停用 Drive provider。

## 5. 連接與播放（冒煙確認）

1. 從主選單開啟「相框KTV2」，進齒輪選雲端來源，按「連接 Google Drive」並完成帳戶同意。
2. 瀏覽到要播放的資料夾，按「使用此資料夾播放」，即開始隨機全螢幕輪播（預設 10 秒，可選 5／10／15／30／60 秒）。
3. 授權過期或無網路時，已快取的照片會繼續離線輪播並顯示錯誤碼；需要重新授權時從齒輪手動重新連接。切換帳戶前先做「本機解除連接」。

測試時不要記錄 access token、Cookie 或 client secret。
