package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewFeature
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.awaitility.kotlin.await
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewTracePropagationTest : BaseUiTest() {

  @Test
  fun injectsTheTraceBeforePageScriptsAndOnlyIntoTheMainFrame() {
    assumeTrue(
      "The System WebView does not support document-start scripts",
      WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT),
    )
    initSentry { it.tracesSampleRate = 1.0 }

    val scenario = launchActivity<WebViewTraceActivity>()
    scenario.moveToState(Lifecycle.State.RESUMED)

    var activity: WebViewTraceActivity? = null
    scenario.onActivity { activity = it }
    val webViewActivity = assertNotNull(activity)
    await.atMost(30, TimeUnit.SECONDS).until {
      webViewActivity.pageTrace != null && webViewActivity.iframeInjected != null
    }

    assertTrue(webViewActivity.installed)
    assertNotNull(webViewActivity.nativeTrace)
    // Read by the page's first synchronous <head> script: present, so there is no race.
    assertEquals(webViewActivity.nativeTrace, webViewActivity.pageTrace)
    assertFalse(webViewActivity.iframeInjected!!)
    scenario.close()
  }
}
