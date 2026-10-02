package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.PropagationContext
import io.sentry.Scope
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import java.util.UUID
import org.junit.Test

class StagedNavDataTest {

  private val realScope = Scope(SentryOptions.empty())
  private val stagedNavData = StagedNavData()

  private val realTraceId = SentryId(UUID.randomUUID())
  private val stagedTraceId = SentryId(UUID.randomUUID())
  private val updatedTraceId = SentryId(UUID.randomUUID())

  @Test
  fun `propagationContext - stages the supplied context without changing the real scope`() {
    realScope.propagationContext.traceId = realTraceId
    val context = PropagationContext().apply { traceId = stagedTraceId }

    stagedNavData.propagationContext = context

    assertThat(stagedNavData.propagationContext.traceId).isEqualTo(stagedTraceId)
    assertThat(realScope.propagationContext.traceId).isEqualTo(realTraceId)
  }

  @Test
  fun `propagationContext - retains the staged context after the supplied context changes`() {
    val context = PropagationContext().apply { traceId = stagedTraceId }
    stagedNavData.propagationContext = context

    context.traceId = updatedTraceId

    assertThat(stagedNavData.propagationContext.traceId).isEqualTo(stagedTraceId)
  }

  @Test
  fun `getScreen() - reads the staged screen without the nav lease`() {
    stagedNavData.setScreen("Profile", realScope, ownsLease = false)

    val screen = stagedNavData.getScreen(realScope, ownsLease = false)

    assertThat(screen).isEqualTo("Profile")
    assertThat(realScope.screen).isNull()
  }

  @Test
  fun `getScreen() - reads the real screen with the nav lease`() {
    stagedNavData.setScreen("Profile", realScope, ownsLease = false)
    realScope.screen = "Host"

    val screen = stagedNavData.getScreen(realScope, ownsLease = true)

    assertThat(screen).isEqualTo("Host")
  }

  @Test
  fun `setScreen() - publishes the screen immediately with the nav lease`() {
    stagedNavData.setScreen("Profile", realScope, ownsLease = true)

    assertThat(realScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `setScreen() - retains the screen for later publication with the nav lease`() {
    stagedNavData.setScreen("Profile", realScope, ownsLease = true)
    realScope.screen = "Host"

    stagedNavData.publishTo(realScope)

    assertThat(realScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `setScreen() - stages the screen without changing the real scope when the nav lease is absent`() {
    realScope.screen = "Host"

    stagedNavData.setScreen("Profile", realScope, ownsLease = false)

    assertThat(stagedNavData.getScreen(realScope, ownsLease = false)).isEqualTo("Profile")
    assertThat(realScope.screen).isEqualTo("Host")
  }

  @Test
  fun `setScreen() - retains the screen for later publication without the nav lease`() {
    stagedNavData.setScreen("Profile", realScope, ownsLease = false)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `setNavigationContext() - publishes the navigation context immediately with the nav lease`() {
    val navigationContext = mapOf("route" to "Profile")

    stagedNavData.setNavigationContext(navigationContext, realScope, ownsLease = true)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(navigationContext)
  }

  @Test
  fun `setNavigationContext() - retains the navigation context for later publication with the nav lease`() {
    val navigationContext = mapOf("route" to "Profile")
    stagedNavData.setNavigationContext(navigationContext, realScope, ownsLease = true)
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("route" to "Host"))

    stagedNavData.publishTo(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(navigationContext)
  }

  @Test
  fun `setNavigationContext() - leaves the real navigation context unchanged without the nav lease`() {
    val navigationContext = mapOf("route" to "Profile")
    val hostContext = mapOf("route" to "Host")
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, hostContext)

    stagedNavData.setNavigationContext(navigationContext, realScope, ownsLease = false)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(hostContext)
  }

  @Test
  fun `setNavigationContext() - retains the navigation context for later publication without the nav lease`() {
    val navigationContext = mapOf("route" to "Profile")
    stagedNavData.setNavigationContext(navigationContext, realScope, ownsLease = false)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(navigationContext)
  }

  @Test
  fun `withPropagationContext() - returns the changed staged context without changing the real scope`() {
    realScope.propagationContext.traceId = realTraceId

    val updated = stagedNavData.withPropagationContext { it.traceId = stagedTraceId }

    assertThat(updated.traceId).isEqualTo(stagedTraceId)
    assertThat(stagedNavData.propagationContext.traceId).isEqualTo(stagedTraceId)
    assertThat(realScope.propagationContext.traceId).isEqualTo(realTraceId)
  }

  @Test
  fun `withPropagationContext() - retains callback changes for later publication`() {
    stagedNavData.withPropagationContext { it.traceId = stagedTraceId }

    stagedNavData.publishTo(realScope)

    assertThat(realScope.propagationContext.traceId).isEqualTo(stagedTraceId)
  }

  @Test
  fun `addBreadcrumb() - publishes immediately with the nav lease`() {
    val published = mutableListOf<String>()

    stagedNavData.addBreadcrumb(ownsLease = true) { published += "Profile" }

    assertThat(published).containsExactly("Profile")
  }

  @Test
  fun `addBreadcrumb() - buffers only the latest breadcrumb without the nav lease before publication`() {
    val published = mutableListOf<String>()

    stagedNavData.addBreadcrumb(ownsLease = false) { published += "Home" }
    stagedNavData.addBreadcrumb(ownsLease = false) { published += "Profile" }

    assertThat(published).isEmpty()
    stagedNavData.publishPendingBreadcrumb(ownsLease = true)
    assertThat(published).containsExactly("Profile")
  }

  @Test
  fun `addBreadcrumb() - discards breadcrumbs received without the nav lease after first publication`() {
    val published = mutableListOf<String>()
    stagedNavData.addBreadcrumb(ownsLease = true) { published += "Home" }

    stagedNavData.addBreadcrumb(ownsLease = false) { published += "Unseen" }
    stagedNavData.publishPendingBreadcrumb(ownsLease = true)

    assertThat(published).containsExactly("Home")
  }

  @Test
  fun `addBreadcrumb() - replaces a pending breadcrumb when the first leased breadcrumb arrives`() {
    val published = mutableListOf<String>()
    stagedNavData.addBreadcrumb(ownsLease = false) { published += "Unseen" }

    stagedNavData.addBreadcrumb(ownsLease = true) { published += "Profile" }
    stagedNavData.publishPendingBreadcrumb(ownsLease = true)

    assertThat(published).containsExactly("Profile")
  }

  @Test
  fun `publishPendingBreadcrumb() - retains the latest pending breadcrumb without the nav lease`() {
    val published = mutableListOf<String>()
    stagedNavData.addBreadcrumb(ownsLease = false) { published += "Home" }
    stagedNavData.addBreadcrumb(ownsLease = false) { published += "Profile" }

    stagedNavData.publishPendingBreadcrumb(ownsLease = false)

    assertThat(published).isEmpty()
    stagedNavData.publishPendingBreadcrumb(ownsLease = true)
    assertThat(published).containsExactly("Profile")
  }

  @Test
  fun `publishPendingBreadcrumb() - publishes the latest pending breadcrumb once with the nav lease`() {
    val published = mutableListOf<String>()
    stagedNavData.addBreadcrumb(ownsLease = false) { published += "Home" }
    stagedNavData.addBreadcrumb(ownsLease = false) { published += "Profile" }

    stagedNavData.publishPendingBreadcrumb(ownsLease = true)
    stagedNavData.publishPendingBreadcrumb(ownsLease = true)

    assertThat(published).containsExactly("Profile")
  }

  @Test
  fun `publishTo() - publishes the staged screen`() {
    stagedNavData.setScreen("Profile", realScope, ownsLease = false)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `publishTo() - clears the real screen when no screen was staged`() {
    realScope.screen = "Previous"

    stagedNavData.publishTo(realScope)

    assertThat(realScope.screen).isNull()
  }

  @Test
  fun `publishTo() - clears the real screen when the staged screen is null`() {
    realScope.screen = "Previous"
    stagedNavData.setScreen(null, realScope, ownsLease = false)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.screen).isNull()
  }

  @Test
  fun `publishTo() - publishes the staged navigation context`() {
    val navigationContext = mapOf("route" to "Profile")
    stagedNavData.setNavigationContext(navigationContext, realScope, ownsLease = false)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(navigationContext)
  }

  @Test
  fun `publishTo() - clears the real navigation context when none was staged`() {
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("route" to "Previous"))
    realScope.setContexts("host", mapOf("value" to "retained"))

    stagedNavData.publishTo(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
    assertThat(realScope.contexts["host"]).isEqualTo(mapOf("value" to "retained"))
  }

  @Test
  fun `publishTo() - clears the real navigation context when the staged context is null`() {
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("route" to "Previous"))
    stagedNavData.setNavigationContext(null, realScope, ownsLease = false)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
  }

  @Test
  fun `publishTo() - publishes the staged propagation context to the real scope`() {
    realScope.propagationContext.traceId = realTraceId
    val context = PropagationContext().apply { traceId = stagedTraceId }
    stagedNavData.propagationContext = context

    stagedNavData.publishTo(realScope)

    assertThat(realScope.propagationContext.traceId).isEqualTo(stagedTraceId)
  }

  @Test
  fun `publishTo() - preserves the real propagation context when none was staged`() {
    realScope.propagationContext.traceId = realTraceId

    stagedNavData.publishTo(realScope)

    assertThat(realScope.propagationContext.traceId).isEqualTo(realTraceId)
  }

  @Test
  fun `clearPublishedStateFrom() - clears screen and navigation context published by this holder`() {
    stagedNavData.setScreen("Profile", realScope, ownsLease = true)
    stagedNavData.setNavigationContext(mapOf("route" to "Profile"), realScope, ownsLease = true)

    stagedNavData.clearPublishedStateFrom(realScope)

    assertThat(realScope.screen).isNull()
    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
  }

  @Test
  fun `clearPublishedStateFrom() - clears the published screen and navigation context after newer values are staged`() {
    stagedNavData.setScreen("Home", realScope, ownsLease = true)
    stagedNavData.setNavigationContext(mapOf("route" to "Home"), realScope, ownsLease = true)
    stagedNavData.setScreen("Profile", realScope, ownsLease = false)
    stagedNavData.setNavigationContext(
      mapOf("route" to "Profile"),
      realScope,
      ownsLease = false,
    )

    stagedNavData.clearPublishedStateFrom(realScope)

    assertThat(realScope.screen).isNull()
    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
  }

  @Test
  fun `clearPublishedStateFrom() - preserves equal but distinct host replacements`() {
    val publishedScreen = String(charArrayOf('H', 'o', 'm', 'e'))
    val publishedContext = mapOf("route" to "Home")
    stagedNavData.setScreen(publishedScreen, realScope, ownsLease = true)
    stagedNavData.setNavigationContext(publishedContext, realScope, ownsLease = true)
    val hostScreen = String(charArrayOf('H', 'o', 'm', 'e'))
    val hostContext = mapOf("route" to "Home")
    realScope.screen = hostScreen
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, hostContext)

    stagedNavData.clearPublishedStateFrom(realScope)

    assertThat(realScope.screen).isSameInstanceAs(hostScreen)
    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(hostContext)
  }

  @Test
  fun `clearPublishedStateFrom() - preserves the real screen and navigation context before publication`() {
    realScope.screen = "Host"
    val hostContext = mapOf("route" to "Host")
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, hostContext)
    stagedNavData.setScreen("Staged", realScope, ownsLease = false)
    stagedNavData.setNavigationContext(mapOf("route" to "Staged"), realScope, ownsLease = false)

    stagedNavData.clearPublishedStateFrom(realScope)

    assertThat(realScope.screen).isEqualTo("Host")
    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(hostContext)
  }
}
