package io.sentry.android.core.internal.util

import io.sentry.transport.ICurrentDateProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class DebouncerTest {

    private class Fixture : ICurrentDateProvider {

        var currentTimeMs: Long = 0

        override fun getCurrentTimeMillis(): Long = currentTimeMs

        fun getDebouncer(waitTimeMs: Long = 3000): Debouncer {
            return Debouncer(this, waitTimeMs)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `set up`() {
        fixture.currentTimeMs = 0
    }

    @Test
    fun `Debouncer should not debounce on the first check`() {
        val debouncer = fixture.getDebouncer()
        assertFalse(debouncer.checkForDebounce())
    }

    @Test
    fun `Debouncer should not debounce if wait time is 0`() {
        val debouncer = fixture.getDebouncer(0)
        assertFalse(debouncer.checkForDebounce())
        assertFalse(debouncer.checkForDebounce())
        assertFalse(debouncer.checkForDebounce())
    }

    @Test
    fun `Debouncer should signal debounce if the second invocation is too early`() {
        fixture.currentTimeMs = 1000
        val debouncer = fixture.getDebouncer(3000)
        assertFalse(debouncer.checkForDebounce())

        fixture.currentTimeMs = 3999
        assertTrue(debouncer.checkForDebounce())
    }

    @Test
    fun `Debouncer should not signal debounce if the second invocation is late enough`() {
        fixture.currentTimeMs = 1000
        val debouncer = fixture.getDebouncer(3000)
        assertFalse(debouncer.checkForDebounce())

        fixture.currentTimeMs = 4000
        assertFalse(debouncer.checkForDebounce())
    }
}
