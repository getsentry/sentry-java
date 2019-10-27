package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryStackTraceFactoryTest {
    private val sut = SentryStackTraceFactory()

    @Test
    fun `when getStackFrames is called passing a valid Array, not empty result`() {
        val stacktraces = Thread.currentThread().stackTrace
        val count = stacktraces.size
        assertEquals(count, sut.getStackFrames(stacktraces).count())
    }

    @Test
    fun `when getStackFrames is called passing null, empty result`() {
        assertEquals(0, sut.getStackFrames(null).count())
    }

    @Test
    fun `when getStackFrames is called passing a valid array, fields should be set`() {
        val element = StackTraceElement("class", "method", "fileName", -2)
        val stacktraces = Array(1) { element }
        val stackFrames = sut.getStackFrames(stacktraces)
        assertEquals("class", stackFrames[0].module)
        assertEquals("method", stackFrames[0].function)
        assertEquals("fileName", stackFrames[0].filename)
        assertEquals(-2, stackFrames[0].lineno)
        assertEquals(true, stackFrames[0].isNative)
    }
}
