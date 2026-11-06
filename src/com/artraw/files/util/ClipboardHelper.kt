package com.artraw.files.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import java.io.File

/**
 * Universal Clipboard & Path Superpower Utility for ArtFiles.
 *
 * Provides instantaneous 1-tap copying for file paths, directory paths,
 * file names, content URIs, and batch paths with clean toast notifications
 * and haptic feedback.
 */
object ClipboardHelper {

    /**
     * Copies a string to clipboard with label, optional toast, and optional haptic feedback.
     */
    fun copyText(
        context: Context,
        label: String,
        text: String,
        showToast: Boolean = true,
        toastMessage: String? = null,
        anchorView: View? = null
    ): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)

            anchorView?.let { v ->
                try {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } catch (_: Throwable) {}
            }

            if (showToast) {
                val msg = toastMessage ?: "Copied: $text"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Throwable) {
            Toast.makeText(context, "Failed to copy: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * 1-Tap Copy Absolute File Path (e.g. /storage/emulated/0/DCIM/Camera/IMG_001.jpg)
     */
    fun copyPath(context: Context, path: String, anchorView: View? = null): Boolean {
        return copyText(
            context = context,
            label = "File Path",
            text = path,
            showToast = true,
            toastMessage = "📋 Path copied:\n$path",
            anchorView = anchorView
        )
    }

    fun copyPath(context: Context, file: File, anchorView: View? = null): Boolean {
        return copyPath(context, file.absolutePath, anchorView)
    }

    /**
     * Copy Parent Folder Directory Path
     */
    fun copyDirectoryPath(context: Context, file: File, anchorView: View? = null): Boolean {
        val parent = file.parentFile?.absolutePath ?: file.absolutePath
        return copyText(
            context = context,
            label = "Folder Path",
            text = parent,
            showToast = true,
            toastMessage = "📁 Folder path copied:\n$parent",
            anchorView = anchorView
        )
    }

    /**
     * Copy File Name
     */
    fun copyFileName(context: Context, file: File, anchorView: View? = null): Boolean {
        return copyText(
            context = context,
            label = "File Name",
            text = file.name,
            showToast = true,
            toastMessage = "🏷️ File name copied: ${file.name}",
            anchorView = anchorView
        )
    }

    /**
     * Copy Multiple Paths separated by newlines (Batch multi-select)
     */
    fun copyMultiplePaths(context: Context, paths: List<String>, anchorView: View? = null): Boolean {
        if (paths.isEmpty()) return false
        val joined = paths.joinToString("\n")
        return copyText(
            context = context,
            label = "File Paths",
            text = joined,
            showToast = true,
            toastMessage = "📋 Copied ${paths.size} file paths to clipboard!",
            anchorView = anchorView
        )
    }
}
