package io.sentry.cache

import io.sentry.SentryOptions
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

internal class CacheUtilsTest {
    @get:Rule
    val tmpDir = TemporaryFolder()

    @Test
    fun `if cacheDir is not set, store does nothing`() {
        CacheUtils.store(SentryOptions(), "Hallo!", "stuff", "test.json")

        val (_, file) = tmpDirAndFile()
        assertFalse(file.exists())
    }

    @Test
    fun `store stores data in the file`() {
        val (cacheDir, file) = tmpDirAndFile()

        CacheUtils.store(
            SentryOptions().apply { cacheDirPath = cacheDir },
            "Hallo!",
            "stuff",
            "test.json",
        )

        assertEquals("\"Hallo!\"", file.readText())

        // test overwrite
        CacheUtils.store(
            SentryOptions().apply { cacheDirPath = cacheDir },
            "Hallo 2!",
            "stuff",
            "test.json",
        )

        assertEquals("\"Hallo 2!\"", file.readText())
    }

    @Test
    fun `if cacheDir is not set, read returns null`() {
        val value =
            CacheUtils.read<String, Any?>(
                SentryOptions(),
                "stuff",
                "test.json",
                String::class.java,
                null,
            )

        assertNull(value)
    }

    @Test
    fun `read reads data from the file`() {
        val (cacheDir, file) = tmpDirAndFile()
        file.writeText("\"Hallo!\"")

        val value =
            CacheUtils.read<String, Any?>(
                SentryOptions().apply { cacheDirPath = cacheDir },
                "stuff",
                "test.json",
                String::class.java,
                null,
            )

        assertEquals("Hallo!", value)
    }

    @Test
    fun `delete deletes the file`() {
        val (cacheDir, file) = tmpDirAndFile()
        file.writeText("Hallo!")

        assertEquals("Hallo!", file.readText())

        val options = SentryOptions().apply { cacheDirPath = cacheDir }

        CacheUtils.delete(options, "stuff", "test.json")

        assertFalse(file.exists())
    }

    private fun tmpDirAndFile(): Pair<String, File> {
        val cacheDir = tmpDir.newFolder().absolutePath
        val dir = File(cacheDir, "stuff").also { it.mkdirs() }
        return cacheDir to File(dir, "test.json")
    }
}
