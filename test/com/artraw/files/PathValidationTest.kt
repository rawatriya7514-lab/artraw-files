package com.artraw.files

import com.artraw.files.util.PathSecurity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PathValidationTest {

    @Test
    fun testDescendantValidation() {
        val parent = File("/storage/emulated/0/Download")
        val child = File("/storage/emulated/0/Download/SubFolder")
        val grandChild = File("/storage/emulated/0/Download/SubFolder/DeepFolder")
        val sibling = File("/storage/emulated/0/Pictures")

        // Direct child is descendant
        assertTrue("Child should be descendant of parent", PathSecurity.isDescendantOf(child, parent))
        // Grandchild is descendant
        assertTrue("Grandchild should be descendant of parent", PathSecurity.isDescendantOf(grandChild, parent))
        // Self is descendant (prevents copy folder into itself)
        assertTrue("Self should be descendant of self", PathSecurity.isDescendantOf(parent, parent))

        // Sibling is NOT descendant
        assertFalse("Sibling should not be descendant", PathSecurity.isDescendantOf(sibling, parent))
        // Parent is NOT descendant of child
        assertFalse("Parent should not be descendant of child", PathSecurity.isDescendantOf(parent, child))
    }

    @Test
    fun testRestrictedPaths() {
        val dataDir = File("/storage/emulated/0/Android/data")
        val dataSubDir = File("/storage/emulated/0/Android/data/com.example.app")
        val obbDir = File("/storage/emulated/0/Android/obb")
        val normalDir = File("/storage/emulated/0/Documents")

        assertTrue(PathSecurity.isAndroidRestrictedStorage(dataDir))
        assertTrue(PathSecurity.isAndroidRestrictedStorage(dataSubDir))
        assertTrue(PathSecurity.isAndroidRestrictedStorage(obbDir))
        assertFalse(PathSecurity.isAndroidRestrictedStorage(normalDir))
    }
}
