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

        // Check if internal text viewer can preview
        if (MimeTypes.isPreviewableText(file) && file.length() < 5L * 1024 * 1024) {
            val intent = Intent(activity, TextPreviewActivity::class.java).apply {
                putExtra(TextPreviewActivity.EXTRA_FILE_PATH, file.absolutePath)
            }
            activity.startActivity(intent)
            return
        }

        // Open with external app using ArtrawFileProvider
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
}
