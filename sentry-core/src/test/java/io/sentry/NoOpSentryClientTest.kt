package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class NoOpSentryClientTest {
    private var sut: NoOpSentryClient = NoOpSentryClient.getInstance()

    @Test
    fun `client is always disabled`() = assertFalse(sut.isEnabled)

    @Test
    fun `captureEvent is returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.captureEvent(null))

    @Test
    fun `captureException is returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.captureException(null))

    @Test
    fun `captureMessage is returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.captureMessage(null))

    @Test
    fun `close does not affect captureEvent`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.captureEvent(null))
    }

    @Test
    fun `close does not affect captureException`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.captureException(null))
    }

    @Test
    fun `close does not affect captureMessage`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.captureMessage(null))
    }
}
