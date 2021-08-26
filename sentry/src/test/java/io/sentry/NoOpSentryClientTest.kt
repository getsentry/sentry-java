package io.sentry

import com.nhaarman.mockitokotlin2.mock
import io.sentry.protocol.SentryId
import io.sentry.test.callMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NoOpSentryClientTest {
    private var sut: NoOpSentryClient = NoOpSentryClient.getInstance()

    @Test
    fun `client is always disabled`() = assertFalse(sut.isEnabled)

    @Test
    fun `captureEvent is returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureEvent", SentryEvent::class.java, null))

    @Test
    fun `captureException is returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureException", Throwable::class.java, null))

    @Test
    fun `captureMessage is returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureMessage", parameterTypes = arrayOf(String::class.java, SentryLevel::class.java), null, null))

    @Test
    fun `close does not affect captureEvent`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureEvent", SentryEvent::class.java, null))
    }

    @Test
    fun `close does not affect captureException`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureException", Throwable::class.java, null))
    }

    @Test
    fun `close does not affect captureMessage`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureMessage", parameterTypes = arrayOf(String::class.java, SentryLevel::class.java), null, null))
    }

    @Test
    fun `captureTransaction returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.captureTransaction(mock(), mock()))
}
