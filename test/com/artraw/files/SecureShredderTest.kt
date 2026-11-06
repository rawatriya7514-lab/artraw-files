package com.artraw.files

import com.artraw.files.operations.SecureShredder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecureShredderTest {

    @Test
    fun shredsRegularFile() {
        val file = File.createTempFile("artfiles-shred", ".bin")
        file.writeBytes(ByteArray(8 * 1024) { (it % 251).toByte() })
        assertTrue(file.exists())
        assertTrue(SecureShredder.shred(file))
        assertFalse(file.exists())
    }

    @Test
    fun refusesDirectories() {
        val dir = File.createTempFile("artfiles-shred-dir", "")
        dir.delete()
        assertTrue(dir.mkdirs())
        assertFalse(SecureShredder.shred(dir))
        assertTrue(dir.exists())
        dir.delete()
    }

    @Test
    fun missingFileReturnsFalse() {
        assertFalse(SecureShredder.shred(File("/definitely/not/here/artfiles.bin")))
    }
}
