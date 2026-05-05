package io.sentry.kafka;

import io.sentry.BaggageHeader;
import io.sentry.DateUtils;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ITransaction;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.util.SpanUtils;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Helper methods for instrumenting raw Kafka consumer record processing. */
@ApiStatus.Experimental
public final class SentryKafkaConsumerTracing {

  public static final @NotNull String TRACE_ORIGIN = "manual.queue.kafka.consumer";

  private static final @NotNull String CREATOR = "SentryKafkaConsumerTracing";
  private static final @NotNull String DELIVERY_ATTEMPT_HEADER = "kafka_deliveryAttempt";
  private static final @NotNull String MESSAGE_ID_HEADER = "messaging.message.id";

  private final @NotNull IScopes scopes;

  SentryKafkaConsumerTracing(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  /**
   * Runs the provided {@link Callable} with a Kafka consumer processing transaction for the given
   * record.
   *
   * @param record the Kafka record being processed
   * @param callable the processing callback
   * @return the return value of the callback
   * @param <K> the Kafka record key type
   * @param <V> the Kafka record value type
   * @param <U> the callback return type
   */
  public static <K, V, U> U withTracing(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull Callable<U> callable)
      throws Exception {
    return new SentryKafkaConsumerTracing(ScopesAdapter.getInstance())
        .withTracingImpl(record, callable);
  }

  /**
   * Runs the provided {@link Runnable} with a Kafka consumer processing transaction for the given
   * record.
   *
   * @param record the Kafka record being processed
   * @param runnable the processing callback
   * @param <K> the Kafka record key type
   * @param <V> the Kafka record value type
   */
  public static <K, V> void withTracing(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull Runnable runnable) {
    new SentryKafkaConsumerTracing(ScopesAdapter.getInstance()).withTracingImpl(record, runnable);
  }

  <K, V, U> U withTracingImpl(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull Callable<U> callable)
      throws Exception {
    if (!scopes.getOptions().isEnableQueueTracing() || isIgnored()) {
      return callable.call();
    }

    final @NotNull IScopes forkedScopes;
    final @NotNull ISentryLifecycleToken lifecycleToken;
    try {
      forkedScopes = scopes.forkedRootScopes(CREATOR);
      lifecycleToken = forkedScopes.makeCurrent();
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to fork scopes for Kafka consumer tracing.", t);
      return callable.call();
    }

    try (final @NotNull ISentryLifecycleToken ignored = lifecycleToken) {
      final @Nullable ITransaction transaction = startTransaction(forkedScopes, record);
      boolean didError = false;
      @Nullable Throwable callbackThrowable = null;

      try {
        return callable.call();
      } catch (Throwable t) {
        didError = true;
        callbackThrowable = t;
        throw t;
      } finally {
        finishTransaction(
            transaction, didError ? SpanStatus.INTERNAL_ERROR : SpanStatus.OK, callbackThrowable);
      }
    }
  }

  <K, V> void withTracingImpl(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull Runnable runnable) {
    try {
      withTracingImpl(
          record,
          () -> {
            runnable.run();
            return null;
          });
    } catch (Throwable t) {
      throwUnchecked(t);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void throwUnchecked(final @NotNull Throwable throwable)
      throws T {
    throw (T) throwable;
  }

  private boolean isIgnored() {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), TRACE_ORIGIN);
  }

  private <K, V> @Nullable ITransaction startTransaction(
      final @NotNull IScopes forkedScopes, final @NotNull ConsumerRecord<K, V> record) {
    try {
      final @Nullable TransactionContext continued = continueTrace(forkedScopes, record);
      if (!forkedScopes.getOptions().isTracingEnabled()) {
        return null;
      }

      final @NotNull TransactionContext txContext =
          continued != null ? continued : new TransactionContext("queue.process", "queue.process");
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

      final @Nullable String messageId = headerValue(record, MESSAGE_ID_HEADER);
      if (messageId != null) {
        transaction.setData(SpanDataConvention.MESSAGING_MESSAGE_ID, messageId);
      }

      final int bodySize = record.serializedValueSize();
      if (bodySize >= 0) {
        transaction.setData(SpanDataConvention.MESSAGING_MESSAGE_BODY_SIZE, bodySize);
      }

      final @Nullable Integer retryCount = retryCount(record);
      if (retryCount != null) {
        transaction.setData(SpanDataConvention.MESSAGING_MESSAGE_RETRY_COUNT, retryCount);
      }

      final @Nullable Long receiveLatency = receiveLatency(record);
      if (receiveLatency != null) {
        transaction.setData(SpanDataConvention.MESSAGING_MESSAGE_RECEIVE_LATENCY, receiveLatency);
      }

      return transaction;
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to start Kafka consumer tracing transaction.", t);
      return null;
    }
  }

  private void finishTransaction(
      final @Nullable ITransaction transaction,
      final @NotNull SpanStatus status,
      final @Nullable Throwable throwable) {
    if (transaction == null || transaction.isNoOp()) {
      return;
    }

    try {
      transaction.setStatus(status);
      if (throwable != null) {
        transaction.setThrowable(throwable);
      }
      transaction.finish();
    } catch (Throwable t) {
      // Instrumentation must never break customer processing.
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to finish Kafka consumer tracing transaction.", t);
    }
  }

  private <K, V> @Nullable TransactionContext continueTrace(
      final @NotNull IScopes forkedScopes, final @NotNull ConsumerRecord<K, V> record) {
    final @Nullable String sentryTrace = headerValue(record, SentryTraceHeader.SENTRY_TRACE_HEADER);
    final @Nullable List<String> baggageHeaders =
        headerValues(record, BaggageHeader.BAGGAGE_HEADER);
    return forkedScopes.continueTrace(sentryTrace, baggageHeaders);
  }

  private <K, V> @Nullable Integer retryCount(final @NotNull ConsumerRecord<K, V> record) {
    final @Nullable Header header = record.headers().lastHeader(DELIVERY_ATTEMPT_HEADER);
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

  private <K, V> @Nullable Long receiveLatency(final @NotNull ConsumerRecord<K, V> record) {
    final @Nullable String enqueuedTimeStr =
        headerValue(record, SentryKafkaProducerInterceptor.SENTRY_ENQUEUED_TIME_HEADER);
    if (enqueuedTimeStr == null) {
      return null;
    }

    try {
      final double enqueuedTimeSeconds = Double.parseDouble(enqueuedTimeStr);
      final double nowSeconds = DateUtils.millisToSeconds(System.currentTimeMillis());
      final long latencyMs = (long) ((nowSeconds - enqueuedTimeSeconds) * 1000);
      return latencyMs >= 0 ? latencyMs : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private <K, V> @Nullable String headerValue(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull String headerName) {
    final @Nullable Header header = record.headers().lastHeader(headerName);
    if (header == null || header.value() == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  private <K, V> @Nullable List<String> headerValues(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull String headerName) {
    @Nullable List<String> values = null;
    for (final @NotNull Header header : record.headers().headers(headerName)) {
      if (header.value() != null) {
        if (values == null) {
          values = new ArrayList<>();
        }
        values.add(new String(header.value(), StandardCharsets.UTF_8));
      }
    }
    return values;
  }
}
