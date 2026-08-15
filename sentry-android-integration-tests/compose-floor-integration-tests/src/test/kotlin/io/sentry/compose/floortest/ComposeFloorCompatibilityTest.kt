package io.sentry.compose.floortest

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.compose.SentryTraced
import io.sentry.compose.SentryUserFeedbackButton
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Composes the public entry points of sentry-compose against the oldest Compose version we support.
 *
 * Compose inlines composables such as `Box` into calling bytecode, so sentry-compose can end up
 * referencing Compose internals from whatever version it was compiled against. Those references
 * resolve fine in our own test suite, which runs on a recent Compose, but throw NoSuchMethodError
 * on a consumer using an older one. Compose is pinned to the floor for this module so that mismatch
 * fails here instead of in a user's app.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class ComposeFloorCompatibilityTest {
  // workaround for robolectric tests with composeRule
  // from https://github.com/robolectric/robolectric/pull/4736#issuecomment-1831034882
  @get:Rule(order = 1)
  val addActivityToRobolectricRule =
    object : TestWatcher() {
      override fun starting(description: Description?) {
        super.starting(description)
        val appContext: Application = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(appContext.packageManager)
          .addActivityIfNotPresent(
            ComponentName(appContext.packageName, ComponentActivity::class.java.name)
          )
      }
    }

  @get:Rule(order = 2) val rule = createAndroidComposeRule<ComponentActivity>()

  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun `SentryTraced composes on the oldest supported Compose`() {
    rule.setContent { SentryTraced("floor-tag") { Text("content") } }

    rule.onNodeWithText("content").assertExists()
  }

  @Suppress("DEPRECATION")
  @Test
  fun `SentryUserFeedbackButton composes on the oldest supported Compose`() {
    rule.setContent { SentryUserFeedbackButton(text = "Report a Bug") }

    rule.onNodeWithText("Report a Bug").assertExists()
  }
}
