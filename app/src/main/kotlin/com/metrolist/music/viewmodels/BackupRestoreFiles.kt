/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class BackupArchiveEntry(
    val name: String,
    val source: File,
)

internal fun writeBackupArchive(
    destination: OutputStream?,
    entries: List<BackupArchiveEntry>,
) {
    val output = destination ?: throw IOException("Could not open backup destination")
    require(entries.isNotEmpty()) { "Backup has no entries" }

    output.buffered().use { buffered ->
        ZipOutputStream(buffered).use { zip ->
            entries.forEach { entry ->
                require(entry.source.isFile && entry.source.length() > 0L) {
                    "Backup source is missing or empty: ${entry.source}"
                }
                zip.putNextEntry(ZipEntry(entry.name))
                entry.source.inputStream().buffered().use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
    }
}

internal fun extractBackupArchive(
    source: InputStream?,
    destinations: Map<String, File>,
): Set<String> {
    val input = source ?: throw IOException("Could not open backup source")
    val extractedEntries = mutableSetOf<String>()

    input.buffered().use { buffered ->
        ZipInputStream(buffered).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val destination = destinations[entry.name]
                if (destination != null) {
                    require(extractedEntries.add(entry.name)) {
                        "Backup contains duplicate entry: ${entry.name}"
                    }
                    destination.parentFile?.mkdirs()
                    destination.outputStream().buffered().use { output ->
                        zip.copyTo(output)
                    }
                    require(destination.length() > 0L) {
                        "Backup entry is empty: ${entry.name}"
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    require(extractedEntries.isNotEmpty()) { "Backup contains no supported entries" }
    return extractedEntries
}
