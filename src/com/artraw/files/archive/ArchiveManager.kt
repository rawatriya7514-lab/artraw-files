package com.artraw.files.archive

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveEntryItem(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val lastModified: Long
)

object ArchiveManager {

    /**
     * Compress a list of files or directories into a destination ZIP file.
     */
    fun compress(
        sources: List<File>,
        destinationZip: File,
        onProgress: ((percent: Int, currentName: String) -> Unit)? = null
    ): Boolean {
        try {
            if (destinationZip.exists()) {
                destinationZip.delete()
            }
            destinationZip.parentFile?.mkdirs()

            // Count total files for percentage calculation
            val totalFiles = countFiles(sources)
            var processedFiles = 0

            FileOutputStream(destinationZip).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
                        for (source in sources) {
                            if (source.isDirectory) {
                                zipDirectory(source, source.name, zos) { name ->
                                    processedFiles++
                                    val pct = if (totalFiles > 0) (processedFiles * 100 / totalFiles) else 0
                                    onProgress?.invoke(pct, name)
                                }
                            } else {
                                zipSingleFile(source, source.name, zos)
                                processedFiles++
                                val pct = if (totalFiles > 0) (processedFiles * 100 / totalFiles) else 0
                                onProgress?.invoke(pct, source.name)
                            }
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun countFiles(sources: List<File>): Int {
        var count = 0
        for (f in sources) {
            if (f.isDirectory) {
                val list = f.listFiles() ?: emptyArray()
                count += countFiles(list.toList())
            } else {
                count++
            }
        }
        return count
    }

    private fun zipDirectory(
        folder: File,
        parentPath: String,
        zos: ZipOutputStream,
        onFileAdded: (String) -> Unit
    ) {
        val files = folder.listFiles() ?: return
        if (files.isEmpty()) {
            val entry = ZipEntry("$parentPath/")
            entry.time = folder.lastModified()
            zos.putNextEntry(entry)
            zos.closeEntry()
            return
        }
        for (file in files) {
            val path = "$parentPath/${file.name}"
            if (file.isDirectory) {
                zipDirectory(file, path, zos, onFileAdded)
            } else {
                zipSingleFile(file, path, zos)
                onFileAdded(file.name)
            }
        }
    }

    private fun zipSingleFile(file: File, entryPath: String, zos: ZipOutputStream) {
        val entry = ZipEntry(entryPath)
        entry.time = file.lastModified()
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    zos.write(buffer, 0, bytesRead)
                }
            }
        }
        zos.closeEntry()
    }

    /**
     * Inspect and list the contents of an archive without extracting it.
     */
    fun listEntries(archiveFile: File): List<ArchiveEntryItem> {
        val items = mutableListOf<ArchiveEntryItem>()
        try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    items.add(
                        ArchiveEntryItem(
                            name = entry.name,
                            size = entry.size,
                            compressedSize = entry.compressedSize,
                            isDirectory = entry.isDirectory,
                            lastModified = entry.time
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    /**
     * Extract archive to destination directory with ZipSlip protection.
     */
    fun extract(
        archiveFile: File,
        destinationDir: File,
        onProgress: ((percent: Int, currentName: String) -> Unit)? = null
    ): Boolean {
        try {
            destinationDir.mkdirs()
            val destCanonical = destinationDir.canonicalPath

            // If possible, count entries first for percentage
            var totalEntries = 0
            try {
                ZipFile(archiveFile).use { zip ->
                    totalEntries = zip.size()
                }
            } catch (_: Exception) {}

            var extracted = 0
            FileInputStream(archiveFile).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    ZipInputStream(bis).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        val buffer = ByteArray(8192)
                        while (entry != null) {
                            val newFile = File(destinationDir, entry.name)
                            // Zip Slip security validation
                            if (!newFile.canonicalPath.startsWith(destCanonical)) {
                                throw SecurityException("Zip Slip attack detected in entry: ${entry.name}")
                            }

                            if (entry.isDirectory) {
                                newFile.mkdirs()
                            } else {
                                newFile.parentFile?.mkdirs()
                                FileOutputStream(newFile).use { fos ->
                                    BufferedOutputStream(fos).use { bos ->
                                        var bytesRead: Int
                                        while (zis.read(buffer).also { bytesRead = it } != -1) {
                                            bos.write(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            }

                            if (entry.time > 0) {
                                newFile.setLastModified(entry.time)
                            }
                            zis.closeEntry()
                            extracted++
                            val pct = if (totalEntries > 0) (extracted * 100 / totalEntries) else 0
                            onProgress?.invoke(pct, entry.name)

                            entry = zis.nextEntry
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
