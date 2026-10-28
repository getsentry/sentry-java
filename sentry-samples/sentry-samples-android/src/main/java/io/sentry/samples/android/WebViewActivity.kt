package io.sentry.samples.android

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import io.sentry.Sentry
import io.sentry.android.core.SentryWebViewTracing

/**
 * Connects the app's trace with a page shown in a WebView.
 *
 * The page is bundled in `assets/webview` and served over https by [WebViewAssetLoader], so its
 * host can be allowed like any other. It runs `@sentry/browser`, which continues the injected trace
 * on pageload, and reports back the `sentry-trace` meta tag its first synchronous `<head>` script
 * saw, so the connection can be checked on the device.
 */
class WebViewActivity : AppCompatActivity() {

  private lateinit var receivedView: TextView
  private lateinit var statusView: TextView

  private var nativeTrace: String? = null
  private var pageTrace: String? = null
  private var installed = false

  @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val nativeTraceView = valueView()
    receivedView = valueView()
    statusView = TextView(this).apply { setTypeface(typeface, Typeface.BOLD) }
    val webView = WebView(this)

    setContentView(
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
          val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
          view.setPadding(32, systemBars.top + 32, 32, systemBars.bottom)
          insets
        }
        addView(labelView("App trace"))
        addView(nativeTraceView)
        addView(labelView("WebView received"))
        addView(receivedView)
        addView(statusView)
        addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
      }
    )

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
    // Only the bundled demo page is loaded, so exposing this bridge to it is fine for a sample.
    webView.addJavascriptInterface(PageBridge(), "SentrySample")

    // The trace that addTracePropagation injects, read right before it takes its snapshot.
    nativeTrace = Sentry.getTraceparent()?.value
    nativeTraceView.text = nativeTrace ?: "(no trace)"

    // Before loadUrl: the script applies to documents that start loading after this call.
    installed = SentryWebViewTracing.addTracePropagation(webView, ALLOWED_HOST)
    updateStatus()

    webView.loadUrl(PAGE_URL)
  }

  private fun updateStatus() {
    receivedView.text = pageTrace?.ifEmpty { "(none, not injected)" } ?: "(waiting…)"
    statusView.text =
      when {
        !installed -> "Trace propagation not installed, see the Sentry logs"
        pageTrace != null && pageTrace == nativeTrace -> "WebView trace connected ✓"
        else -> "WebView trace not connected"
      }
  }

  private fun labelView(text: String): TextView =
    TextView(this).apply {
      this.text = text
      setTypeface(typeface, Typeface.BOLD)
      setPadding(0, 16, 0, 0)
    }

  private fun valueView(): TextView =
    TextView(this).apply {
      typeface = Typeface.MONOSPACE
      setTextIsSelectable(true)
    }

  /** Called by the page's scripts, on a background thread. */
  private inner class PageBridge {
    @JavascriptInterface fun dsn(): String? = Sentry.getCurrentScopes().options.dsn

    @JavascriptInterface
    fun onPageTrace(trace: String) {
      runOnUiThread {
        pageTrace = trace
        updateStatus()
      }
    }
  }

  private companion object {
    const val ALLOWED_HOST = WebViewAssetLoader.DEFAULT_DOMAIN
    const val PAGE_URL = "https://$ALLOWED_HOST/assets/webview/index.html"
  }
}
