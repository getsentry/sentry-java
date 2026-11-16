package io.sentry.uitest.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import io.sentry.Sentry
import io.sentry.android.core.SentryWebViewTracing

/** Loads a bundled page with WebView trace propagation and records what the page received. */
class WebViewTraceActivity : AppCompatActivity() {

  /** The trace the app injected. */
  @Volatile var nativeTrace: String? = null

  /** Whether addTracePropagation installed its script. */
  @Volatile var installed: Boolean = false

  /** The sentry-trace meta tag seen by the page's first synchronous script, empty if missing. */
  @Volatile var pageTrace: String? = null

  /** Whether the iframe on the page saw a sentry-trace meta tag. */
  @Volatile var iframeInjected: Boolean? = null

  @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val webView = WebView(this)
    setContentView(webView)

    val assetLoader =
      WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
        .build()
    webView.webViewClient =
      object : WebViewClient() {
        override fun shouldInterceptRequest(
          view: WebView,
          request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
      }
    webView.settings.javaScriptEnabled = true
    webView.addJavascriptInterface(PageBridge(), "SentryTest")

    nativeTrace = Sentry.getTraceparent()?.value
    installed = SentryWebViewTracing.addTracePropagation(webView, WebViewAssetLoader.DEFAULT_DOMAIN)
    webView.loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/webview/index.html")
  }

  private inner class PageBridge {
    @JavascriptInterface
    fun onPageTrace(trace: String) {
      pageTrace = trace
    }

    @JavascriptInterface
    fun onIframeTrace(injected: Boolean) {
      iframeInjected = injected
    }
  }
}
