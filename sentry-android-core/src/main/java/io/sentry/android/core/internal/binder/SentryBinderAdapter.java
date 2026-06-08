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
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"unused", "deprecation"})
@ApiStatus.Internal
public final class SentryBinderAdapter {

  private static final AtomicInteger cookieCounter = new AtomicInteger();
  private static final int NO_COOKIE = -1;

  private static volatile boolean tracingEnabled = false;
  private static volatile boolean loggingEnabled = false;

  /** Configures which binder features are active. Expected to be called once during SDK init. */
  public static void setEnabled(final boolean tracingEnabled, final boolean loggingEnabled) {
    SentryBinderAdapter.tracingEnabled = tracingEnabled;
    SentryBinderAdapter.loggingEnabled = loggingEnabled;
  }

  private static final ThreadLocal<Map<Integer, ISpan>> spanMap =
      new ThreadLocal<Map<Integer, ISpan>>() {
        @Override
        protected Map<Integer, ISpan> initialValue() {
          return new HashMap<>();
        }
      };

  public static int onCallStart(final @NotNull String component, final @NotNull String name) {
    if (!tracingEnabled && !loggingEnabled) {
      return NO_COOKIE;
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
        final int cookie = cookieCounter.incrementAndGet();
        recordSpan(component, name, threadId, threadName, cookie);
        return cookie;
      }
    } catch (Throwable t) {
      // ignored, as instrumentation should never crash
    }
    return NO_COOKIE;
  }

  public static void onCallEnd(final int cookie) {
    if (cookie == NO_COOKIE) {
      return;
    }
    try {
      final @Nullable Map<Integer, ISpan> map = spanMap.get();
      if (map == null) {
        return;
      }
      final @Nullable ISpan span = map.remove(cookie);
      if (span != null) {
        span.finish();
      }
    } catch (Throwable t) {
      // ignored
    }
  }

  private static void recordSpan(
      final @NotNull String component,
      final @NotNull String name,
      final long threadId,
      final @Nullable String threadName,
      final int cookie) {

    final @Nullable ISpan parent = Sentry.getCurrentScopes().getTransaction();
    if (parent == null) {
      return;
    }
    final @Nullable Map<Integer, ISpan> map = spanMap.get();
    if (map == null) {
      return;
    }
    final @NotNull ISpan span = parent.startChild("binder", component + "." + name);
    span.setData(SpanDataConvention.THREAD_ID, String.valueOf(threadId));
    span.setData(SpanDataConvention.THREAD_NAME, threadName);
    map.put(cookie, span);
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
