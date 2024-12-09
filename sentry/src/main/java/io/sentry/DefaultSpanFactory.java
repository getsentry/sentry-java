package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DefaultSpanFactory implements ISpanFactory {
  @Override
  public @NotNull ITransaction createTransaction(
      final @NotNull TransactionContext context,
      final @NotNull IScopes scopes,
      final @NotNull TransactionOptions transactionOptions,
      final @Nullable TransactionPerformanceCollector transactionPerformanceCollector) {
    return new SentryTracer(context, scopes, transactionOptions, transactionPerformanceCollector);
  }

  @Override
  public @NotNull ISpan createSpan(
      final @NotNull IScopes scopes,
      final @NotNull SpanOptions spanOptions,
      final @NotNull SpanContext spanContext,
      @Nullable ISpan parentSpan) {
    /**
     * Be careful here when executing something like parentSpan.startChild() as that might cause a
     * loop and a stack overflow. This can happen, e.g. when OpenTelemetry is creating spans that
     * use OtelSpanWrapper which calls this createSpan method that then in turn calls startChild
     * again causing the loop.
     */
    return NoOpSpan.getInstance();
  }
}
