package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransactionContext extends SpanContext {
  private final @NotNull String name;
  private final @NotNull TransactionNameSource transactionNameSource;
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
    return fromSentryTrace(name, TransactionNameSource.CUSTOM, operation, sentryTrace);
  }

  /**
   * Creates {@link TransactionContext} from sentry-trace header.
   *
   * @param name - the transaction name
   * @param transactionNameSource - source of the transaction name
   * @param operation - the operation
   * @param sentryTrace - the sentry-trace header
   * @return the transaction contexts
   */
  @ApiStatus.Internal
  public static @NotNull TransactionContext fromSentryTrace(
      final @NotNull String name,
      final @NotNull TransactionNameSource transactionNameSource,
      final @NotNull String operation,
      final @NotNull SentryTraceHeader sentryTrace) {
    @Nullable Boolean parentSampled = sentryTrace.isSampled();
    return new TransactionContext(
        name,
        operation,
        sentryTrace.getTraceId(),
        new SpanId(),
        transactionNameSource,
        sentryTrace.getSpanId(),
        parentSampled == null
            ? null
            : new TracesSamplingDecision(
                parentSampled)); // TODO sampleRate should be retrieved from baggage and passed here
    // in the future
  }

  public TransactionContext(final @NotNull String name, final @NotNull String operation) {
    this(name, operation, null);
  }

  @ApiStatus.Internal
  public TransactionContext(
      final @NotNull String name,
      final @NotNull TransactionNameSource transactionNameSource,
      final @NotNull String operation) {
    this(name, transactionNameSource, operation, null);
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
    this(name, TransactionNameSource.CUSTOM, operation, samplingDecision);
  }

  /**
   * Creates {@link TransactionContext} with explicit sampling decision and name source.
   *
   * @param name - transaction name
   * @param operation - operation
   * @param samplingDecision - sampling decision
   */
  @ApiStatus.Internal
  public TransactionContext(
      final @NotNull String name,
      final @NotNull TransactionNameSource transactionNameSource,
      final @NotNull String operation,
      final @Nullable TracesSamplingDecision samplingDecision) {
    super(operation);
    this.name = Objects.requireNonNull(name, "name is required");
    this.transactionNameSource = transactionNameSource;
    this.setSamplingDecision(samplingDecision);
  }

  private TransactionContext(
      final @NotNull String name,
      final @NotNull String operation,
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @NotNull TransactionNameSource transactionNameSource,
      final @Nullable SpanId parentSpanId,
      final @Nullable TracesSamplingDecision parentSamplingDecision) {
    super(traceId, spanId, operation, parentSpanId, null);
    this.name = Objects.requireNonNull(name, "name is required");
    this.parentSamplingDecision = parentSamplingDecision;
    this.transactionNameSource = transactionNameSource;
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

  public @NotNull TransactionNameSource getTransactionNameSource() {
    return transactionNameSource;
  }
}
