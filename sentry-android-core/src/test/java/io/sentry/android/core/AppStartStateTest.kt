package io.sentry.android.core

import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppStartStateTest {

    @BeforeTest
    fun `reset instance`() {
        AppStartState.getInstance().resetInstance()
    }

    @Test
    fun `appStartInterval returns null if end time is not set`() {
        val sut = AppStartState.getInstance()

        sut.setAppStartTime(0, Date())

        assertNull(sut.appStartInterval)
    }

    @Test
    fun `appStartInterval returns null if start time is not set`() {
        val sut = AppStartState.getInstance()

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
}
