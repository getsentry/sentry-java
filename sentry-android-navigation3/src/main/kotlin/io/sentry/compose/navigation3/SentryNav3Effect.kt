package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import org.jetbrains.annotations.ApiStatus

/**
 * A composable side-effect that lets you easily generate Sentry events from a Nav3 backstack.
 * Configure it via [options] and launch it in the composable that owns your `NavDisplay`.
 *
 * ```kotlin
 *  @Composable
 *  fun AppNavigation() {
 *  // TODO ADAM: rememberSaveable?
 *    val navBackStack = remember { mutableStateListOf<Route>(Home) }
 *
 *    // Call before NavDisplay so destination effects can attach work to the route transaction.
 *    SentryNav3Effect(
 *      backStack = navBackStack,
 *      options =
 *        SentryNav3Options().apply {
 *          maxCapturedBackStackEntries = 10
 *        },
 *      nameExtractor = { route -> route.extractName() },
 *      argumentsExtractor = { route -> route.extractArgument() },
 *    )
 *
 *    // Configure your NavDisplay like usual after SentryNav3Effect.
 *    NavDisplay(
 *      backStack = navBackStack,
 *      ...
 *    )
 *  }
 * ```
 *
 * Under the hood it observes updates to your [backStack] and converts them into Sentry events via
 * the provided [scopes] instance.
 *
 * **Gotchas**
 *
 * *Privacy / PII*: Values returned from [nameExtractor] and [argumentsExtractor] are sent to Sentry
 * in breadcrumbs, navigation state, and navigation transactions and are ***not*** scrubbed by the
 * Sentry SDK. Only return route names and arguments that are known to be safe or have been
 * pre-scrubbed.
 *
 * *Composition lifecycle and ordering*: Call `SentryNav3Effect` from a composable that stays in the
 * composition tree for the full navigation session, at the same level as `NavDisplay` rather than
 * inside a single destination. It should be called before `NavDisplay`; `NavDisplay` composes the
 * destination content and destination effects, and Sentry needs to observe the backstack first so
 * immediate destination work, such as `LaunchedEffect(route) { loadData() }`, can attach to the
 * route navigation transaction. When the effect leaves the tree, navigation observation stops and
 * the active navigation transaction is finished.
 *
 * // TODO ADAM: Re dialogs: Nav2 generates breadcrumbs, transactions, etc. for dialogs if they're
 * // routed through the NavController. But that only tends to happen when using Compose. //
 * (DialogFragment.show() does NOT go through the NavController.) So it's worth flagging a //
 * difference in behavior.
 *
 * *Dialogs*: Dialog entries are treated like regular backstack entries. If your app represents
 * dialogs as route keys, they can produce breadcrumbs and navigation transactions like any other
 * route.
 *
 * // TODO ADAM: Alt: *Dialogs*: Nav3 represents dialog routes as regular navigation entries. They
 * can therefore keys to track individual option participate in breadcrumb, screen, transaction, and
 * captured back stack state the same way as fields other routes unless your app filters or renames
 * them explicitly.
 *
 * // TODO ADAM: Alt: *Dialogs*: Nav3 dialog-style destinations are still regular navigation
 * entries. Some Nav3 patterns distinguish them via scene metadata or decorators, but this
 * integration does not currently filter or special-case them, so they can produce breadcrumbs,
 * transactions, and screen updates like any other route.
 *
 * **Limitations**
 *
 * This effect currently observes a single backstack with a single visible entry. Multiple back
 * stacks and multipane scenes require a separate API and are not yet supported.
 *
 * @param backStack The navigation backstack to observe.
 * @param scopes A Sentry scopes instance.
 * @param options The sorts of navigation info this effect should record.
 * @param nameExtractor Optional lambda to extract a human-readable route name from a backstack
 *   entry. If not provided, defaults to the entry's class simple name.
 * @param argumentsExtractor Optional lambda to extract arguments from a backstack entry. If not
 *   provided, no arguments are attached. Values should be primitives (`String`, `Number`,
 *   `Boolean`), `null`, or nested `Map`/`Collection` thereof. Non-primitive values are coerced to
 *   their `toString()` representation with a warning logged.
 */
@Suppress("LongParameterList", "FunctionNaming")
@ApiStatus.Experimental
@Composable
@NonRestartableComposable
// TODO ADAM: Any recomposition issues / performance issues we need to worry about?
public fun <T : Any> SentryNav3Effect(
  backStack: SnapshotStateList<T>,
  scopes: IScopes = ScopesAdapter.getInstance(),
  options: SentryNav3Options = SentryNav3Options(),
  // TODO ADAM: Consider a single argument for name + arg extractors.
  // TODO ADAM: Should we add our extractors to SentryNav3Options?
  nameExtractor: ((T) -> String)? = null,
  argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
) {
  require(options.maxCapturedBackStackEntries > 0) {
    "maxCapturedBackStackEntries must be positive, was ${options.maxCapturedBackStackEntries}"
  }
  val observer =
    remember(
      backStack,
      scopes,
      options.enableNavigationBreadcrumbs,
      options.enableNavigationTransactions,
      options.captureBackStack,
      options.maxCapturedBackStackEntries,
    ) {
      SentryBackStackObserver(
        scopes = scopes,
        enableNavigationBreadcrumbs = options.enableNavigationBreadcrumbs,
        enableNavigationTransactions = options.enableNavigationTransactions,
        captureBackStack = options.captureBackStack,
        maxCapturedBackStackEntries = options.maxCapturedBackStackEntries,
        nameExtractor = nameExtractor,
        argumentsExtractor = argumentsExtractor,
      )
    }

  observer.nameExtractor = nameExtractor
  observer.argumentsExtractor = argumentsExtractor

  DisposableEffect(observer, backStack) {
    // Observe snapshot application so pushed routes are bound before NavDisplay recomposes them.
    val handle: ObserverHandle = Snapshot.registerApplyObserver { changed, _ ->
      if (backStack in changed) {
        observer.onBackStackChanged(backStack = backStack.currentSnapshot())
      }
    }

    observer.onBackStackChanged(backStack = backStack.currentSnapshot())

    onDispose {
      handle.dispose()
      observer.cleanup()
    }
  }
}

private fun <T : Any> SnapshotStateList<T>.currentSnapshot(): List<T> =
  Snapshot.withoutReadObservation {
    toList()
  }
