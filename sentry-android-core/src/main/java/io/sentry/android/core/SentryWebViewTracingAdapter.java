package io.sentry.android.core;

import android.webkit.WebView;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.util.ExceptionUtils;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Target of the Sentry Android Gradle Plugin's WebView instrumentation, which replaces {@code
 * WebView.loadUrl} calls with calls to this class. Refreshes the trace propagated to the page, then
 * calls the original {@code loadUrl}. It never changes how the load itself behaves.
 */
@ApiStatus.Internal
public final class SentryWebViewTracingAdapter {

  private SentryWebViewTracingAdapter() {}

  public static void loadUrl(final @NotNull WebView webView, final @Nullable String url) {
    refresh(webView, url);
    webView.loadUrl(url);
  }

  public static void loadUrl(
      final @NotNull WebView webView,
      final @Nullable String url,
      final @Nullable Map<String, String> additionalHttpHeaders) {
    refresh(webView, url);
    webView.loadUrl(url, additionalHttpHeaders);
  }

  private static void refresh(final @NotNull WebView webView, final @Nullable String url) {
    // Only page loads get a trace. Skips e.g. javascript: URLs, which run code in the current page.
    if (url == null || !isHttpUrl(url)) {
      return;
    }
    try {
      SentryWebViewTracing.refreshTracePropagation(webView);
    } catch (Throwable e) {
      ExceptionUtils.rethrowIfFatal(e);
      Sentry.getCurrentScopes()
          .getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Failed to refresh the WebView trace propagation.", e);
    }
  }

  private static boolean isHttpUrl(final @NotNull String url) {
    final @NotNull String lower = url.trim().toLowerCase(Locale.ROOT);
    return lower.startsWith("https://") || lower.startsWith("http://");
  }
}
