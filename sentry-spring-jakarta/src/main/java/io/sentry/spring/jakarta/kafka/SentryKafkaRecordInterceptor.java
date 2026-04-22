package io.sentry.spring.jakarta.kafka;

import io.sentry.BaggageHeader;
import io.sentry.DateUtils;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ITransaction;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.kafka.SentryKafkaProducerInterceptor;
import io.sentry.util.SpanUtils;
import java.nio.ByteBuffer;
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
import org.springframework.kafka.support.KafkaHeaders;

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
    if (!scopes.getOptions().isEnableQueueTracing() || isIgnored()) {
      return delegateIntercept(record, consumer);
    }

    finishStaleContext();

    final @NotNull IScopes forkedScopes = scopes.forkedRootScopes("SentryKafkaRecordInterceptor");
    final @NotNull ISentryLifecycleToken lifecycleToken = forkedScopes.makeCurrent();
    currentContext.set(new SentryRecordContext(lifecycleToken, null));

    final @Nullable TransactionContext transactionContext = continueTrace(forkedScopes, record);

    final @Nullable ITransaction transaction =
        startTransaction(forkedScopes, record, transactionContext);
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

  @Override
  public void clearThreadState(final @NotNull Consumer<?, ?> consumer) {
    finishStaleContext();
  }

  private boolean isIgnored() {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), TRACE_ORIGIN);
  }

  private @Nullable ConsumerRecord<K, V> delegateIntercept(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull Consumer<K, V> consumer) {
    if (delegate != null) {
      return delegate.intercept(record, consumer);
    }
    return record;
  }

  private @Nullable TransactionContext continueTrace(
      final @NotNull IScopes forkedScopes, final @NotNull ConsumerRecord<K, V> record) {
    final @Nullable String sentryTrace = headerValue(record, SentryTraceHeader.SENTRY_TRACE_HEADER);
    final @Nullable String baggage = headerValue(record, BaggageHeader.BAGGAGE_HEADER);
    final @Nullable List<String> baggageHeaders =
        baggage != null ? Collections.singletonList(baggage) : null;
    return forkedScopes.continueTrace(sentryTrace, baggageHeaders);
  }

  private @Nullable ITransaction startTransaction(
      final @NotNull IScopes forkedScopes,
      final @NotNull ConsumerRecord<K, V> record,
      final @Nullable TransactionContext transactionContext) {
    if (!forkedScopes.getOptions().isTracingEnabled()) {
      return null;
    }

    final @NotNull TransactionContext txContext =
        transactionContext != null
            ? transactionContext
            : new TransactionContext("queue.process", "queue.process");
    txContext.setName("queue.process");
    txContext.setOperation("queue.process");

    final @NotNull TransactionOptions txOptions = new TransactionOptions();
    txOptions.setOrigin(TRACE_ORIGIN);
    txOptions.setBindToScope(true);

    final @NotNull ITransaction transaction = forkedScopes.startTransaction(txContext, txOptions);

    if (transaction.isNoOp()) {
      return null;
    }

    transaction.setData(SpanDataConvention.MESSAGING_SYSTEM, "kafka");
    transaction.setData(SpanDataConvention.MESSAGING_DESTINATION_NAME, record.topic());

    final @Nullable String messageId = headerValue(record, "messaging.message.id");
    if (messageId != null) {
      transaction.setData(SpanDataConvention.MESSAGING_MESSAGE_ID, messageId);
    }

    final @Nullable Integer retryCount = retryCount(record);
    if (retryCount != null) {
      transaction.setData(SpanDataConvention.MESSAGING_MESSAGE_RETRY_COUNT, retryCount);
    }

    final @Nullable String enqueuedTimeStr =
        headerValue(record, SentryKafkaProducerInterceptor.SENTRY_ENQUEUED_TIME_HEADER);
    if (enqueuedTimeStr != null) {
      try {
        final double enqueuedTimeSeconds = Double.parseDouble(enqueuedTimeStr);
        final double nowSeconds = DateUtils.millisToSeconds(System.currentTimeMillis());
        final long latencyMs = (long) ((nowSeconds - enqueuedTimeSeconds) * 1000);
        if (latencyMs >= 0) {
          transaction.setData(SpanDataConvention.MESSAGING_MESSAGE_RECEIVE_LATENCY, latencyMs);
        }
      } catch (NumberFormatException ignored) {
        // ignore malformed header
      }
    }

    return transaction;
  }

  private @Nullable Integer retryCount(final @NotNull ConsumerRecord<K, V> record) {
    final @Nullable Header header = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
    if (header == null) {
      return null;
    }

    final byte[] value = header.value();
    if (value == null || value.length != Integer.BYTES) {
      return null;
    }

    final int attempt = ByteBuffer.wrap(value).getInt();
    if (attempt <= 0) {
      return null;
    }

    return attempt - 1;
  }

  private void finishStaleContext() {
    if (currentContext.get() != null) {
      finishSpan(SpanStatus.UNKNOWN, null);
    }
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
