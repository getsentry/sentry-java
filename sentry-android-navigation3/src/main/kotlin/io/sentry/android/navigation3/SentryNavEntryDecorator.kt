package io.sentry.android.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavEntryDecorator
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.ScopesAdapter
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.TracingUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TRACE_ORIGIN = "auto.navigation.nav3"
private const val NAVIGATION_OP = "navigation"

/**
 * Creates a [NavEntryDecorator] that captures navigation breadcrumbs, starts idle transactions,
 * tracks screen names, and attaches backstack context for Sentry.
 *
 * @param backStack The navigation backstack to observe.
 * @param scopes The Sentry scopes instance.
 * @param enableNavigationBreadcrumbs Whether to capture breadcrumbs for navigation events.
 * @param enableNavigationTracing Whether to start idle transactions for navigation events.
 * @param enableBackstackContext Whether to attach the backstack to the Sentry scope as context.
 * @param maxBackstackSize Maximum number of backstack entries to include in crash context.
 * @param nameExtractor Optional lambda to extract a human-readable route name from a backstack key.
 *   If not provided, defaults to the key's class simple name.
 * @param argumentsExtractor Optional lambda to extract arguments from a backstack key. If not
 *   provided, no arguments are attached.
 */
@Suppress("LongParameterList")
@Composable
public fun <T : Any> rememberSentryNavEntryDecorator(
  backStack: SnapshotStateList<T>,
  scopes: IScopes = ScopesAdapter.getInstance(),
  enableNavigationBreadcrumbs: Boolean = true,
  enableNavigationTracing: Boolean = true,
  enableBackstackContext: Boolean = true,
  maxBackstackSize: Int = 30,
  nameExtractor: ((T) -> String)? = null,
  argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
): NavEntryDecorator<T> {
  val stateHolder = remember {
    SentryNavStateHolder(
      scopes = scopes,
      enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
      enableNavigationTracing = enableNavigationTracing,
      enableBackstackContext = enableBackstackContext,
      maxBackstackSize = maxBackstackSize,
      nameExtractor = nameExtractor,
      argumentsExtractor = argumentsExtractor,
    )
  }

  LaunchedEffect(backStack) {
    snapshotFlow { backStack.toList() }
      .map { it.lastOrNull() }
      .distinctUntilChanged()
      .collectLatest { topKey -> stateHolder.onTopKeyChanged(topKey, backStack) }
  }

  return remember { NavEntryDecorator<T>(decorate = { entry -> entry.Content() }, onPop = {}) }
}

@Suppress("LongParameterList")
internal class SentryNavStateHolder<T : Any>(
  private val scopes: IScopes,
  private val enableNavigationBreadcrumbs: Boolean,
  private val enableNavigationTracing: Boolean,
  private val enableBackstackContext: Boolean,
  private val maxBackstackSize: Int,
  private val nameExtractor: ((T) -> String)?,
  private val argumentsExtractor: ((T) -> Map<String, Any?>)?,
) {
  private var previousTopKey: T? = null
  private var activeTransaction: ITransaction? = null

  private val isPerformanceEnabled
    get() = scopes.options.isTracingEnabled && enableNavigationTracing

  init {
    addIntegrationToSdkVersion("Navigation3")
    SentryIntegrationPackageStorage.getInstance()
      .addPackage("maven:io.sentry:sentry-android-navigation3", BuildConfig.VERSION_NAME)
  }

  fun onTopKeyChanged(topKey: T?, backStack: List<T>) {
    if (topKey == null) return

    val routeName = resolveRouteName(topKey)
    val arguments = resolveArguments(topKey)

    addBreadcrumb(topKey, routeName, arguments)

    if (scopes.options.isEnableScreenTracking) {
      scopes.configureScope { it.screen = routeName }
    }

    startTracing(routeName, arguments)
    updateBackstackContext(backStack)

    previousTopKey = topKey
  }

  private fun addBreadcrumb(topKey: T, routeName: String, arguments: Map<String, Any?>) {
    if (!enableNavigationBreadcrumbs) return

    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        previousTopKey?.let { prevKey ->
          data["from"] = resolveRouteName(prevKey)
          val fromArgs = resolveArguments(prevKey)
          if (fromArgs.isNotEmpty()) {
            data["from_arguments"] = fromArgs
          }
        }

        data["to"] = routeName
        if (arguments.isNotEmpty()) {
          data["to_arguments"] = arguments
        }

        level = SentryLevel.INFO
      }

    val hint = Hint()
    hint.set(TypeCheckHint.ANDROID_NAV3_DESTINATION, topKey)
    scopes.addBreadcrumb(breadcrumb, hint)
  }

  private fun startTracing(routeName: String, arguments: Map<String, Any?>) {
    if (!isPerformanceEnabled) {
      TracingUtils.startNewTrace(scopes)
      return
    }

    if (activeTransaction != null) {
      stopTracing()
    }

    val transactionOptions =
      TransactionOptions().also {
        it.isWaitForChildren = true
        it.idleTimeout = scopes.options.idleTimeout

        val deadlineTimeoutMillis = scopes.options.deadlineTimeout
        it.deadlineTimeout = if (deadlineTimeoutMillis <= 0) null else deadlineTimeoutMillis

        it.isTrimEnd = true
      }

    val transaction =
      scopes.startTransaction(
        TransactionContext(routeName, TransactionNameSource.ROUTE, NAVIGATION_OP),
        transactionOptions,
      )

    transaction.spanContext.origin = TRACE_ORIGIN

    if (arguments.isNotEmpty()) {
      transaction.setData("arguments", arguments)
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

    scopes.configureScope { scope ->
      scope.withTransaction { tx ->
        if (tx == activeTransaction) {
          scope.clearTransaction()
        }
      }
    }

    activeTransaction = null
  }

  private fun updateBackstackContext(backStack: List<T>) {
    if (!enableBackstackContext) return

    val backstackData =
      backStack.takeLast(maxBackstackSize).map { key ->
        buildMap<String, Any?> {
          put("route", resolveRouteName(key))
          val args = resolveArguments(key)
          if (args.isNotEmpty()) {
            put("args", args)
          }
        }
      }

    scopes.configureScope { scope ->
      scope.setContexts("navigation", mapOf("backstack" to backstackData))
    }
  }

  internal fun resolveRouteName(key: T): String {
    val name = nameExtractor?.invoke(key) ?: key::class.simpleName ?: "unknown"
    return "/$name"
  }

  private fun resolveArguments(key: T): Map<String, Any?> {
    return argumentsExtractor?.invoke(key) ?: emptyMap()
  }
}
