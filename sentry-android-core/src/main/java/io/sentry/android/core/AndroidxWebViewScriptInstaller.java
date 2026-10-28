package io.sentry.android.core;

import android.webkit.WebView;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Installs Sentry's document-start script through androidx.webkit. All androidx.webkit references
 * are isolated in this class, so it must only be loaded once {@link SentryWebViewTracing} has
 * checked that androidx.webkit is available. That check deliberately lives outside of this class,
 * so that it can run without linking any androidx.webkit type.
 */
final class AndroidxWebViewScriptInstaller implements WebViewScriptInstaller {

  @Override
  public boolean isDocumentStartScriptSupported() {
    return WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
  }

  @Override
  public void removeInstalledScript(final @NotNull WebView webView) {
    final Object handler = webView.getTag(R.id.sentry_webview_trace_propagation);
    // A handler only exists if the feature was supported when it was installed.
    if (handler instanceof ScriptHandler
        && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
      ((ScriptHandler) handler).remove();
    }
    webView.setTag(R.id.sentry_webview_trace_propagation, null);
  }

  @Override
  public boolean installScript(
      final @NotNull WebView webView,
      final @NotNull String script,
      final @NotNull Set<String> allowedOriginRules) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
      return false;
    }
    final @NotNull ScriptHandler handler =
        WebViewCompat.addDocumentStartJavaScript(webView, script, allowedOriginRules);
    webView.setTag(R.id.sentry_webview_trace_propagation, handler);
    return true;
  }
}
