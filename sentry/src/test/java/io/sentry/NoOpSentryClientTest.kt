package io.sentry

import io.sentry.protocol.SentryId
import io.sentry.test.callMethod
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NoOpSentryClientTest {
    private var sut: NoOpSentryClient = NoOpSentryClient.getInstance()

    @Test
    fun `client is always disabled`() = assertFalse(sut.isEnabled)

    @Test
    fun `captureEvent returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureEvent", SentryEvent::class.java, null))

    @Test
    fun `captureException returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureException", Throwable::class.java, null))

    @Test
    fun `captureMessage returns empty SentryId`() =
        assertEquals(
            SentryId.EMPTY_ID,
            sut.callMethod("captureMessage", parameterTypes = arrayOf(String::class.java, SentryLevel::class.java), null, null),
        )

    @Test
    fun `captureEnvelope returns empty SentryId`() = assertEquals(SentryId.EMPTY_ID, sut.captureEnvelope(mock()))

    @Test
    fun `captureFeedback returns empty SentryId`() = assertEquals(SentryId.EMPTY_ID, sut.captureFeedback(mock(), mock(), mock()))

    @Test
    fun `close does not affect captureEvent`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureEvent", SentryEvent::class.java, null))
    }

    @Test
    fun `close with isRestarting true does not affect captureEvent`() {
        sut.close(true)
        assertEquals(SentryId.EMPTY_ID, sut.callMethod("captureEvent", SentryEvent::class.java, null))
    }

    @Test
    fun `close with isRestarting false does not affect captureEvent`() {
        sut.close(false)
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
        assertEquals(
            SentryId.EMPTY_ID,
            sut.callMethod("captureMessage", parameterTypes = arrayOf(String::class.java, SentryLevel::class.java), null, null),
        )
    }

    @Test
    fun `captureTransaction returns empty SentryId`() = assertEquals(SentryId.EMPTY_ID, sut.captureTransaction(mock(), mock()))

    @Test
    fun `captureProfileChunk returns empty SentryId`() = assertEquals(SentryId.EMPTY_ID, sut.captureProfileChunk(mock(), mock()))

    @Test
    fun `captureCheckIn returns empty id`() {
        assertEquals(SentryId.EMPTY_ID, sut.captureCheckIn(mock(), mock(), mock()))
    }
}
