package io.sentry.android.core.internal.gestures

import android.app.Activity
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.util.LazyEvaluator
import kotlin.test.Test
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
class SentryGestureListenerPeekDecorViewTest {

  @Test
  fun `does not force decor view creation when peekDecorView returns null`() {
    // A plain Activity that never calls setContentView — peekDecorView() should return null
    val activity = buildActivity(Activity::class.java).create().get()

    // Sanity check: decor view has not been created yet
    assertNull(activity.window.peekDecorView())

    val scopes = mock<IScopes>()
    val options =
      SentryAndroidOptions().apply {
        isEnableUserInteractionBreadcrumbs = true
        gestureTargetLocators = listOf(AndroidViewGestureTargetLocator(LazyEvaluator { true }))
        dsn = "https://key@sentry.io/proj"
      }

    val sut = SentryGestureListener(activity, scopes, options)
    sut.onSingleTapUp(mock<MotionEvent>())

    // The key assertion: peekDecorView is still null — we did not force view hierarchy creation
    assertNull(activity.window.peekDecorView())

    // And no breadcrumb was captured
    verify(scopes, never()).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
  }
}
