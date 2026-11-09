package io.sentry.compose.navigation3.lifecycle

import io.sentry.IScope
import io.sentry.PropagationContext

/**
 * Holds nav data (i.e., the screen name and nav context) staged for publication to a shared
 * [IScope].
 *
 * Only state that occupies a single slot in the shared `IScope` is staged. Nav events (i.e.,
 * breadcrumbs) are excluded as they only append to the scope and are recorded immediately,
 * irrespective of lease ownership.
 *
 * Propagation context isn't staged either, since an earlier propagation context mustn't overwrite
 * the scope's current one. Instead, a rotation requested without the lease, or implied by a nav
 * transaction that couldn't be started without it, is [deferred][deferPropagationContextRotation]
 * and performed once when the lease is acquired. As a result, any destination reached in the
 * meantime still gets a fresh trace.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Apply the thread safety policy from [NavScopes].
 */
internal class StagedNavData {

  /** The staged screen name, published or not. */
  var screen: String? = null
    private set

  private var navigationContext: Any? = null

  private var hasNavigationContextUpdate = false
  private var hasScreenUpdate = false

  /** Whether a propagation context rotation was requested while this instance lacked the lease. */
  private var hasDeferredPropagationContextRotation = false

  // These may differ from the staged values after background updates. We keep the exact published
  // references so cleanup preserves host replacements, including structurally equal replacements.
  private var publishedNavigationContext: Any? = null
  private var publishedScreen: String? = null

  /** Stages [screen] for publication, replacing any previously staged screen. */
  fun stageScreen(screen: String?) {
    this.screen = screen
    hasScreenUpdate = true
  }

  /** Stages [value] as the nav context for publication, replacing any previously staged value. */
  fun stageNavigationContext(value: Any?) {
    navigationContext = value
    hasNavigationContextUpdate = true
  }

  /** Requests a propagation context rotation for when this instance next [publishes][publishTo]. */
  fun deferPropagationContextRotation() {
    hasDeferredPropagationContextRotation = true
  }

  /**
   * Publishes the staged screen and nav context, and performs any deferred propagation context
   * rotation.
   *
   * Only staged values are published, so values the host app or other integrations set (e.g., a
   * screen name while screen tracking is off) survive.
   */
  fun publishTo(realScope: IScope) {
    if (hasScreenUpdate) {
      publishScreen(realScope)
    }
    if (hasNavigationContextUpdate) {
      publishNavigationContext(realScope)
    }
    if (hasDeferredPropagationContextRotation) {
      realScope.withPropagationContext { realScope.propagationContext = PropagationContext() }
      hasDeferredPropagationContextRotation = false
    }
  }

  /** Publishes the staged screen to [realScope]. */
  fun publishScreen(realScope: IScope) {
    publishedScreen = screen
    realScope.screen = screen
  }

  /** Publishes the staged nav context to [realScope]. */
  fun publishNavigationContext(realScope: IScope) {
    publishedNavigationContext = navigationContext
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, navigationContext)
  }

  /**
   * Clears the screen and nav context this instance published, unless something else has replaced
   * them in [realScope] since.
   */
  fun clearPublishedStateFrom(realScope: IScope) {
    if (publishedScreen != null && realScope.screen === publishedScreen) {
      realScope.screen = null
    }
    if (
      publishedNavigationContext != null &&
        realScope.contexts[NAVIGATION_CONTEXT_KEY] === publishedNavigationContext
    ) {
      // Use setContexts so scope observers also receive the removal.
      realScope.setContexts(NAVIGATION_CONTEXT_KEY, null as Any?)
    }
    publishedScreen = null
    publishedNavigationContext = null
  }
}
