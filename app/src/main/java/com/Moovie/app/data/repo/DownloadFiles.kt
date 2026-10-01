package com.Moovie.app.data.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.Moovie.app.data.model.DownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where finished downloads live and how they're cleaned up.
 *
 * Downloads are published into the shared **Movies/Moovie** gallery collection
 * via MediaStore, so they:
 *  - show up in the user's gallery / Files app,
 *  - survive app updates (they live outside the app's private data),
 *  - need no storage permissions (MediaStore writes on API 29+ are free).
 *
 * [DownloadItem.localPath] stores either a `content://` MediaStore URI
 * (API 29+) or an absolute file path (API 26-28 fallback / publish failure) —
 * both play directly in ExoPlayer via DefaultDataSource.
 */
class DownloadFiles(private val context: Context) {

    /**
     * Publishes the finished [src] file into the shared Movies gallery.
     *
     * Returns the playback path to store on the item: a `content://` MediaStore
     * URI on API 29+, or the original file path on older devices (where a
     * MediaStore row pointing at the private file is still inserted so gallery
     * apps can index it). Falls back to the file path if publishing fails.
     *
     * If the copy fails mid-way the half-written MediaStore row is removed, so
     * no partial entries are left in the gallery. Rows abandoned while
     * IS_PENDING (e.g. the app is killed mid-copy) are cleaned up by the
     * system after a week.
     */
    suspend fun publishToGallery(src: File, displayName: String): String =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + GALLERY_DIR)
                    values.put(MediaStore.Video.Media.IS_PENDING, 1)
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    // Legacy devices: no RELATIVE_PATH support — index the
                    // private file in place so gallery apps can still see it.
                    values.put(MediaStore.Video.Media.DATA, src.absolutePath)
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                val uri = resolver.insert(collection, values) ?: error("MediaStore insert returned null")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching {
                        resolver.openOutputStream(uri)?.use { out ->
                            src.inputStream().use { it.copyTo(out, COPY_BUFFER) }
                        } ?: error("No output stream for $uri")
                        values.clear()
                        values.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }.onFailure {
                        runCatching { resolver.delete(uri, null, null) }
                        throw it
                    }
                    uri.toString()
                } else {
                    // Legacy: the row points at the private file; playback
                    // keeps using the path directly.
                    src.absolutePath
                }
            }.getOrNull() ?: src.absolutePath
        }

    /** True when the stored [localPath] (content:// URI or file path) still exists. */
    fun exists(localPath: String?): Boolean {
        val path = localPath?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            if (path.startsWith("content:")) {
                context.contentResolver.query(
                    Uri.parse(path), arrayOf(MediaStore.Video.Media._ID), null, null, null,
                )?.use { it.count > 0 } ?: false
            } else {
                File(path).exists()
            }
        }.getOrDefault(false)
    }

    /** Deletes the file behind [localPath] (MediaStore row or plain file); ignores failures. */
    fun delete(localPath: String?) {
        val path = localPath?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            if (path.startsWith("content:")) {
                context.contentResolver.delete(Uri.parse(path), null, null)
            } else {
                File(path).delete()
            }
        }
    }

    /** Clean, gallery-friendly file name for a finished download. */
    fun displayNameFor(item: DownloadItem): String {
        val safe = item.titleName.substringBefore(" · ")
            .replace(Regex("[<>:\"/\\\\|?*]"), "")
            .trim().take(80).ifBlank { item.key }
        val se = item.season?.let { s -> item.episode?.let { e -> ".S${s}E$e" } } ?: ""
        return "$safe$se.mp4"
    }

    companion object {
        /** Sub-folder inside the shared Movies collection. */
        const val GALLERY_DIR = "Moovie"
        private const val COPY_BUFFER = 256 * 1024
    }
}
