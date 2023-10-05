package io.sentry.util

import io.sentry.CheckInStatus
import io.sentry.IHub
import io.sentry.Sentry
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import java.lang.AssertionError
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckInUtilsTest {

    @Test
    fun `ignores exact match`() {
        assertTrue(CheckInUtils.isIgnored(listOf("slugA"), "slugA"))
    }

    @Test
    fun `ignores regex match`() {
        assertTrue(CheckInUtils.isIgnored(listOf("slug-.*"), "slug-A"))
    }

    @Test
    fun `does not ignore if ignored list is null`() {
        assertFalse(CheckInUtils.isIgnored(null, "slugA"))
    }

    @Test
    fun `does not ignore if ignored list is empty`() {
        assertFalse(CheckInUtils.isIgnored(emptyList(), "slugA"))
    }

    @Test
    fun `does not ignore if slug is not in ignored list`() {
        assertFalse(CheckInUtils.isIgnored(listOf("slugB"), "slugA"))
    }

    @Test
    fun `does not ignore if slug is does not match ignored list`() {
        assertFalse(CheckInUtils.isIgnored(listOf("slug-.*"), "slugA"))
    }

    @Test
    fun `sends check-in for wrapped supplier`() {
        Mockito.mockStatic(Sentry::class.java).use { sentry ->
            val hub = mock<IHub>()
            sentry.`when`<Any> { Sentry.getCurrentHub() }.thenReturn(hub)
            val returnValue = CheckInUtils.withCheckIn("monitor-1") {
                return@withCheckIn "test1"
            }

            assertEquals("test1", returnValue)
            inOrder(hub) {
                verify(hub).pushScope()
                verify(hub).configureScope(any())
                verify(hub).captureCheckIn(
                    check {
                        assertEquals("monitor-1", it.monitorSlug)
                        assertEquals(CheckInStatus.IN_PROGRESS.apiName(), it.status)
                    }
                )
                verify(hub).captureCheckIn(
                    check {
                        assertEquals("monitor-1", it.monitorSlug)
                        assertEquals(CheckInStatus.OK.apiName(), it.status)
                    }
                )
                verify(hub).popScope()
            }
        }
    }

    @Test
    fun `sends check-in for wrapped supplier with exception`() {
        Mockito.mockStatic(Sentry::class.java).use { sentry ->
            val hub = mock<IHub>()
            sentry.`when`<Any> { Sentry.getCurrentHub() }.thenReturn(hub)

            try {
                CheckInUtils.withCheckIn("monitor-1") {
                    throw RuntimeException("thrown on purpose")
                }
                throw AssertionError("expected exception to be rethrown")
            } catch (e: Exception) {
                assertEquals("thrown on purpose", e.message)
            }

            inOrder(hub) {
                verify(hub).pushScope()
                verify(hub).configureScope(any())
                verify(hub).captureCheckIn(
                    check {
                        assertEquals("monitor-1", it.monitorSlug)
                        assertEquals(CheckInStatus.IN_PROGRESS.apiName(), it.status)
                    }
                )
                verify(hub).captureCheckIn(
                    check {
                        assertEquals("monitor-1", it.monitorSlug)
                        assertEquals(CheckInStatus.ERROR.apiName(), it.status)
                    }
                )
                verify(hub).popScope()
            }
        }
    }
}
