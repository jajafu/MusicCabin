package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RestoreStagingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun staging(): File =
        temporaryFolder.newFolder("restore_staging").apply {
            File(this, "song.db").writeText("new-db")
            File(this, SETTINGS_FILENAME).writeText("new-settings")
            File(this, "artist_name_aliases.json").writeText("""{"artist":"alias"}""")
            markRestoreReady(this)
        }

    private fun targets(): Pair<File, File> {
        val directory = temporaryFolder.newFolder("live")
        return File(directory, "song.db") to File(directory, SETTINGS_FILENAME)
    }

    @Test
    fun `staged files replace live files and are consumed`() {
        val staging = staging()
        val (database, settings) = targets().let { (database, settings) ->
            database.writeText("old-db")
            settings.writeText("old-settings")
            database to settings
        }

        val applied =
            promoteRestoreStaging(
                stagingDirectory = staging,
                databaseTarget = database,
                settingsTarget = settings,
            )

        assertTrue(applied)
        assertEquals("new-db", database.readText())
        assertEquals("new-settings", settings.readText())
        // Promoted files leave the staging, so a retry cannot apply them twice.
        assertFalse(File(staging, "song.db").exists())
        assertFalse(File(staging, SETTINGS_FILENAME).exists())
        assertTrue(File(staging, "artist_name_aliases.json").isFile)
    }

    @Test
    fun `stale wal and shm are removed before the database is replaced`() {
        val staging = staging()
        val (database, settings) = targets()
        val wal = File("${database.absolutePath}-wal").apply { writeText("old-wal") }
        val shm = File("${database.absolutePath}-shm").apply { writeText("old-shm") }

        promoteRestoreStaging(staging, database, settings)

        assertFalse(wal.exists())
        assertFalse(shm.exists())
        assertFalse(File("${wal.absolutePath}.restore_old").exists())
        assertEquals("new-db", database.readText())
    }

    @Test
    fun `incomplete staging is discarded and nothing is promoted`() {
        val staging = temporaryFolder.newFolder("restore_staging")
        File(staging, "song.db").writeText("partial-db")
        val (database, settings) = targets()
        database.writeText("old-db")
        settings.writeText("old-settings")

        val applied = promoteRestoreStaging(staging, database, settings)

        assertFalse(applied)
        assertFalse(staging.exists())
        assertEquals("old-db", database.readText())
        assertEquals("old-settings", settings.readText())
    }

    @Test
    fun `missing staging directory is a no-op`() {
        val (database, settings) = targets()

        val applied =
            promoteRestoreStaging(
                stagingDirectory = File(temporaryFolder.root, "absent"),
                databaseTarget = database,
                settingsTarget = settings,
            )

        assertFalse(applied)
    }

    @Test
    fun `failure keeps only the files that were not promoted`() {
        val staging = staging()
        val (database, settings) = targets()
        database.writeText("old-db")
        settings.writeText("old-settings")

        val result =
            runCatching {
                promoteRestoreStaging(
                    stagingDirectory = staging,
                    databaseTarget = database,
                    settingsTarget = settings,
                    moveFile = { source, target ->
                        if (target == settings) throw IOException("simulated promotion failure")
                        source.renameTo(target)
                    },
                )
            }

        assertTrue(result.isFailure)
        assertTrue(File(staging, "restore.ready").isFile)
        assertFalse(File(staging, "song.db").exists())
        assertTrue(File(staging, SETTINGS_FILENAME).isFile)
        assertEquals("new-db", database.readText())
        assertEquals("old-settings", settings.readText())
    }

    @Test
    fun `a retry after a partial promotion does not reapply the database`() {
        val staging = staging()
        val (database, settings) = targets()
        database.writeText("old-db")
        settings.writeText("old-settings")

        runCatching {
            promoteRestoreStaging(
                stagingDirectory = staging,
                databaseTarget = database,
                settingsTarget = settings,
                moveFile = { source, target ->
                    if (target == settings) throw IOException("simulated promotion failure")
                    source.renameTo(target)
                },
            )
        }

        // The user uses the app before the next start; that data must survive.
        database.writeText("data-added-after-the-failed-promotion")
        val applied = promoteRestoreStaging(staging, database, settings)

        assertTrue(applied)
        assertEquals("data-added-after-the-failed-promotion", database.readText())
        assertEquals("new-settings", settings.readText())
    }

    @Test
    fun `a failed move leaves the live database and its wal untouched`() {
        val staging = staging()
        val (database, settings) = targets()
        database.writeText("old-db")
        settings.writeText("old-settings")
        val wal = File("${database.absolutePath}-wal").apply { writeText("old-wal") }

        val result =
            runCatching {
                promoteRestoreStaging(
                    stagingDirectory = staging,
                    databaseTarget = database,
                    settingsTarget = settings,
                    moveFile = { _, _ -> throw IOException("simulated move failure") },
                )
            }

        assertTrue(result.isFailure)
        assertEquals("old-db", database.readText())
        assertTrue(wal.isFile)
        assertTrue(File(staging, "restore.ready").isFile)
        assertTrue(File(staging, "song.db").isFile)
    }
}
