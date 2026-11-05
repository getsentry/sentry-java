package io.sentry.android.core;

import android.webkit.WebView;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Installs Sentry's document-start script on a WebView. Declares no androidx.webkit types, so it
 * can be referenced before the androidx.webkit availability check.
 */
interface WebViewScriptInstaller {

  boolean isDocumentStartScriptSupported();

  /** Removes the script Sentry installed earlier on this WebView, if any. */
  void removeInstalledScript(@NotNull WebView webView);

  /**
   * Installs the script for documents whose origin matches one of the rules.
   *
   * @return false if document-start scripts are not supported, in which case nothing is installed
   * @throws IllegalArgumentException if an origin rule is malformed
   */
  boolean installScript(
      @NotNull WebView webView, @NotNull String script, @NotNull Set<String> allowedOriginRules);
}
