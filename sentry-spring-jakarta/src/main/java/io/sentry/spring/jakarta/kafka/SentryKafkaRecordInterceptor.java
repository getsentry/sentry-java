package io.sentry.spring.jakarta.kafka;

import io.sentry.BaggageHeader;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ITransaction;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * A {@link RecordInterceptor} that creates {@code queue.process} transactions for incoming Kafka
 * records with distributed tracing support.
 */
@ApiStatus.Internal
public final class SentryKafkaRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

  static final String TRACE_ORIGIN = "auto.queue.spring_jakarta.kafka.consumer";

  private final @NotNull IScopes scopes;
  private final @Nullable RecordInterceptor<K, V> delegate;

  private static final @NotNull ThreadLocal<SentryRecordContext> currentContext =
      new ThreadLocal<>();

  public SentryKafkaRecordInterceptor(final @NotNull IScopes scopes) {
    this(scopes, null);
  }

  public SentryKafkaRecordInterceptor(
      final @NotNull IScopes scopes, final @Nullable RecordInterceptor<K, V> delegate) {
    this.scopes = scopes;
    this.delegate = delegate;
  }

  @Override
  public @Nullable ConsumerRecord<K, V> intercept(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull Consumer<K, V> consumer) {
    if (!scopes.getOptions().isEnableQueueTracing()) {
      return delegateIntercept(record, consumer);
    }

    final @NotNull IScopes forkedScopes = scopes.forkedScopes("SentryKafkaRecordInterceptor");
    final @NotNull ISentryLifecycleToken lifecycleToken = forkedScopes.makeCurrent();

    continueTrace(forkedScopes, record);

    final @Nullable ITransaction transaction = startTransaction(forkedScopes, record);
    currentContext.set(new SentryRecordContext(lifecycleToken, transaction));

    return delegateIntercept(record, consumer);
  }

  @Override
  public void success(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull Consumer<K, V> consumer) {
    try {
      if (delegate != null) {
        delegate.success(record, consumer);
      }
    } finally {
      finishSpan(SpanStatus.OK, null);
    }
  }

  @Override
  public void failure(
      final @NotNull ConsumerRecord<K, V> record,
      final @NotNull Exception exception,
      final @NotNull Consumer<K, V> consumer) {
    try {
      if (delegate != null) {
        delegate.failure(record, exception, consumer);
      }
    } finally {
      finishSpan(SpanStatus.INTERNAL_ERROR, exception);
    }
  }

  @Override
  public void afterRecord(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull Consumer<K, V> consumer) {
    if (delegate != null) {
      delegate.afterRecord(record, consumer);
    }
  }

  private @Nullable ConsumerRecord<K, V> delegateIntercept(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull Consumer<K, V> consumer) {
    if (delegate != null) {
      return delegate.intercept(record, consumer);
    }
    return record;
  }

  private void continueTrace(
      final @NotNull IScopes forkedScopes, final @NotNull ConsumerRecord<K, V> record) {
    final @Nullable String sentryTrace = headerValue(record, SentryTraceHeader.SENTRY_TRACE_HEADER);
    final @Nullable String baggage = headerValue(record, BaggageHeader.BAGGAGE_HEADER);
    final @Nullable List<String> baggageHeaders =
        baggage != null ? Collections.singletonList(baggage) : null;
    forkedScopes.continueTrace(sentryTrace, baggageHeaders);
  }

  private @Nullable ITransaction startTransaction(
      final @NotNull IScopes forkedScopes, final @NotNull ConsumerRecord<K, V> record) {
    if (!forkedScopes.getOptions().isTracingEnabled()) {
      return null;
    }

    final @NotNull TransactionOptions txOptions = new TransactionOptions();
    txOptions.setOrigin(TRACE_ORIGIN);
    txOptions.setBindToScope(true);

    final @NotNull ITransaction transaction =
        forkedScopes.startTransaction(
            new TransactionContext("queue.process", "queue.process"), txOptions);

    if (transaction.isNoOp()) {
      return null;
    }

    transaction.setData(SpanDataConvention.MESSAGING_SYSTEM, "kafka");
    transaction.setData(SpanDataConvention.MESSAGING_DESTINATION_NAME, record.topic());

    final @Nullable String messageId = headerValue(record, "messaging.message.id");
    if (messageId != null) {
      transaction.setData(SpanDataConvention.MESSAGING_MESSAGE_ID, messageId);
    }

    final @Nullable String enqueuedTimeStr =
        headerValue(record, SentryProducerInterceptor.SENTRY_ENQUEUED_TIME_HEADER);
    if (enqueuedTimeStr != null) {
      try {
        final long enqueuedTime = Long.parseLong(enqueuedTimeStr);
        final long latencyMs = System.currentTimeMillis() - enqueuedTime;
        if (latencyMs >= 0) {
          transaction.setData(SpanDataConvention.MESSAGING_MESSAGE_RECEIVE_LATENCY, latencyMs);
        }
      } catch (NumberFormatException ignored) {
        // ignore malformed header
      }
    }

    return transaction;
  }

  private void finishSpan(final @NotNull SpanStatus status, final @Nullable Throwable throwable) {
    final @Nullable SentryRecordContext ctx = currentContext.get();
    if (ctx == null) {
      return;
    }
    currentContext.remove();

    try {
      final @Nullable ITransaction transaction = ctx.transaction;
      if (transaction != null) {
        transaction.setStatus(status);
        if (throwable != null) {
          transaction.setThrowable(throwable);
        }
        transaction.finish();
      }
    } finally {
      ctx.lifecycleToken.close();
    }
  }

  private @Nullable String headerValue(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull String headerName) {
    final @Nullable Header header = record.headers().lastHeader(headerName);
    if (header == null || header.value() == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  private static final class SentryRecordContext {
    final @NotNull ISentryLifecycleToken lifecycleToken;
    final @Nullable ITransaction transaction;

    SentryRecordContext(
        final @NotNull ISentryLifecycleToken lifecycleToken,
        final @Nullable ITransaction transaction) {
      this.lifecycleToken = lifecycleToken;
      this.transaction = transaction;
    }
  }
}
