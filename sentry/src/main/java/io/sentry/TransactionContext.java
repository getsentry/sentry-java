package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransactionContext extends SpanContext {
  private final @NotNull String name;
  private @Nullable Boolean parentSampled;

  /**
   * Creates {@link TransactionContext} from sentry-trace header.
   *
   * @param name - the transaction name
   * @param sentryTrace - the sentry-trace header
   * @return the transaction contexts
   */
  public static @NotNull TransactionContext fromSentryTrace(
      final @NotNull String name, final @NotNull SentryTraceHeader sentryTrace) {
    return new TransactionContext(
        name,
        sentryTrace.getTraceId(),
        new SpanId(),
        sentryTrace.getSpanId(),
        sentryTrace.isSampled());
  }

  public TransactionContext(final @NotNull String name) {
    this.name = Objects.requireNonNull(name, "name is required");
    this.parentSampled = null;
  }

  public TransactionContext(
      final @NotNull String name,
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable SpanId parentSpanId,
      final @Nullable Boolean parentSampled) {
    super(traceId, spanId, parentSpanId, null);
    this.name = Objects.requireNonNull(name, "name is required");
    this.parentSampled = parentSampled;
  }

  public @NotNull String getName() {
    return name;
  }

  public @Nullable Boolean getParentSampled() {
    return parentSampled;
  }

  public void setParentSampled(@Nullable Boolean parentSampled) {
    this.parentSampled = parentSampled;
  }
}
