package io.sentry.android.core

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.common.truth.Truth.assertThat
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.TransactionOptions
import io.sentry.util.LoadClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SentryWebViewTracingTest {

  private class FakeInstaller(
    var supported: Boolean = true,
    var installFailure: IllegalArgumentException? = null,
    var linkageError: LinkageError? = null,
  ) : WebViewScriptInstaller {
    var removeCount = 0
    val installed = mutableListOf<Pair<String, Set<String>>>()

    override fun isDocumentStartScriptSupported(): Boolean = supported

    override fun removeInstalledScript(webView: WebView) {
      linkageError?.let { throw it }
      removeCount++
    }

    override fun installScript(
      webView: WebView,
      script: String,
      allowedOriginRules: Set<String>,
    ): Boolean {
      installFailure?.let { throw it }
      installed.add(script to allowedOriginRules)
      return true
    }
  }

  private lateinit var webView: WebView
  private val installer = FakeInstaller()

  @BeforeTest
  fun setUp() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
    webView = WebView(ApplicationProvider.getApplicationContext())
  }

  @AfterTest
  fun tearDown() {
    Sentry.close()
  }

  private fun initSentry(): IScopes {
    Sentry.init {
      it.dsn = "https://key@sentry.io/123"
      it.release = "app@1.0"
      it.environment = "prod"
      it.tracesSampleRate = 1.0
    }
    return Sentry.getCurrentScopes()
  }

  private fun add(
    allowedHosts: List<String> = listOf("example.com"),
    scopes: IScopes = Sentry.getCurrentScopes(),
  ): Boolean = SentryWebViewTracing.install(webView, allowedHosts, scopes, installer)

  @Test
  fun `installs the current trace for the allowed hosts`() {
    val scopes = initSentry()

    assertThat(add()).isTrue()

    val (script, rules) = installer.installed.single()
    assertThat(script).contains(scopes.traceparent!!.value)
    assertThat(script).contains("sentry-release=app%401.0")
    assertThat(rules).containsExactly("https://example.com", "https://*.example.com")
  }

  @Test
  fun `does not propagate the root span name`() {
    initSentry()
    val transaction =
      Sentry.startTransaction(
        "Checkout user@example.com",
        "ui.load",
        TransactionOptions().apply { isBindToScope = true },
      )

    assertThat(add()).isTrue()

    val (script, _) = installer.installed.single()
    assertThat(script).contains(transaction.toSentryTrace().value)
    assertThat(script).contains("sentry-sampled=true")
    assertThat(script).doesNotContain("sentry-transaction")
    transaction.finish()
  }

  @Test
  fun `removes the previous script before installing again`() {
    initSentry()

    assertThat(add()).isTrue()
    assertThat(add()).isTrue()

    assertThat(installer.removeCount).isEqualTo(2)
    assertThat(installer.installed).hasSize(2)
  }

  @Test
  fun `returns false and leaves no script when allowedHosts has no valid entry`() {
    initSentry()

    assertThat(add(listOf("  ", ""))).isFalse()

    assertThat(installer.removeCount).isEqualTo(1)
    assertThat(installer.installed).isEmpty()
  }

  @Test
  fun `returns false and leaves no script when document-start scripts are unsupported`() {
    initSentry()
    installer.supported = false

    assertThat(add()).isFalse()

    assertThat(installer.removeCount).isEqualTo(1)
    assertThat(installer.installed).isEmpty()
  }

  @Test
  fun `returns false and leaves no script when the SDK is not initialized`() {
    assertThat(add()).isFalse()

    assertThat(installer.removeCount).isEqualTo(1)
    assertThat(installer.installed).isEmpty()
  }

  @Test
  fun `a re-add after Sentry close removes the previous script`() {
    initSentry()
    assertThat(add()).isTrue()
    Sentry.close()

    assertThat(add()).isFalse()

    assertThat(installer.removeCount).isEqualTo(2)
    assertThat(installer.installed).hasSize(1)
  }

  @Test
  fun `treats a subdomain wildcard entry like the bare host`() {
    initSentry()

    assertThat(add(listOf("*.example.com"))).isTrue()

    val (_, rules) = installer.installed.single()
    assertThat(rules).containsExactly("https://example.com", "https://*.example.com")
  }

  @Test
  fun `returns false and touches nothing when called off the WebView's thread`() {
    initSentry()
    var result: Boolean? = null

    val thread = Thread { result = add() }
    thread.start()
    thread.join()

    assertThat(result).isFalse()
    assertThat(installer.removeCount).isEqualTo(0)
    assertThat(installer.installed).isEmpty()
  }

  @Test
  fun `returns false when androidx webkit is too old`() {
    initSentry()
    installer.linkageError = NoSuchMethodError("addDocumentStartJavaScript")

    assertThat(add()).isFalse()

    assertThat(installer.installed).isEmpty()
  }

  @Test
  fun `passes a non-default port through to the origin rules`() {
    initSentry()

    assertThat(add(listOf("example.com:8443"))).isTrue()

    val (_, rules) = installer.installed.single()
    assertThat(rules).containsExactly("https://example.com:8443", "https://*.example.com:8443")
  }

  @Test
  fun `returns false when the origin rules are rejected`() {
    initSentry()
    installer.installFailure = IllegalArgumentException("bad rule")

    assertThat(add(listOf("*"))).isFalse()
  }

  @Test
  fun `returns false when androidx webkit is not available and still records adoption`() {
    val scopes = initSentry()
    val loadClass = mock<LoadClass>()
    whenever(
        loadClass.isClassAvailable(
          eq(SentryWebViewTracing.SCRIPT_HANDLER_CLASS_NAME),
          any<io.sentry.SentryOptions>(),
        )
      )
      .thenReturn(false)

    assertThat(
        SentryWebViewTracing.addTracePropagation(
          webView,
          listOf("example.com"),
          scopes,
          loadClass,
        )
      )
      .isFalse()
    // The adoption marker is registered on use, even when nothing can be installed.
    assertThat(SentryIntegrationPackageStorage.getInstance().integrations)
      .contains("WebViewTracePropagation")
  }

  @Test
  fun `androidx installer stores the script handler and removes it on the next call`() {
    val handler = mock<ScriptHandler>()
    mockStatic(WebViewCompat::class.java).use { webViewCompat ->
      mockStatic(WebViewFeature::class.java).use { webViewFeature ->
        webViewFeature
          .`when`<Boolean> {
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
          }
          .thenReturn(true)
        webViewCompat
          .`when`<ScriptHandler> {
            WebViewCompat.addDocumentStartJavaScript(eq(webView), any(), any())
          }
          .thenReturn(handler)
        val androidxInstaller = AndroidxWebViewScriptInstaller()

        assertThat(androidxInstaller.isDocumentStartScriptSupported).isTrue()
        androidxInstaller.removeInstalledScript(webView)
        assertThat(androidxInstaller.installScript(webView, "script", setOf("https://example.com")))
          .isTrue()
        assertThat(webView.getTag(R.id.sentry_webview_trace_propagation)).isSameInstanceAs(handler)

        androidxInstaller.removeInstalledScript(webView)

        verify(handler, times(1)).remove()
        assertThat(webView.getTag(R.id.sentry_webview_trace_propagation)).isNull()
      }
    }
  }

  @Test
  fun `androidx installer installs nothing when document-start scripts are unsupported`() {
    mockStatic(WebViewCompat::class.java).use { webViewCompat ->
      mockStatic(WebViewFeature::class.java).use { webViewFeature ->
        webViewFeature
          .`when`<Boolean> {
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
          }
          .thenReturn(false)

        val installed =
          AndroidxWebViewScriptInstaller()
            .installScript(webView, "script", setOf("https://example.com"))

        assertThat(installed).isFalse()
        webViewCompat.verifyNoInteractions()
        assertThat(webView.getTag(R.id.sentry_webview_trace_propagation)).isNull()
      }
    }
  }
}
