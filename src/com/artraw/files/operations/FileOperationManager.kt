package com.artraw.files.operations

import com.artraw.files.model.FileItem
import com.artraw.files.util.PathSecurity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

enum class ClipboardMode {
    COPY,
    CUT
}

enum class ConflictMode {
    REPLACE,
    KEEP_BOTH,
    CANCEL,
    SKIP
}

data class OperationProgress(
    val currentFileName: String,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

data class OperationResult(
    val successfulItems: List<File>,
    val failedItems: Map<File, String>,
    val canUndo: Boolean = false,
    val undoAction: (() -> Unit)? = null
)

class FileOperationManager {

    private var clipboardItems: List<FileItem> = emptyList()
    private var clipboardMode: ClipboardMode = ClipboardMode.COPY

    fun setClipboard(items: List<FileItem>, mode: ClipboardMode) {
        clipboardItems = items
        clipboardMode = mode
    }

    fun getClipboard(): Pair<List<FileItem>, ClipboardMode>? {
        if (clipboardItems.isEmpty()) return null
        return Pair(clipboardItems, clipboardMode)
    }

    fun hasClipboard(): Boolean = clipboardItems.isNotEmpty()

    fun clearClipboard() {
        clipboardItems = emptyList()
    }

    /**
     * Executes paste operation (copy or move) with progress updates and conflict resolution.
     */
    fun executePaste(
        destinationDir: File,
        conflictMode: ConflictMode,
        cancelFlag: AtomicBoolean,
        onProgress: (OperationProgress) -> Unit
    ): OperationResult {
        val successful = mutableListOf<File>()
        val failed = mutableMapOf<File, String>()
        val undoMoves = mutableListOf<Pair<File, File>>() // (newLocation, originalLocation)

        val totalBytes = clipboardItems.sumOf { it.size }
        var bytesCopied = 0L

        for ((index, item) in clipboardItems.withIndex()) {
            if (cancelFlag.get()) {
                failed[item.file] = "Operation cancelled by user."
                break
            }

            val source = item.file
            if (!source.exists()) {
                failed[source] = "Source file no longer exists."
                continue
            }

            // Descendant validation: prevent copying/moving folder into itself or its children
            if (source.isDirectory && PathSecurity.isDescendantOf(destinationDir, source)) {
                failed[source] = "Cannot copy or move a folder into itself or its descendant."
                continue
            }

            var destFile = File(destinationDir, source.name)

            if (destFile.exists()) {
                when (conflictMode) {
                    ConflictMode.CANCEL -> {
                        failed[source] = "Cancelled due to file conflict."
                        break
                    }
                    ConflictMode.SKIP -> {
                        continue
                    }
                    ConflictMode.KEEP_BOTH -> {
                        destFile = PathSecurity.resolveUniqueDestination(destinationDir, source.name)
                    }
                    ConflictMode.REPLACE -> {
                        if (destFile.isDirectory) {
                            destFile.deleteRecursively()
                        } else {
                            destFile.delete()
                        }
                    }
                }
            }

            try {
                if (clipboardMode == ClipboardMode.CUT) {
                    // Try atomic rename first if on same mount
                    val renamed = source.renameTo(destFile)
                    if (renamed) {
                        successful.add(destFile)
                        undoMoves.add(Pair(destFile, source))
                        bytesCopied += item.size
                    } else {
                        // Cross-device fallback: copy then delete
                        copyRecursive(source, destFile, cancelFlag) { bytes ->
                            bytesCopied += bytes
                            onProgress(
                                OperationProgress(
                                    currentFileName = source.name,
                                    currentFileIndex = index + 1,
                                    totalFiles = clipboardItems.size,
                                    bytesTransferred = bytesCopied,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                        source.deleteRecursively()
                        successful.add(destFile)
                        undoMoves.add(Pair(destFile, source))
                    }
                } else {
                    // COPY mode
                    copyRecursive(source, destFile, cancelFlag) { bytes ->
                        bytesCopied += bytes
                        onProgress(
                            OperationProgress(
                                currentFileName = source.name,
                                currentFileIndex = index + 1,
                                totalFiles = clipboardItems.size,
                                bytesTransferred = bytesCopied,
                                totalBytes = totalBytes
                            )
                        )
                    }
                    successful.add(destFile)
                }
            } catch (e: Exception) {
                failed[source] = e.message ?: "Unknown I/O error"
            }

            onProgress(
                OperationProgress(
                    currentFileName = source.name,
                    currentFileIndex = index + 1,
                    totalFiles = clipboardItems.size,
                    bytesTransferred = bytesCopied,
                    totalBytes = totalBytes
                )
            )
        }

        // Clear clipboard if cut completed
        if (clipboardMode == ClipboardMode.CUT && failed.isEmpty()) {
            clearClipboard()
        }

        val canUndo = clipboardMode == ClipboardMode.CUT && undoMoves.isNotEmpty()
        val undoAction: (() -> Unit)? = if (canUndo) {
            {
                for ((newLoc, origLoc) in undoMoves) {
                    try {
                        newLoc.renameTo(origLoc)
                    } catch (ignored: Exception) {
                    }
                }
            }
        } else null

        return OperationResult(successful, failed, canUndo, undoAction)
    }

    /**
     * Delete files permanently with explicit reporting (no false undo promises).
     */
    fun deleteFiles(items: List<FileItem>): OperationResult {
        val successful = mutableListOf<File>()
        val failed = mutableMapOf<File, String>()

        for (item in items) {
            val file = item.file
            try {
                val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (ok) {
                    successful.add(file)
                } else {
                    failed[file] = "Failed to delete file. Check write permissions."
                }
            } catch (e: Exception) {
                failed[file] = e.message ?: "Permission denied"
            }
        }
        return OperationResult(successful, failed, canUndo = false, undoAction = null)
    }

    /**
     * Rename single file or folder.
     */
    fun renameFile(item: FileItem, newName: String): Pair<Boolean, String?> {
        val src = item.file
        if (!src.exists()) return Pair(false, "Source file does not exist.")
        if (newName.isBlank() || newName.contains("/") || newName.contains("\\")) {
            return Pair(false, "Invalid name contains illegal characters.")
        }
        val dest = File(src.parentFile, newName)
        if (dest.exists()) {
            return Pair(false, "A file or folder with that name already exists.")
        }
        return try {
            val ok = src.renameTo(dest)
            if (ok) Pair(true, null) else Pair(false, "Rename failed.")
        } catch (e: Exception) {
            Pair(false, e.message)
        }
    }

    /**
     * Safe copying using a temporary file before final rename to prevent partial/corrupted files.
     */
    private fun copyFileSafe(src: File, dst: File, cancelFlag: AtomicBoolean, onBytes: (Long) -> Unit) {
        val tempFile = File(dst.parentFile, ".artraw_tmp_" + System.currentTimeMillis() + "_" + dst.name)
        var streamClosedCleanly = false
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (cancelFlag.get()) {
                            throw IOException("Copy cancelled.")
                        }
                        output.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                    output.flush()
                }
            }
            streamClosedCleanly = true
            if (dst.exists()) dst.delete()
            if (!tempFile.renameTo(dst)) {
                // Fallback copy if rename fails
                tempFile.copyTo(dst, overwrite = true)
                tempFile.delete()
            }
        } finally {
            if (!streamClosedCleanly && tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun copyRecursive(src: File, dst: File, cancelFlag: AtomicBoolean, onBytes: (Long) -> Unit) {
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw IOException("Failed to create directory: ${dst.absolutePath}")
            }
            val children = src.listFiles() ?: return
            for (child in children) {
                if (cancelFlag.get()) return
                val targetChild = File(dst, child.name)
                copyRecursive(child, targetChild, cancelFlag, onBytes)
            }
        } else {
            copyFileSafe(src, dst, cancelFlag, onBytes)
        }
    }
}
