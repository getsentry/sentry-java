package io.sentry.android.core

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IScopes
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AppLifecycleIntegrationTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    val options = SentryAndroidOptions()

    fun getSut(): AppLifecycleIntegration {
      return AppLifecycleIntegration()
    }
  }

  private val fixture = Fixture()

  @Test
  fun `When AppLifecycleIntegration is added, lifecycle watcher should be started`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.watcher)
  }

  @Test
  fun `When SessionTracking and AppLifecycle breadcrumbs are disabled, lifecycle watcher should not be started`() {
    val sut = fixture.getSut()
    fixture.options.apply {
      isEnableAppLifecycleBreadcrumbs = false
      isEnableAutoSessionTracking = false
    }

    sut.register(fixture.scopes, fixture.options)

    assertNull(sut.watcher)
  }

  @Test
  fun `When AppLifecycleIntegration is closed, lifecycle watcher should be closed`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.watcher)

    sut.close()

    assertNull(sut.watcher)
  }

  @Test
  fun `When AppLifecycleIntegration is closed from a background thread, watcher is set to null`() {
    val sut = fixture.getSut()
    val latch = CountDownLatch(1)

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.watcher)

    Thread {
        sut.close()
        latch.countDown()
      }
      .start()

    latch.await()

    // ensure all messages on main looper got processed
    shadowOf(Looper.getMainLooper()).idle()

    assertNull(sut.watcher)
  }

  @Test
  fun `When AppLifecycleIntegration is closed, AppState unregisterLifecycleObserver is called`() {
    val sut = fixture.getSut()
    val appState = AppState.getInstance()

    sut.register(fixture.scopes, fixture.options)

    // Verify that lifecycleObserver is not null after registration
    assertNotNull(appState.lifecycleObserver)

    sut.close()

    // Verify that lifecycleObserver is null after unregistering
    assertNull(appState.lifecycleObserver)
  }
}
