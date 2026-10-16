package io.sentry.compose.navigation3

import io.sentry.IScopes
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock

class NavSessionTest {

  @Test
  fun `dispose() - disposes nav scopes before cleaning up the back stack observer`() {
    val fixture = Fixture.create()
    val navSession = fixture.navSession

    navSession.dispose()

    inOrder(fixture.navScopes, fixture.observer) {
      verify(fixture.navScopes).dispose()
      verify(fixture.observer).cleanup()
    }
  }

  private class Fixture {
    val navScopes = mock<NavScopes>()
    val observer = mock<BackStackObserver<String>>()
    val navSession =
      NavSession(
        scopes = mock<IScopes>(),
        options = SentryNavOptions(),
        coordinator = mock<NavLeaseCoordinator>(),
        entryMapper = mock<ForwardingBackStackEntryMapper<String>>(),
        navScopes = navScopes,
        observer = observer,
      )

    companion object {
      fun create(): Fixture = Fixture()
    }
  }
}
