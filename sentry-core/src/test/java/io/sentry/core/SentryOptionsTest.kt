package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryOptionsTest {
    @Test
    fun `when options is initialized, logger is not null`() {
        assertNotNull(SentryOptions().logger)
    }

    @Test
    fun `when logger is set to null, logger getter returns not null`() {
        val options = SentryOptions()
        options.logger = null
        assertNotNull(options.logger)
    }

    @Test
    fun `when options is initialized, diagnostic level is default`() {
        assertEquals(SentryOptions.DEFAULT_DIAGNOSTIC_LEVEL, SentryOptions().diagnosticLevel)
    }

    @Test
    fun `when diagnostic is set to null, diagnostic getter returns no default`() {
        val options = SentryOptions()
        options.diagnosticLevel = null
        assertEquals(SentryOptions.DEFAULT_DIAGNOSTIC_LEVEL, SentryOptions().diagnosticLevel)
    }

    @Test
    fun `when options is initialized, debug is false`() {
        assertFalse(SentryOptions().isDebug)
    }

    @Test
    fun `when options is initialized, integrations contain UncaughtExceptionHandlerIntegration`() {
        assertTrue(SentryOptions().integrations.any { it is UncaughtExceptionHandlerIntegration })
    }

    @Test
    fun `when options is initialized, default maxBreadcrumb is 100`() =
        assertEquals(100, SentryOptions().maxBreadcrumbs)

    @Test
    fun `when setMaxBreadcrumb is called, overrides default`() {
        val options = SentryOptions()
        options.maxBreadcrumbs = 1
        assertEquals(1, options.maxBreadcrumbs)
    }

    @Test
    fun `when options is initialized, default sampling is null`() =
        assertNull(SentryOptions().sampling)

    @Test
    fun `when setSampling is called, overrides default`() {
        val options = SentryOptions()
        options.sampling = 0.5
        assertEquals(0.5, options.sampling)
    }

    @Test
    fun `when setSampling is called with null, disables it`() {
        val options = SentryOptions()
        options.sampling = null
        assertNull(options.sampling)
    }

    @Test
    fun `when setSampling is set to higher than 1_0, setter throws`() {
        assertFailsWith<IllegalArgumentException> { SentryOptions().sampling = 1.0000000000001 }
    }

    @Test
    fun `when setSampling is set to lower than 0, setter throws`() {
        assertFailsWith<IllegalArgumentException> { SentryOptions().sampling = -0.0000000000001 }
    }

    @Test
    fun `when setSampling is set to exactly 0, setter throws`() {
        assertFailsWith<IllegalArgumentException> { SentryOptions().sampling = 0.0 }
    }
}
