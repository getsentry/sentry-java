package io.sentry.android.core;

import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SentryAttribute;
import io.sentry.SentryAttributes;
import io.sentry.SentryLogLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanDataConvention;
import io.sentry.logger.SentryLogParameters;
import io.sentry.util.thread.IThreadChecker;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Entry point invoked by bytecode instrumented by the Sentry Android Gradle plugin around binder
 * IPC call sites. The instrumentation emits calls to {@link #onCallStart(String, String)} and
 * {@link #onCallEnd(int)} wrapped in a try/finally, so both methods MUST NOT throw and MUST stay
 * cheap when the feature is disabled.
 */
@ApiStatus.Internal
public final class SentryIpcTracer {

  private static final String OP_BINDER = "binder.ipc";
  private static final int DISABLED = -1;

  private static final AtomicInteger COUNTER = new AtomicInteger();
  private static final ConcurrentHashMap<Integer, ISpan> IN_FLIGHT = new ConcurrentHashMap<>();

  private SentryIpcTracer() {}

  public static int onCallStart(final @NotNull String component, final @NotNull String method) {
    try {
      final @NotNull IScopes scopes = Sentry.getCurrentScopes();
      final @NotNull SentryOptions options = scopes.getOptions();
      if (!(options instanceof SentryAndroidOptions)) {
        return DISABLED;
      }
      final SentryAndroidOptions androidOptions = (SentryAndroidOptions) options;
      final boolean tracingEnabled = androidOptions.isEnableBinderTracing();
      final boolean logsEnabled = androidOptions.isEnableBinderLogs();
      if (!tracingEnabled && !logsEnabled) {
        return DISABLED;
      }

      final @NotNull IThreadChecker threadChecker = options.getThreadChecker();
      final @NotNull String threadId = String.valueOf(threadChecker.currentThreadSystemId());
      final @NotNull String threadName = threadChecker.getCurrentThreadName();

      if (logsEnabled) {
        final @NotNull SentryAttributes attributes =
            SentryAttributes.of(
                SentryAttribute.stringAttribute(SpanDataConvention.THREAD_ID, threadId),
                SentryAttribute.stringAttribute(SpanDataConvention.THREAD_NAME, threadName));
        scopes
            .logger()
            .log(
                SentryLogLevel.INFO,
                SentryLogParameters.create(attributes),
                "Binder IPC %s.%s",
                component,
                method);
      }

      if (tracingEnabled) {
        final @Nullable ISpan parent = scopes.getSpan();
        if (parent == null) {
          return DISABLED;
        }
        final ISpan child = parent.startChild(OP_BINDER, component + "." + method);
        child.setData(SpanDataConvention.THREAD_ID, threadId);
        child.setData(SpanDataConvention.THREAD_NAME, threadName);
        // keep cookies non-negative so they never collide with the DISABLED sentinel
        // even after AtomicInteger overflow past Integer.MAX_VALUE
        final int cookie = COUNTER.incrementAndGet() & Integer.MAX_VALUE;
        IN_FLIGHT.put(cookie, child);
        return cookie;
      }
    } catch (Throwable ignored) {
      // never throw from an instrumented call site
    }
    return DISABLED;
  }

  public static void onCallEnd(final int cookie) {
    if (cookie == DISABLED) {
      return;
    }
    try {
      final @Nullable ISpan span = IN_FLIGHT.remove(cookie);
      if (span != null) {
        span.finish();
      }
    } catch (Throwable ignored) {
      // never throw from an instrumented call site
    }
  }

  @TestOnly
  static void resetForTest() {
    IN_FLIGHT.clear();
    COUNTER.set(0);
  }

  @TestOnly
  static int inFlightCount() {
    return IN_FLIGHT.size();
  }
}
