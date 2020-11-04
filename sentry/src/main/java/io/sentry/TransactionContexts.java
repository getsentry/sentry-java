package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class TransactionContexts extends Contexts {
  private static final long serialVersionUID = 252445813254943011L;

  public TransactionContexts() {
    this(new TraceContext());
  }

  public TransactionContexts(final boolean sampled) {
    this(new TraceContext(sampled));
  }

  private TransactionContexts(final @NotNull TraceContext traceContext) {
    this.setTraceContext(traceContext);
  }

  /**
   * Creates {@link TransactionContexts} from sentry-trace header.
   *
   * @param sentryTrace - the sentry-trace header
   * @return the transaction contexts
   */
  public static @NotNull TransactionContexts fromSentryTrace(
      final @NotNull SentryTraceHeader sentryTrace) {
    return new TransactionContexts(
        new TraceContext(
            sentryTrace.getTraceId(),
            new SpanId(),
            sentryTrace.getSpanId(),
            sentryTrace.isSampled()));
  }

  public @NotNull TraceContext getTraceContext() {
    return toContextType(TraceContext.TYPE, TraceContext.class);
  }

  public void setTraceContext(final @NotNull TraceContext traceContext) {
    Objects.requireNonNull(traceContext, "traceContext is required");
    this.put(TraceContext.TYPE, traceContext);
  }
}
