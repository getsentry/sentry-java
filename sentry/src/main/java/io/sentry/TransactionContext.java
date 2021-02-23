package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransactionContext extends SpanContext {
  private @NotNull String name;
  private @Nullable Boolean parentSampled;

  /**
   * Creates {@link TransactionContext} from sentry-trace header.
   *
   * @param name - the transaction name
   * @param operation - the operation
   * @param sentryTrace - the sentry-trace header
   * @return the transaction contexts
   */
  public static @NotNull TransactionContext fromSentryTrace(
      final @NotNull String name,
      final @NotNull String operation,
      final @NotNull SentryTraceHeader sentryTrace) {
    return new TransactionContext(
        name,
        operation,
        sentryTrace.getTraceId(),
        new SpanId(),
        sentryTrace.getSpanId(),
        sentryTrace.isSampled());
  }

  public TransactionContext(final @NotNull String name, final @NotNull String operation) {
    super(operation);
    this.name = Objects.requireNonNull(name, "name is required");
    this.parentSampled = null;
  }

  /**
   * Creates {@link TransactionContext} with explicit sampling decision.
   *
   * @param name - transaction name
   * @param operation - operation
   * @param sampled - sampling decision
   */
  public TransactionContext(
      final @NotNull String name,
      final @NotNull String operation,
      final @Nullable Boolean sampled) {
    super(operation);
    this.name = Objects.requireNonNull(name, "name is required");
    this.setSampled(sampled);
  }

  private TransactionContext(
      final @NotNull String name,
      final @NotNull String operation,
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable SpanId parentSpanId,
      final @Nullable Boolean parentSampled) {
    super(traceId, spanId, operation, parentSpanId, null);
    this.name = Objects.requireNonNull(name, "name is required");
    this.parentSampled = parentSampled;
  }

  public @NotNull String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public @Nullable Boolean getParentSampled() {
    return parentSampled;
  }

  public void setParentSampled(final @Nullable Boolean parentSampled) {
    this.parentSampled = parentSampled;
  }
}
