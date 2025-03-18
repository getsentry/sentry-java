package io.sentry

import io.sentry.protocol.SentryId
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NoOpContinuousProfilerTest {
    private var profiler = NoOpContinuousProfiler.getInstance()

    @Test
    fun `start does not throw`() =
        profiler.startProfileSession(mock(), mock())

    @Test
    fun `stop does not throw`() =
        profiler.stopProfileSession(mock())

    @Test
    fun `isRunning returns false`() {
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `close does not throw`() =
        profiler.close()

    @Test
    fun `getProfilerId returns Empty SentryId`() {
        assertEquals(profiler.profilerId, SentryId.EMPTY_ID)
    }

    @Test
    fun `reevaluateSampling does not throw`() {
        profiler.reevaluateSampling()
    }
}
