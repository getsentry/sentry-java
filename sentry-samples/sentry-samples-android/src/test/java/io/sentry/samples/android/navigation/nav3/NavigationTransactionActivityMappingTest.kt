package io.sentry.samples.android.navigation.nav3

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.MeasurementValue
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.TransactionInfo
import kotlin.test.Test

class NavigationTransactionActivityMappingTest {

  @Test
  fun `trace uses the Activity tag recorded on the transaction`() {
    val transaction =
      transaction().apply {
        setTag(NAVIGATION_SAMPLE_ACTIVITY_TAG, "Nav3ProductsActivity")
      }

    val trace = transaction.toNavigationTransactionTrace(dsn = null)

    assertThat(trace.activity).isEqualTo("Nav3ProductsActivity")
  }

  @Test
  fun `trace uses an unknown-activity label when the transaction has no Activity tag`() {
    val trace = transaction().toNavigationTransactionTrace(dsn = null)

    assertThat(trace.activity).isEqualTo("Unknown")
  }

  private fun transaction() =
    SentryTransaction(
      "/ProductList",
      1.0,
      2.0,
      emptyList<SentrySpan>(),
      emptyMap<String, MeasurementValue>(),
      TransactionInfo("route"),
    )
}
