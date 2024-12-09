package io.sentry;

import io.sentry.util.AutoClosableReentrantLock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Has been moved to `sentry` gradle module to include it in the bootstrap classloader without
 * having to introduce yet another module for OpenTelemetry support.
 *
 * @deprecated please use SentryWeakSpanStorage (from sentry-opentelemetry-bootstrap) instead.
 */
@Deprecated
@ApiStatus.Internal
public final class SentrySpanStorage {
  private static volatile @Nullable SentrySpanStorage INSTANCE;
  private static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

  public static @NotNull SentrySpanStorage getInstance() {
    if (INSTANCE == null) {
      try (final @NotNull ISentryLifecycleToken ignored = staticLock.acquire()) {
        if (INSTANCE == null) {
          INSTANCE = new SentrySpanStorage();
        }
      }
    }

    return INSTANCE;
  }

  private final @NotNull Map<String, ISpan> spans = new ConcurrentHashMap<>();

  private SentrySpanStorage() {}

  public void store(final @NotNull String spanId, final @NotNull ISpan span) {
    spans.put(spanId, span);
  }

  public @Nullable ISpan get(final @Nullable String spanId) {
    return spans.get(spanId);
  }

  public @Nullable ISpan removeAndGet(final @Nullable String spanId) {
    return spans.remove(spanId);
  }
}
