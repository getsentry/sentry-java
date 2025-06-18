package io.sentry

import io.sentry.FullyDisplayedReporter.FullyDisplayedReporterListener
import io.sentry.test.getProperty
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullyDisplayedReporterTest {
    private val reporter = FullyDisplayedReporter.getInstance()
    private val listeners = reporter.getProperty<MutableList<FullyDisplayedReporterListener>>("listeners")
    private val listener1 = FullyDisplayedReporterListener {}
    private val listener2 = FullyDisplayedReporterListener {}
    private val mockListener1 = mock<FullyDisplayedReporterListener>()
    private val mockListener2 = mock<FullyDisplayedReporterListener>()

    @AfterTest
    fun shutdown() {
        listeners.clear()
    }

    @Test
    fun `reporter can register multiple listeners`() {
        reporter.registerFullyDrawnListener(mock())
        reporter.registerFullyDrawnListener(mock())
        assertEquals(2, listeners.size)
    }

    @Test
    fun `reportFullyDrawn calls all registered listeners`() {
        reporter.registerFullyDrawnListener(mockListener1)
        reporter.registerFullyDrawnListener(mockListener2)
        reporter.reportFullyDrawn()
        verify(mockListener1).onFullyDrawn()
        verify(mockListener2).onFullyDrawn()
    }

    @Test
    fun `reportFullyDrawn removes current listeners`() {
        reporter.registerFullyDrawnListener(listener1)
        reporter.registerFullyDrawnListener(listener2)
        reporter.reportFullyDrawn()
        assertTrue(listeners.isEmpty())
    }
}
