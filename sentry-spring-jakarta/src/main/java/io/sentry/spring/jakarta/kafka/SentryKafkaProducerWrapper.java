package io.sentry.spring.jakarta.kafka;

import io.micrometer.observation.Observation;
import io.sentry.BaggageHeader;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.util.TracingUtils;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Wraps a {@link KafkaTemplate} to create {@code queue.publish} spans for Kafka send operations.
 *
 * <p>Overrides {@code doSend} which is the common path for all send variants in {@link
 * KafkaTemplate}.
 */
@ApiStatus.Internal
public final class SentryKafkaProducerWrapper<K, V> extends KafkaTemplate<K, V> {

  static final String TRACE_ORIGIN = "auto.queue.spring_jakarta.kafka.producer";
  static final String SENTRY_ENQUEUED_TIME_HEADER = "sentry-task-enqueued-time";

  private final @NotNull IScopes scopes;

  public SentryKafkaProducerWrapper(
      final @NotNull KafkaTemplate<K, V> delegate, final @NotNull IScopes scopes) {
    super(delegate.getProducerFactory());
    this.scopes = scopes;
    this.setDefaultTopic(delegate.getDefaultTopic());
    if (delegate.isTransactional()) {
      this.setTransactionIdPrefix(delegate.getTransactionIdPrefix());
    }
    this.setMessageConverter(delegate.getMessageConverter());
    this.setMicrometerTagsProvider(delegate.getMicrometerTagsProvider());
  }

  @Override
  protected @NotNull CompletableFuture<SendResult<K, V>> doSend(
      final @NotNull ProducerRecord<K, V> record, final @Nullable Observation observation) {
    if (!scopes.getOptions().isEnableQueueTracing()) {
      return super.doSend(record, observation);
    }

    final @Nullable ISpan activeSpan = scopes.getSpan();
    if (activeSpan == null || activeSpan.isNoOp()) {
      return super.doSend(record, observation);
    }

    final @NotNull SpanOptions spanOptions = new SpanOptions();
    spanOptions.setOrigin(TRACE_ORIGIN);
    final @NotNull ISpan span = activeSpan.startChild("queue.publish", record.topic(), spanOptions);
    if (span.isNoOp()) {
      return super.doSend(record, observation);
    }

    span.setData(SpanDataConvention.MESSAGING_SYSTEM, "kafka");
    span.setData(SpanDataConvention.MESSAGING_DESTINATION_NAME, record.topic());

    try {
      injectHeaders(record.headers(), span);
    } catch (Throwable ignored) {
      // Header injection must not break the send
    }

    final @NotNull CompletableFuture<SendResult<K, V>> future;
    try {
      future = super.doSend(record, observation);
      return future.whenComplete(
          (result, throwable) -> {
            if (throwable != null) {
              span.setStatus(SpanStatus.INTERNAL_ERROR);
              span.setThrowable(throwable);
            } else {
              span.setStatus(SpanStatus.OK);
            }
            span.finish();
          });
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      span.finish();
      throw e;
    }
  }

  private void injectHeaders(final @NotNull Headers headers, final @NotNull ISpan span) {
    final @Nullable TracingUtils.TracingHeaders tracingHeaders =
        TracingUtils.trace(scopes, null, span);
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
        String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
  }
}
