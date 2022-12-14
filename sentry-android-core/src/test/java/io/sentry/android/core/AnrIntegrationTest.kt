package io.sentry.android.core

import android.content.Context
import io.sentry.IHub
import io.sentry.exception.ExceptionMechanismException
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnrIntegrationTest {

    private class Fixture {
        val context = mock<Context>()
        val hub = mock<IHub>()
        val options = SentryAndroidOptions()

        fun getSut(): AnrIntegration {
            return AnrIntegration(context)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `before each test`() {
        val sut = fixture.getSut()
        // watch dog is static and has shared state
        sut.close()
    }

    @Test
    fun `When ANR is enabled, ANR watch dog should be started`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)

        assertNotNull(sut.anrWatchDog)
        assertTrue((sut.anrWatchDog as ANRWatchDog).isAlive)
    }

    @Test
    fun `When ANR is disabled, ANR should not be started`() {
        val sut = fixture.getSut()
        fixture.options.isAnrEnabled = false

        sut.register(fixture.hub, fixture.options)

        assertNull(sut.anrWatchDog)
    }

    @Test
    fun `When ANR watch dog is triggered, it should capture exception`() {
        val sut = fixture.getSut()

        sut.reportANR(fixture.hub, mock(), getApplicationNotResponding())

        verify(fixture.hub).captureException(any())
    }

    @Test
    fun `When ANR integration is closed, watch dog should stop`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)

        assertNotNull(sut.anrWatchDog)

        sut.close()

        assertNull(sut.anrWatchDog)
    }

    @Test
    fun `When ANR watch dog is triggered, snapshot flag should be true`() {
        val sut = fixture.getSut()

        sut.reportANR(fixture.hub, mock(), getApplicationNotResponding())

        verify(fixture.hub).captureException(
            check {
                val ex = it as ExceptionMechanismException
                assertTrue(ex.isSnapshot)
            }
        )
    }

    private fun getApplicationNotResponding(): ApplicationNotResponding {
        return ApplicationNotResponding("ApplicationNotResponding", Thread.currentThread())
    }
}
