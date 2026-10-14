package com.artraw.files

import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import com.artraw.files.model.SortDirection
import com.artraw.files.model.SortField
import com.artraw.files.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SortingRulesTest {

    @Test
    fun testFoldersStayOnTop() {
        val folder = FileItem(file = File("/dummy/ZFolder"), name = "ZFolder", isDirectory = true)
        val file = FileItem(file = File("/dummy/AFile.txt"), name = "AFile.txt", isDirectory = false)

        val sortAsc = SortOption(field = SortField.NAME, direction = SortDirection.ASCENDING)
        val sortDesc = SortOption(field = SortField.NAME, direction = SortDirection.DESCENDING)

        // Even with AFile vs ZFolder, folder must be first
        assertTrue(sortAsc.compare(folder, file) < 0)
        assertTrue(sortDesc.compare(folder, file) < 0)
    }

    @Test
    fun testNameSorting() {
        val fileA = FileItem(file = File("/dummy/alpha.txt"), name = "alpha.txt", isDirectory = false)
        val fileB = FileItem(file = File("/dummy/beta.txt"), name = "beta.txt", isDirectory = false)

        val sortAsc = SortOption(field = SortField.NAME, direction = SortDirection.ASCENDING)
        val sortDesc = SortOption(field = SortField.NAME, direction = SortDirection.DESCENDING)

        assertTrue(sortAsc.compare(fileA, fileB) < 0)
        assertTrue(sortDesc.compare(fileA, fileB) > 0)
    }

    @Test
    fun testSizeSorting() {
        val small = FileItem(file = File("/dummy/small.bin"), name = "small.bin", size = 100L, isDirectory = false)
        val large = FileItem(file = File("/dummy/large.bin"), name = "large.bin", size = 10000L, isDirectory = false)

        val sortAsc = SortOption(field = SortField.SIZE, direction = SortDirection.ASCENDING)
        val sortDesc = SortOption(field = SortField.SIZE, direction = SortDirection.DESCENDING)

        assertTrue(sortAsc.compare(small, large) < 0)
        assertTrue(sortDesc.compare(small, large) > 0)
    }
}
