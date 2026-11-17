package io.sentry.android.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Options for connecting the app's trace with web content in a {@code WebView}. */
public final class SentryWebViewTracingOptions {

  private @NotNull List<String> allowedHosts = Collections.emptyList();

  /**
   * Returns the hosts {@link SentryWebViewTracing#addTracePropagation(android.webkit.WebView)}
   * propagates the trace to.
   *
   * @return the allowed hosts, empty by default
   */
  public @NotNull List<String> getAllowedHosts() {
    return allowedHosts;
  }

  /**
   * Sets the hosts {@link SentryWebViewTracing#addTracePropagation(android.webkit.WebView)}
   * propagates the trace to. This is a trust boundary: only list hosts whose content you control.
   * It is separate from {@code tracePropagationTargets}, which controls outgoing HTTP requests.
   *
   * @param allowedHosts the allowed hosts, matched like the ones passed to {@link
   *     SentryWebViewTracing#addTracePropagation(android.webkit.WebView, List)}
   */
  public void setAllowedHosts(final @NotNull List<String> allowedHosts) {
    this.allowedHosts = new ArrayList<>(allowedHosts);
  }
}
