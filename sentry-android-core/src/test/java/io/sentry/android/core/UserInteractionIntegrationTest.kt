package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.view.Window
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Scopes
import io.sentry.android.core.internal.gestures.NoOpWindowCallback
import io.sentry.android.core.internal.gestures.SentryWindowCallback
import junit.framework.TestCase.assertNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
class UserInteractionIntegrationTest {
  private class Fixture {
    val application = mock<Application>()
    val scopes = mock<Scopes>()
    val options = SentryAndroidOptions().apply { dsn = "https://key@sentry.io/proj" }
    val activity: EmptyActivity = buildActivity(EmptyActivity::class.java).setup().get()
    val window: Window = activity.window
    val loadClass = mock<LoadClass>()

    fun getSut(
      callback: Window.Callback? = null,
      isLifecycleAvailable: Boolean = true,
    ): UserInteractionIntegration {
      whenever(
          loadClass.isClassAvailable(
            eq("androidx.lifecycle.Lifecycle"),
            anyOrNull<SentryAndroidOptions>(),
          )
        )
        .thenReturn(isLifecycleAvailable)
      whenever(scopes.options).thenReturn(options)
      if (callback != null) {
        window.callback = callback
      }
      return UserInteractionIntegration(application, loadClass)
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun setup() {
    CurrentActivityHolder.getInstance().clearActivity()
  }

  @Test
  fun `when user interaction breadcrumb is enabled registers a callback`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    verify(fixture.application).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `when user interaction breadcrumb is disabled doesn't register a callback`() {
    val sut = fixture.getSut()
    fixture.options.isEnableUserInteractionBreadcrumbs = false

    sut.register(fixture.scopes, fixture.options)

    verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `when UserInteractionIntegration is closed unregisters the callback`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    sut.close()

    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `registers window callback on activity resumed`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    sut.onActivityResumed(fixture.activity)
    assertIs<SentryWindowCallback>(fixture.activity.window.callback)
  }

  @Test
  fun `when no original callback delegates to NoOpWindowCallback`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    fixture.window.callback = null

    sut.onActivityResumed(fixture.activity)
    assertIs<SentryWindowCallback>(fixture.activity.window.callback)
    assertIs<NoOpWindowCallback>(
      (fixture.activity.window.callback as SentryWindowCallback).delegate
    )
  }

  @Test
  fun `unregisters window callback on activity paused`() {
    val sut = fixture.getSut()
    fixture.activity.window.callback = null

    sut.onActivityResumed(fixture.activity)
    sut.onActivityPaused(fixture.activity)

    assertNull(fixture.activity.window.callback)
  }

  @Test
  fun `preserves original callback on activity paused`() {
    val sut = fixture.getSut()
    val mockCallback = mock<Window.Callback>()

    fixture.window.callback = mockCallback

    sut.onActivityResumed(fixture.activity)
    sut.onActivityPaused(fixture.activity)

    assertSame(mockCallback, fixture.activity.window.callback)
  }

  @Test
  fun `stops tracing on activity paused`() {
    val callback = mock<SentryWindowCallback>()
    val sut = fixture.getSut()
    fixture.activity.window.callback = callback

    sut.onActivityPaused(fixture.activity)

    verify(callback).stopTracking()
  }

  @Test
  fun `resume after buried pause installs a fresh wrapper on top`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    sut.onActivityResumed(fixture.activity)
    val originalSentryCallback = fixture.window.callback
    assertIs<SentryWindowCallback>(originalSentryCallback)

    // Third-party wraps on top of us mid-activity.
    val outerWrapper = WrapperCallback(originalSentryCallback)
    fixture.window.callback = outerWrapper

    sut.onActivityPaused(fixture.activity)
    sut.onActivityResumed(fixture.activity)

    val newTop = fixture.window.callback
    assertIs<SentryWindowCallback>(newTop)
    assertNotSame(originalSentryCallback, newTop)
    assertSame(outerWrapper, newTop.delegate)
  }

  @Test
  fun `close unwraps windows so re-init does not double-wrap`() {
    val mockCallback = mock<Window.Callback>()
    fixture.window.callback = mockCallback

    val sutA = fixture.getSut()
    sutA.register(fixture.scopes, fixture.options)
    sutA.onActivityResumed(fixture.activity)
    assertIs<SentryWindowCallback>(fixture.window.callback)

    sutA.close()
    assertSame(mockCallback, fixture.window.callback)

    val sutB = UserInteractionIntegration(fixture.application, fixture.loadClass)
    sutB.register(fixture.scopes, fixture.options)
    sutB.onActivityResumed(fixture.activity)

    val newWrapper = fixture.window.callback
    assertIs<SentryWindowCallback>(newWrapper)
    assertSame(mockCallback, newWrapper.delegate)
  }

  @Test
  fun `paused with another wrapper on top does not cut it out of the chain`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    sut.onActivityResumed(fixture.activity)
    val sentryCallback = fixture.window.callback as SentryWindowCallback

    val outerWrapper = WrapperCallback(sentryCallback)
    fixture.window.callback = outerWrapper

    sut.onActivityPaused(fixture.activity)

    assertSame(outerWrapper, fixture.window.callback)
  }

  @Test
  fun `when androidx lifecycle is unavailable doesn't hook into activity`() {
    val sut = fixture.getSut(isLifecycleAvailable = false)
    CurrentActivityHolder.getInstance().setActivity(fixture.activity)
    sut.register(fixture.scopes, fixture.options)
    assertIsNot<SentryWindowCallback>(fixture.window)
  }

  @Test
  fun `when activity is resumed and is a LifecycleOwner, starts tracking immediately`() {
    val sut = fixture.getSut()
    whenever(fixture.activity.lifecycle.currentState).thenReturn(Lifecycle.State.RESUMED)
    CurrentActivityHolder.getInstance().setActivity(fixture.activity)

    sut.register(fixture.scopes, fixture.options)
    assertIs<SentryWindowCallback>(fixture.window.callback)
  }

  @Test
  fun `when activity is resumed but not a LifecycleOwner, does not start tracking immediately`() {
    val sut = fixture.getSut()
    val activity = mock<Activity>()
    val window = mock<Window>()
    whenever(activity.window).thenReturn(window)

    CurrentActivityHolder.getInstance().setActivity(activity)
    sut.register(fixture.scopes, fixture.options)

    verify(window, never()).callback = any()
  }

  @Test
  fun `when activity is not in RESUMED state, does not start tracking immediately`() {
    val sut = fixture.getSut()
    whenever(fixture.activity.lifecycle.currentState).thenReturn(Lifecycle.State.CREATED)
    CurrentActivityHolder.getInstance().setActivity(fixture.activity)

    sut.register(fixture.scopes, fixture.options)
    assertIsNot<SentryWindowCallback>(fixture.activity.window.callback)
  }
}

private class EmptyActivity : Activity(), LifecycleOwner {
  override val lifecycle: Lifecycle = mock<Lifecycle>()
}

/** Simulates a third-party callback wrapper (e.g. Session Replay's FixedWindowCallback). */
private open class WrapperCallback(@JvmField val delegate: Window.Callback) :
  Window.Callback by delegate
