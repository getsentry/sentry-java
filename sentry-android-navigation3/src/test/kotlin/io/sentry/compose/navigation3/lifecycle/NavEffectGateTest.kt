package io.sentry.compose.navigation3.lifecycle

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.lifecycle.NavEffectKind.AUTO
import io.sentry.compose.navigation3.lifecycle.NavEffectKind.MANUAL
import org.junit.Test

class NavEffectGateTest {

  private val backStack = Any()
  private val otherBackStack = Any()

  private val navRoot = Any()
  private val otherNavRoot = Any()

  // region register()

  @Test
  fun `register() - activates a manual effect`() {
    val fixture = Fixture.create()

    val manual = fixture.register("manual", MANUAL, navRoot, backStack)

    assertThat(manual.isActive).isTrue()
    assertThat(fixture.events).containsExactly("manual activated")
  }

  @Test
  fun `register() - activates an auto effect`() {
    val fixture = Fixture.create()

    val auto = fixture.register("auto", AUTO, navRoot, backStack)

    assertThat(auto.isActive).isTrue()
    assertThat(fixture.events).containsExactly("auto activated")
  }

  // endregion

  // region one effect per back stack

  @Test
  fun `a manual effect wins over an auto effect registered later on the same back stack`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, navRoot, backStack)

    val auto = fixture.register("auto", AUTO, otherNavRoot, backStack)

    assertThat(manual.isActive).isTrue()
    assertThat(auto.isActive).isFalse()
  }

  @Test
  fun `a manual effect wins over an auto effect registered earlier on the same back stack`() {
    val fixture = Fixture.create()
    val auto = fixture.register("auto", AUTO, otherNavRoot, backStack)

    val manual = fixture.register("manual", MANUAL, navRoot, backStack)

    assertThat(manual.isActive).isTrue()
    assertThat(auto.isActive).isFalse()
  }

  @Test
  fun `the first of two manual effects on the same back stack wins`() {
    val fixture = Fixture.create()
    val first = fixture.register("first", MANUAL, navRoot, backStack)

    val second = fixture.register("second", MANUAL, otherNavRoot, backStack)

    assertThat(first.isActive).isTrue()
    assertThat(second.isActive).isFalse()
  }

  @Test
  fun `the first of two auto effects on the same back stack wins`() {
    val fixture = Fixture.create()
    val first = fixture.register("first", AUTO, navRoot, backStack)

    val second = fixture.register("second", AUTO, otherNavRoot, backStack)

    assertThat(first.isActive).isTrue()
    assertThat(second.isActive).isFalse()
  }

  @Test
  fun `effects on equal but distinct back stacks don't compete for a back stack`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, navRoot, listOf("Home"))

    val auto = fixture.register("auto", AUTO, otherNavRoot, listOf("Home"))

    assertThat(manual.isActive).isTrue()
    assertThat(auto.isActive).isTrue()
  }

  // endregion

  // region manual effects outrank auto effects in a nav root

  @Test
  fun `the first of two auto effects in the same nav root wins`() {
    val fixture = Fixture.create()
    val outer = fixture.register("outer", AUTO, navRoot, backStack)

    val inner = fixture.register("inner", AUTO, navRoot, otherBackStack)

    assertThat(outer.isActive).isTrue()
    assertThat(inner.isActive).isFalse()
  }

  @Test
  fun `a manual effect keeps an auto effect on another back stack in the same nav root dormant`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, navRoot, backStack)

    val auto = fixture.register("auto", AUTO, navRoot, otherBackStack)

    assertThat(manual.isActive).isTrue()
    assertThat(auto.isActive).isFalse()
  }

  @Test
  fun `manual effects on different back stacks in the same nav root are all active`() {
    val fixture = Fixture.create()
    val first = fixture.register("first", MANUAL, navRoot, backStack)

    val second = fixture.register("second", MANUAL, navRoot, otherBackStack)

    assertThat(first.isActive).isTrue()
    assertThat(second.isActive).isTrue()
  }

  @Test
  fun `auto effects in different nav roots are all active`() {
    val fixture = Fixture.create()
    val first = fixture.register("first", AUTO, navRoot, backStack)

    val second = fixture.register("second", AUTO, otherNavRoot, otherBackStack)

    assertThat(first.isActive).isTrue()
    assertThat(second.isActive).isTrue()
  }

  @Test
  fun `a manual effect in another nav root leaves an auto effect active`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, otherNavRoot, otherBackStack)

    val auto = fixture.register("auto", AUTO, navRoot, backStack)

    assertThat(manual.isActive).isTrue()
    assertThat(auto.isActive).isTrue()
  }

  @Test
  fun `auto effects in equal but distinct nav roots are all active`() {
    val fixture = Fixture.create()
    val first = fixture.register("first", AUTO, listOf("root"), backStack)

    val second = fixture.register("second", AUTO, listOf("root"), otherBackStack)

    assertThat(first.isActive).isTrue()
    assertThat(second.isActive).isTrue()
  }

  @Test
  fun `an auto effect that loses its back stack doesn't keep a later auto effect in its nav root dormant`() {
    val fixture = Fixture.create()
    fixture.register("manual", MANUAL, otherNavRoot, backStack)
    val loser = fixture.register("loser", AUTO, navRoot, backStack)

    val auto = fixture.register("auto", AUTO, navRoot, otherBackStack)

    assertThat(loser.isActive).isFalse()
    assertThat(auto.isActive).isTrue()
  }

  // endregion

  // region handover

  @Test
  fun `registering a manual effect deactivates an active auto effect in its nav root before activating itself`() {
    val fixture = Fixture.create()
    val auto = fixture.register("auto", AUTO, navRoot, backStack)

    val manual = fixture.register("manual", MANUAL, navRoot, otherBackStack)

    assertThat(auto.isActive).isFalse()
    assertThat(manual.isActive).isTrue()
    assertThat(fixture.events)
      .containsExactly("auto activated", "auto deactivated", "manual activated")
      .inOrder()
  }

  @Test
  fun `disposing the active auto effect activates the dormant auto effect in the same nav root`() {
    val fixture = Fixture.create()
    val outer = fixture.register("outer", AUTO, navRoot, backStack)
    val inner = fixture.register("inner", AUTO, navRoot, otherBackStack)
    fixture.events.clear()

    outer.registration.dispose()

    assertThat(inner.isActive).isTrue()
    assertThat(fixture.events).containsExactly("outer deactivated", "inner activated").inOrder()
  }

  @Test
  fun `disposing a manual effect activates the dormant auto effect on its back stack`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, navRoot, backStack)
    val auto = fixture.register("auto", AUTO, otherNavRoot, backStack)
    fixture.events.clear()

    manual.registration.dispose()

    assertThat(auto.isActive).isTrue()
    assertThat(fixture.events).containsExactly("manual deactivated", "auto activated").inOrder()
  }

  @Test
  fun `effects that take over at once are activated in registration order`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, navRoot, backStack)
    // Dormant because the manual effect wins its nav root.
    fixture.register("first", AUTO, navRoot, otherBackStack)
    // Dormant because the manual effect wins its back stack.
    fixture.register("second", AUTO, otherNavRoot, backStack)
    fixture.events.clear()

    manual.registration.dispose()

    assertThat(fixture.events)
      .containsExactly("manual deactivated", "first activated", "second activated")
      .inOrder()
  }

  // endregion

  // region dispose()

  @Test
  fun `dispose() - deactivates an active effect`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, navRoot, backStack)

    manual.registration.dispose()

    assertThat(manual.isActive).isFalse()
    assertThat(fixture.events).containsExactly("manual activated", "manual deactivated").inOrder()
  }

  @Test
  fun `dispose() - doesn't notify a dormant effect or change the active effects`() {
    val fixture = Fixture.create()
    val outer = fixture.register("outer", AUTO, navRoot, backStack)
    val inner = fixture.register("inner", AUTO, navRoot, otherBackStack)
    fixture.events.clear()

    inner.registration.dispose()

    assertThat(outer.isActive).isTrue()
    assertThat(fixture.events).isEmpty()
  }

  @Test
  fun `dispose() - does nothing once disposed`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, navRoot, backStack)
    manual.registration.dispose()
    fixture.events.clear()

    manual.registration.dispose()

    assertThat(fixture.events).isEmpty()
  }

  // endregion

  // region updateBackStack()

  @Test
  fun `updateBackStack() - moving an auto effect onto a manual effect's back stack deactivates it`() {
    val fixture = Fixture.create()
    fixture.register("manual", MANUAL, navRoot, backStack)
    val auto = fixture.register("auto", AUTO, otherNavRoot, otherBackStack)
    fixture.events.clear()

    auto.registration.updateBackStack(backStack)

    assertThat(auto.isActive).isFalse()
    assertThat(fixture.events).containsExactly("auto deactivated")
  }

  @Test
  fun `updateBackStack() - moving an auto effect off a manual effect's back stack activates it`() {
    val fixture = Fixture.create()
    fixture.register("manual", MANUAL, navRoot, backStack)
    val auto = fixture.register("auto", AUTO, otherNavRoot, backStack)
    fixture.events.clear()

    auto.registration.updateBackStack(otherBackStack)

    assertThat(auto.isActive).isTrue()
    assertThat(fixture.events).containsExactly("auto activated")
  }

  @Test
  fun `updateBackStack() - moving a manual effect onto an auto effect's back stack deactivates the auto effect`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, navRoot, backStack)
    val auto = fixture.register("auto", AUTO, otherNavRoot, otherBackStack)
    fixture.events.clear()

    manual.registration.updateBackStack(otherBackStack)

    assertThat(manual.isActive).isTrue()
    assertThat(auto.isActive).isFalse()
    assertThat(fixture.events).containsExactly("auto deactivated")
  }

  @Test
  fun `updateBackStack() - the same back stack instance changes nothing`() {
    val fixture = Fixture.create()
    fixture.register("manual", MANUAL, navRoot, backStack)
    val auto = fixture.register("auto", AUTO, otherNavRoot, otherBackStack)
    fixture.events.clear()

    auto.registration.updateBackStack(otherBackStack)

    assertThat(auto.isActive).isTrue()
    assertThat(fixture.events).isEmpty()
  }

  @Test
  fun `updateBackStack() - does nothing once disposed`() {
    val fixture = Fixture.create()
    val manual = fixture.register("manual", MANUAL, navRoot, backStack)
    val auto = fixture.register("auto", AUTO, otherNavRoot, otherBackStack)
    auto.registration.dispose()
    fixture.events.clear()

    auto.registration.updateBackStack(backStack)
    manual.registration.dispose()

    // The disposed auto effect neither rejoined the gate nor took over from the manual effect.
    assertThat(auto.isActive).isFalse()
    assertThat(fixture.events).containsExactly("manual deactivated")
  }

  // endregion

  private class Fixture private constructor() {

    val gate = NavEffectGate()

    /** Activations and deactivations of all registered effects, in the order they happened. */
    val events = mutableListOf<String>()

    fun register(name: String, kind: NavEffectKind, navRoot: Any, backStack: Any): Effect {
      val effect = Effect(name, events)
      effect.registration = gate.register(kind, navRoot, backStack, effect)
      return effect
    }

    companion object {
      fun create(): Fixture = Fixture()
    }
  }

  private class Effect(private val name: String, private val events: MutableList<String>) :
    NavEffectGateListener {

    lateinit var registration: NavEffectGateRegistration

    var isActive: Boolean = false
      private set

    override fun onActivated() {
      isActive = true
      events += "$name activated"
    }

    override fun onDeactivated() {
      isActive = false
      events += "$name deactivated"
    }
  }
}
