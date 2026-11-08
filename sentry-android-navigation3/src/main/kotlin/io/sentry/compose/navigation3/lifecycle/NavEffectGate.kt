package io.sentry.compose.navigation3.lifecycle

import io.sentry.ILogger
import io.sentry.ScopesAdapter
import io.sentry.SentryLevel

/** How a `SentryNavEffect` came to exist, which decides its priority in a [NavEffectGate]. */
internal enum class NavEffectKind {
  /** Called by the host app. */
  MANUAL,

  /** Installed by the Sentry Android Gradle Plugin's Navigation 3 auto-instrumentation. */
  AUTO,
}

/**
 * Decides which `SentryNavEffect`s may observe their back stack, before any of them compete for the
 * nav lease in [NavLeaseCoordinator].
 *
 * `NavLeaseCoordinator` arbitrates between nav roots (Activities and Fragments) as they come and
 * go. It assumes each nav root has at most one effect. Auto-instrumentation breaks that assumption:
 * it gives every `NavDisplay(backStack = ...)` an effect, including nested `NavDisplay`s and ones
 * whose app also calls `SentryNavEffect` itself. This gate reduces those effects to at most one per
 * back stack and, for auto-instrumented effects, one per nav root, so the coordinator keeps
 * receiving the setups it was designed for.
 *
 * An effect is *active* while the gate lets it observe, and *dormant* otherwise. The gate applies
 * two rules, in order:
 * 1. **One effect per back stack.** Among effects observing the same back stack instance, the first
 *    registered [MANUAL][NavEffectKind.MANUAL] effect wins; without one, the first registered
 *    [AUTO][NavEffectKind.AUTO] effect wins. This covers an app that keeps its own
 *    `SentryNavEffect` after turning on auto-instrumentation.
 * 2. **Manual effects outrank auto effects in a nav root.** Remaining manual effects are always
 *    active. A remaining auto effect is active only if no manual effect remains in its nav root and
 *    no auto effect in that nav root registered before it. Parents compose before children, so the
 *    winner is usually the outermost `NavDisplay`.
 *
 * Whenever an effect registers, leaves, or changes back stacks, the gate re-applies the rules at
 * once. It deactivates effects that lost first, then activates the new winners in registration
 * order, so a dormant effect takes over when the effect ahead of it leaves.
 *
 * Applying the rules at once lets an effect start recording before the destination's own effects
 * run in the same frame, so they see its nav transaction. It's final for supported setups, where a
 * later effect never outranks an earlier one: manual effects are called before their `NavDisplay`,
 * and therefore before the auto effect inside it. A manual effect called after its `NavDisplay`
 * takes over from that auto effect in the same frame, and the first screen is recorded twice.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Confine its use to the main thread, where compositions apply
 * their effects.
 */
internal class NavEffectGate(
  private val logger: () -> ILogger = { ScopesAdapter.getInstance().options.logger }
) {

  /** Registered effects, in registration order. */
  private val members = mutableListOf<Member>()

  /**
   * Registers an effect of [kind] that belongs to [navRoot] and observes [backStack], and activates
   * or deactivates registered effects as the rules require. [listener] is told when the effect
   * becomes active or dormant, possibly before this method returns.
   *
   * [navRoot] and [backStack] are compared by identity.
   */
  fun register(
    kind: NavEffectKind,
    navRoot: Any,
    backStack: Any,
    listener: NavEffectGateListener,
  ): NavEffectGateRegistration {
    val member = Member(kind, navRoot, backStack, listener)
    members += member
    update()
    return member
  }

  private fun update() {
    val winners = winners()
    for (member in members.toList()) {
      if (member.isActive && member !in winners) {
        member.isActive = false
        log(member, "is now dormant")
        member.listener.onDeactivated()
      }
    }
    for (member in members.toList()) {
      if (!member.isActive && member in winners && member in members) {
        member.isActive = true
        log(member, "is now active")
        member.listener.onActivated()
      }
    }
  }

  private fun winners(): Set<Member> {
    val backStackWinners = members.filter { member ->
      val sameBackStack = members.filter { it.backStack === member.backStack }
      val winner =
        sameBackStack.firstOrNull { it.kind == NavEffectKind.MANUAL } ?: sameBackStack.first()
      winner === member
    }

    return backStackWinners
      .filter { member ->
        when (member.kind) {
          NavEffectKind.MANUAL -> true
          NavEffectKind.AUTO -> {
            val sameNavRoot = backStackWinners.filter { it.navRoot === member.navRoot }
            sameNavRoot.none { it.kind == NavEffectKind.MANUAL } &&
              sameNavRoot.first { it.kind == NavEffectKind.AUTO } === member
          }
        }
      }
      .toSet()
  }

  private fun log(member: Member, transition: String) {
    logger()
      .log(
        SentryLevel.DEBUG,
        "%s SentryNavEffect %s (%d effects registered).",
        member.kind.name.lowercase(),
        transition,
        members.size,
      )
  }

  private inner class Member(
    val kind: NavEffectKind,
    val navRoot: Any,
    var backStack: Any,
    val listener: NavEffectGateListener,
  ) : NavEffectGateRegistration {

    var isActive: Boolean = false

    override fun updateBackStack(backStack: Any) {
      if (this.backStack === backStack || this !in members) {
        return
      }
      this.backStack = backStack
      update()
    }

    override fun dispose() {
      if (!members.remove(this)) {
        return
      }
      if (isActive) {
        isActive = false
        listener.onDeactivated()
      }
      update()
    }
  }
}

/** Receives a [NavEffectGate]'s decisions about one registered effect. */
internal interface NavEffectGateListener {

  /** The effect may now observe its back stack. */
  fun onActivated()

  /** The effect must stop observing its back stack and release anything it published. */
  fun onDeactivated()
}

/** An effect's registration with a [NavEffectGate], until it's [disposed][dispose]. */
internal interface NavEffectGateRegistration {

  /**
   * Tells the gate that the effect now observes [backStack] (compared by identity), and re-applies
   * the gate's rules if it changed.
   */
  fun updateBackStack(backStack: Any)

  /**
   * Unregisters the effect, deactivating it first if it's active, and lets the next eligible effect
   * take over. Does nothing once disposed.
   */
  fun dispose()
}
