package io.sentry.android.core.internal.threaddump

import io.sentry.SentryLockReason
import io.sentry.SentryOptions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertNull(firstFrame.isNative)
        assertNull(firstFrame.platform)

        val jniFrame = randomThread.stacktrace!!.frames!!.get(4)
        assertEquals("android.os.MessageQueue", jniFrame.module)
        assertEquals("nativePollOnce", jniFrame.function)
        assertNull(jniFrame.lineno)
        assertEquals(true, jniFrame.isNative)
        assertNull(firstFrame.platform)

        val nativeFrame = randomThread.stacktrace!!.frames!!.get(5)
        assertEquals("/system/lib64/libandroid_runtime.so", nativeFrame.`package`)
        assertEquals("android::android_os_MessageQueue_nativePollOnce(_JNIEnv*, _jobject*, long, int)",
                     nativeFrame.function)
        assertEquals(44, nativeFrame.lineno)
        assertNull(nativeFrame.isNative) // Confusing, but "isNative" means JVM frame for a JNI method
        assertEquals("native", nativeFrame.platform)
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

        // Reverse frames so we can index them with the active frame at index 0
        val frames = thread.stacktrace!!.frames!!.reversed()

        val lastFrame = frames.get(0)
        assertEquals("/apex/com.android.runtime/lib64/bionic/libc.so", lastFrame.`package`)
        assertEquals("syscall", lastFrame.function)
        assertEquals(28, lastFrame.lineno)
        assertNull(lastFrame.isInApp)
        assertEquals("0x000000000004c35c", lastFrame.instructionAddr)
        assertEquals("rel:499d48ba-c085-17cf-3209-da67405662f9", lastFrame.addrMode)
        assertEquals("native", lastFrame.platform)

        val nosymFrame = frames.get(21)
        assertEquals("/apex/com.android.art/javalib/core-oj.jar", nosymFrame.`package`)
        assertNull(nosymFrame.function)
        assertNull(nosymFrame.lineno)
        assertEquals("0x00000000000ec474", nosymFrame.instructionAddr)
        assertNull(nosymFrame.addrMode)

        val spaceFrame = frames.get(14)
        assertEquals(
            "[anon:dalvik-classes16.dex extracted in memory from /data/app/~~izn1xSZpFlzfVmWi_I0xlQ=="
            + "/io.sentry.samples.android-tQSGMNiGA-qdjZm6lPOcNw==/base.apk!classes16.dex]",
            spaceFrame.`package`)
        assertNull(spaceFrame.function)
        assertNull(spaceFrame.lineno)
        assertEquals("0x00000000000306f0", spaceFrame.instructionAddr)
        assertNull(spaceFrame.addrMode)

        val offsetFrame = frames.get(145)
        assertEquals("/system/framework/framework.jar (offset 0x12c2000)", offsetFrame.`package`)
        assertNull(offsetFrame.function)
        assertNull(offsetFrame.lineno)
        assertEquals("0x00000000002c8e18", offsetFrame.instructionAddr)
        assertNull(offsetFrame.addrMode)

        val deletedFrame = frames.get(117)
        assertEquals("/memfd:jit-cache (deleted) (offset 0x2000000)", deletedFrame.`package`)
        assertEquals("kotlinx.coroutines.DispatchedTask.run", deletedFrame.function)
        assertEquals(1816, deletedFrame.lineno)
        assertEquals("0x00000000020b89d8", deletedFrame.instructionAddr)
        assertNull(deletedFrame.addrMode)

        val debugImages = parser.debugImages
        val image = debugImages["499d48ba-c085-17cf-3209-da67405662f9"]
        assertNotNull(image)
        assertEquals("499d48ba-c085-17cf-3209-da67405662f9", image.debugId)
        assertEquals("/apex/com.android.runtime/lib64/bionic/libc.so", image.codeFile)
        assertEquals("ba489d4985c0cf173209da67405662f9", image.codeId)
    }

    @Test
    fun `thread dump garbage`() {
        val lines = Lines.readLines(File("src/test/resources/thread_dump_bad_data.txt"))
        val parser = ThreadDumpParser(
            SentryOptions().apply { addInAppInclude("io.sentry.samples") },
            false
        )
        val threads = parser.parse(lines)
        assertTrue(threads.isEmpty())
    }
}
