package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ISpanFactory {
  @NotNull
  ITransaction createTransaction(
      @NotNull TransactionContext context,
      @NotNull IScopes scopes,
      @NotNull TransactionOptions transactionOptions,
      @Nullable TransactionPerformanceCollector transactionPerformanceCollector);

  @NotNull
  ISpan createSpan(
      @NotNull IScopes scopes,
      @NotNull SpanOptions spanOptions,
      @NotNull SpanContext spanContext,
      @Nullable ISpan parentSpan);
}
