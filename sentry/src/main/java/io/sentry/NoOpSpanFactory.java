package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpSpanFactory implements ISpanFactory {

  private static final NoOpSpanFactory instance = new NoOpSpanFactory();

  private NoOpSpanFactory() {}

  public static NoOpSpanFactory getInstance() {
    return instance;
  }

  @Override
  public @NotNull ITransaction createTransaction(
      @NotNull TransactionContext context,
      @NotNull IScopes scopes,
      @NotNull TransactionOptions transactionOptions,
      @Nullable TransactionPerformanceCollector transactionPerformanceCollector) {
    return NoOpTransaction.getInstance();
  }

  @Override
  public @NotNull ISpan createSpan(
      @NotNull IScopes scopes,
      @NotNull SpanOptions spanOptions,
      @NotNull SpanContext spanContext,
      @Nullable ISpan parentSpan) {
    return NoOpSpan.getInstance();
  }
}
