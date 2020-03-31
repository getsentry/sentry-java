package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTransportGateTest {

    private lateinit var context: Context
    private lateinit var transportGate: AndroidTransportGate

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        transportGate = AndroidTransportGate(context, mock())
    }

    @Test
    fun `isSendingAllowed is not null`() {
        assertNotNull(transportGate.isConnected)
    }

    @Test
    fun `isConnected returns true if connection was not found or no permission`() {
        assertTrue(transportGate.isConnected(null))
    }

    @Test
    fun `isConnected returns true if connection is connected`() {
        assertTrue(transportGate.isConnected(true))
    }

    @Test
    fun `isConnected returns false if connection is not connected`() {
        assertFalse(transportGate.isConnected(false))
    }
}
