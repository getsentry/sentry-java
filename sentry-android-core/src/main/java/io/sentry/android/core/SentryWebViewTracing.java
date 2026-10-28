package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.os.Build;
import android.os.Looper;
import android.webkit.WebView;
import androidx.annotation.MainThread;
import io.sentry.BaggageHeader;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.util.LoadClass;
import io.sentry.util.TracingUtils;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Connects the app's trace with the trace of web content shown in a {@link WebView}.
 *
 * <p>{@link #addTracePropagation(WebView, List)} writes the current trace into every document the
 * WebView loads from an allowed host as {@code sentry-trace} and {@code baggage} meta tags, before
 * any page script runs. A {@code @sentry/browser} running in the page with {@code
 * browserTracingIntegration} then continues the trace on pageload.
 *
 * <p>Requires the {@code androidx.webkit:webkit} dependency and an Android System WebView that
 * supports {@code WebViewFeature.DOCUMENT_START_SCRIPT}.
 */
public final class SentryWebViewTracing {

  static final @NotNull String SCRIPT_HANDLER_CLASS_NAME = "androidx.webkit.ScriptHandler";

  private static final @NotNull String INTEGRATION_NAME = "WebViewTracePropagation";

  private SentryWebViewTracing() {}

  /**
   * Same as {@link #addTracePropagation(WebView, List)}.
   *
   * @param webView the WebView to connect
   * @param allowedHosts hosts whose pages receive the trace
   * @return true if the trace propagation script was installed
   */
  @MainThread
  public static boolean addTracePropagation(
      final @NotNull WebView webView, final @NotNull String... allowedHosts) {
    return addTracePropagation(webView, Arrays.asList(allowedHosts));
  }

  /**
   * Installs a script that writes the current trace into documents the WebView loads from an
   * allowed host. Call it on the main thread, before loading a URL: it applies to documents that
   * start loading after the call.
   *
   * <p>{@code allowedHosts} is a trust boundary: only list hosts whose content you control. A page
   * matches when its hostname equals an entry or is a subdomain of it, over https. Entries are
   * normalized, so a scheme, a path, and a trailing dot are ignored. A port other than 443 limits
   * the entry to pages served on that port (for example {@code "example.com:8443"}).
   *
   * <p>The trace is a snapshot taken when this is called. To propagate a newer trace to a later
   * page load, call this again: it replaces the script Sentry installed earlier and leaves other
   * scripts alone. If the call fails, no Sentry script is left on the WebView.
   *
   * <p>The root span name ({@code sentry-transaction}) is removed from {@code baggage}, as it is
   * free-form text that may carry user data.
   *
   * @param webView the WebView to connect
   * @param allowedHosts hosts whose pages receive the trace
   * @return true if the script was installed; false if androidx.webkit is missing, the WebView does
   *     not support document-start scripts, {@code allowedHosts} has no valid entry, or there is no
   *     trace (for example the SDK is not initialized), or it is called off the WebView's thread
   */
  @MainThread
  public static boolean addTracePropagation(
      final @NotNull WebView webView, final @NotNull List<String> allowedHosts) {
    return addTracePropagation(webView, allowedHosts, Sentry.getCurrentScopes(), new LoadClass());
  }

  static boolean addTracePropagation(
      final @NotNull WebView webView,
      final @NotNull List<String> allowedHosts,
      final @NotNull IScopes scopes,
      final @NotNull LoadClass loadClass) {
    // Registered on use rather than on success, like the other SDKs, so adoption counts apps that
    // wired the feature up even when this call cannot install.
    addIntegrationToSdkVersion(INTEGRATION_NAME);

    if (!loadClass.isClassAvailable(SCRIPT_HANDLER_CLASS_NAME, scopes.getOptions())) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "WebView trace propagation requires the androidx.webkit dependency, skipping.");
      return false;
    }
    return install(webView, allowedHosts, scopes, new AndroidxWebViewScriptInstaller());
  }

  static boolean install(
      final @NotNull WebView webView,
      final @NotNull List<String> allowedHosts,
      final @NotNull IScopes scopes,
      final @NotNull WebViewScriptInstaller installer) {
    final @NotNull ILogger logger = scopes.getOptions().getLogger();

    // WebView methods throw when called off the WebView's thread; fail like any other misuse.
    if (!isOnWebViewThread(webView)) {
      logger.log(
          SentryLevel.WARNING,
          "addTracePropagation must be called on the WebView's thread, skipping WebView trace propagation.");
      return false;
    }

    try {
      return replaceScript(webView, allowedHosts, scopes, installer, logger);
    } catch (LinkageError e) {
      // androidx.webkit is compileOnly, so the version in the app's apk may be older than the one
      // we compiled against and lack the APIs used here.
      logger.log(
          SentryLevel.WARNING,
          "androidx.webkit is too old for WebView trace propagation, skipping.",
          e);
      return false;
    }
  }

  private static boolean replaceScript(
      final @NotNull WebView webView,
      final @NotNull List<String> allowedHosts,
      final @NotNull IScopes scopes,
      final @NotNull WebViewScriptInstaller installer,
      final @NotNull ILogger logger) {
    // Remove Sentry's previous script first, so a failed re-add never leaves a stale trace behind.
    installer.removeInstalledScript(webView);

    final @NotNull List<String> hosts = WebViewTracePropagationScript.normalizeHosts(allowedHosts);
    if (hosts.isEmpty()) {
      logger.log(
          SentryLevel.WARNING,
          "No valid allowedHosts provided, skipping WebView trace propagation.");
      return false;
    }

    if (!installer.isDocumentStartScriptSupported()) {
      logger.log(
          SentryLevel.WARNING,
          "The WebView does not support document-start scripts, skipping WebView trace propagation.");
      return false;
    }

    final @Nullable TracingUtils.TracingHeaders headers =
        TracingUtils.trace(scopes, null, scopes.getSpan());
    if (headers == null) {
      logger.log(
          SentryLevel.DEBUG,
          "No trace available (the SDK is disabled or not initialized), skipping WebView trace propagation.");
      return false;
    }

    final @Nullable BaggageHeader baggageHeader = headers.getBaggageHeader();
    final @Nullable String baggage =
        WebViewTracePropagationScript.stripTransactionFromBaggage(
            baggageHeader == null ? null : baggageHeader.getValue());
    final @NotNull String script =
        WebViewTracePropagationScript.build(
            hosts, headers.getSentryTraceHeader().getValue(), baggage);

    try {
      if (!installer.installScript(
          webView, script, WebViewTracePropagationScript.originRules(allowedHosts))) {
        logger.log(
            SentryLevel.WARNING,
            "The WebView does not support document-start scripts, skipping WebView trace propagation.");
        return false;
      }
    } catch (IllegalArgumentException e) {
      logger.log(
          SentryLevel.WARNING, "Invalid allowedHosts, skipping WebView trace propagation.", e);
      return false;
    }

    return true;
  }

  private static boolean isOnWebViewThread(final @NotNull WebView webView) {
    final @NotNull Looper webViewLooper =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? webView.getWebViewLooper()
            : Looper.getMainLooper();
    return Looper.myLooper() == webViewLooper;
  }
}
