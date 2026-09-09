/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.innertube.utils.sha1
import com.metrolist.music.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.extensions.div
import com.metrolist.music.extensions.tryOrNull
import com.metrolist.music.extensions.zipInputStream
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicService.Companion.PERSISTENT_AUTOMIX_FILE
import com.metrolist.music.playback.MusicService.Companion.PERSISTENT_PLAYER_STATE_FILE
import com.metrolist.music.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.ArtistNameAliases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject

data class BackupPreviewInfo(
    val hasAuthData: Boolean = false,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val accountImageUrl: String? = null,
    val cookie: String? = null,
)

data class CsvImportState(
    val previewRows: List<List<String>> = emptyList(),
    val artistColumnIndex: Int = 0,
    val titleColumnIndex: Int = 1,
    val urlColumnIndex: Int = -1,
    val hasHeader: Boolean = true,
)

data class ConvertedSongLog(
    val title: String,
    val artists: String,
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    fun backup(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    database.checkpoint()
                    val dbPath =
                        database.openHelper.writableDatabase.path
                            ?: error("Database path is unavailable")
                    val settingsFile = appContext.filesDir / "datastore" / SETTINGS_FILENAME
                    val databaseFile = File(dbPath)
                    val aliasesFile = File(appContext.cacheDir, ArtistNameAliases.BACKUP_FILENAME)
                    aliasesFile.writeText(ArtistNameAliases.serialize())
                    val entries =
                        buildList {
                            add(BackupArchiveEntry(SETTINGS_FILENAME, settingsFile))
                            add(BackupArchiveEntry(ArtistNameAliases.BACKUP_FILENAME, aliasesFile))
                            add(BackupArchiveEntry(InternalDatabase.DB_NAME, databaseFile))
                            File("$dbPath-wal")
                                .takeIf { it.isFile && it.length() > 0L }
                                ?.let { add(BackupArchiveEntry("${InternalDatabase.DB_NAME}-wal", it)) }
                            File("$dbPath-shm")
                                .takeIf { it.isFile && it.length() > 0L }
                                ?.let { add(BackupArchiveEntry("${InternalDatabase.DB_NAME}-shm", it)) }
                        }
                    writeBackupArchive(
                        destination = appContext.contentResolver.openOutputStream(uri),
                        entries = entries,
                    )
                }

            withContext(Dispatchers.Main) {
                result.onSuccess {
                    Toast.makeText(appContext, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    reportException(error)
                    Toast.makeText(appContext, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun restore(context: Context, uri: Uri, clearAuthData: Boolean = false) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val restoreDirectory = File(appContext.cacheDir, "backup_restore")
            val restoreDbName = "restored_${InternalDatabase.DB_NAME}"
            val extractedSettings = File(restoreDirectory, SETTINGS_FILENAME)
            val extractedAliases = File(restoreDirectory, ArtistNameAliases.BACKUP_FILENAME)
            val restoredDatabase = appContext.getDatabasePath(restoreDbName)
            val actualSettings = appContext.filesDir / "datastore" / SETTINGS_FILENAME
            val stagedSettings =
                appContext.filesDir / "datastore" / "settings.restore_staged.preferences_pb"
            var stagedDatabase: File? = null
            var restartRequired = false
            var incompatibleDatabase = false

            val result =
                runCatching {
                    Timber.tag("RESTORE").i("Starting restore from URI: $uri, clearAuthData: $clearAuthData")
                    restoreDirectory.deleteRecursively()
                    check(restoreDirectory.mkdirs()) { "Could not create restore staging directory" }
                    deleteFileOrThrow(restoredDatabase)
                    deleteFileOrThrow(File("${restoredDatabase.absolutePath}-wal"))
                    deleteFileOrThrow(File("${restoredDatabase.absolutePath}-shm"))
                    stagedSettings.delete()

                    val extractedEntries =
                        extractBackupArchive(
                            source = appContext.contentResolver.openInputStream(uri),
                            destinations =
                                mapOf(
                                    SETTINGS_FILENAME to extractedSettings,
                                    ArtistNameAliases.BACKUP_FILENAME to extractedAliases,
                                    InternalDatabase.DB_NAME to restoredDatabase,
                                ),
                        )
                    val foundDatabase = InternalDatabase.DB_NAME in extractedEntries
                    val foundSettings = SETTINGS_FILENAME in extractedEntries
                    val foundAliases = ArtistNameAliases.BACKUP_FILENAME in extractedEntries
                    val currentDbPath =
                        database.openHelper.writableDatabase.path
                            ?: error("Database path is unavailable")
                    val currentDatabase = File(currentDbPath)
                    stagedDatabase = File("$currentDbPath.restore_staged")

                    if (foundDatabase) {
                        val backupDbVersion = InternalDatabase.readDatabaseVersion(restoredDatabase.absolutePath)
                        val currentDbVersion = database.openHelper.writableDatabase.version
                        if (backupDbVersion <= 0 || backupDbVersion > currentDbVersion) {
                            incompatibleDatabase = true
                            error(
                                "Backup database version $backupDbVersion is incompatible with current version $currentDbVersion",
                            )
                        }
                        validateAndMigrateRestoredDatabase(appContext, restoreDbName)
                        copyFileVerified(restoredDatabase, checkNotNull(stagedDatabase))
                    }

                    if (foundSettings) {
                        prepareSettingsStage(
                            source = extractedSettings,
                            stagedSettings = stagedSettings,
                            clearAuthData = clearAuthData,
                        )
                    }

                    val replacements =
                        buildList {
                            if (foundDatabase) {
                                add(StagedFileReplacement(checkNotNull(stagedDatabase), currentDatabase))
                            }
                            if (foundSettings) {
                                add(StagedFileReplacement(stagedSettings, actualSettings))
                            }
                        }

                    restartRequired = true
                    appContext.stopService(Intent(appContext, MusicService::class.java))
                    withTimeout(MUSIC_SERVICE_SHUTDOWN_TIMEOUT_MS) {
                        MusicService.shutdownDeferred.await()
                    }

                    if (foundDatabase) {
                        database.checkpoint()
                        database.close()
                        deleteFileOrThrow(File("$currentDbPath-wal"))
                        deleteFileOrThrow(File("$currentDbPath-shm"))
                    }
                    promoteStagedFiles(replacements)

                    appContext.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
                    appContext.filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete()
                    appContext.filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete()
                    if (foundAliases) {
                        ArtistNameAliases.restore(
                            appContext,
                            ArtistNameAliases.deserialize(extractedAliases.readText()),
                        )
                    }
                }

            restoreDirectory.deleteRecursively()
            restoredDatabase.delete()
            File("${restoredDatabase.absolutePath}-wal").delete()
            File("${restoredDatabase.absolutePath}-shm").delete()
            stagedDatabase?.delete()
            stagedSettings.delete()

            result.onFailure { error ->
                reportException(error)
                Timber.tag("RESTORE").e(error, "Restore failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        if (incompatibleDatabase) {
                            R.string.restore_database_incompatible
                        } else {
                            R.string.restore_failed
                        },
                        if (incompatibleDatabase) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            if (result.isSuccess || restartRequired) {
                restartApplication(appContext)
            }
        }
    }

    private fun validateAndMigrateRestoredDatabase(
        context: Context,
        databaseName: String,
    ) {
        val stagedDatabase = InternalDatabase.newInternalDatabaseInstance(context, databaseName)
        try {
            stagedDatabase.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)")
                .close()
        } finally {
            stagedDatabase.close()
        }
        val databasePath = context.getDatabasePath(databaseName).absolutePath
        File("$databasePath-wal").delete()
        File("$databasePath-shm").delete()
    }

    private suspend fun prepareSettingsStage(
        source: File,
        stagedSettings: File,
        clearAuthData: Boolean,
    ) {
        copyFileVerified(source, stagedSettings)
        val dataStoreScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        try {
            val stageDataStore =
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                    scope = dataStoreScope,
                ) {
                    stagedSettings
                }
            stageDataStore.data.first()
            if (clearAuthData) {
                stageDataStore.edit { preferences ->
                    preferences.remove(InnerTubeCookieKey)
                    preferences.remove(VisitorDataKey)
                    preferences.remove(DataSyncIdKey)
                    preferences.remove(InnerTubeAuthUserKey)
                }
            }
        } finally {
            dataStoreScope.cancel()
        }
    }

    private suspend fun restartApplication(context: Context) {
        withContext(Dispatchers.Main) {
            context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }?.let(context::startActivity)
        }
        Runtime.getRuntime().exit(0)
    }

    private fun deleteFileOrThrow(file: File) {
        if (file.exists() && !file.delete()) {
            error("Could not remove stale restore file: $file")
        }
    }

    suspend fun previewBackup(
        context: Context,
        uri: Uri,
    ): BackupPreviewInfo = withContext(Dispatchers.IO) {
        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use { raw ->
                raw.zipInputStream().use { inputStream ->
                    var entry = tryOrNull { inputStream.nextEntry }
                    while (entry != null) {
                        if (entry.name == SETTINGS_FILENAME) {
                            val bytes = inputStream.readUpTo(MAX_SETTINGS_PREVIEW_BYTES)
                            val content = bytes.decodeToString(throwOnInvalidSequence = false)

                            // Check for auth data (SAPISID cookie indicates logged in)
                            val hasAuthData = content.contains("SAPISID=")

                            // Extract cookie string from backup
                            val cookie = if (hasAuthData) {
                                extractCookieFromPrefs(content)
                            } else null

                            val accountName = if (hasAuthData) {
                                extractAccountNameFromPrefs(content) ?: "YouTube Account"
                            } else null

                            return@runCatching BackupPreviewInfo(
                                hasAuthData = hasAuthData,
                                accountName = accountName,
                                accountEmail = null,
                                accountImageUrl = null,
                                cookie = cookie,
                            )
                        }
                        entry = tryOrNull { inputStream.nextEntry }
                    }
                }
            }
            BackupPreviewInfo()
        }.getOrElse {
            Timber.tag("BACKUP_PREVIEW").e(it, "Failed to preview backup")
            BackupPreviewInfo()
        }
    }

    private fun extractAccountNameFromPrefs(content: String): String? {
        val sessionPattern = Regex("""(?:SESSION_INDEX|sessionIndex|session)_?\s*["':]*\s*(\d+)""")
        val index = sessionPattern.find(content)?.groupValues?.getOrNull(1)
        return if (index != null) "Account #$index" else null
    }

    private fun extractCookieFromPrefs(content: String): String? {
        // Find innerTubeCookie key and extract the cookie value.
        // The proto format has the key followed by type markers and then the string value.
        val keyMarker = "innerTubeCookie"
        val keyIndex = content.indexOf(keyMarker)
        if (keyIndex == -1) return null

        val afterKey = content.substring(keyIndex + keyMarker.length)

        // Cookie starts after some proto markers and contains semicolon-separated values.
        // Look for the first cookie key pattern like "__Secure-" or "HSID=" etc.
        val cookiePatterns = listOf("__Secure-", "HSID=", "SSID=", "SID=", "SAPISID=")
        var cookieStart = -1
        for (pattern in cookiePatterns) {
            val idx = afterKey.indexOf(pattern)
            if (idx != -1 && (cookieStart == -1 || idx < cookieStart)) {
                cookieStart = idx
            }
        }
        if (cookieStart == -1) return null

        // Find the end of the cookie (next control character or next key).
        val cookieContent = afterKey.substring(cookieStart)
        val cookieEnd = cookieContent.indexOfFirst {
            it.code < 32 && it != '\t' && it != '\n' && it != '\r'
        }

        val rawCookie = if (cookieEnd > 0) {
            cookieContent.substring(0, cookieEnd)
        } else {
            cookieContent.take(5000) // Reasonable max length
        }
        // Remove any control characters (newlines, etc.) that are invalid in HTTP headers.
        return rawCookie.replace(Regex("[\\x00-\\x1F\\x7F]"), "").trim()
    }

    suspend fun fetchAccountInfoFromBackup(cookie: String): BackupPreviewInfo? {
        return runCatching {
            // Parse cookie to get SAPISID for auth header
            val cookieMap = parseCookieString(cookie)
            val sapisid = cookieMap["SAPISID"] ?: return@runCatching null

            // Generate SAPISIDHASH auth header
            val origin = "https://music.youtube.com"
            val currentTime = System.currentTimeMillis() / 1000
            val sapisidHash = sha1("$currentTime $sapisid $origin")
            val authHeader = "SAPISIDHASH ${currentTime}_$sapisidHash"

            val client = OkHttpClient()
            val requestBody = """{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20240101.01.00"}}}"""
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false")
                .post(requestBody)
                .header("Cookie", cookie)
                .header("Authorization", authHeader)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@runCatching null

            // Parse the JSON response
            val json = Json { ignoreUnknownKeys = true }
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            // Navigate to activeAccountHeaderRenderer
            val header = jsonResponse["actions"]
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("openPopupAction")
                ?.jsonObject?.get("popup")
                ?.jsonObject?.get("multiPageMenuRenderer")
                ?.jsonObject?.get("header")
                ?.jsonObject?.get("activeAccountHeaderRenderer")
                ?.jsonObject

            if (header != null) {
                val name = header["accountName"]
                    ?.jsonObject?.get("runs")
                    ?.jsonArray?.getOrNull(0)
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content

                val email = header["email"]
                    ?.jsonObject?.get("runs")
                    ?.jsonArray?.getOrNull(0)
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content

                val thumbnailUrl = header["accountPhoto"]
                    ?.jsonObject?.get("thumbnails")
                    ?.jsonArray?.getOrNull(0)
                    ?.jsonObject?.get("url")
                    ?.jsonPrimitive?.content

                if (name != null) {
                    BackupPreviewInfo(
                        hasAuthData = true,
                        accountName = name,
                        accountEmail = email,
                        accountImageUrl = thumbnailUrl,
                        cookie = cookie,
                    )
                } else null
            } else null
        }.getOrElse {
            Timber.tag("BACKUP_PREVIEW").e(it, "Failed to fetch account info from backup")
            null
        }
    }

    suspend fun previewCsvFile(
        context: Context,
        uri: Uri,
    ): CsvImportState = withContext(Dispatchers.IO) {
        runCatching {
            val lines =
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().useLines { sequence ->
                        sequence.take(CSV_PREVIEW_LINE_COUNT).toList()
                    }
                }.orEmpty()
            CsvImportState(
                previewRows = lines.map(::parseCsvLine),
                hasHeader = lines.firstOrNull()?.contains(",") == true,
            )
        }.getOrElse {
            reportException(it)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to preview CSV file", Toast.LENGTH_SHORT).show()
            }
            CsvImportState()
        }
    }

    suspend fun importPlaylistFromCsv(
        context: Context,
        uri: Uri,
        columnMapping: CsvImportState,
        onProgress: (Int) -> Unit = {},
        onLogUpdate: (List<ConvertedSongLog>) -> Unit = {},
    ): ArrayList<Song> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val songs = arrayListOf<Song>()
        val recentLogs = mutableListOf<ConvertedSongLog>()

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                val startIndex = if (columnMapping.hasHeader) 1 else 0
                val totalLines = lines.size - startIndex

                lines.drop(startIndex).forEachIndexed { index, line ->
                    val parts = parseCsvLine(line)

                    if (parts.isNotEmpty()) {
                        if (columnMapping.artistColumnIndex < parts.size && columnMapping.titleColumnIndex < parts.size) {
                            val title = parts[columnMapping.titleColumnIndex].trim()
                            val artistStr = parts[columnMapping.artistColumnIndex].trim()

                            if (title.isNotEmpty() && artistStr.isNotEmpty()) {
                                val artists = artistStr.split(";", ",").map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .map { ArtistEntity(id = "", name = it) }

                                val mockSong = Song(
                                    song = SongEntity(
                                        id = "",
                                        title = title,
                                    ),
                                    artists = artists,
                                )
                                songs.add(mockSong)

                                val logEntry = ConvertedSongLog(
                                    title = title,
                                    artists = artists.joinToString(", ") { it.name },
                                )
                                recentLogs.add(0, logEntry)
                                if (recentLogs.size > 3) {
                                    recentLogs.removeAt(recentLogs.size - 1)
                                }
                                onLogUpdate(recentLogs.toList())
                            }
                        }
                    }

                    val progress = ((index + 1) * 100) / totalLines
                    onProgress(progress)
                }
            }
        }.onFailure {
            reportException(it)
        }

        songs
    }

    suspend fun importPlaylistFromCsv(context: Context, uri: Uri): ArrayList<Song> {
        return importPlaylistFromCsv(context, uri, CsvImportState())
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result.map { it.trim().trim('"') }
    }

    suspend fun loadM3UOnline(
        context: Context,
        uri: Uri,
    ): ArrayList<Song> = withContext(Dispatchers.IO) {
        val songs = ArrayList<Song>()

        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().useLines { lines ->
                    var validHeader = false
                    lines.forEachIndexed { index, rawLine ->
                        if (index == 0) {
                            validHeader = rawLine.startsWith("#EXTM3U")
                        } else if (validHeader && rawLine.startsWith("#EXTINF:")) {
                            val artists =
                                rawLine.substringAfter("#EXTINF:").substringAfter(',').substringBefore(" - ").split(';')
                            val title = rawLine.substringAfter("#EXTINF:").substringAfter(',').substringAfter(" - ")

                            val mockSong = Song(
                                song = SongEntity(
                                    id = "",
                                    title = title,
                                ),
                                artists = artists.map { ArtistEntity("", it) },
                            )
                            songs.add(mockSong)
                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "No songs found. Invalid file, or perhaps no song matches were found.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        songs
    }

    private fun InputStream.readUpTo(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (output.size() < maxBytes) {
            val bytesRead = read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
            if (bytesRead <= 0) break
            output.write(buffer, 0, bytesRead)
        }
        return output.toByteArray()
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
        private const val CSV_PREVIEW_LINE_COUNT = 6
        private const val MAX_SETTINGS_PREVIEW_BYTES = 5 * 1024 * 1024
        private const val MUSIC_SERVICE_SHUTDOWN_TIMEOUT_MS = 5_000L
    }
}
