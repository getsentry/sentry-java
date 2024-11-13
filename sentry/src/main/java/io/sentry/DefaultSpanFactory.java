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
      final @Nullable CompositePerformanceCollector compositePerformanceCollector) {
    return new SentryTracer(context, scopes, transactionOptions, compositePerformanceCollector);
  }

  @Override
  public @NotNull ISpan createSpan(
      final @NotNull IScopes scopes,
      final @NotNull SpanOptions spanOptions,
      final @NotNull SpanContext spanContext,
      @Nullable ISpan parentSpan) {
    if (parentSpan == null) {
      return NoOpSpan.getInstance();
    }
    return parentSpan.startChild(spanContext, spanOptions);
  }
}
