package io.sentry.compose

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.TransactionOptions
import io.sentry.test.createTestScopes
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class SentryTracedTest {
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

  @After
  fun tearDown() {
    Sentry.close()
  }

  private fun newTracingScopes(): IScopes =
    createTestScopes(
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        tracesSampleRate = 1.0
      }
    )

  private fun IScopes.startBoundTransaction(name: String): ITransaction =
    startTransaction(name, "test", TransactionOptions().apply { isBindToScope = true })

  @Test
  fun `SentryTraced creates its root span on the scopes provided via LocalSentryScopes`() {
    val scopes = newTracingScopes()
    val tx = scopes.startBoundTransaction("custom-scopes-tx")

    rule.setContent {
      CompositionLocalProvider(LocalSentryScopes provides scopes) {
        SentryTraced(tag = "custom") { Box {} }
      }
    }
    rule.waitForIdle()

    assertEquals(1, tx.spans.count { it.operation == "ui.compose.composition" })
    assertEquals(1, tx.spans.count { it.operation == "ui.compose" })
  }

  @Test
  fun `sibling SentryTraced composables under the same scopes share one root span`() {
    val scopes = newTracingScopes()
    val tx = scopes.startBoundTransaction("custom-scopes-tx")

    rule.setContent {
      CompositionLocalProvider(LocalSentryScopes provides scopes) {
        SentryTraced(tag = "first") { Box {} }
        SentryTraced(tag = "second") { Box {} }
      }
    }
    rule.waitForIdle()

    assertEquals(1, tx.spans.count { it.operation == "ui.compose.composition" })
    assertEquals(2, tx.spans.count { it.operation == "ui.compose" })
  }

  @Test
  fun `SentryTraced composables under different scopes do not interfere with each other`() {
    val scopesA = newTracingScopes()
    val txA = scopesA.startBoundTransaction("scopes-a-tx")
    val scopesB = newTracingScopes()
    val txB = scopesB.startBoundTransaction("scopes-b-tx")

    rule.setContent {
      CompositionLocalProvider(LocalSentryScopes provides scopesA) {
        SentryTraced(tag = "a") { Box {} }
      }
      CompositionLocalProvider(LocalSentryScopes provides scopesB) {
        SentryTraced(tag = "b") { Box {} }
      }
    }
    rule.waitForIdle()

    assertEquals(1, txA.spans.count { it.operation == "ui.compose.composition" })
    assertEquals(1, txA.spans.count { it.operation == "ui.compose" })
    assertEquals(1, txB.spans.count { it.operation == "ui.compose.composition" })
    assertEquals(1, txB.spans.count { it.operation == "ui.compose" })
  }

  @Test
  fun `repeatedly replacing a traced composable under the same scopes keeps sharing the root span`() {
    // Each swap disposes the outgoing keyed composable and mounts a new one under the same
    // scopes within a single recomposition. Compose dispatches the outgoing composable's
    // onDispose before the incoming one's DisposableEffect runs, so a second swap is needed to
    // surface a root span cache that was cleared out from under a still-live retain.
    val scopes = newTracingScopes()
    val tx = scopes.startBoundTransaction("custom-scopes-tx")
    var step by mutableStateOf(0)

    rule.setContent {
      CompositionLocalProvider(LocalSentryScopes provides scopes) {
        key(step) { SentryTraced(tag = "step-$step") { Box {} } }
      }
    }
    rule.waitForIdle()

    step = 1
    rule.waitForIdle()

    step = 2
    rule.waitForIdle()

    assertEquals(1, tx.spans.count { it.operation == "ui.compose.composition" })
  }
}
