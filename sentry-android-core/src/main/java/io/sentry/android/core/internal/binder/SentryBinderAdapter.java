package io.sentry.android.core.internal.binder;

import android.os.Build;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SentryAttributes;
import io.sentry.SentryLogLevel;
import io.sentry.SpanDataConvention;
import io.sentry.logger.SentryLogParameters;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"unused", "deprecation"})
@ApiStatus.Internal
public final class SentryBinderAdapter {

  private static volatile boolean tracingEnabled = false;
  private static volatile boolean loggingEnabled = false;

  /** Configures which binder features are active. Expected to be called once during SDK init. */
  public static void setEnabled(final boolean tracingEnabled, final boolean loggingEnabled) {
    SentryBinderAdapter.tracingEnabled = tracingEnabled;
    SentryBinderAdapter.loggingEnabled = loggingEnabled;
  }

  /**
   * This method is used by the Sentry Android Gradle plugin for binder instrumentation. Called
   * right before a binder call starts. Returns an opaque token that must be passed back to {@link
   * #onCallEnd(Object)} once the call completes, or {@code null} if nothing was recorded.
   *
   * @param component the component being called, e.g. "ActivityManager"
   * @param name the method being called, e.g. "startActivity"
   * @return an opaque token which must be later passed to {@link #onCallEnd(Object)},
   */
  public static @Nullable Object onCallStart(
      final @NotNull String component, final @NotNull String name) {
    if (!tracingEnabled && !loggingEnabled) {
      return null;
    }

    try {
      final @NotNull Thread currentThread = Thread.currentThread();
      final long threadId;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        threadId = currentThread.threadId();
      } else {
        threadId = currentThread.getId();
      }
      final @Nullable String threadName = currentThread.getName();

      if (loggingEnabled) {
        recordLog(component, name, threadId, threadName);
      }
      if (tracingEnabled) {
        return recordSpan(component, name, threadId, threadName);
      }
    } catch (Throwable t) {
      // ignored, as instrumentation should never crash
    }
    return null;
  }

  /**
   * This method is used by the Sentry Android Gradle plugin for binder instrumentation. Called
   * right after a binder call ends.
   *
   * @param token the token returned by {@link #onCallStart(String, String)}
   */
  public static void onCallEnd(final @Nullable Object token) {
    if (token == null) {
      return;
    }
    try {
      if (token instanceof ISpan) {
        ((ISpan) token).finish();
      }
    } catch (Throwable t) {
      // ignored
    }
  }

  private static @Nullable ISpan recordSpan(
      final @NotNull String component,
      final @NotNull String name,
      final long threadId,
      final @Nullable String threadName) {

    final @Nullable ISpan parent = Sentry.getCurrentScopes().getTransaction();
    if (parent == null) {
      return null;
    }
    final @NotNull ISpan span = parent.startChild("binder", component + "." + name);
    span.setData(SpanDataConvention.THREAD_ID, String.valueOf(threadId));
    span.setData(SpanDataConvention.THREAD_NAME, threadName);
    return span;
  }

  private static void recordLog(
      final @NotNull String component,
      final @NotNull String name,
      final long threadId,
      final @Nullable String threadName) {
    final @NotNull Map<String, Object> logAttributes = new HashMap<>();
    logAttributes.put(SpanDataConvention.THREAD_ID, threadId);
    logAttributes.put(SpanDataConvention.THREAD_NAME, threadName);

    Sentry.logger()
        .log(
            SentryLogLevel.INFO,
            SentryLogParameters.create(SentryAttributes.fromMap(logAttributes)),
            "binder call: %s.%s",
            component,
            name);
  }
}
