package io.sentry.android.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.ScopesAdapter
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private const val TRACE_ORIGIN = "auto.navigation.navigation3"
private const val NAVIGATION_OP = "navigation"

/**
 * A [DisposableEffect] that observes a [SnapshotStateList] back stack and captures a [Breadcrumb]
 * and starts an [ITransaction] for each navigation event, sending them to Sentry.
 *
 * This integration is designed for Android Navigation 3 which uses a back stack-based approach with
 * Compose state observation instead of traditional listeners.
 *
 * @param T The type of keys in the back stack
 * @param enableNavigationBreadcrumbs Whether the integration should capture breadcrumbs for
 *   navigation events.
 * @param enableNavigationTracing Whether the integration should start a new idle [ITransaction]
 *   with [SentryOptions.idleTimeout] for navigation events.
 * @param keyToRoute A function to extract a route name from a back stack key. Defaults to
 *   [Any.toString].
 * @param scopes The [IScopes] instance to use for capturing events. Defaults to the singleton
 *   instance.
 * @return The same [SnapshotStateList] for chaining.
 */
@Composable
@NonRestartableComposable
public fun <T> SnapshotStateList<T>.withSentryObservableEffect(
  enableNavigationBreadcrumbs: Boolean = true,
  enableNavigationTracing: Boolean = true,
  keyToRoute: (T) -> String? = { it.toString() },
  scopes: IScopes = ScopesAdapter.getInstance(),
): SnapshotStateList<T> {
  val enableBreadcrumbsSnapshot by rememberUpdatedState(enableNavigationBreadcrumbs)
  val enableTracingSnapshot by rememberUpdatedState(enableNavigationTracing)
  val keyToRouteSnapshot by rememberUpdatedState(keyToRoute)
  val scopesSnapshot by rememberUpdatedState(scopes)

  DisposableEffect(this) {
    addIntegrationToSdkVersion("Navigation3")

    val observer =
      SentryBackStackObserver(
        backStack = this@withSentryObservableEffect,
        enableNavigationBreadcrumbs = enableBreadcrumbsSnapshot,
        enableNavigationTracing = enableTracingSnapshot,
        keyToRoute = keyToRouteSnapshot,
        scopes = scopesSnapshot,
      )

    val scope =
      kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
      )
    val job = observer.observe(scope)

    onDispose { job.cancel() }
  }

  return this
}

/** Internal observer that monitors back stack changes and creates Sentry events. */
internal class SentryBackStackObserver<T>(
  private val backStack: SnapshotStateList<T>,
  private val enableNavigationBreadcrumbs: Boolean,
  private val enableNavigationTracing: Boolean,
  private val keyToRoute: (T) -> String?,
  private val scopes: IScopes,
) {
  private var previousKey: T? = null
  private var activeTransaction: ITransaction? = null

  private val isPerformanceEnabled: Boolean
    get() = scopes.options.isTracingEnabled && enableNavigationTracing

  init {
    SentryIntegrationPackageStorage.getInstance()
      .addPackage("maven:io.sentry:sentry-android-navigation3", BuildConfig.VERSION_NAME)
  }

  fun observe(scope: kotlinx.coroutines.CoroutineScope) =
    snapshotFlow { backStack.toList() }
      .drop(1) // Skip initial state
      .onEach { currentStack ->
        val currentKey = currentStack.lastOrNull()
        if (currentKey != null && currentKey != previousKey) {
          handleNavigation(currentKey, currentStack)
          previousKey = currentKey
        }
      }
      .launchIn(scope)

  internal fun handleNavigation(currentKey: T, currentStack: List<T>) {
    val currentRoute = keyToRoute(currentKey)
    if (currentRoute != null) {
      addBreadcrumb(currentRoute, currentStack)

      if (scopes.options.isEnableScreenTracking) {
        scopes.configureScope { it.screen = currentRoute }
      }

      startTracing(currentRoute, currentStack)
    }
  }

  private fun addBreadcrumb(toRoute: String, currentStack: List<T>) {
    if (!enableNavigationBreadcrumbs) {
      return
    }

    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        val fromKey = previousKey
        if (fromKey != null) {
          val fromRoute = keyToRoute(fromKey)
          if (fromRoute != null) {
            data["from"] = fromRoute
          }
        }

        data["to"] = toRoute

        // Capture back stack keys as a list
        val backStackKeys = currentStack.mapNotNull { keyToRoute(it) }
        if (backStackKeys.isNotEmpty()) {
          data["back_stack"] = backStackKeys
        }

        level = SentryLevel.INFO
      }

    val hint = Hint()
    hint.set(TypeCheckHint.ANDROID_NAV_DESTINATION, toRoute)
    scopes.addBreadcrumb(breadcrumb, hint)
  }

  private fun startTracing(routeName: String, currentStack: List<T>) {
    if (!isPerformanceEnabled) {
      io.sentry.util.TracingUtils.startNewTrace(scopes)
      return
    }

    // we can only have one nav transaction at a time
    if (activeTransaction != null) {
      stopTracing()
    }

    val transactionOptions =
      TransactionOptions().also {
        it.isWaitForChildren = true
        it.idleTimeout = scopes.options.idleTimeout

        // Set deadline timeout based on configured option
        val deadlineTimeoutMillis = scopes.options.deadlineTimeout
        // No deadline when zero or negative value is set
        it.deadlineTimeout = if (deadlineTimeoutMillis <= 0) null else deadlineTimeoutMillis

        it.isTrimEnd = true
      }

    val transaction =
      scopes.startTransaction(
        TransactionContext(routeName, TransactionNameSource.ROUTE, NAVIGATION_OP),
        transactionOptions,
      )

    transaction.spanContext.origin = TRACE_ORIGIN

    // Capture back stack keys as data
    val backStackKeys = currentStack.mapNotNull { keyToRoute(it) }
    if (backStackKeys.isNotEmpty()) {
      transaction.setData("back_stack", backStackKeys)
    }

    scopes.configureScope { scope ->
      scope.withTransaction { tx ->
        if (tx == null) {
          scope.transaction = transaction
        }
      }
    }
    activeTransaction = transaction
  }

  private fun stopTracing() {
    val status = activeTransaction?.status ?: SpanStatus.OK
    activeTransaction?.finish(status)

    // clear transaction from scope so others can bind to it
    scopes.configureScope { scope ->
      scope.withTransaction { tx ->
        if (tx == activeTransaction) {
          scope.clearTransaction()
        }
      }
    }

    activeTransaction = null
  }
}
