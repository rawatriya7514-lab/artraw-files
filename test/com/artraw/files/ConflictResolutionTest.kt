package com.artraw.files

import com.artraw.files.util.PathSecurity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ConflictResolutionTest {

    @Test
    fun testKeepBothResolution() {
        val tempDir = File.createTempFile("artraw_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val fileA = File(tempDir, "document.pdf")
            fileA.writeText("test file A")

            // Destination document.pdf already exists -> Keep Both produces document (1).pdf
            val resolved1 = PathSecurity.resolveUniqueDestination(tempDir, "document.pdf")
            assertEquals("document (1).pdf", resolved1.name)

            // When document (1).pdf also exists -> Keep Both produces document (2).pdf
            resolved1.writeText("test file B")
            val resolved2 = PathSecurity.resolveUniqueDestination(tempDir, "document.pdf")
            assertEquals("document (2).pdf", resolved2.name)

            // Non-conflicting filename returns itself
            val nonConflicting = PathSecurity.resolveUniqueDestination(tempDir, "other.txt")
            assertEquals("other.txt", nonConflicting.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
