package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransactionContext extends SpanContext {
  private final @NotNull String name;
  private @Nullable TracesSamplingDecision parentSamplingDecision;

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
    @Nullable Boolean parentSampled = sentryTrace.isSampled();
    return new TransactionContext(
        name,
        operation,
        sentryTrace.getTraceId(),
        new SpanId(),
        sentryTrace.getSpanId(),
        parentSampled == null
            ? null
            : new TracesSamplingDecision(
                parentSampled)); // TODO sampleRate should be retrieved from baggage and passed here
    // in the future
  }

  public TransactionContext(final @NotNull String name, final @NotNull String operation) {
    super(operation);
    this.name = Objects.requireNonNull(name, "name is required");
    this.parentSamplingDecision = null;
  }

  /**
   * Creates {@link TransactionContext} with explicit sampling decision.
   *
   * @param name - transaction name
   * @param operation - operation
   * @param samplingDecision - sampling decision
   */
  public TransactionContext(
      final @NotNull String name,
      final @NotNull String operation,
      final @Nullable TracesSamplingDecision samplingDecision) {
    super(operation);
    this.name = Objects.requireNonNull(name, "name is required");
    this.setSamplingDecision(samplingDecision);
  }

  private TransactionContext(
      final @NotNull String name,
      final @NotNull String operation,
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable SpanId parentSpanId,
      final @Nullable TracesSamplingDecision parentSamplingDecision) {
    super(traceId, spanId, operation, parentSpanId, null);
    this.name = Objects.requireNonNull(name, "name is required");
    this.parentSamplingDecision = parentSamplingDecision;
  }

  public @NotNull String getName() {
    return name;
  }

  public @Nullable Boolean getParentSampled() {
    if (parentSamplingDecision == null) {
      return null;
    }

    return parentSamplingDecision.getSampled();
  }

  public @Nullable TracesSamplingDecision getParentSamplingDecision() {
    return parentSamplingDecision;
  }

  public void setParentSampled(final @Nullable Boolean parentSampled) {
    if (parentSampled == null) {
      this.parentSamplingDecision = null;
    } else {
      this.parentSamplingDecision = new TracesSamplingDecision(parentSampled);
    }
  }
}
