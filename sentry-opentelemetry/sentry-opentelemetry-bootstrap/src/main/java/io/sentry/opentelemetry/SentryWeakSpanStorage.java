package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.internal.shaded.WeakConcurrentMap;
import io.sentry.IScopes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class may have to be moved to a new gradle module to include it in the bootstrap
 * classloader.
 *
 * <p>This uses multiple maps instead of a single one with a wrapper object as porting this to
 * Android would mean there's no access to methods like compute etc. before API level 24. There's
 * also no easy way to pre-initialize the map for all keys as spans are used as keys. For span IDs
 * it would also not work as they are random. For client report storage we know beforehand what keys
 * can exist.
 */
@ApiStatus.Internal
public final class SentryWeakSpanStorage {
  private static volatile @Nullable SentryWeakSpanStorage INSTANCE;

  public static @NotNull SentryWeakSpanStorage getInstance() {
    if (INSTANCE == null) {
      synchronized (SentryWeakSpanStorage.class) {
        if (INSTANCE == null) {
          INSTANCE = new SentryWeakSpanStorage();
        }
      }
    }

    return INSTANCE;
  }

  // weak keys, spawns a thread to clean up values that have been garbage collected
  private final @NotNull WeakConcurrentMap<SpanContext, IScopes> scopes =
      new WeakConcurrentMap<>(true);

  private SentryWeakSpanStorage() {}

  public @Nullable IScopes getScopes(final @NotNull SpanContext spanContext) {
    return scopes.get(spanContext);
  }

  public void storeScopes(final @NotNull SpanContext otelSpan, final @NotNull IScopes scopes) {
    this.scopes.put(otelSpan, scopes);
  }
}
