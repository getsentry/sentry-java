package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.lifecycle.NavEffectGate
import io.sentry.compose.navigation3.lifecycle.NavEffectGateRegistration
import io.sentry.compose.navigation3.lifecycle.NavEffectKind
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GatedBackStackObserverTest {

  private val backStack = mutableListOf("Home")
  private val homeCopy = listOf("Home")
  private val profileCopy = listOf("Home", "Profile")

  private val navRoot = Any()

  // region activation

  @Test
  fun `activation attaches a new observer to the lifecycle`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver

    gatedObserver.onActivated()

    verify(fixture.createdObservers.single()).attach(fixture.lifecycle)
  }

  @Test
  fun `activation applies the latest back stack copy to the new observer after attaching it`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.onBackStackChanged(backStack, homeCopy)
    gatedObserver.onBackStackChanged(backStack, profileCopy)

    gatedObserver.onActivated()

    val observer = fixture.createdObservers.single()
    inOrder(observer) {
      verify(observer).attach(fixture.lifecycle)
      verify(observer).onBackStackChanged(profileCopy)
    }
    verify(observer, never()).onBackStackChanged(homeCopy)
  }

  @Test
  fun `activation without a back stack applies no back stack`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver

    gatedObserver.onActivated()

    verify(fixture.createdObservers.single(), never()).onBackStackChanged(any())
  }

  @Test
  fun `reactivation creates a fresh observer that starts from the latest back stack copy`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.onActivated()
    gatedObserver.onBackStackChanged(backStack, homeCopy)
    gatedObserver.onDeactivated()
    gatedObserver.onBackStackChanged(backStack, profileCopy)

    gatedObserver.onActivated()

    assertThat(fixture.createdObservers).hasSize(2)
    val freshObserver = fixture.createdObservers.last()
    verify(freshObserver).attach(fixture.lifecycle)
    verify(freshObserver).onBackStackChanged(profileCopy)
  }

  // endregion

  // region onBackStackChanged()

  @Test
  fun `onBackStackChanged() - while active, applies the back stack copy to the observer`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.onActivated()

    gatedObserver.onBackStackChanged(backStack, homeCopy)

    verify(fixture.createdObservers.single()).onBackStackChanged(homeCopy)
  }

  @Test
  fun `onBackStackChanged() - while dormant, creates no observer`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver

    gatedObserver.onBackStackChanged(backStack, homeCopy)

    assertThat(fixture.createdObservers).isEmpty()
  }

  @Test
  fun `onBackStackChanged() - after deactivation, doesn't apply the back stack copy to the disposed observer`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.onActivated()
    gatedObserver.onDeactivated()

    gatedObserver.onBackStackChanged(backStack, homeCopy)

    verify(fixture.createdObservers.single(), never()).onBackStackChanged(any())
  }

  @Test
  fun `onBackStackChanged() - updates the gate registration's back stack before applying the copy`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.onActivated()
    val observer = fixture.createdObservers.single()

    gatedObserver.onBackStackChanged(backStack, homeCopy)

    inOrder(fixture.registration, observer) {
      verify(fixture.registration).updateBackStack(backStack)
      verify(observer).onBackStackChanged(homeCopy)
    }
  }

  @Test
  fun `onBackStackChanged() - when the back stack update deactivates the effect, doesn't apply the copy`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.onActivated()
    val observer = fixture.createdObservers.single()
    fixture.onBackStackUpdate { gatedObserver.onDeactivated() }

    gatedObserver.onBackStackChanged(backStack, homeCopy)

    verify(observer).dispose()
    verify(observer, never()).onBackStackChanged(any())
  }

  @Test
  fun `onBackStackChanged() - when the back stack update activates the effect, applies the copy once`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    fixture.onBackStackUpdate { gatedObserver.onActivated() }

    gatedObserver.onBackStackChanged(backStack, homeCopy)

    verify(fixture.createdObservers.single(), times(1)).onBackStackChanged(homeCopy)
  }

  // endregion

  // region deactivation

  @Test
  fun `deactivation disposes the observer`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.onActivated()

    gatedObserver.onDeactivated()

    verify(fixture.createdObservers.single()).dispose()
  }

  @Test
  fun `deactivation disposes the observer only once`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.onActivated()
    gatedObserver.onDeactivated()

    gatedObserver.onDeactivated()

    verify(fixture.createdObservers.single(), times(1)).dispose()
  }

  // endregion

  // region gate registration

  @Test
  fun `register() - registers with the gate as the listener for its kind, nav root, and back stack`() {
    val fixture = Fixture.create(isRegistered = false)
    val gatedObserver = fixture.gatedObserver

    gatedObserver.register(fixture.gate, NavEffectKind.AUTO, navRoot, backStack)

    verify(fixture.gate).register(NavEffectKind.AUTO, navRoot, backStack, gatedObserver)
  }

  @Test
  fun `unregister() - disposes the gate registration`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver

    gatedObserver.unregister()

    verify(fixture.registration).dispose()
  }

  @Test
  fun `unregister() - disposes the gate registration only once`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.unregister()

    gatedObserver.unregister()

    verify(fixture.registration, times(1)).dispose()
  }

  @Test
  fun `onBackStackInstanceChanged() - updates the gate registration's back stack`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver

    gatedObserver.onBackStackInstanceChanged(backStack)

    verify(fixture.registration).updateBackStack(backStack)
  }

  @Test
  fun `onBackStackInstanceChanged() - after unregistering, doesn't update the back stack`() {
    val fixture = Fixture.create()
    val gatedObserver = fixture.gatedObserver
    gatedObserver.unregister()

    gatedObserver.onBackStackInstanceChanged(backStack)

    verify(fixture.registration, never()).updateBackStack(any())
  }

  // endregion

  private class Fixture private constructor() {

    val lifecycle = mock<Lifecycle>()
    val registration = mock<NavEffectGateRegistration>()
    val gate =
      mock<NavEffectGate>().also {
        whenever(it.register(any(), any(), any(), any())).thenReturn(registration)
      }

    /** Observers created by [gatedObserver], in creation order. */
    val createdObservers = mutableListOf<CoordinatedBackStackObserver<String>>()

    val gatedObserver =
      GatedBackStackObserver(lifecycle) {
        mock<CoordinatedBackStackObserver<String>>().also(createdObservers::add)
      }

    /** Makes the gate react to each back stack update of [registration] by running [action]. */
    fun onBackStackUpdate(action: () -> Unit) {
      doAnswer { action() }.whenever(registration).updateBackStack(any())
    }

    companion object {
      fun create(isRegistered: Boolean = true): Fixture =
        Fixture().also {
          if (isRegistered) {
            it.gatedObserver.register(it.gate, NavEffectKind.MANUAL, Any(), listOf("Home"))
          }
        }
    }
  }
}
