package com.artraw.files.operations

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Best-effort secure deletion.
 *
 * On flash storage a true physical overwrite cannot be guaranteed, but the
 * common forensic path (raw file contents, file name) is meaningfully removed:
 *  1. overwrite the file twice with random bytes and once with zeros,
 *  2. sync to force the write through,
 *  3. rename to a random name, then delete.
 */
object SecureShredder {

    fun shred(file: File): Boolean {
        if (!file.exists() || file.isDirectory) return false
        val length = file.length()
        try {
            if (length > 0) {
                RandomAccessFile(file, "rws").use { raf ->
                    val random = SecureRandom()
                    val buffer = ByteArray(64 * 1024)
                    for (pass in 0 until 3) {
                        raf.seek(0)
                        var remaining = length
                        while (remaining > 0) {
                            if (pass == 2) {
                                java.util.Arrays.fill(buffer, 0)
                            } else {
                                random.nextBytes(buffer)
                            }
                            val chunk = minOf(buffer.size.toLong(), remaining).toInt()
                            raf.write(buffer, 0, chunk)
                            remaining -= chunk
                        }
                        raf.fd.sync()
                    }
                }
            }
            val scrambled = File(file.parentFile, ".shred_" + System.nanoTime())
            val renamed = file.renameTo(scrambled)
            return (if (renamed) scrambled else file).delete()
        } catch (_: Throwable) {
            return try {
                file.delete()
            } catch (_: Throwable) {
                false
            }
        }
    }
}
