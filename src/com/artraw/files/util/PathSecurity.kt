package com.artraw.files.util

import java.io.File
import java.io.IOException

object PathSecurity {

    /**
     * Prevents cyclic copy/move operations where a directory is copied or moved into itself
     * or into any of its subfolders.
     */
    fun isDescendantOf(candidateChild: File, potentialParent: File): Boolean {
        try {
            val childCanonical = candidateChild.canonicalFile
            val parentCanonical = potentialParent.canonicalFile

            if (childCanonical == parentCanonical) return true

            var current: File? = childCanonical.parentFile
            while (current != null) {
                if (current == parentCanonical) {
                    return true
                }
                current = current.parentFile
            }
        } catch (e: IOException) {
            // Fallback to strict path prefix matching if canonicalization fails
            val childPath = candidateChild.absolutePath.trimEnd(File.separatorChar) + File.separator
            val parentPath = potentialParent.absolutePath.trimEnd(File.separatorChar) + File.separator
            return childPath.startsWith(parentPath)
        }
        return false
    }

    /**
     * Determines whether a path is subject to strict Android OS privacy restrictions (Android/data or Android/obb).
     */
    fun isAndroidRestrictedStorage(file: File): Boolean {
        val path = file.absolutePath.replace('\\', '/')
        return path.contains("/Android/data") || path.contains("/Android/obb")
    }

    /**
     * Resolves safe destination filename avoiding collisions if requested.
     */
    fun resolveUniqueDestination(destDir: File, desiredName: String): File {
        var target = File(destDir, desiredName)
        if (!target.exists()) return target

        val lastDot = desiredName.lastIndexOf('.')
        val baseName = if (lastDot != -1) desiredName.substring(0, lastDot) else desiredName
        val extension = if (lastDot != -1) desiredName.substring(lastDot) else ""

        var counter = 1
        while (target.exists()) {
            val newName = "$baseName ($counter)$extension"
            target = File(destDir, newName)
            counter++
        }
        return target
    }
}
