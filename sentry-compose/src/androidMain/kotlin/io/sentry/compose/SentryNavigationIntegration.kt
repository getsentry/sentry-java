package io.sentry.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import io.sentry.Breadcrumb
import io.sentry.ITransaction
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.android.navigation.SentryNavigationListener
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion

private const val TRACE_ORIGIN_APPENDIX = "jetpack_compose"

internal class SentryLifecycleObserver(
  private val navController: NavController,
  private var navListener: NavController.OnDestinationChangedListener =
    SentryNavigationListener(traceOriginAppendix = TRACE_ORIGIN_APPENDIX),
) : LifecycleEventObserver {
  private var isListening = false

  private companion object {
    init {
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-compose", BuildConfig.VERSION_NAME)
    }
  }

  init {
    addIntegrationToSdkVersion("ComposeNavigation")
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    if (event == Lifecycle.Event.ON_RESUME) {
      startListening()
    } else if (event == Lifecycle.Event.ON_PAUSE) {
      stopListening()
    }
  }

  fun syncWithLifecycle(lifecycle: Lifecycle) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      startListening()
    } else {
      stopListening()
    }
  }

  fun updateNavListener(navListener: NavController.OnDestinationChangedListener) {
    if (this.navListener === navListener) {
      return
    }

    val wasListening = isListening
    stopListening()
    this.navListener = navListener
    if (wasListening) {
      startListening()
    }
  }

  fun dispose() {
    stopListening()
  }

  private fun startListening() {
    if (isListening) {
      return
    }

    navController.addOnDestinationChangedListener(navListener)
    isListening = true
  }

  private fun stopListening() {
    navController.removeOnDestinationChangedListener(navListener)
    isListening = false
  }
}

/**
 * A [DisposableEffect] that captures a [Breadcrumb] and starts an [ITransaction] and sends them to
 * Sentry for every navigation event when being attached to the respective [NavHostController].
 *
 * @param navListener An instance of a [SentryNavigationListener] that is shared with other sentry
 *   integrations, like the fragment navigation integration.
 */
@Composable
@NonRestartableComposable
public fun NavHostController.withSentryObservableEffect(
  navListener: SentryNavigationListener
): NavHostController {
  val navListenerSnapshot by rememberUpdatedState(navListener)

  // As described in
  // https://developer.android.com/codelabs/jetpack-compose-advanced-state-side-effects#6
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val observer =
    remember(lifecycle, this) {
      SentryLifecycleObserver(this@withSentryObservableEffect, navListener = navListenerSnapshot)
    }

  SideEffect {
    observer.updateNavListener(navListenerSnapshot)
  }

  DisposableEffect(lifecycle, observer) {
    lifecycle.addObserver(observer)
    observer.syncWithLifecycle(lifecycle)

    onDispose {
      observer.dispose()
      lifecycle.removeObserver(observer)
    }
  }
  return this
}

/**
 * A [DisposableEffect] that captures a [Breadcrumb] and starts an [ITransaction] and sends them to
 * Sentry for every navigation event when being attached to the respective [NavHostController]. This
 * version of withSentryObservableEffect should be used if you are working purely with Compose.
 *
 * @param enableNavigationBreadcrumbs Whether the integration should capture breadcrumbs for
 *   navigation events.
 * @param enableNavigationTracing Whether the integration should start a new [ITransaction] with
 *   [SentryOptions.idleTimeout] for navigation events.
 */
@Composable
@NonRestartableComposable
public fun NavHostController.withSentryObservableEffect(
  enableNavigationBreadcrumbs: Boolean = true,
  enableNavigationTracing: Boolean = true,
): NavHostController {
  val navListener =
    remember(enableNavigationBreadcrumbs, enableNavigationTracing) {
      SentryNavigationListener(
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        enableNavigationTracing = enableNavigationTracing,
        traceOriginAppendix = TRACE_ORIGIN_APPENDIX,
      )
    }

  return withSentryObservableEffect(navListener = navListener)
}

/**
 * A [DisposableEffect] that captures a [Breadcrumb] and starts an [ITransaction] and sends them to
 * Sentry for every navigation event when being attached to the respective [NavHostController].
 *
 * Used by the sentry android gradle plugin for Jetpack Compose instrumentation.
 */
@Composable
internal fun NavHostController.withSentryObservableEffect(): NavHostController =
  withSentryObservableEffect(enableNavigationBreadcrumbs = true, enableNavigationTracing = true)
