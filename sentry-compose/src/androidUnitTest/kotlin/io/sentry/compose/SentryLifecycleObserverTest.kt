package io.sentry.compose

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import io.sentry.android.navigation.SentryNavigationListener
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class SentryLifecycleObserverTest {
  class Fixture {
    val navListener = mock<SentryNavigationListener>()
    val replacementNavListener = mock<SentryNavigationListener>()
    val navController = mock<NavController>()
    val lifecycleOwner = mock<androidx.lifecycle.LifecycleOwner>()

    fun getSut(): SentryLifecycleObserver = SentryLifecycleObserver(navController, navListener)
  }

  private val fixture = Fixture()

  @Test
  fun `onResume adds navigation listener`() {
    val sut = fixture.getSut()

    sut.onStateChanged(mock(), Lifecycle.Event.ON_RESUME)

    verify(fixture.navController).addOnDestinationChangedListener(fixture.navListener)
  }

  @Test
  fun `onPause removes navigation listener`() {
    val sut = fixture.getSut()

    sut.onStateChanged(mock(), Lifecycle.Event.ON_PAUSE)

    verify(fixture.navController).removeOnDestinationChangedListener(fixture.navListener)
  }

  @Test
  fun `dispose removes navigation listener`() {
    val sut = fixture.getSut()

    sut.dispose()

    verify(fixture.navController).removeOnDestinationChangedListener(fixture.navListener)
  }

  @Test
  fun `updating listener while resumed swaps listeners`() {
    val sut = fixture.getSut()

    sut.onStateChanged(fixture.lifecycleOwner, Lifecycle.Event.ON_RESUME)
    sut.updateNavListener(fixture.replacementNavListener)

    verify(fixture.navController).removeOnDestinationChangedListener(fixture.navListener)
    verify(fixture.navController).addOnDestinationChangedListener(fixture.replacementNavListener)
  }

  @Test
  fun `syncWithLifecycle adds listener when lifecycle is resumed`() {
    val lifecycle = mock<Lifecycle>()
    whenever(lifecycle.currentState).thenReturn(Lifecycle.State.RESUMED)
    val sut = fixture.getSut()

    sut.syncWithLifecycle(lifecycle)

    verify(fixture.navController).addOnDestinationChangedListener(fixture.navListener)
  }
}
