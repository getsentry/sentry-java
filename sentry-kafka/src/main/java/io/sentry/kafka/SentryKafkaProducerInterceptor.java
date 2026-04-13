package io.sentry.kafka;

import io.sentry.BaggageHeader;
import io.sentry.DateUtils;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ScopesAdapter;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.util.TracingUtils;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryKafkaProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

  public static final @NotNull String TRACE_ORIGIN = "auto.queue.kafka.producer";
  public static final @NotNull String SENTRY_ENQUEUED_TIME_HEADER = "sentry-task-enqueued-time";

  private final @NotNull IScopes scopes;
  private final @NotNull String traceOrigin;

  public SentryKafkaProducerInterceptor() {
    this(ScopesAdapter.getInstance(), TRACE_ORIGIN);
  }

  public SentryKafkaProducerInterceptor(final @NotNull IScopes scopes) {
    this(scopes, TRACE_ORIGIN);
  }

  public SentryKafkaProducerInterceptor(
      final @NotNull IScopes scopes, final @NotNull String traceOrigin) {
    this.scopes = scopes;
    this.traceOrigin = traceOrigin;
  }

  @Override
  public @NotNull ProducerRecord<K, V> onSend(final @NotNull ProducerRecord<K, V> record) {
    if (!scopes.getOptions().isEnableQueueTracing()) {
      return record;
    }

    final @Nullable ISpan activeSpan = scopes.getSpan();
    if (activeSpan == null || activeSpan.isNoOp()) {
      return record;
    }

    try {
      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(traceOrigin);
      final @NotNull ISpan span =
          activeSpan.startChild("queue.publish", record.topic(), spanOptions);
      if (span.isNoOp()) {
        return record;
      }

      span.setData(SpanDataConvention.MESSAGING_SYSTEM, "kafka");
      span.setData(SpanDataConvention.MESSAGING_DESTINATION_NAME, record.topic());

      injectHeaders(record.headers(), span);

      span.setStatus(SpanStatus.OK);
      span.finish();
    } catch (Throwable ignored) {
      // Instrumentation must never break the customer's Kafka send.
    }

    return record;
  }

  @Override
  public void onAcknowledgement(
      final @Nullable RecordMetadata metadata, final @Nullable Exception exception) {}

  @Override
  public void close() {}

  @Override
  public void configure(final @Nullable Map<String, ?> configs) {}

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
        String.valueOf(DateUtils.millisToSeconds(System.currentTimeMillis()))
            .getBytes(StandardCharsets.UTF_8));
  }
}
