package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransactionContext extends SpanContext {
  private final @NotNull String name;

  public TransactionContext(final @NotNull String name) {
    this.name = Objects.requireNonNull(name, "name is required");
  }

  public TransactionContext(final @NotNull String name, final Boolean sampled) {
    super(sampled);
    this.name = Objects.requireNonNull(name, "name is required");
  }

  public TransactionContext(final @NotNull SentryId traceId, final @NotNull SpanId spanId, final @Nullable SpanId parentSpanId, final @Nullable Boolean sampled, final @NotNull String name) {
    super(traceId, spanId, parentSpanId, sampled);
    this.name = Objects.requireNonNull(name, "name is required");
  }

  public @NotNull String getName() {
    return name;
  }
}
