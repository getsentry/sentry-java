package io.sentry.kafka;

import io.sentry.BaggageHeader;
import io.sentry.DateUtils;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.util.SpanUtils;
import io.sentry.util.TracingUtils;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a Kafka {@link Producer} to record a {@code queue.publish} span around each {@code send}
 * and to inject Sentry trace propagation headers into the produced record.
 *
 * <p>Unlike a {@link org.apache.kafka.clients.producer.ProducerInterceptor}, the wrapper keeps the
 * span open until the send callback fires, so the span reflects the actual broker-ack lifecycle.
 *
 * <p>For raw Kafka usage:
 *
 * <pre>{@code
 * Producer<String, String> producer =
 *     new SentryKafkaProducer<>(new KafkaProducer<>(props));
 * }</pre>
 *
 * <p>For Spring Kafka, the {@code SentryKafkaProducerBeanPostProcessor} in {@code
 * sentry-spring-jakarta} installs this wrapper automatically via {@code
 * ProducerFactory.addPostProcessor(...)}.
 */
@ApiStatus.Experimental
public final class SentryKafkaProducer<K, V> implements Producer<K, V> {

  public static final @NotNull String TRACE_ORIGIN = "auto.queue.kafka.producer";
  public static final @NotNull String SENTRY_ENQUEUED_TIME_HEADER = "sentry-task-enqueued-time";

  private final @NotNull Producer<K, V> delegate;
  private final @NotNull IScopes scopes;
  private final @NotNull String traceOrigin;

  public SentryKafkaProducer(final @NotNull Producer<K, V> delegate) {
    this(delegate, ScopesAdapter.getInstance(), TRACE_ORIGIN);
  }

  public SentryKafkaProducer(
      final @NotNull Producer<K, V> delegate, final @NotNull IScopes scopes) {
    this(delegate, scopes, TRACE_ORIGIN);
  }

  public SentryKafkaProducer(
      final @NotNull Producer<K, V> delegate,
      final @NotNull IScopes scopes,
      final @NotNull String traceOrigin) {
    this.delegate = delegate;
    this.scopes = scopes;
    this.traceOrigin = traceOrigin;
  }

  /** Returns the wrapped producer. */
  public @NotNull Producer<K, V> getDelegate() {
    return delegate;
  }

  @Override
  public @NotNull Future<RecordMetadata> send(final @NotNull ProducerRecord<K, V> record) {
    return send(record, null);
  }

  @Override
  public @NotNull Future<RecordMetadata> send(
      final @NotNull ProducerRecord<K, V> record, final @Nullable Callback callback) {
    if (!scopes.getOptions().isEnableQueueTracing() || isIgnored()) {
      return delegate.send(record, callback);
    }

    final @Nullable ISpan activeSpan = scopes.getSpan();
    if (activeSpan == null || activeSpan.isNoOp()) {
      return delegate.send(record, callback);
    }

    @Nullable ISpan span = null;
    try {
      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(traceOrigin);
      span = activeSpan.startChild("queue.publish", record.topic(), spanOptions);
      if (span.isNoOp()) {
        return delegate.send(record, callback);
      }

      span.setData(SpanDataConvention.MESSAGING_SYSTEM, "kafka");
      span.setData(SpanDataConvention.MESSAGING_DESTINATION_NAME, record.topic());
      injectHeaders(record.headers(), span);
    } catch (Throwable t) {
      if (span != null) {
        span.setThrowable(t);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
        if (!span.isFinished()) {
          span.finish();
        }
      }
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to instrument Kafka producer record.", t);
      return delegate.send(record, callback);
    }

    final @NotNull ISpan finalSpan = span;
    final @NotNull Callback wrappedCallback = wrapCallback(callback, finalSpan);

    try {
      return delegate.send(record, wrappedCallback);
    } catch (Throwable t) {
      finishWithError(finalSpan, t);
      throw t;
    }
  }

  private @NotNull Callback wrapCallback(
      final @Nullable Callback userCallback, final @NotNull ISpan span) {
    return (metadata, exception) -> {
      try {
        if (exception != null) {
          span.setThrowable(exception);
          span.setStatus(SpanStatus.INTERNAL_ERROR);
        } else {
          span.setStatus(SpanStatus.OK);
        }
      } catch (Throwable t) {
        scopes
            .getOptions()
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to set status on Kafka producer span.", t);
      } finally {
        if (!span.isFinished()) {
          span.finish();
        }
        if (userCallback != null) {
          userCallback.onCompletion(metadata, exception);
        }
      }
    };
  }

  private void finishWithError(final @NotNull ISpan span, final @NotNull Throwable t) {
    span.setThrowable(t);
    span.setStatus(SpanStatus.INTERNAL_ERROR);
    if (!span.isFinished()) {
      span.finish();
    }
  }

  private boolean isIgnored() {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), traceOrigin);
  }

  private void injectHeaders(final @NotNull Headers headers, final @NotNull ISpan span) {
    final @Nullable List<String> existingBaggageHeaders =
        readHeaderValues(headers, BaggageHeader.BAGGAGE_HEADER);
    final @Nullable TracingUtils.TracingHeaders tracingHeaders =
        TracingUtils.trace(scopes, existingBaggageHeaders, span);
    if (tracingHeaders != null) {
      final @NotNull SentryTraceHeader sentryTraceHeader = tracingHeaders.getSentryTraceHeader();
      headers.remove(sentryTraceHeader.getName());
      headers.add(
          sentryTraceHeader.getName(),
          sentryTraceHeader.getValue().getBytes(StandardCharsets.UTF_8));

      final @Nullable BaggageHeader baggageHeader = tracingHeaders.getBaggageHeader();
      if (baggageHeader != null) {
        headers.remove(baggageHeader.getName());
        headers.add(
            baggageHeader.getName(), baggageHeader.getValue().getBytes(StandardCharsets.UTF_8));
      }
    }

    headers.remove(SENTRY_ENQUEUED_TIME_HEADER);
    headers.add(
        SENTRY_ENQUEUED_TIME_HEADER,
        DateUtils.doubleToBigDecimal(DateUtils.millisToSeconds(System.currentTimeMillis()))
            .toString()
            .getBytes(StandardCharsets.UTF_8));
  }

  private static @Nullable List<String> readHeaderValues(
      final @NotNull Headers headers, final @NotNull String name) {
    @Nullable List<String> values = null;
    for (final @NotNull Header header : headers.headers(name)) {
      final byte @Nullable [] value = header.value();
      if (value != null) {
        if (values == null) {
          values = new ArrayList<>();
        }
        values.add(new String(value, StandardCharsets.UTF_8));
      }
    }
    return values;
  }

  // --- Pure delegation for everything else ---

  @Override
  public void initTransactions() {
    delegate.initTransactions();
  }

  @Override
  public void beginTransaction() throws ProducerFencedException {
    delegate.beginTransaction();
  }

  @Override
  @SuppressWarnings("deprecation")
  public void sendOffsetsToTransaction(
      final @NotNull Map<TopicPartition, OffsetAndMetadata> offsets,
      final @NotNull String consumerGroupId)
      throws ProducerFencedException {
    delegate.sendOffsetsToTransaction(offsets, consumerGroupId);
  }

  @Override
  public void sendOffsetsToTransaction(
      final @NotNull Map<TopicPartition, OffsetAndMetadata> offsets,
      final @NotNull ConsumerGroupMetadata groupMetadata)
      throws ProducerFencedException {
    delegate.sendOffsetsToTransaction(offsets, groupMetadata);
  }

  @Override
  public void commitTransaction() throws ProducerFencedException {
    delegate.commitTransaction();
  }

  @Override
  public void abortTransaction() throws ProducerFencedException {
    delegate.abortTransaction();
  }

  @Override
  public void flush() {
    delegate.flush();
  }

  @Override
  public @NotNull List<PartitionInfo> partitionsFor(final @NotNull String topic) {
    return delegate.partitionsFor(topic);
  }

  @Override
  public @NotNull Map<MetricName, ? extends Metric> metrics() {
    return delegate.metrics();
  }

  @Override
  public @NotNull Uuid clientInstanceId(final @NotNull Duration timeout) {
    return delegate.clientInstanceId(timeout);
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public void close(final @NotNull Duration timeout) {
    delegate.close(timeout);
  }

  @Override
  public @NotNull String toString() {
    return "SentryKafkaProducer[delegate=" + delegate + "]";
  }
}
