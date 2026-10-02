package io.sentry.samples.android.navigation.nav3

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class NavigationTransactionHistoryStoreTest {

  @Test
  fun `records transactions from multiple activities in one history`() {
    val store = NavigationTransactionHistoryStore()

    store.add(trace(activity = "Nav3ProductsActivity", name = "/ProductDetail"))
    store.add(trace(activity = "Nav3CheckoutActivity", name = "/Checkout"))

    assertThat(store.transactions.map { it.activity })
      .containsExactly("Nav3CheckoutActivity", "Nav3ProductsActivity")
      .inOrder()
    assertThat(store.transactions.map { it.name })
      .containsExactly("/Checkout", "/ProductDetail")
      .inOrder()
  }

  @Test
  fun `retains only the most recent transactions`() {
    val store = NavigationTransactionHistoryStore(transactionLimit = 2)

    store.add(trace(activity = "FirstActivity", name = "/First"))
    store.add(trace(activity = "SecondActivity", name = "/Second"))
    store.add(trace(activity = "ThirdActivity", name = "/Third"))

    assertThat(store.transactions.map { it.name }).containsExactly("/Third", "/Second").inOrder()
  }

  @Test
  fun `clearing starts the next sample session with empty history`() {
    val store = NavigationTransactionHistoryStore()
    store.add(trace(activity = "Nav3ProductsActivity", name = "/ProductList"))

    store.clear()

    assertThat(store.transactions).isEmpty()
  }

  private fun trace(activity: String, name: String) =
    NavigationTransactionTrace(
      activity = activity,
      name = name,
      operation = "navigation",
      eventId = "event-id",
      traceId = "trace-id",
      status = "OK",
      tab = "Multi-Activity",
      durationMillis = 1.0,
      sentryUrl = null,
      spans = emptyList(),
    )
}
