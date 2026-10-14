package com.artraw.files.analysis

import com.artraw.files.model.FileItem
import com.artraw.files.util.PathSecurity
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

data class DuplicateGroup(
    val size: Long,
    val hash: String,
    val items: List<FileItem>
)

class DuplicateDetector {

    /**
     * Finds potential duplicates using file size as a fast preliminary filter without hashing.
     */
    fun findPotentialDuplicatesBySize(
        root: File,
        minSizeBytes: Long = 1024L,
        cancelFlag: AtomicBoolean
    ): Map<Long, List<File>> {
        val sizeMap = mutableMapOf<Long, MutableList<File>>()

        fun scan(dir: File, depth: Int) {
            if (depth > 6 || cancelFlag.get()) return
            if (PathSecurity.isAndroidRestrictedStorage(dir)) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (cancelFlag.get()) return
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    scan(f, depth + 1)
                } else {
                    val len = f.length()
                    if (len >= minSizeBytes) {
                        sizeMap.getOrPut(len) { mutableListOf() }.add(f)
                    }
                }
            }
        }

        scan(root, 1)
        // Only return sizes that have 2 or more files
        return sizeMap.filter { it.value.size >= 2 }
    }

    /**
     * Performs cryptographic SHA-256 verification only on candidate files that share the exact same size.
     * Avoids unnecessary I/O on unique files.
     */
    fun verifyDuplicatesWithHash(
        candidatesBySize: Map<Long, List<File>>,
        cancelFlag: AtomicBoolean,
        onProgress: (scanned: Int, total: Int) -> Unit
    ): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()
        val totalCandidates = candidatesBySize.values.sumOf { it.size }
        var processed = 0

        for ((size, files) in candidatesBySize) {
            if (cancelFlag.get()) break
            val hashMap = mutableMapOf<String, MutableList<FileItem>>()

            for (file in files) {
                if (cancelFlag.get()) break
                processed++
                onProgress(processed, totalCandidates)

                val hash = computeSha256(file, cancelFlag) ?: continue
                val item = FileItem(
                    file = file,
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = false,
                    size = size,
                    lastModified = file.lastModified()
                )
                hashMap.getOrPut(hash) { mutableListOf() }.add(item)
            }

            for ((hash, items) in hashMap) {
                if (items.size >= 2) {
                    groups.add(DuplicateGroup(size = size, hash = hash, items = items))
                }
            }
        }

        return groups.sortedByDescending { it.size * it.items.size }
    }

    private fun computeSha256(file: File, cancelFlag: AtomicBoolean): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(32 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    if (cancelFlag.get()) return null
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
