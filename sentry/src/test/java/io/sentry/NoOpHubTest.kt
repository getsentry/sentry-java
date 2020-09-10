package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class NoOpHubTest {
    private var sut: NoOpHub = NoOpHub.getInstance()

    @Test
    fun `getLastEventId returns empty SentryId`() =
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)

    @Test
    fun `addBreadcrumb is doesn't throw on null breadcrumb`() =
        sut.addBreadcrumb(null)

    @Test
    fun `hub is always disabled`() = assertFalse(sut.isEnabled)

    @Test
    fun `hub is returns empty SentryId`() =
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

    @Test
    fun `pushScope is no op`() = sut.pushScope()

    @Test
    fun `popScope is no op`() = sut.popScope()

    @Test
    fun `bindClient doesn't throw on null param`() = sut.bindClient(null)

    @Test
    fun `withScope doesn't throw on null param`() = sut.withScope(null)

    @Test
    fun `configureScope doesn't throw on null param`() = sut.configureScope(null)

    @Test
    fun `flush doesn't throw on null param`() = sut.flush(30000)

    @Test
    fun `clone returns the same instance`() = assertSame(NoOpHub.getInstance(), sut.clone())
}
