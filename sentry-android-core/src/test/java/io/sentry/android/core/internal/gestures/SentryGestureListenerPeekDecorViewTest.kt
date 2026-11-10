package io.sentry.android.core.internal.gestures

import android.app.Activity
import android.view.MotionEvent
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.util.LazyEvaluator
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class SentryGestureListenerPeekDecorViewTest {

  @Test
  fun `does not force decor view creation when peekDecorView returns null`() {
    // Robolectric creates the decor view in onCreate on API 23+, so mock a window without one
    val window = mock<Window>()
    val activity = mock<Activity> { on { this.window } doReturn window }

    val scopes = mock<IScopes>()
    val options =
      SentryAndroidOptions().apply {
        isEnableUserInteractionBreadcrumbs = true
        gestureTargetLocators = listOf(AndroidViewGestureTargetLocator(LazyEvaluator { true }))
        dsn = "https://key@sentry.io/proj"
      }

    val sut = SentryGestureListener(activity, scopes, options)
    sut.onSingleTapUp(mock<MotionEvent>())

    // The key assertion: we did not force view hierarchy creation
    verify(window, never()).decorView

    // And no breadcrumb was captured
    verify(scopes, never()).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
  }
}
