package io.sentry.compose

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import io.sentry.android.navigation.SentryNavigationListener
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class SentryLifecycleObserverTest {
  class Fixture {
    val navListener = mock<SentryNavigationListener>()
    val navController = mock<NavController>()

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
}
