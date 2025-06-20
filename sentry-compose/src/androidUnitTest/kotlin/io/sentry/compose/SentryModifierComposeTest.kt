package io.sentry.compose

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.compose.SentryModifier.sentryTag
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class SentryModifierComposeTest {
  companion object {
    private const val TAG_VALUE = "ExampleTagValue"
  }

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

  @Test
  fun sentryModifierAppliesTag() {
    rule.setContent { Box(modifier = Modifier.sentryTag(TAG_VALUE)) }
    rule
      .onNode(
        SemanticsMatcher(TAG_VALUE) {
          it.config.find { (key, _) -> key.name == SentryModifier.TAG }?.value == TAG_VALUE
        }
      )
      .assertExists()
  }
}
