package io.sentry.spotlight

import io.sentry.IScopes
import io.sentry.ISerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.SentryOptions.BeforeEnvelopeCallback
import io.sentry.protocol.SentryId
import io.sentry.util.SpotlightPlatformTestManipulator
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify

class SpotlightIntegrationTest {
  @Test
  fun `Integration does not register before-envelope callback when disabled`() {
    val options = SentryOptions().apply { isEnableSpotlight = false }

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
    val options = SentryOptions().apply { isEnableSpotlight = true }

    val spotlight = SpotlightIntegration()
    spotlight.register(mock<IScopes>(), options)

    assertEquals(options.beforeEnvelopeCallback, spotlight)
    spotlight.close()
    assertNull(options.beforeEnvelopeCallback)
  }

  @Test
  fun `spotlight connection url falls back to platform defaults`() {
    val spotlight = SpotlightIntegration()

    SpotlightPlatformTestManipulator.pretendIsAndroid(true)
    assertEquals("http://10.0.2.2:8969/stream", spotlight.spotlightConnectionUrl)

    SpotlightPlatformTestManipulator.pretendIsAndroid(false)
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

  @Test
  fun `serializes the envelope before execute returns`() {
    val serializer = mock<ISerializer>()
    val options =
      SentryOptions().apply {
        isEnableSpotlight = true
        // Nothing listens there, so the background post fails fast.
        spotlightConnectionUrl = "http://localhost:1/stream"
        setSerializer(serializer)
      }
    val envelope = SentryEnvelope(SentryId(), null, emptyList())
    val spotlight = SpotlightIntegration()
    spotlight.register(mock<IScopes>(), options)

    spotlight.execute(envelope, null)

    verify(serializer).serialize(same(envelope), any<OutputStream>())
    spotlight.close()
  }
}
