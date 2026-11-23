package io.sentry.compose.navigation3.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryNavEffect
import io.sentry.compose.navigation3.SentryNavOptions
import io.sentry.compose.navigation3.internal.backstack.defaultNavEffectGate
import io.sentry.compose.navigation3.internal.backstack.defaultNavLeaseCoordinator
import io.sentry.compose.navigation3.internal.lifecycle.NavEffectGate
import io.sentry.compose.navigation3.internal.lifecycle.NavEffectKind
import io.sentry.compose.navigation3.internal.lifecycle.NavLeaseCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.annotations.ApiStatus

/**
 * Entry point for the Sentry Android Gradle Plugin's Navigation 3 auto-instrumentation. Not for use
 * by apps; call [SentryNavEffect] instead.
 *
 * The plugin redirects the `rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)`
 * call inside navigation3-ui's `NavDisplay(backStack = ...)` to
 * [rememberSentryDecoratedNavEntries], naming this class by its binary name,
 * `io/sentry/compose/navigation3/internal/SentryNavAutoInstrumentation`.
 *
 * Once a plugin release ships the redirect, this class's name and package and the method's compiled
 * signature form a contract with every SDK version that plugin supports, as apps can combine any
 * plugin version with any such SDK version. Don't move or rename the class, and keep the method's
 * signature identical to the original's. To change the contract, add a new entry point instead.
 * `SentryNavAutoInstrumentationSignatureTest` pins both.
 */
@ApiStatus.Internal
public object SentryNavAutoInstrumentation {

  /**
   * Observes [backStack] like [SentryNavEffect], then returns
   * `rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)` unchanged.
   *
   * Mirrors the original's parameters, including the default value of [entryDecorators], so that
   * both compile to `(List, List, Function1, Composer, int, int) -> List`. The default produces the
   * trailing `$default` parameter; without it, redirected calls would fail with
   * `NoSuchMethodError`.
   *
   * Entries are named with [BackStackEntryMapper.SerialName] and default [SentryNavOptions].
   * Exceptions thrown by the app's `entryProvider` propagate unchanged.
   */
  @ApiStatus.Internal
  @JvmStatic
  @Composable
  public fun <T : Any> rememberSentryDecoratedNavEntries(
    backStack: List<T>,
    entryDecorators: List<@JvmSuppressWildcards NavEntryDecorator<T>> = listOf(),
    entryProvider: (key: T) -> NavEntry<T>,
  ): List<NavEntry<T>> =
    rememberSentryDecoratedNavEntries(
      backStack = backStack,
      entryDecorators = entryDecorators,
      entryProvider = entryProvider,
      scopes = ScopesAdapter.getInstance(),
      coordinator = defaultNavLeaseCoordinator,
      gate = defaultNavEffectGate,
    )

  @Suppress("LongParameterList")
  @Composable
  internal fun <T : Any> rememberSentryDecoratedNavEntries(
    backStack: List<T>,
    entryDecorators: List<NavEntryDecorator<T>>,
    entryProvider: (key: T) -> NavEntry<T>,
    scopes: IScopes,
    coordinator: NavLeaseCoordinator,
    gate: NavEffectGate,
  ): List<NavEntry<T>> {
    // Observe before the entries are built, as SentryNavEffect asks apps to, so the initial
    // destination's work lands in its navigation transaction.
    AutoNavEffect(backStack, scopes, coordinator, gate)
    return rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)
  }
}

/**
 * The auto-instrumented counterpart of [SentryNavEffect], registered with [gate] as an
 * [AUTO][NavEffectKind.AUTO] effect.
 *
 * Its nav root is the Activity or Fragment that hosts the composition (the View tree's
 * [LifecycleOwner]), not [androidx.lifecycle.compose.LocalLifecycleOwner]. `NavDisplay`, and
 * Navigation 2's `NavHost`, give their content per-scene and per-entry lifecycle owners, which drop
 * to STARTED during transitions and while overlays are shown. A `NavDisplay` nested in another's
 * entry, or one placed in a Navigation 2 destination, would otherwise lose and regain the nav lease
 * whenever navigation animates. The View tree's owner is the one a correctly placed manual
 * [SentryNavEffect] sees, so the nav lease policy treats auto effects like manual ones.
 *
 * Without a View tree owner, the effect stays dormant and logs once.
 */
@Composable
private fun <T : Any> AutoNavEffect(
  backStack: List<T>,
  scopes: IScopes,
  coordinator: NavLeaseCoordinator,
  gate: NavEffectGate,
) {
  val navRoot = LocalView.current.findViewTreeLifecycleOwner()
  if (navRoot == null) {
    if (loggedMissingNavRoot.compareAndSet(false, true)) {
      scopes.options.logger.log(
        SentryLevel.DEBUG,
        "Navigation 3 auto-instrumentation is inactive for a NavDisplay without a View tree " +
          "LifecycleOwner.",
      )
    }
    return
  }

  SentryNavEffect(
    backStack = backStack,
    backStackEntryMapper = autoBackStackEntryMapper(),
    options = autoNavOptions,
    scopes = scopes,
    coordinator = coordinator,
    gate = gate,
    kind = NavEffectKind.AUTO,
    navRoot = navRoot,
  )
}

/**
 * Chooses how auto-instrumented back stack entries are named. The only place that decides what
 * auto-instrumented apps record, so it can later follow a runtime option (e.g., a Data Collection
 * setting) without changing the plugin's bytecode.
 */
private fun autoBackStackEntryMapper(): BackStackEntryMapper<Any> = BackStackEntryMapper.SerialName

/** A single instance, so recompositions don't recreate the effect's observer. */
private val autoNavOptions: SentryNavOptions = SentryNavOptions()

private val loggedMissingNavRoot = AtomicBoolean(false)
