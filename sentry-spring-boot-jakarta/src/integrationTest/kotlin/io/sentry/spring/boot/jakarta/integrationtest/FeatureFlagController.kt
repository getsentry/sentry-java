package io.sentry.spring.boot.jakarta.integrationtest

import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.providers.memory.Flag
import dev.openfeature.sdk.providers.memory.InMemoryProvider
import io.sentry.openfeature.SentryOpenFeatureHook
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

open class FeatureFlagResponse(var flagKey: String? = null, var value: Boolean = false)

@RestController
@RequestMapping("/feature-flag/")
open class FeatureFlagController(private val openFeature: OpenFeatureAPI) {
  @GetMapping("check/{flagKey}")
  open fun check(@PathVariable("flagKey") flagKey: String): FeatureFlagResponse =
    FeatureFlagResponse(flagKey, evaluate(flagKey))

  @GetMapping("error/{flagKey}")
  open fun error(@PathVariable("flagKey") flagKey: String): String {
    val value = evaluate(flagKey)
    throw RuntimeException("Error occurred with feature flag: $flagKey = $value")
  }

  private fun evaluate(flagKey: String): Boolean =
    openFeature.client.getBooleanValue(flagKey, false)
}

@Configuration
open class OpenFeatureConfig {
  /** Evaluations go through [SentryOpenFeatureHook], which records them on the current scope. */
  @Bean
  open fun openFeatureAPI(): OpenFeatureAPI =
    OpenFeatureAPI.getInstance().apply {
      setProviderAndWait(
        InMemoryProvider(
          mapOf(
            "new-checkout-flow" to booleanFlag(true),
            "experimental-feature" to booleanFlag(false),
            "beta-features" to booleanFlag(true),
          )
        )
      )
      addHooks(SentryOpenFeatureHook())
    }

  private fun booleanFlag(value: Boolean): Flag<Boolean> =
    Flag.builder<Boolean>().variant("value", value).defaultVariant("value").build()
}
