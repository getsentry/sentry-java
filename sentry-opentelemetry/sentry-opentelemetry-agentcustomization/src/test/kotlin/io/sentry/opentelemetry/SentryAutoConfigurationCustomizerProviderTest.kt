package io.sentry.opentelemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SentryAutoConfigurationCustomizerProviderTest {
  private val provider = SentryAutoConfigurationCustomizerProvider()
  private val config = mock<ConfigProperties>()

  @Test
  fun `current GraphQL operation name property takes precedence`() {
    whenever(config.getString(currentProperty)).thenReturn("false")
    whenever(config.getString(deprecatedProperty)).thenReturn("true")

    assertThat(provider.customizeOpenTelemetryDefaults(config)).isEmpty()
  }

  @Test
  fun `migrates deprecated GraphQL operation name property`() {
    whenever(config.getString(deprecatedProperty)).thenReturn("false")

    assertThat(provider.customizeOpenTelemetryDefaults(config))
      .containsExactly(currentProperty, "false")
  }

  @Test
  fun `enables GraphQL operation names by default`() {
    assertThat(provider.customizeOpenTelemetryDefaults(config))
      .containsExactly(currentProperty, "true")
  }

  private companion object {
    const val currentProperty = "otel.instrumentation.graphql.operation-name-in-span-name.enabled"
    const val deprecatedProperty =
      "otel.instrumentation.graphql.add-operation-name-to-span-name.enabled"
  }
}
