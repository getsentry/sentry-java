package io.sentry

import com.google.common.truth.Truth.assertThat
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SentryOptionsSealTest {
  @AfterTest
  fun tearDown() {
    Sentry.close()
  }

  @Test
  fun `setters apply before the options are sealed`() {
    val options = SentryOptions()
    options.environment = "staging"
    assertThat(options.environment).isEqualTo("staging")
  }

  @Test
  fun `setters are ignored once the options are sealed`() {
    val options = SentryOptions()
    options.environment = "staging"
    options.seal()

    options.environment = "production"

    assertThat(options.environment).isEqualTo("staging")
  }

  @Test
  fun `setters throw once the options are sealed and debug is enabled`() {
    val options = SentryOptions()
    options.isDebug = true
    options.seal()

    assertFailsWith<IllegalStateException> { options.environment = "production" }
  }

  @Test
  fun `unseal makes the options writable again`() {
    val options = SentryOptions()
    options.seal()
    options.unseal()

    options.environment = "production"

    assertThat(options.environment).isEqualTo("production")
  }

  // SpotlightIntegration claims and releases this slot from register()/close(), both of which run
  // after the seal. See the exemption note on the setter.
  @Test
  fun `beforeEnvelopeCallback stays writable after the seal`() {
    val options = SentryOptions()
    val callback = SentryOptions.BeforeEnvelopeCallback { _, _ -> }
    options.seal()

    options.setBeforeEnvelopeCallback(callback)

    assertThat(options.beforeEnvelopeCallback).isSameInstanceAs(callback)
  }

  // The SDK can be restarted with the same options instance, and init has to be able to re-wire it.
  @Test
  fun `restarting the SDK with the same options instance re-opens them for wiring`() {
    val options = SentryOptions()
    options.dsn = "https://key@sentry.io/proj"

    Sentry.init(options)
    Sentry.close()
    Sentry.init(options)

    assertThat(options.executorService.isClosed).isFalse()
    assertThat(Sentry.isEnabled()).isTrue()
  }

  @Test
  fun `Sentry init seals the options it was given`() {
    val options = SentryOptions()
    options.dsn = "https://key@sentry.io/proj"
    Sentry.init(options)

    options.environment = "changed-after-init"

    assertThat(options.environment).isNotEqualTo("changed-after-init")
  }
}
