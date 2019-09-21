package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class SentryOptionsTest {
    @Test
    fun `when options is initialized, logger is not null`() {
        assertNotNull(SentryOptions().logger)
    }

    @Test
    fun `when logger is set to null, logger getter returns not null`() {
        var options = SentryOptions()
        options.logger = null
        assertNotNull(options.logger)
    }

    @Test
    fun `when options is initialized, diagnostic level is default`() {
        assertEquals(SentryOptions.DEFAULT_DIAGNOSTIC_LEVEL, SentryOptions().diagnosticLevel)
    }

    @Test
    fun `when diagnostic is set to null, diagnostic getter returns no default`() {
        var options = SentryOptions()
        options.diagnosticLevel = null
        assertEquals(SentryOptions.DEFAULT_DIAGNOSTIC_LEVEL, SentryOptions().diagnosticLevel)
    }

    @Test
    fun `when options is initialized, debug is false`() {
        assertFalse(SentryOptions().isDebug)
    }
}
