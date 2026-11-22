package com.metrolist.music.viewmodels

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.OutputStream

class BackupRestoreFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test(expected = IOException::class)
    fun `null backup output stream fails`() {
        val source = temporaryFolder.newFile("settings.preferences_pb").apply { writeText("settings") }

        writeBackupArchive(
            destination = null,
            entries = listOf(BackupArchiveEntry("settings.preferences_pb", source)),
        )
    }

    @Test(expected = IOException::class)
    fun `backup write failure is propagated`() {
        val source = temporaryFolder.newFile("database.db").apply { writeText("database") }
        val failingOutput =
            object : OutputStream() {
                override fun write(value: Int) {
                    throw IOException("simulated write failure")
                }
            }

        writeBackupArchive(
            destination = failingOutput,
            entries = listOf(BackupArchiveEntry("database.db", source)),
        )
    }

    @Test(expected = IOException::class)
    fun `null restore input stream fails`() {
        extractBackupArchive(
            source = null,
            destinations = mapOf("database.db" to temporaryFolder.newFile("restored.db")),
        )
    }
}
