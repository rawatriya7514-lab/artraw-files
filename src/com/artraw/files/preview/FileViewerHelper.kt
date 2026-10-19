package com.artraw.files.preview

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.artraw.files.model.FileItem
import com.artraw.files.ui.TextPreviewActivity
import com.artraw.files.util.MimeTypes
import java.io.File

object FileViewerHelper {

    fun openFile(activity: Activity, fileItem: FileItem) {
        val file = fileItem.file
        if (!file.exists()) {
            Toast.makeText(activity, "File does not exist.", Toast.LENGTH_SHORT).show()
            return
        }

        val category = fileItem.category
        val ext = fileItem.extension.lowercase(java.util.Locale.ROOT)

        // 1. Built-in Music & Audio Player
        if (category == com.artraw.files.model.FileCategory.AUDIO) {
            try {
                val intent = Intent(activity, com.artraw.files.ui.AudioPlayerActivity::class.java).apply {
                    putExtra(com.artraw.files.ui.AudioPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                }
                activity.startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(activity, "Error opening audio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 2. Built-in Video Player
        if (category == com.artraw.files.model.FileCategory.VIDEOS) {
            try {
                val intent = Intent(activity, com.artraw.files.ui.VideoPlayerActivity::class.java).apply {
                    putExtra(com.artraw.files.ui.VideoPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                }
                activity.startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(activity, "Error opening video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 3. Built-in Photo & Image Gallery Viewer
        if (category == com.artraw.files.model.FileCategory.IMAGES) {
            try {
                val intent = Intent(activity, com.artraw.files.ui.ImageViewerActivity::class.java).apply {
                    putExtra(com.artraw.files.ui.ImageViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                }
                activity.startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(activity, "Error opening image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 4. Built-in ZArchiver-grade Archive Viewer & Extractor
        if (ext == "zip" || ext == "tar" || ext == "gz" || ext == "tgz" || ext == "jar") {
            try {
                val intent = Intent(activity, com.artraw.files.ui.ArchiveViewerActivity::class.java).apply {
                    putExtra(com.artraw.files.ui.ArchiveViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                }
                activity.startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(activity, "Error opening archive: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 5. Built-in Text & Code Viewer
        if (MimeTypes.isPreviewableText(file) && file.length() < 5L * 1024 * 1024) {
            try {
                val intent = Intent(activity, TextPreviewActivity::class.java).apply {
                    putExtra(TextPreviewActivity.EXTRA_FILE_PATH, file.absolutePath)
                }
                activity.startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(activity, "Error opening text file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 6. External App Handler using Secure FileProvider
        val uri = ArtrawFileProvider.getUriForFile(file)
        val mime = fileItem.mimeType

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            activity.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            val ext = fileItem.extension.uppercase()
            Toast.makeText(
                activity,
                "No compatible app installed to open $ext files.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFiles(context: Context, items: List<FileItem>) {
        if (items.isEmpty()) return

        if (items.size == 1) {
            val file = items[0].file
            val uri = ArtrawFileProvider.getUriForFile(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = items[0].mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share file"))
        } else {
            val uris = ArrayList<Uri>()
            for (item in items) {
                uris.add(ArtrawFileProvider.getUriForFile(item.file))
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share files"))
        }
    }

    fun showFileDetails(activity: Activity, item: FileItem) {
        val msg = buildString {
            append("Name: ${item.name}\n\n")
            append("Path: ${item.file.absolutePath}\n\n")
            append("Type: ${if (item.isDirectory) "Folder" else item.mimeType}\n\n")
            append("Size: ${com.artraw.files.util.Formatters.formatFileSize(item.size)}\n\n")
            append("Modified: ${com.artraw.files.util.Formatters.formatDate(item.lastModified)}\n")
            if (item.isApk) {
                append("\nPackage: ${item.apkPackageName ?: "Unknown"}\n")
                append("Version: ${item.apkVersionName ?: "Unknown"}\n")
            }
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle(com.artraw.files.R.string.action_details)
            .setMessage(msg)
            .setPositiveButton(com.artraw.files.R.string.btn_ok, null)
            .setNeutralButton(com.artraw.files.R.string.action_copy_path) { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("File Path", item.file.absolutePath)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(activity, "Path copied: ${item.file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
