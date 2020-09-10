package io.sentry.android.core

import android.os.FileObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.sentry.IEnvelopeSender
import io.sentry.ILogger
import io.sentry.SentryOptions
import io.sentry.hints.ApplyScopeData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnvelopeFileObserverTest {

    private class Fixture {
        var path: String? = "."
        var envelopeSender: IEnvelopeSender = mock()
        var logger: ILogger = mock()
        var options: SentryOptions = SentryOptions()

        init {
            options.isDebug = true
            options.setLogger(logger)
        }

        fun getSut(): EnvelopeFileObserver {
            return EnvelopeFileObserver(path, envelopeSender, logger, options.flushTimeoutMillis)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `envelope sender is called with fully qualified path`() {
        val sut = fixture.getSut()
        val param = "file-name.txt"
        sut.onEvent(FileObserver.CLOSE_WRITE, param)
        verify(fixture.envelopeSender).processEnvelopeFile(eq(fixture.path + File.separator + param), any())
    }

    @Test
    fun `when event type is not close write, envelope sender is not called`() {
        val sut = fixture.getSut()
        sut.onEvent(FileObserver.CLOSE_WRITE.inv(), "file-name.txt")
        verifyZeroInteractions(fixture.envelopeSender)
    }

    @Test
    fun `when event is fired with null path, envelope reader is not called`() {
        val sut = fixture.getSut()
        sut.onEvent(0, null)
        verify(fixture.envelopeSender, never()).processEnvelopeFile(anyOrNull(), any())
    }

    @Test
    fun `when null is passed as a path, ctor throws`() {
        fixture.path = null
        val exception = assertFailsWith<Exception> { fixture.getSut() }
        assertEquals("File path is required.", exception.message)
    }

    @Test
    fun `envelope sender is called with fully qualified path and ApplyScopeData hint`() {
        val sut = fixture.getSut()
        val param = "file-name.txt"
        sut.onEvent(FileObserver.CLOSE_WRITE, param)
        verify(fixture.envelopeSender).processEnvelopeFile(eq(fixture.path + File.separator + param), argWhere { it is ApplyScopeData })
    }
}
