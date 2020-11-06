package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransactionContexts extends Contexts {
  private static final long serialVersionUID = 252445813254943011L;

  /** If trace is sampled. */
  private transient @Nullable Boolean sampled;

  public TransactionContexts() {
    this(null, new TraceContext());
  }

  public TransactionContexts(final @Nullable Boolean sampled) {
    this(sampled, new TraceContext());
  }

  private TransactionContexts(
      final @Nullable Boolean sampled, final @NotNull TraceContext traceContext) {
    this.sampled = sampled;
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
        sentryTrace.isSampled(),
        new TraceContext(sentryTrace.getTraceId(), new SpanId(), sentryTrace.getSpanId()));
  }

  public @NotNull TraceContext getTraceContext() {
    return toContextType(TraceContext.TYPE, TraceContext.class);
  }

  public void setTraceContext(final @NotNull TraceContext traceContext) {
    Objects.requireNonNull(traceContext, "traceContext is required");
    this.put(TraceContext.TYPE, traceContext);
  }

  void setSampled(final @Nullable Boolean sampled) {
    this.sampled = sampled;
  }

  public @Nullable Boolean getSampled() {
    return sampled;
  }
}
