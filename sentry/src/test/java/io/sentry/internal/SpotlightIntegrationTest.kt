package io.sentry.internal

import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryOptions.BeforeEnvelopeCallback
import io.sentry.SpotlightIntegration
import io.sentry.util.PlatformTestManipulator
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotlightIntegrationTest {
    @Test
    fun `Integration does not register before-envelope callback when disabled`() {
        val options =
            SentryOptions().apply {
                isEnableSpotlight = false
            }

        val spotlight = SpotlightIntegration()
        spotlight.register(mock<IScopes>(), options)

        assertNull(options.beforeEnvelopeCallback)
    }

    @Test
    fun `Integration does not register before-envelope callback when before-envelope is already set`() {
        val envelopeCallback = mock<BeforeEnvelopeCallback>()
        val options =
            SentryOptions().apply {
                isEnableSpotlight = true
                beforeEnvelopeCallback = envelopeCallback
            }

        val spotlight = SpotlightIntegration()
        spotlight.register(mock<IScopes>(), options)

        assertEquals(envelopeCallback, options.beforeEnvelopeCallback)
    }

    @Test
    fun `Integration does register and un-register before-envelope callback`() {
        val options =
            SentryOptions().apply {
                isEnableSpotlight = true
            }

        val spotlight = SpotlightIntegration()
        spotlight.register(mock<IScopes>(), options)

        assertEquals(options.beforeEnvelopeCallback, spotlight)
        spotlight.close()
        assertNull(options.beforeEnvelopeCallback)
    }

    @Test
    fun `spotlight connection url falls back to platform defaults`() {
        val spotlight = SpotlightIntegration()

        PlatformTestManipulator.pretendIsAndroid(true)
        assertEquals("http://10.0.2.2:8969/stream", spotlight.spotlightConnectionUrl)

        PlatformTestManipulator.pretendIsAndroid(false)
        assertEquals("http://localhost:8969/stream", spotlight.spotlightConnectionUrl)
    }

    @Test
    fun `respects spotlight connection url set via options`() {
        val options =
            SentryOptions().apply {
                isEnableSpotlight = true
                spotlightConnectionUrl = "http://example.com:1234/stream"
            }

        val spotlight = SpotlightIntegration()
        spotlight.register(mock<IScopes>(), options)

        assertEquals("http://example.com:1234/stream", spotlight.spotlightConnectionUrl)
    }
}
