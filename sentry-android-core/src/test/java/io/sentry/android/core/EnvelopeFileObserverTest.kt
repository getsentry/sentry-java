package io.sentry.android.core

import android.os.FileObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IEnvelopeSender
import io.sentry.ILogger
import io.sentry.hints.ApplyScopeData
import io.sentry.hints.Resettable
import io.sentry.hints.Retryable
import io.sentry.hints.SubmissionResult
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class EnvelopeFileObserverTest {

    private class Fixture {
        val fileName = "file-name.txt"
        var path: String? = "."
        val envelopeSender = mock<IEnvelopeSender>()
        val logger = mock<ILogger>()

        fun getSut(flushTimeoutMillis: Long): EnvelopeFileObserver {
            return EnvelopeFileObserver(path, envelopeSender, logger, flushTimeoutMillis)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `envelope sender is called with fully qualified path`() {
        triggerEvent()

        verify(fixture.envelopeSender).processEnvelopeFile(eq(fixture.path + File.separator + fixture.fileName), any())
    }

    @Test
    fun `when event type is not close write, envelope sender is not called`() {
        triggerEvent(eventType = FileObserver.CLOSE_WRITE.inv())

        verify(fixture.envelopeSender, never()).processEnvelopeFile(any(), anyOrNull())
    }

    @Test
    fun `when event is fired with null path, envelope reader is not called`() {
        triggerEvent(relativePath = null)

        verify(fixture.envelopeSender, never()).processEnvelopeFile(anyOrNull(), any())
    }

    @Test
    fun `when null is passed as a path, ctor throws`() {
        fixture.path = null

        // since EnvelopeFileObserver extends FileObserver and FileObserver requires a File(path),
        // it throws NullPointerException instead of our own IllegalArgumentException
        assertFailsWith<NullPointerException> { fixture.getSut(0) }
    }

    @Test
    fun `envelope sender is called with fully qualified path and ApplyScopeData hint`() {
        triggerEvent()

        verify(fixture.envelopeSender).processEnvelopeFile(
            eq(fixture.path + File.separator + fixture.fileName),
            check { it is ApplyScopeData }
        )
    }

    @Test
    fun `envelope sender Hint is Resettable`() {
        triggerEvent()

        verify(fixture.envelopeSender).processEnvelopeFile(
            eq(fixture.path + File.separator + fixture.fileName),
            check { it is Resettable }
        )
    }

    @Test
    fun `Hint resets its state`() {
        triggerEvent(flushTimeoutMillis = 0)

        verify(fixture.envelopeSender).processEnvelopeFile(
            eq(fixture.path + File.separator + fixture.fileName),
            check {
                (it as SubmissionResult).setResult(true)
                (it as Retryable).isRetry = true

                (it as Resettable).reset()

                assertFalse(it.isRetry)
                assertFalse(it.isSuccess)
            }
        )
    }

    private fun triggerEvent(
        flushTimeoutMillis: Long = 15_000,
        eventType: Int = FileObserver.CLOSE_WRITE,
        relativePath: String? = fixture.fileName
    ) {
        val sut = fixture.getSut(flushTimeoutMillis)
        sut.onEvent(eventType, relativePath)
    }
}
