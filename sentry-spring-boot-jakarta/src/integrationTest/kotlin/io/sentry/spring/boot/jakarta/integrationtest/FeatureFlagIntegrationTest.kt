package io.sentry.spring.boot.jakarta.integrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.FeatureFlag
import io.sentry.test.event
import io.sentry.test.hasFlag
import io.sentry.test.hasOp
import io.sentry.test.transaction
import kotlin.test.Test

/** Feature flags evaluated through OpenFeature end up on the transaction and on errors. */
class FeatureFlagIntegrationTest : IntegrationTest() {

  @Test
  fun `evaluated flag is on the transaction`() {
    assertThat(check("new-checkout-flow")).isTrue()

    assertThat(
        awaitTransaction("new-checkout-flow")
          .hasOp("http.server", FeatureFlag("flag.evaluation.new-checkout-flow", true))
      )
      .isTrue()
  }

  @Test
  fun `flag evaluated to false is on the transaction`() {
    assertThat(check("experimental-feature")).isFalse()

    assertThat(
        awaitTransaction("experimental-feature")
          .hasOp("http.server", FeatureFlag("flag.evaluation.experimental-feature", false))
      )
      .isTrue()
  }

  @Test
  fun `unknown flag falls back to its default`() {
    assertThat(check("non-existent-flag")).isFalse()

    awaitTransaction("non-existent-flag")
  }

  @Test
  fun `flag evaluated before an error is on the error and the transaction`() {
    val response =
      restTemplate.getForEntity("/feature-flag/error/beta-features", String::class.java)
    assertThat(response.statusCode.value()).isEqualTo(500)

    val error =
      server
        .awaitEnvelope {
          it.event()?.exceptions?.any { e ->
            e.value == "Error occurred with feature flag: beta-features = true"
          } == true
        }
        .event()!!
    assertThat(error.hasFlag("beta-features", true)).isTrue()

    val transaction =
      server
        .awaitEnvelope {
          it.transaction()?.let { t ->
            t.transaction == "GET /feature-flag/error/{flagKey}" &&
              t.contexts.trace?.data?.containsKey("flag.evaluation.beta-features") == true
          } == true
        }
        .transaction()!!
    assertThat(transaction.hasOp("http.server", FeatureFlag("flag.evaluation.beta-features", true)))
      .isTrue()
  }

  private fun check(flagKey: String): Boolean {
    val response =
      restTemplate.getForEntity("/feature-flag/check/$flagKey", FeatureFlagResponse::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.flagKey).isEqualTo(flagKey)
    return response.body!!.value
  }

  /**
   * Every check request shares a transaction name, so this picks [flagKey]'s by the request URL.
   */
  private fun awaitTransaction(flagKey: String) =
    server
      .awaitEnvelope {
        it.transaction()?.let { t ->
          t.transaction == "GET /feature-flag/check/{flagKey}" &&
            t.request?.url?.endsWith("/feature-flag/check/$flagKey") == true
        } == true
      }
      .transaction()!!
}
