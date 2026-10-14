package com.artraw.files

import com.artraw.files.model.ArchiveSupportLevel
import com.artraw.files.model.FileCategory
import com.artraw.files.util.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileClassificationTest {

    @Test
    fun testDocumentFormats() {
        val docs = listOf(
            "report.pdf" to FileCategory.DOCUMENTS,
            "notes.txt" to FileCategory.DOCUMENTS,
            "letter.doc" to FileCategory.DOCUMENTS,
            "contract.docx" to FileCategory.DOCUMENTS,
            "budget.xls" to FileCategory.DOCUMENTS,
            "sheet.xlsx" to FileCategory.DOCUMENTS,
            "slides.ppt" to FileCategory.DOCUMENTS,
            "presentation.pptx" to FileCategory.DOCUMENTS,
            "data.csv" to FileCategory.DOCUMENTS,
            "README.md" to FileCategory.DOCUMENTS,
            "config.json" to FileCategory.DOCUMENTS,
            "manifest.xml" to FileCategory.DOCUMENTS
        )

        for ((name, expectedCategory) in docs) {
            val file = File("/dummy/$name")
            assertEquals("Category mismatch for $name", expectedCategory, MimeTypes.getCategory(file))
        }
    }

    @Test
    fun testArchiveFormatsAndCapabilities() {
        val archives = listOf("bundle.zip", "data.tar", "logs.gz", "backup.7z", "archive.rar")
        for (name in archives) {
            val file = File("/dummy/$name")
            assertEquals("Category should be ARCHIVES for $name", FileCategory.ARCHIVES, MimeTypes.getCategory(file))
        }

        // Verify honest reporting
        val zipCap = MimeTypes.getArchiveCapability("zip")
        assertEquals(ArchiveSupportLevel.FULLY_SUPPORTED, zipCap.supportLevel)

        val gzCap = MimeTypes.getArchiveCapability("gz")
        assertEquals(ArchiveSupportLevel.FULLY_SUPPORTED, gzCap.supportLevel)

        val tarCap = MimeTypes.getArchiveCapability("tar")
        assertEquals(ArchiveSupportLevel.STREAM_SUPPORTED, tarCap.supportLevel)

        val sevenZipCap = MimeTypes.getArchiveCapability("7z")
        assertEquals(ArchiveSupportLevel.EXTERNAL_APP_NEEDED, sevenZipCap.supportLevel)

        val rarCap = MimeTypes.getArchiveCapability("rar")
        assertEquals(ArchiveSupportLevel.EXTERNAL_APP_NEEDED, rarCap.supportLevel)
    }

    @Test
    fun testMediaAndApkFormats() {
        assertEquals(FileCategory.APKS, MimeTypes.getCategory(File("/dummy/app.apk")))
        assertEquals(FileCategory.IMAGES, MimeTypes.getCategory(File("/dummy/photo.jpg")))
        assertEquals(FileCategory.IMAGES, MimeTypes.getCategory(File("/dummy/image.png")))
        assertEquals(FileCategory.IMAGES, MimeTypes.getCategory(File("/dummy/graphic.webp")))
        assertEquals(FileCategory.VIDEOS, MimeTypes.getCategory(File("/dummy/clip.mp4")))
        assertEquals(FileCategory.VIDEOS, MimeTypes.getCategory(File("/dummy/movie.mkv")))
        assertEquals(FileCategory.AUDIO, MimeTypes.getCategory(File("/dummy/song.mp3")))
        assertEquals(FileCategory.AUDIO, MimeTypes.getCategory(File("/dummy/recording.wav")))
    }
}
