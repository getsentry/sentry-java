package io.sentry.android.core.internal.threaddump

import io.sentry.SentryLockReason
import io.sentry.SentryOptions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ThreadDumpParserTest {

    @Test
    fun `parses thread dump into SentryThread list`() {
        val lines = Lines.readLines(File("src/test/resources/thread_dump.txt"))
        val parser = ThreadDumpParser(
            SentryOptions().apply { addInAppInclude("io.sentry.samples") },
            false
        )
        val threads = parser.parse(lines)
        // just verifying a few important threads, as there are many
        val main = threads.find { it.name == "main" }
        assertEquals(1, main!!.id)
        assertEquals("Blocked", main.state)
        assertEquals(true, main.isCrashed)
        assertEquals(true, main.isMain)
        assertEquals(true, main.isCurrent)
        assertNotNull(main.heldLocks!!["0x0d3a2f0a"])
        assertEquals(SentryLockReason.BLOCKED, main.heldLocks!!["0x0d3a2f0a"]!!.type)
        assertEquals(5, main.heldLocks!!["0x0d3a2f0a"]!!.threadId)
        val firstFrame = main.stacktrace!!.frames!!.first()
        assertEquals("io.sentry.samples.android.MainActivity$2", firstFrame.module)
        assertEquals("MainActivity.java", firstFrame.filename)
        assertEquals("run", firstFrame.function)
        assertEquals(177, firstFrame.lineno)
        assertEquals(true, firstFrame.isInApp)

        val blockingThread = threads.find { it.name == "Thread-9" }
        assertEquals(5, blockingThread!!.id)
        assertEquals("Sleeping", blockingThread.state)
        assertEquals(false, blockingThread.isCrashed)
        assertEquals(false, blockingThread.isMain)
        assertNotNull(blockingThread.heldLocks!!["0x0d3a2f0a"])
        assertEquals(SentryLockReason.LOCKED, blockingThread.heldLocks!!["0x0d3a2f0a"]!!.type)
        assertEquals(null, blockingThread.heldLocks!!["0x0d3a2f0a"]!!.threadId)
        assertNotNull(blockingThread.heldLocks!!["0x09228c2d"])
        assertEquals(SentryLockReason.SLEEPING, blockingThread.heldLocks!!["0x09228c2d"]!!.type)
        assertEquals(null, blockingThread.heldLocks!!["0x09228c2d"]!!.threadId)

        val randomThread =
            threads.find { it.name == "io.sentry.android.core.internal.util.SentryFrameMetricsCollector" }
        assertEquals(19, randomThread!!.id)
        assertEquals("Native", randomThread.state)
        assertEquals(false, randomThread.isCrashed)
        assertEquals(false, randomThread.isMain)
        assertEquals(false, randomThread.isCurrent)
        assertEquals(
            "/apex/com.android.runtime/lib64/bionic/libc.so (__epoll_pwait+8) (BuildId: 01331f74b0bb2cb958bdc15282b8ec7b)",
            randomThread.stacktrace!!.frames!!.first().`package`
        )
        val lastFrame = randomThread.stacktrace!!.frames!!.last()
        assertEquals("android.os.HandlerThread", lastFrame.module)
        assertEquals("run", lastFrame.function)
        assertEquals("HandlerThread.java", lastFrame.filename)
        assertEquals(67, lastFrame.lineno)
        assertEquals(null, lastFrame.isInApp)
    }
}
