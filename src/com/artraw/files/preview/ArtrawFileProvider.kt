package com.artraw.files.preview

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.artraw.files.util.MimeTypes
import java.io.File
import java.io.FileNotFoundException

class ArtrawFileProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.artraw.files.fileprovider"

        fun getUriForFile(file: File): Uri {
            val encodedPath = Uri.encode(file.absolutePath)
            return Uri.parse("content://$AUTHORITY/root$encodedPath")
        }

        fun getFileForUri(uri: Uri): File {
            val path = uri.path ?: throw FileNotFoundException("Missing URI path")
            val cleanPath = if (path.startsWith("/root/")) {
                path.substring("/root".length)
            } else if (path.startsWith("/root")) {
                path.substring("/root".length)
            } else {
                path
            }
            val decoded = Uri.decode(cleanPath)
            return File(decoded)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = getFileForUri(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val matrix = MatrixCursor(cols, 1)
        val row = matrix.newRow()
        for (col in cols) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return matrix
    }

    override fun getType(uri: Uri): String {
        val file = getFileForUri(uri)
        return MimeTypes.getMimeType(file)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = getFileForUri(uri)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: ${file.absolutePath}")
        }
        val fileMode = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w" -> ParcelFileDescriptor.MODE_WRITE_ONLY
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, fileMode)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
