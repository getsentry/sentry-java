package io.sentry.android.core.internal.threaddump

import io.sentry.SentryLockReason
import io.sentry.SentryOptions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        val lastFrame = main.stacktrace!!.frames!!.last()
        assertEquals("io.sentry.samples.android.MainActivity$2", lastFrame.module)
        assertEquals("MainActivity.java", lastFrame.filename)
        assertEquals("run", lastFrame.function)
        assertEquals(177, lastFrame.lineno)
        assertEquals(true, lastFrame.isInApp)
        val lock = lastFrame.lock
        assertEquals("0x0d3a2f0a", lock!!.address)
        assertEquals(SentryLockReason.BLOCKED, lock.type)
        assertEquals("java.lang", lock.packageName)
        assertEquals("Object", lock.className)
        assertEquals(5, lock.threadId)

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
            "/apex/com.android.runtime/lib64/bionic/libc.so",
            randomThread.stacktrace!!.frames!!.last().`package`
        )
        assertEquals("__epoll_pwait", randomThread.stacktrace!!.frames!!.last()!!.function)
        assertEquals(8, randomThread.stacktrace!!.frames!!.last()!!.lineno)
        val firstFrame = randomThread.stacktrace!!.frames!!.first()
        assertEquals("android.os.HandlerThread", firstFrame.module)
        assertEquals("run", firstFrame.function)
        assertEquals("HandlerThread.java", firstFrame.filename)
        assertEquals(67, firstFrame.lineno)
        assertEquals(null, firstFrame.isInApp)
    }

    @Test
    fun `parses native only thread dump`() {
        val lines = Lines.readLines(File("src/test/resources/thread_dump_native_only.txt"))
        val parser = ThreadDumpParser(
            SentryOptions().apply { addInAppInclude("io.sentry.samples") },
            false
        )
        val threads = parser.parse(lines)
        // just verifying a few important threads, as there are many
        val thread = threads.find { it.name == "samples.android" }
        assertEquals(9955, thread!!.id)
        assertNull(thread.state)
        assertEquals(false, thread.isCrashed)
        assertEquals(false, thread.isMain)
        assertEquals(false, thread.isCurrent)
        val lastFrame = thread.stacktrace!!.frames!!.last()
        assertEquals("/apex/com.android.runtime/lib64/bionic/libc.so", lastFrame.`package`)
        assertEquals("syscall", lastFrame.function)
        assertEquals(28, lastFrame.lineno)
        assertNull(lastFrame.isInApp)
    }
}
