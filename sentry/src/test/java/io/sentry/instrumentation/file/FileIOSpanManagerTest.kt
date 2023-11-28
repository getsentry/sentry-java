package io.sentry.instrumentation.file

import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.util.PlatformTestManipulator
import org.junit.After
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test

class FileIOSpanManagerTest {

    @After
    fun cleanup() {
        PlatformTestManipulator.pretendIsAndroid(false)
    }

    @Test
    fun `startSpan uses transaction on Android platform`() {
        val hub = mock<IHub>()
        val transaction = mock<ITransaction>()
        whenever(hub.transaction).thenReturn(transaction)

        PlatformTestManipulator.pretendIsAndroid(true)

        FileIOSpanManager.startSpan(hub, "op.read")
        verify(transaction).startChild(any())
    }

    @Test
    fun `startSpan uses last span on non-Android platforms`() {
        val hub = mock<IHub>()
        val span = mock<ISpan>()
        whenever(hub.span).thenReturn(span)

        PlatformTestManipulator.pretendIsAndroid(false)

        FileIOSpanManager.startSpan(hub, "op.read")
        verify(span).startChild(any())
    }
}
