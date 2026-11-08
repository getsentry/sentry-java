package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import io.sentry.compose.navigation3.lifecycle.NavEffectGate
import io.sentry.compose.navigation3.lifecycle.NavEffectGateListener
import io.sentry.compose.navigation3.lifecycle.NavEffectGateRegistration
import io.sentry.compose.navigation3.lifecycle.NavEffectKind

/**
 * Observes a back stack through a [CoordinatedBackStackObserver] only while a [NavEffectGate] keeps
 * the effect active.
 *
 * A dormant effect records nothing and holds no registration with the nav lease coordinator. It
 * still remembers the latest back stack it was given, so that if the gate activates it later (e.g.,
 * because the effect ahead of it left), it starts from the current back stack instead of waiting
 * for the next navigation.
 *
 * Each activation creates a fresh observer via [createObserver] and attaches it to [lifecycle];
 * each deactivation disposes it, which unpublishes its nav state and finishes its nav transaction.
 *
 * **Usage**
 *
 * Call [register] before the first [onBackStackChanged], and [unregister] when observation ends.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Confine its use to the main thread.
 */
internal class GatedBackStackObserver<T : Any>(
  private val lifecycle: Lifecycle,
  private val createObserver: () -> CoordinatedBackStackObserver<T>,
) : NavEffectGateListener {

  private var registration: NavEffectGateRegistration? = null
  private var observer: CoordinatedBackStackObserver<T>? = null
  private var latestBackStack: List<T>? = null

  /**
   * Registers this effect with [gate] as an effect of [kind] that belongs to [navRoot] and observes
   * [backStack]. The gate may activate it before this method returns.
   */
  fun register(gate: NavEffectGate, kind: NavEffectKind, navRoot: Any, backStack: List<T>) {
    registration = gate.register(kind, navRoot, backStack, this)
  }

  /** Unregisters this effect from its gate, deactivating it if it's active. */
  fun unregister() {
    registration?.dispose()
    registration = null
  }

  /**
   * Records [copy], a snapshot of [backStack], as the latest back stack and, while this effect is
   * active, applies it.
   *
   * [backStack] is the observed list itself. The gate tells effects apart by its identity, so it's
   * synced first: if a new instance makes this effect lose to another, the effect is deactivated
   * before it would apply [copy].
   */
  fun onBackStackChanged(backStack: List<T>, copy: List<T>) {
    latestBackStack = copy
    val wasActive = observer != null
    // If this activates the effect, activation applies the latest back stack itself.
    registration?.updateBackStack(backStack)
    if (wasActive) {
      observer?.onBackStackChanged(copy)
    }
  }

  /**
   * Tells the gate which list instance this effect observes. The gate compares effects by that
   * identity, so a new instance can change which effect is active even if its contents are the
   * same. Does nothing if [backStack] is the instance the gate already knows.
   */
  fun onBackStackInstanceChanged(backStack: List<T>) {
    registration?.updateBackStack(backStack)
  }

  override fun onActivated() {
    val activeObserver = createObserver()
    observer = activeObserver
    activeObserver.attach(lifecycle)
    latestBackStack?.let { activeObserver.onBackStackChanged(it) }
  }

  override fun onDeactivated() {
    observer?.dispose()
    observer = null
  }
}
