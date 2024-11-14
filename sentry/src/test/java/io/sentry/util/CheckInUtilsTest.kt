package io.sentry.util

import io.sentry.CheckInStatus
import io.sentry.IHub
import io.sentry.MonitorConfig
import io.sentry.MonitorSchedule
import io.sentry.MonitorScheduleUnit
import io.sentry.Sentry
import io.sentry.SentryOptions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.AssertionError
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
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
            whenever(hub.options).thenReturn(SentryOptions())
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
    fun `sends check-in for wrapped supplier with environment`() {
        Mockito.mockStatic(Sentry::class.java).use { sentry ->
            val hub = mock<IHub>()
            sentry.`when`<Any> { Sentry.getCurrentHub() }.thenReturn(hub)
            whenever(hub.options).thenReturn(SentryOptions())
            val returnValue = CheckInUtils.withCheckIn("monitor-1", "environment-1") {
                return@withCheckIn "test1"
            }

            assertEquals("test1", returnValue)
            inOrder(hub) {
                verify(hub).pushScope()
                verify(hub).configureScope(any())
                verify(hub).captureCheckIn(
                    check {
                        assertEquals("monitor-1", it.monitorSlug)
                        assertEquals("environment-1", it.environment)
                        assertEquals(CheckInStatus.IN_PROGRESS.apiName(), it.status)
                    }
                )
                verify(hub).captureCheckIn(
                    check {
                        assertEquals("monitor-1", it.monitorSlug)
                        assertEquals("environment-1", it.environment)
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

    @Test
    fun `sends check-in for wrapped supplier with upsert`() {
        Mockito.mockStatic(Sentry::class.java).use { sentry ->
            val hub = mock<IHub>()
            sentry.`when`<Any> { Sentry.getCurrentHub() }.thenReturn(hub)
            whenever(hub.options).thenReturn(SentryOptions())
            val monitorConfig = MonitorConfig(MonitorSchedule.interval(7, MonitorScheduleUnit.DAY))
            val returnValue = CheckInUtils.withCheckIn("monitor-1", monitorConfig) {
                "test1"
            }

            assertEquals("test1", returnValue)
            inOrder(hub) {
                verify(hub).pushScope()
                verify(hub).configureScope(any())
                verify(hub).captureCheckIn(
                    check {
                        assertEquals("monitor-1", it.monitorSlug)
                        assertSame(monitorConfig, it.monitorConfig)
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
    fun `sends check-in for wrapped supplier with upsert and thresholds`() {
        Mockito.mockStatic(Sentry::class.java).use { sentry ->
            val hub = mock<IHub>()
            sentry.`when`<Any> { Sentry.getCurrentHub() }.thenReturn(hub)
            whenever(hub.options).thenReturn(SentryOptions())
            val monitorConfig = MonitorConfig(MonitorSchedule.interval(7, MonitorScheduleUnit.DAY)).apply {
                failureIssueThreshold = 10
                recoveryThreshold = 20
            }
            val returnValue = CheckInUtils.withCheckIn("monitor-1", monitorConfig) {
                "test1"
            }

            assertEquals("test1", returnValue)
            inOrder(hub) {
                verify(hub).pushScope()
                verify(hub).configureScope(any())
                verify(hub).captureCheckIn(
                    check {
                        assertEquals("monitor-1", it.monitorSlug)
                        assertSame(monitorConfig, it.monitorConfig)
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
    fun `sets defaults for MonitorConfig from SentryOptions`() {
        Mockito.mockStatic(Sentry::class.java).use { sentry ->
            val hub = mock<IHub>()
            sentry.`when`<Any> { Sentry.getCurrentHub() }.thenReturn(hub)
            whenever(hub.options).thenReturn(
                SentryOptions().apply {
                    cron = SentryOptions.Cron().apply {
                        defaultCheckinMargin = 20
                        defaultMaxRuntime = 30
                        defaultTimezone = "America/New_York"
                        defaultFailureIssueThreshold = 40
                        defaultRecoveryThreshold = 50
                    }
                }
            )

            val monitorConfig = MonitorConfig(MonitorSchedule.interval(7, MonitorScheduleUnit.DAY))

            assertEquals(20, monitorConfig.checkinMargin)
            assertEquals(30, monitorConfig.maxRuntime)
            assertEquals("America/New_York", monitorConfig.timezone)
            assertEquals(40, monitorConfig.failureIssueThreshold)
            assertEquals(50, monitorConfig.recoveryThreshold)
        }
    }

    @Test
    fun `defaults for MonitorConfig from SentryOptions can be overridden`() {
        Mockito.mockStatic(Sentry::class.java).use { sentry ->
            val hub = mock<IHub>()
            sentry.`when`<Any> { Sentry.getCurrentHub() }.thenReturn(hub)
            whenever(hub.options).thenReturn(
                SentryOptions().apply {
                    cron = SentryOptions.Cron().apply {
                        defaultCheckinMargin = 20
                        defaultMaxRuntime = 50
                        defaultTimezone = "America/New_York"
                    }
                }
            )

            val monitorConfig = MonitorConfig(MonitorSchedule.interval(7, MonitorScheduleUnit.DAY)).apply {
                checkinMargin = 10
                maxRuntime = 30
                timezone = "America/Los_Angeles"
            }

            assertEquals(10, monitorConfig.checkinMargin)
            assertEquals(30, monitorConfig.maxRuntime)
            assertEquals("America/Los_Angeles", monitorConfig.timezone)
        }
    }
}
