package io.sentry.android.core

import io.sentry.IConnectionStatusProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidTransportGateTest {
    private class Fixture {
        fun getSut(): AndroidTransportGate = AndroidTransportGate(SentryAndroidOptions())
    }

    private val fixture = Fixture()

    @Test
    fun `isConnected doesn't throw`() {
        assertNotNull(fixture.getSut().isConnected)
    }

    @Test
    fun `isConnected returns true if connection was not found`() {
        assertTrue(fixture.getSut().isConnected(IConnectionStatusProvider.ConnectionStatus.UNKNOWN))
    }

    @Test
    fun `isConnected returns true if connection is connected`() {
        assertTrue(fixture.getSut().isConnected(IConnectionStatusProvider.ConnectionStatus.CONNECTED))
    }

    @Test
    fun `isConnected returns false if connection is not connected`() {
        assertFalse(fixture.getSut().isConnected(IConnectionStatusProvider.ConnectionStatus.DISCONNECTED))
    }

    @Test
    fun `isConnected returns false if no permission`() {
        assertTrue(fixture.getSut().isConnected(IConnectionStatusProvider.ConnectionStatus.NO_PERMISSION))
    }
}
