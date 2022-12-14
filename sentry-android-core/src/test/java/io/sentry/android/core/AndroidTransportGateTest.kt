package io.sentry.android.core

import io.sentry.android.core.internal.util.ConnectivityChecker
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidTransportGateTest {

    private class Fixture {
        fun getSut(): AndroidTransportGate {
            return AndroidTransportGate(mock(), mock())
        }
    }
    private val fixture = Fixture()

    @Test
    fun `isConnected doesn't throw`() {
        assertNotNull(fixture.getSut().isConnected)
    }

    @Test
    fun `isConnected returns true if connection was not found`() {
        assertTrue(fixture.getSut().isConnected(ConnectivityChecker.Status.UNKNOWN))
    }

    @Test
    fun `isConnected returns true if connection is connected`() {
        assertTrue(fixture.getSut().isConnected(ConnectivityChecker.Status.CONNECTED))
    }

    @Test
    fun `isConnected returns false if connection is not connected`() {
        assertFalse(fixture.getSut().isConnected(ConnectivityChecker.Status.NOT_CONNECTED))
    }

    @Test
    fun `isConnected returns false if no permission`() {
        assertTrue(fixture.getSut().isConnected(ConnectivityChecker.Status.NO_PERMISSION))
    }
}
