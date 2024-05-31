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
    // TODO [POTEL] forward to SentryTracer.createChild?
    return NoOpSpan.getInstance();
  }

  @Override
  public @Nullable ISpan retrieveCurrentSpan(final IScopes scopes) {
    return scopes.getSpan();
  }

  @Override
  public @Nullable ISpan retrieveCurrentSpan(final IScope scope) {
    return scope.getSpan();
  }
}
