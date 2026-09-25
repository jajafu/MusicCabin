package com.metrolist.music.photo

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class FrameDocument(val uri: String, val name: String, val mimeType: String) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    val isImage: Boolean get() = mimeType.startsWith("image/", ignoreCase = true)
}

internal class InvalidFrameImageException : IOException()

internal interface FrameDocumentAccess {
    fun hasPersistedRead(uri: Uri): Boolean
    fun persistRead(uri: Uri): Boolean
    suspend fun picked(uri: Uri): FrameDocument
    suspend fun folder(uri: Uri): FrameDocument
    suspend fun children(treeUri: Uri, directoryUri: String): List<FrameDocument>
}

internal class AndroidFrameDocumentAccess(
    private val context: Context,
    private val allowTvImports: Boolean = false,
) : FrameDocumentAccess {
    private val resolver = context.contentResolver
    private var directRoots = emptyList<File>()
    private var directRootsExpiresAt = 0L

    override fun hasPersistedRead(uri: Uri): Boolean = uri.scheme == ContentResolver.SCHEME_FILE ||
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    override fun persistRead(uri: Uri): Boolean = if (uri.scheme == ContentResolver.SCHEME_FILE) true else try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    override suspend fun picked(uri: Uri): FrameDocument {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return pickedDirectFile(uri)
        requireContentUri(uri)
        val type = resolver.getType(uri) ?: throw InvalidFrameImageException()
        if (!type.startsWith("image/", ignoreCase = true)) throw InvalidFrameImageException()
        return queryCancellable { signal ->
            resolver.openAssetFileDescriptor(uri, "r", signal)?.use { } ?: throw FileNotFoundException()
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, signal)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            FrameDocument(uri.toString(), name ?: uri.lastPathSegment.orEmpty(), type)
        }
    }

    override suspend fun folder(uri: Uri): FrameDocument {
        requireContentUri(uri)
        if (!DocumentsContract.isTreeUri(uri)) throw IllegalArgumentException("Not a tree URI")
        val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        return queryCancellable { signal ->
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null, signal)?.use { cursor ->
                if (!cursor.moveToFirst()) throw FileNotFoundException()
                FrameDocument(document.toString(), cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty())
                    .also { if (!it.isDirectory) throw IOException("Selected document is not a folder") }
            } ?: throw FileNotFoundException()
        }
    }

    override suspend fun children(treeUri: Uri, directoryUri: String): List<FrameDocument> {
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(directoryUri.toUri()),
        )
        return queryCancellable { signal ->
            resolver.query(
                childUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null,
                null,
                null,
                signal,
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        signal.throwIfCanceled()
                        val documentId = cursor.getString(0) ?: continue
                        add(
                            FrameDocument(
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                                cursor.getString(1).orEmpty(),
                                cursor.getString(2).orEmpty(),
                            ),
                        )
                    }
                }
            } ?: throw FileNotFoundException()
        }
    }

    private fun requireContentUri(uri: Uri) {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) { "Photo selection must use a content URI" }
    }

    private fun pickedDirectFile(uri: Uri): FrameDocument {
        val file = uri.path?.let(::File)?.canonicalFile ?: throw InvalidFrameImageException()
        val roots = if (allowTvImports) currentDirectRoots() + tvPhotoImportsDirectory(context) else currentDirectRoots()
        val allowed = roots.any { root -> isFileWithinRoot(file, root) }
        if (!allowed || !file.isFile || !file.canRead() || !isSupportedDirectImage(file)) {
            throw FileNotFoundException()
        }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?.takeIf { it.startsWith("image/", ignoreCase = true) }
            ?: "image/${file.extension.lowercase()}"
        return FrameDocument(uri.toString(), file.name, mimeType)
    }

    private fun currentDirectRoots(): List<File> {
        val now = SystemClock.elapsedRealtime()
        if (now >= directRootsExpiresAt) {
            directRoots = mountedDirectStorageRoots(context)
            directRootsExpiresAt = now + DirectRootCacheMillis
        }
        return directRoots
    }

    private suspend fun <T> queryCancellable(block: (CancellationSignal) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                val result = block(signal)
                if (continuation.isActive) continuation.resume(result)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
}

private const val DirectRootCacheMillis = 5_000L

internal fun isFileWithinRoot(file: File, root: File): Boolean {
    val checkedFile = runCatching { file.canonicalFile }.getOrNull() ?: return false
    val checkedRoot = runCatching { root.canonicalFile }.getOrNull() ?: return false
    return checkedFile == checkedRoot || checkedFile.path.startsWith(checkedRoot.path + File.separator)
}

// Keep the existing directory so TV upgrades can reuse received photos without copying them.
internal fun tvPhotoImportsDirectory(context: Context) = File(context.filesDir, "photo_frame_v2/tv_imports")

internal suspend fun scanFrameFolder(
    sourceUri: String,
    rootDocumentUri: String,
    children: suspend (String) -> List<FrameDocument>,
    progress: (Int) -> Unit,
): List<FramePhoto> {
    val directories = ArrayDeque<String>().apply { add(rootDocumentUri) }
    val visited = hashSetOf<String>()
    val photos = linkedMapOf<String, FramePhoto>()
    while (directories.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val directory = directories.removeFirst()
        if (!visited.add(directory)) continue
        for (document in children(directory)) {
            currentCoroutineContext().ensureActive()
            when {
                document.isDirectory -> directories.addLast(document.uri)
                document.isImage -> photos.putIfAbsent(document.uri, FramePhoto(document.uri, sourceUri))
            }
            if (photos.isNotEmpty() && photos.size % 100 == 0) progress(photos.size)
        }
        progress(photos.size)
    }
    return photos.values.toList()
}
