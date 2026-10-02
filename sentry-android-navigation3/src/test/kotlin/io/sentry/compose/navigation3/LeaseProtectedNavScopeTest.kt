package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.sentry.PropagationContext
import io.sentry.Scope
import io.sentry.SentryOptions
import org.junit.Test

class LeaseProtectedNavScopeTest {

  @Test
  fun `getScreen() - reads the real screen with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    leaseProtectedNavScope.screen = "Staged"
    fixture.realScope.screen = "Host"

    val screen = leaseProtectedNavScope.screen

    assertThat(screen).isEqualTo("Host")
  }

  @Test
  fun `getScreen() - reads the staged screen without the nav lease`() {
    val fixture = Fixture.create()
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    fixture.realScope.screen = "Host"
    fixture.stagedNavData.setScreen("Staged", fixture.realScope, ownsLease = false)

    val screen = leaseProtectedNavScope.screen

    assertThat(screen).isEqualTo("Staged")
  }

  @Test
  fun `setScreen() - publishes the screen with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope

    leaseProtectedNavScope.screen = "Profile"

    assertThat(fixture.realScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `setScreen() - stages the screen without the nav lease`() {
    val fixture = Fixture.create()
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    fixture.realScope.screen = "Host"

    leaseProtectedNavScope.screen = "Profile"

    assertThat(fixture.realScope.screen).isEqualTo("Host")
    assertThat(leaseProtectedNavScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `setContexts() - publishes the navigation context with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    val navigationContext = mapOf("route" to "Profile")

    leaseProtectedNavScope.setContexts(NAVIGATION_CONTEXT_KEY, navigationContext)

    assertThat(fixture.realScope.contexts[NAVIGATION_CONTEXT_KEY])
      .isSameInstanceAs(navigationContext)
  }

  @Test
  fun `setContexts() - stages the navigation context without the nav lease`() {
    val fixture = Fixture.create()
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    val hostContext = mapOf("route" to "Host")
    val navigationContext = mapOf("route" to "Profile")
    fixture.realScope.setContexts(NAVIGATION_CONTEXT_KEY, hostContext)

    leaseProtectedNavScope.setContexts(NAVIGATION_CONTEXT_KEY, navigationContext)

    assertThat(fixture.realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(hostContext)
    fixture.stagedNavData.publishTo(fixture.realScope)
    assertThat(fixture.realScope.contexts[NAVIGATION_CONTEXT_KEY])
      .isSameInstanceAs(navigationContext)
  }

  @Test
  fun `setContexts() - delegates unrelated contexts without the nav lease`() {
    val fixture = Fixture.create()
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    val hostContext = mapOf("value" to "retained")

    leaseProtectedNavScope.setContexts("host", hostContext)

    assertThat(fixture.realScope.contexts["host"]).isSameInstanceAs(hostContext)
  }

  @Test
  fun `getPropagationContext() - reads the real context with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    val realContext = fixture.realScope.propagationContext

    val context = leaseProtectedNavScope.propagationContext

    assertThat(context).isSameInstanceAs(realContext)
  }

  @Test
  fun `getPropagationContext() - reads the staged context without the nav lease`() {
    val fixture = Fixture.create()
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    val stagedContext = fixture.stagedNavData.propagationContext

    val context = leaseProtectedNavScope.propagationContext

    assertThat(context).isSameInstanceAs(stagedContext)
  }

  @Test
  fun `setPropagationContext() - publishes and stages the context with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    val context = PropagationContext()

    leaseProtectedNavScope.propagationContext = context

    assertThat(fixture.realScope.propagationContext.traceId).isEqualTo(context.traceId)
    assertThat(fixture.stagedNavData.propagationContext.traceId).isEqualTo(context.traceId)
  }

  @Test
  fun `setPropagationContext() - stages the context without the nav lease`() {
    val fixture = Fixture.create()
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    val realContext = fixture.realScope.propagationContext
    val context = PropagationContext()

    leaseProtectedNavScope.propagationContext = context

    assertThat(fixture.stagedNavData.propagationContext.traceId).isEqualTo(context.traceId)
    assertThat(fixture.realScope.propagationContext).isSameInstanceAs(realContext)
  }

  @Test
  fun `withPropagationContext() - updates the real and staged contexts with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    val traceId = PropagationContext().traceId

    val updated = leaseProtectedNavScope.withPropagationContext { it.traceId = traceId }

    assertThat(updated.traceId).isEqualTo(traceId)
    assertThat(fixture.realScope.propagationContext.traceId).isEqualTo(traceId)
    assertThat(fixture.stagedNavData.propagationContext.traceId).isEqualTo(traceId)
  }

  @Test
  fun `withPropagationContext() - updates only the staged context without the nav lease`() {
    val fixture = Fixture.create()
    val leaseProtectedNavScope = fixture.leaseProtectedNavScope
    val realContext = fixture.realScope.propagationContext
    val traceId = PropagationContext().traceId

    val updated = leaseProtectedNavScope.withPropagationContext { it.traceId = traceId }

    assertThat(updated.traceId).isEqualTo(traceId)
    assertThat(fixture.stagedNavData.propagationContext.traceId).isEqualTo(traceId)
    assertThat(fixture.realScope.propagationContext).isSameInstanceAs(realContext)
  }

  private class Fixture
  private constructor(
    val realScope: Scope,
    val stagedNavData: StagedNavData,
    val leaseProtectedNavScope: LeaseProtectedNavScope,
  ) {

    companion object {

      fun create(ownsLease: Boolean = false): Fixture {
        val realScope = Scope(SentryOptions.empty())
        val stagedNavData = StagedNavData()
        val coordinator = NavLeaseCoordinator()
        val owner = Any()
        coordinator.registerCandidate(
          owner,
          Lifecycle.State.RESUMED,
          { stagedNavData.publishTo(realScope) },
        )
        if (ownsLease) {
          coordinator.onBackStackUpdate(owner, realScope)
        }

        return Fixture(
          realScope,
          stagedNavData,
          LeaseProtectedNavScope(realScope, owner, stagedNavData, coordinator),
        )
      }
    }
  }
}
