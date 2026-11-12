package io.sentry.android.core

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.common.truth.Truth.assertThat
import io.sentry.OptionsContainer
import io.sentry.Sentry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SentryWebViewTracingAdapterTest {

  private lateinit var webView: WebView
  private lateinit var webViewCompat: MockedStatic<WebViewCompat>
  private lateinit var webViewFeature: MockedStatic<WebViewFeature>
  private val installedOrigins = mutableListOf<Set<String>>()
  private var installFailure: Throwable? = null

  @BeforeTest
  fun setUp() {
    webView = WebView(ApplicationProvider.getApplicationContext())
    webViewFeature = mockStatic(WebViewFeature::class.java)
    webViewFeature
      .`when`<Boolean> { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
      .thenReturn(true)
    webViewCompat = mockStatic(WebViewCompat::class.java)
    webViewCompat
      .`when`<ScriptHandler> { WebViewCompat.addDocumentStartJavaScript(eq(webView), any(), any()) }
      .thenAnswer { invocation ->
        installFailure?.let { throw it }
        installedOrigins.add(invocation.getArgument(2))
        mock<ScriptHandler>()
      }
  }

  @AfterTest
  fun tearDown() {
    webViewCompat.close()
    webViewFeature.close()
    Sentry.close()
  }

  private fun initSentry(allowedHosts: List<String> = listOf("example.com")) {
    Sentry.init(OptionsContainer.create(SentryAndroidOptions::class.java)) {
      it.dsn = "https://key@sentry.io/123"
      it.webViewTracing.allowedHosts = allowedHosts
    }
  }

  @Test
  fun `refreshes the trace for the hosts in the options and loads the url`() {
    initSentry()

    SentryWebViewTracingAdapter.loadUrl(webView, "https://example.com")
    SentryWebViewTracingAdapter.loadUrl(webView, "https://example.com/next")

    assertThat(installedOrigins).hasSize(2)
    assertThat(installedOrigins.last())
      .containsExactly("https://example.com", "https://*.example.com")
    assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo("https://example.com/next")
  }

  @Test
  fun `passes the headers through`() {
    initSentry()

    SentryWebViewTracingAdapter.loadUrl(webView, "https://example.com", mapOf("a" to "b"))

    assertThat(installedOrigins).hasSize(1)
    assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo("https://example.com")
    assertThat(shadowOf(webView).lastAdditionalHttpHeaders).containsExactly("a", "b")
  }

  @Test
  fun `keeps the hosts the app passed explicitly`() {
    initSentry(allowedHosts = listOf("example.com", "other.com"))
    assertThat(SentryWebViewTracing.addTracePropagation(webView, "docs.example.com")).isTrue()

    SentryWebViewTracingAdapter.loadUrl(webView, "https://docs.example.com")

    assertThat(installedOrigins.last())
      .containsExactly("https://docs.example.com", "https://*.docs.example.com")
  }

  @Test
  fun `uses the options again after the app connects the WebView from the options`() {
    initSentry()
    SentryWebViewTracing.addTracePropagation(webView, "docs.example.com")
    SentryWebViewTracing.addTracePropagation(webView)

    SentryWebViewTracingAdapter.loadUrl(webView, "https://example.com")

    assertThat(installedOrigins.last())
      .containsExactly("https://example.com", "https://*.example.com")
  }

  @Test
  fun `without hosts only loads the url and keeps a script the app installed`() {
    initSentry(allowedHosts = emptyList())
    val handler = mock<ScriptHandler>()
    webView.setTag(R.id.sentry_webview_trace_propagation, handler)

    SentryWebViewTracingAdapter.loadUrl(webView, "https://example.com")

    assertThat(installedOrigins).isEmpty()
    verify(handler, never()).remove()
    assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo("https://example.com")
  }

  @Test
  fun `does not refresh for urls that are not http`() {
    initSentry()

    SentryWebViewTracingAdapter.loadUrl(webView, "javascript:void(0)")
    SentryWebViewTracingAdapter.loadUrl(webView, "about:blank")
    SentryWebViewTracingAdapter.loadUrl(webView, null)

    assertThat(installedOrigins).isEmpty()
  }

  @Test
  fun `loads the url when the refresh fails`() {
    initSentry()
    installFailure = IllegalStateException("WebView destroyed")

    SentryWebViewTracingAdapter.loadUrl(webView, "https://example.com")

    assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo("https://example.com")
  }

  @Test
  fun `rethrows fatal errors from the refresh`() {
    initSentry()
    installFailure = OutOfMemoryError()

    assertFailsWith<OutOfMemoryError> {
      SentryWebViewTracingAdapter.loadUrl(webView, "https://example.com")
    }
  }

  @Test
  fun `does not refresh off the WebView's thread`() {
    initSentry()
    var refreshed: Boolean? = null

    val thread = Thread { refreshed = SentryWebViewTracing.refreshTracePropagation(webView) }
    thread.start()
    thread.join()

    assertThat(refreshed).isFalse()
    assertThat(installedOrigins).isEmpty()
  }
}
