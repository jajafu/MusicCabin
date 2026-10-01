/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.metrolist.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set
    
    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()
    
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    private const val STAGED_APK_PREFIX = "MusicCabin-"
    const val GITHUB_RELEASES_URL = "https://github.com/jajafu/MusicCabin/releases"
    private const val GITHUB_API_BASE = "https://api.github.com/repos/jajafu/MusicCabin"

    private fun versionParts(version: String): List<Int> {
        return Regex("\\d+")
            .findAll(version)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
    }

    /**
     * Compares two version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = versionParts(v1)
        val v2Parts = versionParts(v2)
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    /**
     * Checks if the latest version is newer than the current version.
     * Returns true if an update is available (latestVersion > currentVersion)
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    /**
     * Get the current app's architecture and variant
     */
    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = BuildConfig.ARCHITECTURE
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    /**
     * Parse release assets from GitHub API response
     */
    internal fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()
        
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            
            // Skip non-APK files
            if (!name.endsWith(".apk")) continue
            
            val downloadUrl = asset.getString("browser_download_url")
            val size = asset.getLong("size")
            
            // Parse architecture and variant from filename
            val (arch, variant) = when {
                name.startsWith("MusicCabin-v") && name.endsWith("-car.apk") -> "universal" to "foss"
                // Older published APKs remain available after the repository rename.
                name == "Metrolist.apk" -> "universal" to "foss"
                name == "Metrolist-with-Google-Cast.apk" -> "universal" to "gms"
                name.startsWith("Metrolist-AndroidCar-") && name.endsWith(".apk") -> "universal" to "foss"
                name.startsWith("app-") && name.endsWith("-release.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-release.apk")
                    arch to "foss"
                }
                name.startsWith("app-") && name.endsWith("-with-Google-Cast.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-with-Google-Cast.apk")
                    arch to "gms"
                }
                else -> null to null
            }
            
            if (arch != null && variant != null) {
                assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
            }
        }
        
        return assets
    }

    /**
     * Fetch latest release from GitHub API
     */
    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Return cached if available and not forcing refresh
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo!!
                }
                
                val response = client.get("$GITHUB_API_BASE/releases/latest")
                    .bodyAsText()
                val json = JSONObject(response)
                
                val releaseInfo = ReleaseInfo(
                    tagName = json.getString("tag_name"),
                    versionName = json.getString("name"),
                    description = json.getString("body"),
                    releaseDate = json.getString("published_at"),
                    assets = parseAssets(json.getJSONArray("assets"))
                )
                
                cachedReleaseInfo = releaseInfo
                lastCheckTime = System.currentTimeMillis()
                releaseInfo
            }
        }

    /**
     * Fetch all releases from GitHub API (paginated)
     */
    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }
                
                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                var hasMore = true
                
                while (hasMore && page <= 10) { // Limit to 10 pages
                    val response = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30")
                        .bodyAsText()
                    val json = JSONArray(response)
                    
                    if (json.length() == 0) {
                        hasMore = false
                        break
                    }
                    
                    for (i in 0 until json.length()) {
                        val releaseObj = json.getJSONObject(i)
                        releases.add(ReleaseInfo(
                            tagName = releaseObj.getString("tag_name"),
                            versionName = releaseObj.getString("name"),
                            description = releaseObj.getString("body"),
                            releaseDate = releaseObj.getString("published_at"),
                            assets = parseAssets(releaseObj.getJSONArray("assets"))
                        ))
                    }
                    
                    page++
                }
                
                cachedAllReleases = releases
                releases
            }
        }

    /**
     * Get the download URL for the correct app variant
     */
    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        val (currentArch, currentVariant) = getCurrentAppVariant()
        
        return releaseInfo.assets
            .find { it.architecture == currentArch && it.variant == currentVariant }
            ?.downloadUrl
    }

    /**
     * Get all available download URLs for a release
     */
    fun getAllDownloadUrls(releaseInfo: ReleaseInfo): Map<String, String> {
        return releaseInfo.assets.associate { "${it.architecture}-${it.variant}" to it.downloadUrl }
    }

    /**
     * Check if update is needed (respects 2-hour cache)
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Check if we should fetch (2 hour interval)
                val shouldFetch = forceRefresh || 
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS
                
                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        cachedReleaseInfo!!.versionName
                    )
                    return@runCatching cachedReleaseInfo!! to hasUpdate
                }
                
                val result = getLatestRelease(forceRefresh = true)
                if (result.isSuccess) {
                    val releaseInfo = result.getOrThrow()
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        releaseInfo.versionName
                    )
                    releaseInfo to hasUpdate
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            }
        }

    /**
     * Get the download URL for the correct app variant
     * Returns null if no matching asset is found
     */
    fun getLatestDownloadUrl(): String? {
        return cachedReleaseInfo?.let { getDownloadUrlForCurrentVariant(it) }
    }
    
    /**
     * Get the latest release info (cached)
     */
    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo

    /**
     * File used to stage a downloaded update APK. Kept under the app cache dir so
     * no storage permission is needed and FileProvider can share it with the installer.
     */
    fun updateApkFile(context: Context, versionName: String): File {
        val dir = File(context.externalCacheDir ?: context.cacheDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        val safeName = versionName.replace(Regex("[^A-Za-z0-9._-]+"), "_")
        return File(dir, "MusicCabin-$safeName.apk")
    }

    /**
     * Delete APKs staged for other versions, keeping [keep]. Prevents the update
     * cache from accumulating one full APK per release the user ever saw.
     */
    fun removeOtherStagedApks(keep: File) {
        val dir = keep.parentFile ?: return
        val prefix = STAGED_APK_PREFIX
        dir.listFiles()?.forEach { file ->
            if (file.name != keep.name && file.name.startsWith(prefix)) file.delete()
        }
    }

    /**
     * Download an APK with progress callbacks. Runs on IO dispatcher.
     * onProgress receives (downloadedBytes, totalBytesOrNull).
     */
    suspend fun downloadApk(
        url: String,
        destFile: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        val tmpFile = File(destFile.parentFile, "${destFile.name}.part")
        runCatching {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    setRequestProperty("Accept", "application/octet-stream")
                    connect()
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Download failed: HTTP $code")
                val total = connection.contentLengthLong.takeIf { it > 0 }
                connection.inputStream.use { input ->
                    tmpFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
                if (tmpFile.length() == 0L) throw IllegalStateException("Downloaded file is empty")
                if (destFile.exists()) destFile.delete()
                if (!tmpFile.renameTo(destFile)) {
                    tmpFile.copyTo(destFile, overwrite = true)
                    tmpFile.delete()
                }
                destFile
            } finally {
                connection?.disconnect()
            }
        }.onFailure {
            // Never leave a half-written staging file behind.
            runCatching { tmpFile.delete() }
        }
    }

    // minSdk is 26, so the API 26 unknown-sources APIs are always available.
    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Build an install intent for a staged APK. Returns null if the file is missing.
     * Caller should ensure [canInstallPackages] first and guide the user to
     * the unknown-sources setting otherwise.
     */
    fun buildInstallIntent(context: Context, apkFile: File): Intent? {
        if (!apkFile.exists() || apkFile.length() == 0L) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.FileProvider",
            apkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
