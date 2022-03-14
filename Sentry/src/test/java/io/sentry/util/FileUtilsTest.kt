package io.sentry.util

import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FileUtilsTest {

    @Test
    fun `deleteRecursively returns true on non-existing file or null`() {
        assert(FileUtils.deleteRecursively(null))
        assert(FileUtils.deleteRecursively(File("")))
    }

    @Test
    fun `deleteRecursively deletes a simple file`() {
        val f = Files.createTempFile("here", "test").toFile()
        assert(f.exists())
        assert(FileUtils.deleteRecursively(f))
        assertFalse(f.exists())
    }

    @Test
    fun `deleteRecursively deletes a folder`() {
        val d = Files.createTempDirectory("here").toFile()
        val f = File(d, "test")
        val d2 = File(d, "dir2")
        val f2 = File(d2, "test")
        f.createNewFile()
        d2.mkdir()
        f2.createNewFile()
        assert(d.exists() && d.isDirectory && f.exists() && d2.exists() && d2.isDirectory)
        assert(f2.exists())
        assert(FileUtils.deleteRecursively(d))
        assertFalse(f.exists() || d.exists() || f2.exists() || d2.exists())
    }

    @Test
    fun `readText returns null on null, non existing or unreadable file`() {
        val f = File("here", "test")
        val unreadableFile = Files.createTempFile("here", "test").toFile()
        unreadableFile.setReadable(false)
        assertNull(FileUtils.readText(null))
        assertNull(FileUtils.readText(f))
        assertNull(FileUtils.readText(unreadableFile))
    }

    @Test
    fun `readText returns the content of a file`() {
        val f = Files.createTempFile("here", "test").toFile()
        val text = "Lorem ipsum dolor sit amet\nLorem ipsum dolor sit amet"
        f.writeText(text)
        assertEquals(text, FileUtils.readText(f))
    }
}
