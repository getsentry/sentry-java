package io.sentry

import io.sentry.protocol.SentryId
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class NoOpHubTest {
    private var sut: NoOpHub = NoOpHub.getInstance()

    @Test
    fun `getLastEventId returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)

    @Test
    fun `addBreadcrumb doesn't throw on null breadcrumb`() =
        sut.addBreadcrumb("breadcrumb")

    @Test
    fun `hub is always disabled`() = assertFalse(sut.isEnabled)

    @Test
    fun `captureEvent returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.captureEvent(SentryEvent()))

    @Test
    fun `captureTransaction returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.captureTransaction(mock(), mock<Hint>()))

    @Test
    fun `captureException returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.captureException(RuntimeException()))

    @Test
    fun `captureMessage returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.captureMessage("message"))

    @Test
    fun `close does not affect captureEvent`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.captureEvent(SentryEvent()))
    }

    @Test
    fun `close does not affect captureException`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.captureException(RuntimeException()))
    }

    @Test
    fun `close does not affect captureMessage`() {
        sut.close()
        assertEquals(SentryId.EMPTY_ID, sut.captureMessage("message"))
    }

    @Test
    fun `pushScope is no op`() = sut.pushScope()

    @Test
    fun `popScope is no op`() = sut.popScope()

    @Test
    fun `flush doesn't throw on null param`() = sut.flush(30000)

    @Test
    fun `clone returns the same instance`() = assertSame(NoOpHub.getInstance(), sut.clone())

    @Test
    fun `traceHeaders is not null`() {
        assertNotNull(sut.traceHeaders())
    }

    @Test
    fun `getSpan returns null`() {
        assertNull(sut.span)
    }

    @Test
    fun `setSpanContext doesnt throw`() = sut.setSpanContext(RuntimeException(), mock(), "")
}
