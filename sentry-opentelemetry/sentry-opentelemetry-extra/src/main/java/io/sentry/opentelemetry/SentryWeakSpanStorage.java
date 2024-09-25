package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.internal.shaded.WeakConcurrentMap;
import io.sentry.ISentryLifecycleToken;
import io.sentry.util.AutoClosableReentrantLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Weakly references wrappers for OpenTelemetry spans meaning they'll be cleaned up when the
 * OpenTelemetry span is garbage collected.
 */
@ApiStatus.Internal
public final class SentryWeakSpanStorage {
  private static volatile @Nullable SentryWeakSpanStorage INSTANCE;
  private static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

  public static @NotNull SentryWeakSpanStorage getInstance() {
    if (INSTANCE == null) {
      try (final @NotNull ISentryLifecycleToken ignored = staticLock.acquire()) {
        if (INSTANCE == null) {
          INSTANCE = new SentryWeakSpanStorage();
        }
      }
    }

    return INSTANCE;
  }

  // weak keys, spawns a thread to clean up values that have been garbage collected
  private final @NotNull WeakConcurrentMap<SpanContext, OtelSpanWrapper> sentrySpans =
      new WeakConcurrentMap<>(true);

  private SentryWeakSpanStorage() {}

  public @Nullable OtelSpanWrapper getSentrySpan(final @NotNull SpanContext spanContext) {
    return sentrySpans.get(spanContext);
  }

  public void storeSentrySpan(
      final @NotNull SpanContext otelSpan, final @NotNull OtelSpanWrapper sentrySpan) {
    this.sentrySpans.put(otelSpan, sentrySpan);
  }
}
