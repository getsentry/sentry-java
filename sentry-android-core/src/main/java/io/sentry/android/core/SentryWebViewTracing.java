package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import androidx.annotation.MainThread;
import io.sentry.BaggageHeader;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.ExceptionUtils;
import io.sentry.util.LoadClass;
import io.sentry.util.TracingUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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

  /**
   * Counts, per WebView, the calls that finished replacing Sentry's script on the WebView's thread.
   * Weak keys, so it doesn't keep WebViews alive.
   */
  private static final @NotNull Map<WebView, Long> replacements =
      Collections.synchronizedMap(new WeakHashMap<>());

  /**
   * The hosts the app passed explicitly, per WebView, so an automatic refresh keeps them instead of
   * widening the WebView to the hosts in the options. Weak keys, so it doesn't keep WebViews alive.
   */
  private static final @NotNull Map<WebView, List<String>> explicitHosts =
      Collections.synchronizedMap(new WeakHashMap<>());

  /** Whether the automatic refresh already warned that androidx.webkit is missing. */
  private static final @NotNull AtomicBoolean missingWebkitWarned = new AtomicBoolean(false);

  private SentryWebViewTracing() {}

  /**
   * Same as {@link #addTracePropagation(WebView, List)}, using the hosts set in {@link
   * SentryWebViewTracingOptions#setAllowedHosts(List)}. If none are set, no page is connected and
   * this returns false.
   *
   * @param webView the WebView to connect
   * @return true if the trace propagation script was installed
   */
  @MainThread
  public static boolean addTracePropagation(final @NotNull WebView webView) {
    explicitHosts.remove(webView);
    final @NotNull IScopes scopes = Sentry.getCurrentScopes();
    return addTracePropagation(webView, optionsHosts(scopes.getOptions()), scopes, new LoadClass());
  }

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
   * <p>With the Sentry Android Gradle Plugin's WebView instrumentation, which refreshes the trace
   * on every {@code loadUrl} call, the refresh keeps these hosts for this WebView instead of using
   * the ones in {@link SentryWebViewTracingOptions}.
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
    explicitHosts.put(webView, new ArrayList<>(allowedHosts));
    return addTracePropagation(webView, allowedHosts, Sentry.getCurrentScopes(), new LoadClass());
  }

  /**
   * Refreshes the trace propagated to the WebView's next page load, for {@link
   * SentryWebViewTracingAdapter}. Uses the hosts the app last passed explicitly for this WebView,
   * or else the hosts in the options. Does nothing, and leaves any installed script alone, when
   * there are no hosts or it is called off the WebView's thread.
   *
   * @return true if the trace propagation script was installed
   */
  static boolean refreshTracePropagation(final @NotNull WebView webView) {
    return refreshTracePropagation(webView, new LoadClass());
  }

  static boolean refreshTracePropagation(
      final @NotNull WebView webView, final @NotNull LoadClass loadClass) {
    if (!isOnWebViewThread(webView)) {
      return false;
    }
    final @NotNull IScopes scopes = Sentry.getCurrentScopes();
    final @Nullable List<String> explicit = explicitHosts.get(webView);
    final @NotNull List<String> hosts =
        explicit != null ? explicit : optionsHosts(scopes.getOptions());
    if (hosts.isEmpty()) {
      return false;
    }
    return addTracePropagation(webView, hosts, scopes, loadClass, true);
  }

  private static @NotNull List<String> optionsHosts(final @NotNull SentryOptions options) {
    return options instanceof SentryAndroidOptions
        ? ((SentryAndroidOptions) options).getWebViewTracing().getAllowedHosts()
        : Collections.<String>emptyList();
  }

  static boolean addTracePropagation(
      final @NotNull WebView webView,
      final @NotNull List<String> allowedHosts,
      final @NotNull IScopes scopes,
      final @NotNull LoadClass loadClass) {
    return addTracePropagation(webView, allowedHosts, scopes, loadClass, false);
  }

  /**
   * @param automatic whether the call comes from the automatic refresh, which runs on every {@code
   *     loadUrl}: it doesn't record manual adoption, as the Gradle plugin reports its own feature,
   *     and warns about a missing androidx.webkit only once
   */
  private static boolean addTracePropagation(
      final @NotNull WebView webView,
      final @NotNull List<String> allowedHosts,
      final @NotNull IScopes scopes,
      final @NotNull LoadClass loadClass,
      final boolean automatic) {
    if (!automatic) {
      // Registered on use rather than on success, like the other SDKs, so adoption counts apps that
      // wired the feature up even when this call cannot install.
      addIntegrationToSdkVersion(INTEGRATION_NAME);
    }

    if (!loadClass.isClassAvailable(SCRIPT_HANDLER_CLASS_NAME, scopes.getOptions())) {
      if (!automatic || missingWebkitWarned.compareAndSet(false, true)) {
        scopes
            .getOptions()
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "WebView trace propagation requires the androidx.webkit dependency, skipping.");
      }
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
      removeOnWebViewThread(webView, installer, logger);
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
    } finally {
      // Counted once the replacement is done, so an off-thread call made while it runs doesn't
      // later remove the script it installed. Only written on the WebView's thread.
      replacements.put(webView, replacementsOf(webView) + 1);
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

  /**
   * A failed call must not leave a stale trace behind, but off the WebView's thread the script can
   * only be removed later, on that thread. The removal is skipped if a call on the WebView's thread
   * replaced this WebView's script in the meantime, so it never removes a newer script.
   */
  private static void removeOnWebViewThread(
      final @NotNull WebView webView,
      final @NotNull WebViewScriptInstaller installer,
      final @NotNull ILogger logger) {
    final long replacementsSeen = replacementsOf(webView);
    new Handler(webViewLooper(webView))
        .post(
            () -> {
              if (replacementsOf(webView) != replacementsSeen) {
                return;
              }
              // Runs outside of any app call, so only fatal errors may escape: for example, the
              // WebView may have been destroyed since.
              try {
                installer.removeInstalledScript(webView);
              } catch (LinkageError e) {
                logger.log(
                    SentryLevel.WARNING,
                    "androidx.webkit is too old for WebView trace propagation, skipping.",
                    e);
              } catch (Throwable e) {
                ExceptionUtils.rethrowIfFatal(e);
                logger.log(
                    SentryLevel.WARNING,
                    "Failed to remove the WebView trace propagation script.",
                    e);
              }
            });
  }

  private static long replacementsOf(final @NotNull WebView webView) {
    final @Nullable Long count = replacements.get(webView);
    return count == null ? 0 : count;
  }

  @TestOnly
  static void resetMissingWebkitWarning() {
    missingWebkitWarned.set(false);
  }

  private static boolean isOnWebViewThread(final @NotNull WebView webView) {
    return Looper.myLooper() == webViewLooper(webView);
  }

  private static @NotNull Looper webViewLooper(final @NotNull WebView webView) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ? webView.getWebViewLooper()
        : Looper.getMainLooper();
  }
}
