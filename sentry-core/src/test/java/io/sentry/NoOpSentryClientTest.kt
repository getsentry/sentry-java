package io.sentry

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class NoOpSentryClientTest {
    private var sut: NoOpSentryClient = NoOpSentryClient.getInstance()

    @Test
    fun `client is always disabled`() = assertFalse(sut.isEnabled)

    @Test
    fun `captureEvent is returns empty UUID`() =
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000000"), sut.captureEvent(null))

    @Test
    fun `close does not affect captureEvent`() {
        sut.close()
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000000"), sut.captureEvent(null))
    }
}
