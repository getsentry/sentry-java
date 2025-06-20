package io.sentry.uitest.android

import android.view.InputDevice
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroidOptions
import kotlin.test.Test
import kotlin.test.assertTrue
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assume.assumeThat
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserInteractionTests : BaseUiTest() {
  @Before
  fun setup() {
    assumeThat(classExists("io.sentry.compose.gestures.ComposeGestureTargetLocator"), `is`(true))
  }

  @Test
  fun composableClickGeneratesMatchingBreadcrumb() {
    val breadcrumbs = mutableListOf<Breadcrumb>()
    initSentryAndCollectBreadcrumbs(breadcrumbs)

    val activity = launchActivity<ComposeActivity>()
    activity.moveToState(Lifecycle.State.RESUMED)

    // some sane defaults
    var height = 500
    var width = 500
    activity.onActivity {
      height = it.resources.displayMetrics.heightPixels
      width = it.resources.displayMetrics.widthPixels
    }

    Espresso.onView(ViewMatchers.withId(android.R.id.content))
      .perform(
        GeneralClickAction(
          Tap.SINGLE,
          { floatArrayOf(width / 2f, height / 2f) },
          Press.FINGER,
          InputDevice.SOURCE_UNKNOWN,
          MotionEvent.BUTTON_PRIMARY,
        )
      )
    activity.moveToState(Lifecycle.State.DESTROYED)
    assertTrue(
      breadcrumbs
        .filter { it.category == "ui.click" && it.data["view.tag"] == "button_login" }
        .size == 1
    )
  }

  @Test
  fun composableSwipeGeneratesMatchingBreadcrumb() {
    val breadcrumbs = mutableListOf<Breadcrumb>()
    initSentryAndCollectBreadcrumbs(breadcrumbs)

    val activity = launchActivity<ComposeActivity>()
    activity.moveToState(Lifecycle.State.RESUMED)
    Espresso.onView(ViewMatchers.withId(android.R.id.content)).perform(ViewActions.swipeUp())
    activity.moveToState(Lifecycle.State.DESTROYED)
    assertTrue(
      breadcrumbs
        .filter {
          it.category == "ui.swipe" && it.data["view.tag"] == "list" && it.data["direction"] == "up"
        }
        .size == 1
    )
  }

  private fun initSentryAndCollectBreadcrumbs(breadcrumbs: MutableList<Breadcrumb>) {
    initSentry(false) { options: SentryAndroidOptions ->
      options.isDebug = true
      options.setDiagnosticLevel(SentryLevel.DEBUG)
      options.tracesSampleRate = 1.0
      options.profilesSampleRate = 1.0
      options.isEnableUserInteractionTracing = true
      options.isEnableUserInteractionBreadcrumbs = true
      options.beforeBreadcrumb =
        SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
          breadcrumbs.add(breadcrumb)
          breadcrumb
        }
    }
  }
}
