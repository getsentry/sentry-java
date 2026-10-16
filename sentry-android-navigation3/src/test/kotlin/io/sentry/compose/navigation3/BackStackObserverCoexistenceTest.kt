package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import io.sentry.IScope
import io.sentry.ISentryClient
import io.sentry.Scope
import io.sentry.ScopeType
import io.sentry.Scopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.protocol.TransactionNameSource
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers how a [BackStackObserver] coexists with other observers writing to the same scope. */
class BackStackObserverCoexistenceTest {

  private data class HomeScreen(val id: String = "home")

  private data class ProfileScreen(val userId: String)

  private data class CartScreen(val productId: String)

  private class Fixture {
    val options =
      SentryOptions().apply {
        dsn = "http://key@localhost/proj"
        defaultScopeType = ScopeType.CURRENT
        setTracesSampleRate(1.0)
        idleTimeout = null
        deadlineTimeout = 0
      }
    val scope = Scope(options)
    val scopes = Scopes(scope, Scope(options), Scope(options), "coexistence test")

    init {
      val client = mock<ISentryClient>()
      whenever(client.isEnabled).thenReturn(true)
      scopes.bindClient(client)
    }

    fun getSut(enableScreenTracking: Boolean = true): BackStackObserver<Any> {
      options.isEnableScreenTracking = enableScreenTracking
      return BackStackObserver(
        scopes = scopes,
        options = SentryNavOptions(),
        entryMapper =
          ForwardingBackStackEntryMapper {
            BackStackEntryMapper { entry -> SentryBackStackEntry(entry::class.simpleName!!) }
          },
      )
    }
  }

  @Test
  fun `ON_RESUME republishes screen and nav context replaced by another writer`() {
    val fixture = Fixture()
    val sut = fixture.getSut()
    sut.onBackStackChanged(listOf(HomeScreen()))
    fixture.scope.screen = "Other"
    fixture.scope.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("other" to true))

    sut.resume()

    assertThat(fixture.scope.screen).isEqualTo("/HomeScreen")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/HomeScreen")))
  }

  @Test
  fun `ON_RESUME leaves values this observer never published untouched`() {
    val fixture = Fixture()
    val sut = fixture.getSut(enableScreenTracking = false)
    sut.onBackStackChanged(listOf(HomeScreen()))
    fixture.scope.screen = "HostScreen"

    sut.resume()

    assertThat(fixture.scope.screen).isEqualTo("HostScreen")
  }

  @Test
  fun `ON_RESUME rebinds an unfinished nav transaction only when the scope is free`() {
    val fixture = Fixture()
    val sut = fixture.getSut()
    sut.onBackStackChanged(listOf(HomeScreen()))
    val navTransaction = requireNotNull(fixture.scope.transaction)
    val other =
      SentryTracer(
        TransactionContext("other", TransactionNameSource.CUSTOM, "ui.load"),
        fixture.scopes,
      )
    fixture.scope.transaction = other

    sut.resume()
    assertThat(fixture.scope.transaction).isSameInstanceAs(other)

    fixture.scope.clearTransaction()
    sut.resume()
    assertThat(fixture.scope.transaction).isSameInstanceAs(navTransaction)
  }

  @Test
  fun `ON_RESUME takes the scope back from another observer's nav transaction`() {
    val fixture = Fixture()
    val returning = fixture.getSut()
    returning.onBackStackChanged(listOf(HomeScreen()))
    val returningTransaction = requireNotNull(fixture.scope.transaction)
    val other = fixture.getSut()
    other.onBackStackChanged(listOf(CartScreen("1")))
    val otherTransaction = requireNotNull(fixture.scope.transaction)

    returning.resume()

    assertThat(fixture.scope.transaction).isSameInstanceAs(returningTransaction)
    assertThat(otherTransaction.isFinished).isFalse()
  }

  @Test
  fun `an obscured observer holds its new top until the active observer is cleaned up`() {
    val fixture = Fixture()
    val outgoing = fixture.getSut()
    outgoing.onBackStackChanged(listOf(HomeScreen()))
    val outgoingTransaction = requireNotNull(fixture.scope.transaction)
    val incoming = fixture.getSut()
    incoming.onBackStackChanged(listOf(CartScreen("1")))
    val incomingTransaction = requireNotNull(fixture.scope.transaction)

    outgoing.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("1")))

    assertThat(outgoingTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(incomingTransaction)
    assertThat(fixture.scope.screen).isEqualTo("/CartScreen")

    incoming.cleanup()

    assertThat(incomingTransaction.isFinished).isTrue()
    assertThat(outgoingTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction?.name).isEqualTo("/ProfileScreen")
    assertThat(fixture.scope.screen).isEqualTo("/ProfileScreen")
  }

  @Test
  fun `an empty back stack only clears screen and nav context this observer published`() {
    val fixture = Fixture()
    val outgoing = fixture.getSut()
    outgoing.onBackStackChanged(listOf(HomeScreen()))
    val incoming = fixture.getSut()
    incoming.onBackStackChanged(listOf(CartScreen("1")))

    outgoing.onBackStackChanged(emptyList())

    assertThat(fixture.scope.screen).isEqualTo("/CartScreen")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/CartScreen")))
  }

  private fun BackStackObserver<*>.resume() {
    onStateChanged(mock<LifecycleOwner>(), Lifecycle.Event.ON_RESUME)
  }

  private fun IScope.navigationBackStack(): List<Map<String, Any?>>? {
    val navigationContext = contexts[NAVIGATION_CONTEXT_KEY] as? Map<*, *> ?: return null

    @Suppress("UNCHECKED_CAST")
    return navigationContext["Back Stack"] as? List<Map<String, Any?>>
  }
}
