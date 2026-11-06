package com.artraw.files

import com.artraw.files.gallery.ai.AiQueryParser
import com.artraw.files.gallery.ai.ImageFeatureExtractor
import com.artraw.files.gallery.model.AiCollectionType
import com.artraw.files.gallery.model.GalleryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSmartGalleryTest {

    @Test
    fun testAiQueryParserMountains() {
        val parsed = AiQueryParser.parse("Show my photos with mountains")
        assertTrue("Should contain mountain tag", parsed.requiredTags.contains("mountain"))
    }

    @Test
    fun testAiQueryParserDog() {
        val parsed = AiQueryParser.parse("Find pictures of my dog")
        assertTrue("Should contain dog tag", parsed.requiredTags.contains("dog"))
    }

    @Test
    fun testAiQueryParserRedCar() {
        val parsed = AiQueryParser.parse("Show images with a red car")
        assertEquals("Should detect RED color", "RED", parsed.colorFilter)
        assertTrue("Should contain car tag", parsed.requiredTags.contains("car"))
    }

    @Test
    fun testAiQueryParserScreenshotsLastMonth() {
        val parsed = AiQueryParser.parse("Show screenshots from last month")
        assertEquals("Should detect SCREENSHOTS category", GalleryCategory.SCREENSHOTS, parsed.targetCategory)
        assertNotNull("Should parse min date", parsed.minDateMs)
        assertNotNull("Should parse max date", parsed.maxDateMs)
        assertTrue("minDate < maxDate", parsed.minDateMs!! < parsed.maxDateMs!!)
    }

    @Test
    fun testAiQueryParserDocuments() {
        val parsed = AiQueryParser.parse("Find photos containing documents")
        assertEquals("Should target DOCUMENTS", GalleryCategory.DOCUMENTS, parsed.targetCategory)
        assertTrue("Should include document tag", parsed.requiredTags.contains("document"))
    }

    @Test
    fun testAiQueryParserDuplicates() {
        val parsed = AiQueryParser.parse("Find duplicate photos")
        assertTrue("Should detect duplicates intent", parsed.isDuplicatesOnly)
    }

    @Test
    fun testAiQueryParserBlurry() {
        val parsed = AiQueryParser.parse("Find blurry pictures")
        assertTrue("Should detect blurry intent", parsed.isBlurryOnly)
    }

    @Test
    fun testAiQueryParserBestPhotos() {
        val parsed = AiQueryParser.parse("Find my best photos")
        assertTrue("Should detect best photos intent", parsed.isBestPhotosOnly)
    }

    @Test
    fun testAiQueryParserDiwali() {
        val parsed = AiQueryParser.parse("Show all photos from Diwali")
        assertTrue("Should detect Diwali tag", parsed.requiredTags.contains("diwali"))
    }

    @Test
    fun testHammingDistance() {
        val h1 = 0b1010101010101010L
        val h2 = 0b1010101010101010L
        assertEquals("Identical hashes must have 0 distance", 0, ImageFeatureExtractor.hammingDistance(h1, h2))

        val h3 = 0b1010101010101011L // 1 bit flip
        assertEquals("Single bit flip must have distance 1", 1, ImageFeatureExtractor.hammingDistance(h1, h3))

        val h4 = 0b0101010101010101L // inverted 16 bits
        assertTrue("Inverted bits must have distance >= 16", ImageFeatureExtractor.hammingDistance(h1, h4) >= 16)
    }

    @Test
    fun testAiCollectionTypes() {
        val nature = AiCollectionType.NATURE
        assertTrue(nature.matchingTags.contains("mountain"))
        assertTrue(nature.matchingTags.contains("landscape"))

        val pets = AiCollectionType.PETS
        assertTrue(pets.matchingTags.contains("dog"))
        assertTrue(pets.matchingTags.contains("cat"))

        val vehicles = AiCollectionType.VEHICLES
        assertTrue(vehicles.matchingTags.contains("car"))

        val docs = AiCollectionType.DOCUMENTS
        assertTrue(docs.matchingTags.contains("receipt"))
        assertTrue(docs.matchingTags.contains("invoice"))
    }

    @Test
    fun testGalleryCategoryMapping() {
        assertEquals(GalleryCategory.ALL, GalleryCategory.fromId("all"))
        assertEquals(GalleryCategory.PHOTOS, GalleryCategory.fromId("photos"))
        assertEquals(GalleryCategory.VIDEOS, GalleryCategory.fromId("videos"))
        assertEquals(GalleryCategory.SCREENSHOTS, GalleryCategory.fromId("screenshots"))
        assertEquals(GalleryCategory.CAMERA, GalleryCategory.fromId("camera"))
        assertEquals(GalleryCategory.RAW_ART, GalleryCategory.fromId("raw_art"))
        assertEquals("RAW & Fine Art", GalleryCategory.RAW_ART.displayName)
        assertEquals(GalleryCategory.VAULT, GalleryCategory.fromId("vault"))
        assertEquals("Vault", GalleryCategory.VAULT.displayName)
        assertEquals(GalleryCategory.DUPLICATES, GalleryCategory.fromId("duplicates"))
    }
}
