package io.sentry.android.core

import io.sentry.SentryInstantDate
import io.sentry.SentryLongDate
import io.sentry.SentryNanotimeDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppStartStateTest {

    @BeforeTest
    fun `reset instance`() {
        AppStartState.getInstance().resetInstance()
    }

    @Test
    fun `appStartInterval returns null if end time is not set`() {
        val sut = AppStartState.getInstance()

        sut.setAppStartTime(0, SentryNanotimeDate(Date(0), 0))
        sut.setColdStart(true)

        assertNull(sut.appStartInterval)
    }

    @Test
    fun `appStartInterval returns null if start time is not set`() {
        val sut = AppStartState.getInstance()

        sut.setAppStartEnd()
        sut.setColdStart(true)

        assertNull(sut.appStartInterval)
    }

    @Test
    fun `appStartInterval returns null if coldStart is not set`() {
        val sut = AppStartState.getInstance()

        sut.setAppStartTime(0, SentryNanotimeDate(Date(0), 0))
        sut.setAppStartEnd()

        assertNull(sut.appStartInterval)
    }

    @Test
    fun `do not overwrite app start values if already set`() {
        val sut = AppStartState.getInstance()

        val date = SentryNanotimeDate()
        sut.setAppStartTime(0, date)
        sut.setAppStartTime(1, SentryInstantDate())

        assertSame(date, sut.appStartTime)
    }

    @Test
    fun `do not overwrite app start end time if already set`() {
        val sut = AppStartState.getInstance()

        sut.setColdStart(true)
        sut.setAppStartTime(1, SentryLongDate(1000000))
        sut.setAppStartEnd(2)
        sut.setAppStartEnd(3)

        assertEquals(0, SentryLongDate(2000000).compareTo(sut.appStartEndTime!!))
    }

    @Test
    fun `do not overwrite cold start value if already set`() {
        val sut = AppStartState.getInstance()

        sut.setColdStart(true)
        sut.setColdStart(false)

        assertTrue(sut.isColdStart!!)
    }

    @Test
    fun `getAppStartInterval returns right calculation`() {
        val sut = AppStartState.getInstance()

        val date = SentryNanotimeDate()
        sut.setAppStartTime(100, date)
        sut.setAppStartEnd(500)
        sut.setColdStart(true)

        assertEquals(400, sut.appStartInterval)
    }

    @Test
    fun `getAppStartInterval returns null if more than 60s`() {
        val sut = AppStartState.getInstance()

        val date = SentryNanotimeDate()
        sut.setAppStartTime(100, date)
        sut.setAppStartEnd(60100)
        sut.setColdStart(true)

        assertNull(sut.appStartInterval)
    }
}
