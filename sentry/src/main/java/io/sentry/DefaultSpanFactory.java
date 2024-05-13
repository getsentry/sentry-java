package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DefaultSpanFactory implements ISpanFactory {
  @Override
  public @NotNull ITransaction createTransaction(
      @NotNull TransactionContext context,
      @NotNull IScopes scopes,
      @NotNull TransactionOptions transactionOptions,
      @Nullable TransactionPerformanceCollector transactionPerformanceCollector) {
    return new SentryTracer(context, scopes, transactionOptions, transactionPerformanceCollector);
  }

  @Override
  public @NotNull ISpan createSpan(
      @NotNull String name,
      @NotNull IScopes scopes,
      @NotNull SpanOptions spanOptions,
      @Nullable ISpan parentSpan) {
    // TODO [POTEL] forward to SentryTracer.createChild?
    return NoOpSpan.getInstance();
  }

  @Override
  public @Nullable ISpan retrieveCurrentSpan(IScopes scopes) {
    return scopes.getSpan();
  }
}
