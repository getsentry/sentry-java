package io.sentry.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ISpanFactory;
import io.sentry.ITransaction;
import io.sentry.SpanOptions;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.TransactionPerformanceCollector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelSpanFactory implements ISpanFactory {

  private final @NotNull SentryWeakSpanStorage storage = SentryWeakSpanStorage.getInstance();

  @Override
  public @NotNull ITransaction createTransaction(
      @NotNull TransactionContext context,
      @NotNull IScopes scopes,
      @NotNull TransactionOptions transactionOptions,
      @Nullable TransactionPerformanceCollector transactionPerformanceCollector) {
    final @NotNull ISpan span = createSpan(context.getName(), scopes, transactionOptions, null);
    return new OtelTransactionSpanForwarder(span);
  }

  @Override
  public @NotNull ISpan createSpan(
      final @NotNull String name,
      final @NotNull IScopes scopes,
      final @NotNull SpanOptions spanOptions,
      final @Nullable ISpan parentSpan) {
    final @NotNull SpanBuilder spanBuilder = getTracer().spanBuilder(name);
    if (parentSpan == null) {
      spanBuilder.setNoParent();
    } else {
      if (parentSpan instanceof OtelSpanWrapper) {
        // TODO [POTEL] retrieve context from span
        //      spanBuilder.setParent()
      }
    }
    // TODO [POTEL] start timestamp
    final @NotNull Span span = spanBuilder.startSpan();
    return new OtelSpanWrapper(span, scopes);
  }

  @Override
  public @Nullable ISpan retrieveCurrentSpan(IScopes scopes) {
    // TODO [POTEL] should we use Context.fromContextOrNull and read span from there?
    final @NotNull Span span = Span.current();
    return storage.getSentrySpan(span.getSpanContext());
  }

  private @NotNull Tracer getTracer() {
    return GlobalOpenTelemetry.getTracer(
        "sentry-instrumentation-scope-name", "sentry-instrumentation-scope-version");
  }
}
