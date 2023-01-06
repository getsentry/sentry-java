package io.sentry.android.core

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

        sut.setAppStartTime(0, Date(0))
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

        sut.setAppStartTime(0, Date(0))
        sut.setAppStartEnd()

        assertNull(sut.appStartInterval)
    }

    @Test
    fun `do not overwrite app start values if already set`() {
        val sut = AppStartState.getInstance()

        val date = Date()
        sut.setAppStartTime(0, date)
        sut.setAppStartTime(1, Date())

        assertSame(date, sut.appStartTime)
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

        val date = Date()
        sut.setAppStartTime(100, date)
        sut.setAppStartEnd(500)
        sut.setColdStart(true)

        assertEquals(400, sut.appStartInterval)
    }

    @Test
    fun `getAppStartInterval returns null if more than 60s`() {
        val sut = AppStartState.getInstance()

        val date = Date()
        sut.setAppStartTime(100, date)
        sut.setAppStartEnd(60100)
        sut.setColdStart(true)

        assertNull(sut.appStartInterval)
    }

    @Test
    fun `setFirstActivityCreatedMillis can make getAppStartInterval returns right interval even if more than 60s`() {
        val sut = AppStartState.getInstance()

        // performance provider init
        val date = Date()
        sut.setAppStartTime(100, date)
        // first activity displayed cost 100ms
        sut.setColdStart(true)
        sut.reset()
        sut.setFirstActivityCreatedMillis(100)
        // sentry init after 60000ms
        sut.setAppStartTime(60100, Date())
        // next activity displayed cost 200ms
        sut.setAppStartEnd(60300)

        assertEquals(300, sut.appStartInterval)
    }
}
