package io.sentry.cache.tape

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals

class CorruptQueueFileTest {
    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var file: File

    @Before
    fun setUp() {
        val parent = folder.root
        file = File(parent, "queue-file")
    }

    @Test
    fun `does not fail to operate with a corrupt file`() {
        val testFile = this::class.java.classLoader.getResource("corrupt_queue_file.txt")!!
        Files.copy(Paths.get(testFile.toURI()), file.outputStream())

        val queueFile = QueueFile.Builder(file).zero(true).build()
        val iterator = queueFile.iterator()
        while (iterator.hasNext()) {
            iterator.next()
        }

        queueFile.add("test".toByteArray())
        assertEquals(1, queueFile.size())

        queueFile.peek()

        queueFile.remove()
        assertEquals(0, queueFile.size())
    }
}
