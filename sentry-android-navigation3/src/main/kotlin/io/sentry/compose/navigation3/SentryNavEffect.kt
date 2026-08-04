package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import org.jetbrains.annotations.ApiStatus

/**
 * A composable integration that lets you generate Sentry events from a Nav3 backstack. Configure it
 * via [options] and launch it in the composable that owns your `NavDisplay`.
 *
 * ```kotlin
 *  @Composable
 *  fun AppNavigation() {
 *    val navBackStack = rememberNavBackStack(Home)
 *
 *    // Call before NavDisplay so destination effects can attach work to the
 *    // route transaction.
 *    SentryNavEffect(
 *      backStack = navBackStack,
 *      options = SentryNavOptions(maxCapturedBackStackEntries = 10),
 *      nameExtractor = { route -> route.extractName() },
 *      argumentsExtractor = { route -> route.extractArgument() },
 *    )
 *
 *    // Configure your NavDisplay like usual after SentryNavEffect.
 *    NavDisplay(
 *      backStack = navBackStack,
 *      ...
 *    )
 *  }
 * ```
 *
 * Under the hood it reads your [backStack] during composition and converts it into Sentry events
 * via the provided [scopes] instance.
 *
 * **Gotchas**
 *
 * *Privacy / PII*: Values returned from [nameExtractor] and [argumentsExtractor] are sent to Sentry
 * in breadcrumbs, navigation state, and navigation transactions and are ***not*** scrubbed by the
 * Sentry SDK. Only return route names and arguments that are known to be safe or have been
 * pre-scrubbed.
 *
 * *Composition lifecycle and ordering*: Call `SentryNavEffect` from a composable that stays in the
 * composition tree for the full navigation session, at the same level as `NavDisplay` rather than
 * inside a single destination. It should be called before `NavDisplay`. This integration
 * intentionally processes the backstack during composition because `NavDisplay` immediately
 * composes the destination, where instrumentation such as `SentryTraced` creates child spans. The
 * route navigation transaction must already be active so that work is attached to the correct
 * transaction. When the effect leaves the tree, navigation observation stops and the active
 * navigation transaction is finished.
 *
 * *Extractor stability*: Changing [nameExtractor] or [argumentsExtractor] instances causes this
 * integration to reprocess the backstack. Remember expensive extractors that capture changing
 * configuration so unrelated recompositions can skip this work. Extractors that read Compose
 * snapshot state also subscribe this effect to that state; when it changes, Sentry reprocesses the
 * captured backstack because extractor output may have changed even if the route entries did not.
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
 * **Limitations**
 *
 * This effect tracks the top entry of the authoritative [backStack] passed by the caller. It does
 * not model multiple retained back stacks, predictive-back previews, or all visible panes in a
 * multipane scene.
 *
 * @param backStack The navigation backstack to observe.
 * @param scopes A Sentry scopes instance.
 * @param options The sorts of navigation info this effect should record.
 * @param nameExtractor Optional lambda to extract a human-readable route name from a backstack
 *   entry. If not provided, defaults to the entry's class simple name.
 * @param argumentsExtractor Optional lambda to extract arguments from a backstack entry. If not
 *   provided, no arguments are attached. Values should be primitives (`String`, `Number`,
 *   `Boolean`), `null`, or nested `Map`/`Collection` thereof. Non-primitive values are coerced to
 *   their `toString()` representation with a warning logged. Extraction and recursive sanitization
 *   run synchronously during composition for every captured entry, so return only the arguments
 *   needed for diagnostics and avoid large or deeply nested structures. Cyclic structures,
 *   structures deeper than 20 nesting levels, and captured back-stack updates containing more than
 *   1,000 total argument values skip the offending arguments. Once the total value budget is
 *   exhausted, older captured entries keep their route names but omit arguments.
 */
@Suppress("LongParameterList", "FunctionNaming")
@ApiStatus.Experimental
@Composable
public fun <T : Any> SentryNavEffect(
  backStack: List<T>,
  scopes: IScopes = ScopesAdapter.getInstance(),
  options: SentryNavOptions = SentryNavOptions(),
  // TODO ADAM: Consider a single argument for name + arg extractors.
  // TODO ADAM: Should we add our extractors to SentryNavOptions?
  nameExtractor: ((T) -> String)? = null,
  argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
) {
  val navStateHolder = remember(scopes) { SentryNavStateHolder<T>(scopes = scopes) }

  // This intentionally updates Sentry during composition rather than from a deferred Compose
  // effect. NavDisplay composes destination bodies immediately after this call, and SentryTraced
  // creates child spans from those bodies. Deferring this update would attach those spans to the
  // previous transaction, or leave them without a parent transaction.
  navStateHolder.onBackStackChanged(
    backStack = backStack,
    options = options,
    nameExtractor = nameExtractor,
    argumentsExtractor = argumentsExtractor,
  )

  DisposableEffect(navStateHolder) {
    onDispose {
      navStateHolder.cleanup()
    }
  }
}

// TODO ADAM: Also create a convenience rememberSentryNavBackStack() method for app's that use
// rememberNavBackStack().
