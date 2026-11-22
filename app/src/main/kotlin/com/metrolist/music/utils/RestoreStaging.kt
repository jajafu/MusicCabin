/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.playback.MusicService
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

const val SETTINGS_FILENAME = "settings.preferences_pb"

private const val RESTORE_STAGING_DIR = "restore_staging"
private const val RESTORE_READY_FILENAME = "restore.ready"

/**
 * Directory holding a fully validated backup until the next process start
 * promotes it. It must survive process death, so it lives under filesDir
 * rather than cacheDir.
 */
fun restoreStagingDirectory(context: Context): File = File(context.filesDir, RESTORE_STAGING_DIR)

fun discardRestoreStaging(context: Context) {
    restoreStagingDirectory(context).deleteRecursively()
}

/** Marks the staging as complete. Written last so a partial staging is never applied. */
fun markRestoreReady(stagingDirectory: File) {
    val readyFile = File(stagingDirectory, RESTORE_READY_FILENAME)
    // Synced like the staged files: a head unit may lose power at any moment,
    // and a marker that survives while its files do not would be worse than
    // no marker at all.
    FileOutputStream(readyFile).use { output ->
        output.write("ready".toByteArray())
        output.flush()
        output.fd.sync()
    }
}

fun copyFileVerified(
    source: File,
    target: File,
) {
    require(source.isFile && source.length() > 0L) {
        "Restore source is missing or empty: $source"
    }
    target.parentFile?.mkdirs()
    FileOutputStream(target).use { file ->
        val output = file.buffered()
        source.inputStream().buffered().use { input ->
            input.copyTo(output)
        }
        // Push the bytes to the device before the copy can be renamed into
        // place; otherwise a power loss could leave an empty file behind.
        output.flush()
        file.fd.sync()
    }
    require(target.isFile && target.length() == source.length()) {
        "Restore staging copy is incomplete: $target"
    }
}

/**
 * Renames [source] over [target], so the live file is never half written even
 * if the process dies during promotion.
 */
private fun moveIntoPlace(
    source: File,
    target: File,
) {
    target.parentFile?.mkdirs()
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

/**
 * Promotes a complete staging over the live files. Each staged file is moved,
 * not copied, so a crash during promotion simply retries the files that are
 * still staged; a file that already made it is never applied twice. Returns
 * false when the staging was incomplete and got discarded.
 */
internal fun promoteRestoreStaging(
    stagingDirectory: File,
    databaseTarget: File,
    settingsTarget: File,
    moveFile: (File, File) -> Unit = ::moveIntoPlace,
): Boolean {
    if (!stagingDirectory.isDirectory) return false
    if (!File(stagingDirectory, RESTORE_READY_FILENAME).isFile) {
        // The previous restore was interrupted before it finished staging.
        stagingDirectory.deleteRecursively()
        return false
    }

    val stagedDatabase = File(stagingDirectory, InternalDatabase.DB_NAME)
    if (stagedDatabase.isFile) {
        require(stagedDatabase.length() > 0L) { "Staged database is empty: $stagedDatabase" }
        // A leftover WAL/SHM belongs to the database being replaced; replaying
        // it against the restored file would corrupt it. They are moved aside
        // rather than deleted so a failed promotion can put them back.
        val sideFiles = moveDatabaseSideFilesAside(databaseTarget)
        try {
            moveFile(stagedDatabase, databaseTarget)
        } catch (error: Throwable) {
            restoreSideFiles(sideFiles)
            throw error
        }
        sideFiles.forEach { (_, aside) -> aside.delete() }
    }

    val stagedSettings = File(stagingDirectory, SETTINGS_FILENAME)
    if (stagedSettings.isFile) {
        require(stagedSettings.length() > 0L) { "Staged settings are empty: $stagedSettings" }
        moveFile(stagedSettings, settingsTarget)
    }

    return true
}

private fun moveDatabaseSideFilesAside(database: File): List<Pair<File, File>> {
    val moved = mutableListOf<Pair<File, File>>()
    try {
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val sideFile = File("${database.absolutePath}$suffix")
            if (!sideFile.exists()) continue
            val aside = File("${sideFile.absolutePath}.restore_old")
            aside.delete()
            Files.move(sideFile.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING)
            moved += sideFile to aside
        }
    } catch (error: Throwable) {
        restoreSideFiles(moved)
        throw error
    }
    return moved
}

private fun restoreSideFiles(moved: List<Pair<File, File>>) {
    moved.forEach { (original, aside) ->
        runCatching {
            Files.move(aside.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * Applies a restore that was staged before the last process restart. Must run
 * in Application.onCreate before the database or the settings DataStore are
 * opened.
 */
fun finishPendingRestore(context: Context) {
    val stagingDirectory = restoreStagingDirectory(context)
    if (!stagingDirectory.isDirectory) return

    val aliasesFile = File(stagingDirectory, ArtistNameAliases.BACKUP_FILENAME)
    val hasStagedFiles =
        File(stagingDirectory, InternalDatabase.DB_NAME).isFile ||
            File(stagingDirectory, SETTINGS_FILENAME).isFile ||
            aliasesFile.isFile
    if (!hasStagedFiles) {
        // Everything was promoted by an earlier start that died before cleaning up.
        stagingDirectory.deleteRecursively()
        return
    }

    val applied =
        promoteRestoreStaging(
            stagingDirectory = stagingDirectory,
            databaseTarget = context.getDatabasePath(InternalDatabase.DB_NAME),
            settingsTarget = File(context.filesDir, "datastore/$SETTINGS_FILENAME"),
        )
    if (!applied) return

    // A backup without aliases restores the empty set, so the result matches
    // the backup instead of keeping aliases that point at replaced data.
    val restoredAliases =
        if (aliasesFile.isFile) {
            ArtistNameAliases.deserialize(aliasesFile.readText())
        } else {
            emptyMap()
        }
    ArtistNameAliases.restore(context, restoredAliases)

    // Player state belongs to the replaced database and settings.
    context.filesDir.resolve(MusicService.PERSISTENT_QUEUE_FILE).delete()
    context.filesDir.resolve(MusicService.PERSISTENT_AUTOMIX_FILE).delete()
    context.filesDir.resolve(MusicService.PERSISTENT_PLAYER_STATE_FILE).delete()

    stagingDirectory.deleteRecursively()
    Timber.tag("Restore").i("Applied staged restore")
}
