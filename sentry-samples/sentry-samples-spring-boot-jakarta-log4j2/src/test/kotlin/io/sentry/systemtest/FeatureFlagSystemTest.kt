package io.sentry.systemtest

import io.sentry.protocol.FeatureFlag
import io.sentry.systemtest.util.FeatureFlagResponse
import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Before

class FeatureFlagSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `check feature flag includes flag in transaction`() {
    val restClient = testHelper.restClient
    val response: FeatureFlagResponse? = restClient.checkFeatureFlag("new-checkout-flow")

    assertEquals(200, restClient.lastKnownStatusCode)
    assertNotNull(response)
    assertEquals("new-checkout-flow", response!!.flagKey)
    assertEquals(true, response.value)

    // Verify feature flag is included in the transaction
    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "GET /feature-flag/check/{flagKey}" &&
        testHelper.doesTransactionHave(
          transaction,
          op = "http.server",
          featureFlag = FeatureFlag("flag.evaluation.new-checkout-flow", true),
        )
    }
  }

  @Test
  fun `error with feature flag includes flag in error event and transaction`() {
    val restClient = testHelper.restClient
    restClient.errorWithFeatureFlag("beta-features")
    assertEquals(500, restClient.lastKnownStatusCode)

    // Verify feature flag is included in the error event
    testHelper.ensureErrorReceived { event ->
      testHelper.doesEventHaveExceptionMessage(
        event,
        "Error occurred with feature flag: beta-features = true",
      ) && testHelper.doesEventHaveFlag(event, "beta-features", true)
    }

    // Verify feature flag is also included in the transaction
    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "GET /feature-flag/error/{flagKey}" &&
        testHelper.doesTransactionHave(
          transaction,
          op = "http.server",
          featureFlag = FeatureFlag("flag.evaluation.beta-features", true),
        )
    }
  }

  @Test
  fun `check non-existent feature flag returns default value`() {
    val restClient = testHelper.restClient
    val response: FeatureFlagResponse? = restClient.checkFeatureFlag("non-existent-flag")

    assertEquals(200, restClient.lastKnownStatusCode)
    assertNotNull(response)
    assertEquals("non-existent-flag", response!!.flagKey)
    assertEquals(false, response.value) // Default value

    // Verify transaction is still created
    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "GET /feature-flag/check/{flagKey}"
    }
  }

  @Test
  fun `check feature flag with false value`() {
    val restClient = testHelper.restClient
    val response: FeatureFlagResponse? = restClient.checkFeatureFlag("experimental-feature")

    assertEquals(200, restClient.lastKnownStatusCode)
    assertNotNull(response)
    assertEquals("experimental-feature", response!!.flagKey)
    assertEquals(false, response.value)

    // Verify feature flag with false value is included in transaction
    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "GET /feature-flag/check/{flagKey}" &&
        testHelper.doesTransactionHave(
          transaction,
          op = "http.server",
          featureFlag = FeatureFlag("flag.evaluation.experimental-feature", false),
        )
    }
  }
}
