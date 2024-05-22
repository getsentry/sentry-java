package io.sentry.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ISpanFactory;
import io.sentry.ITransaction;
import io.sentry.NoOpSpan;
import io.sentry.NoOpTransaction;
import io.sentry.SentryDate;
import io.sentry.SpanOptions;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.TransactionPerformanceCollector;
import java.util.concurrent.TimeUnit;
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
    final @Nullable OtelSpanWrapper span =
        createSpanInternal(
            context.getName(), context.getDescription(), scopes, transactionOptions, null);
    if (span == null) {
      return NoOpTransaction.getInstance();
    }
    return new OtelTransactionSpanForwarder(span);
  }

  @Override
  public @NotNull ISpan createSpan(
      final @NotNull String name,
      final @Nullable String description,
      final @NotNull IScopes scopes,
      final @NotNull SpanOptions spanOptions,
      final @Nullable ISpan parentSpan) {
    final @Nullable OtelSpanWrapper span =
        createSpanInternal(name, description, scopes, spanOptions, parentSpan);
    if (span == null) {
      return NoOpSpan.getInstance();
    }
    return span;
  }

  private @Nullable OtelSpanWrapper createSpanInternal(
      final @NotNull String name,
      final @Nullable String description,
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

    final @Nullable SentryDate startTimestampFromOptions = spanOptions.getStartTimestamp();
    final @NotNull SentryDate startTimestamp =
        startTimestampFromOptions == null
            ? scopes.getOptions().getDateProvider().now()
            : startTimestampFromOptions;
    spanBuilder.setStartTimestamp(startTimestamp.nanoTimestamp(), TimeUnit.NANOSECONDS);

    final @NotNull Span otelSpan = spanBuilder.startSpan();
    final @Nullable OtelSpanWrapper sentrySpan = storage.getSentrySpan(otelSpan.getSpanContext());
    if (sentrySpan != null && description != null) {
      sentrySpan.setDescription(description);
    }
    return sentrySpan;
  }

  @Override
  public @Nullable ISpan retrieveCurrentSpan(IScopes scopes) {
    final @Nullable Span span = Span.fromContextOrNull(Context.current());
    if (span == null) {
      return null;
    }
    return storage.getSentrySpan(span.getSpanContext());
  }

  private @NotNull Tracer getTracer() {
    return GlobalOpenTelemetry.getTracer(
        "sentry-instrumentation-scope-name", "sentry-instrumentation-scope-version");
  }
}
