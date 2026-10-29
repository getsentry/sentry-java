package io.sentry.compose.navigation3.lifecycle

import com.google.common.truth.Truth.assertThat
import io.sentry.Scope
import io.sentry.SentryOptions
import org.junit.Test

class StagedNavDataTest {

  private val realScope = Scope(SentryOptions.empty())
  private val stagedNavData = StagedNavData()

  @Test
  fun `stageScreen() - stages the screen without changing the real scope`() {
    realScope.screen = "Host"

    stagedNavData.stageScreen("Profile")

    assertThat(stagedNavData.screen).isEqualTo("Profile")
    assertThat(realScope.screen).isEqualTo("Host")
  }

  @Test
  fun `publishScreen() - publishes the staged screen`() {
    stagedNavData.stageScreen("Profile")

    stagedNavData.publishScreen(realScope)

    assertThat(realScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `stageNavigationContext() - leaves the real navigation context unchanged`() {
    val hostContext = mapOf("route" to "Host")
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, hostContext)

    stagedNavData.stageNavigationContext(mapOf("route" to "Profile"))

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(hostContext)
  }

  @Test
  fun `publishNavigationContext() - publishes the staged navigation context`() {
    val navigationContext = mapOf("route" to "Profile")
    stagedNavData.stageNavigationContext(navigationContext)

    stagedNavData.publishNavigationContext(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(navigationContext)
  }

  @Test
  fun `publishTo() - publishes the staged screen`() {
    stagedNavData.stageScreen("Profile")

    stagedNavData.publishTo(realScope)

    assertThat(realScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `publishTo() - republishes an already published screen that was since replaced`() {
    stagedNavData.stageScreen("Profile")
    stagedNavData.publishScreen(realScope)
    realScope.screen = "Host"

    stagedNavData.publishTo(realScope)

    assertThat(realScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `publishTo() - leaves the real screen alone when no screen was staged`() {
    realScope.screen = "Host"

    stagedNavData.publishTo(realScope)

    assertThat(realScope.screen).isEqualTo("Host")
  }

  @Test
  fun `publishTo() - clears the real screen when the staged screen is null`() {
    realScope.screen = "Previous"
    stagedNavData.stageScreen(null)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.screen).isNull()
  }

  @Test
  fun `publishTo() - publishes the staged navigation context`() {
    val navigationContext = mapOf("route" to "Profile")
    stagedNavData.stageNavigationContext(navigationContext)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(navigationContext)
  }

  @Test
  fun `publishTo() - republishes an already published navigation context that was since replaced`() {
    val navigationContext = mapOf("route" to "Profile")
    stagedNavData.stageNavigationContext(navigationContext)
    stagedNavData.publishNavigationContext(realScope)
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("route" to "Host"))

    stagedNavData.publishTo(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(navigationContext)
  }

  @Test
  fun `publishTo() - leaves the real navigation context alone when none was staged`() {
    val hostContext = mapOf("route" to "Host")
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, hostContext)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(hostContext)
  }

  @Test
  fun `publishTo() - clears the real navigation context when the staged context is null`() {
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("route" to "Previous"))
    stagedNavData.stageNavigationContext(null)

    stagedNavData.publishTo(realScope)

    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
  }

  @Test
  fun `clearPublishedStateFrom() - preserves a host screen set after publication without a staged screen`() {
    stagedNavData.publishTo(realScope)
    realScope.screen = "Host"

    stagedNavData.clearPublishedStateFrom(realScope)

    assertThat(realScope.screen).isEqualTo("Host")
  }

  @Test
  fun `clearPublishedStateFrom() - clears screen and navigation context published by this holder`() {
    stagedNavData.stageScreen("Profile")
    stagedNavData.stageNavigationContext(mapOf("route" to "Profile"))
    stagedNavData.publishTo(realScope)

    stagedNavData.clearPublishedStateFrom(realScope)

    assertThat(realScope.screen).isNull()
    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
  }

  @Test
  fun `clearPublishedStateFrom() - clears the published screen and navigation context after newer values are staged`() {
    stagedNavData.stageScreen("Home")
    stagedNavData.stageNavigationContext(mapOf("route" to "Home"))
    stagedNavData.publishTo(realScope)
    stagedNavData.stageScreen("Profile")
    stagedNavData.stageNavigationContext(mapOf("route" to "Profile"))

    stagedNavData.clearPublishedStateFrom(realScope)

    assertThat(realScope.screen).isNull()
    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
  }

  @Test
  fun `clearPublishedStateFrom() - preserves equal but distinct host replacements`() {
    stagedNavData.stageScreen(String(charArrayOf('H', 'o', 'm', 'e')))
    stagedNavData.stageNavigationContext(mapOf("route" to "Home"))
    stagedNavData.publishTo(realScope)
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
    stagedNavData.stageScreen("Staged")
    stagedNavData.stageNavigationContext(mapOf("route" to "Staged"))

    stagedNavData.clearPublishedStateFrom(realScope)

    assertThat(realScope.screen).isEqualTo("Host")
    assertThat(realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(hostContext)
  }

  @Test
  fun `publishTo() - leaves the real propagation context alone when no rotation was deferred`() {
    val realContext = realScope.propagationContext

    stagedNavData.publishTo(realScope)

    assertThat(realScope.propagationContext).isSameInstanceAs(realContext)
  }

  @Test
  fun `publishTo() - performs a deferred propagation context rotation once`() {
    stagedNavData.deferPropagationContextRotation()
    stagedNavData.deferPropagationContextRotation()
    val realContext = realScope.propagationContext

    stagedNavData.publishTo(realScope)
    val rotatedContext = realScope.propagationContext
    stagedNavData.publishTo(realScope)

    assertThat(rotatedContext).isNotSameInstanceAs(realContext)
    assertThat(realScope.propagationContext).isSameInstanceAs(rotatedContext)
  }
}
