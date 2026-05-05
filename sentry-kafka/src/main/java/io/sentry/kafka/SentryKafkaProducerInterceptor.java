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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
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
    if (!scopes.getOptions().isEnableQueueTracing() || isIgnored()) {
      return record;
    }

    final @Nullable ISpan activeSpan = scopes.getSpan();
    if (activeSpan == null || activeSpan.isNoOp()) {
      return record;
    }

    @Nullable ISpan span = null;
    try {
      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(traceOrigin);
      span = activeSpan.startChild("queue.publish", record.topic(), spanOptions);
      if (span.isNoOp()) {
        return record;
      }

      span.setData(SpanDataConvention.MESSAGING_SYSTEM, "kafka");
      span.setData(SpanDataConvention.MESSAGING_DESTINATION_NAME, record.topic());

      injectHeaders(record.headers(), span);
      span.setStatus(SpanStatus.OK);
    } catch (Throwable t) {
      if (span != null) {
        span.setThrowable(t);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
      }
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to instrument Kafka producer record.", t);
    } finally {
      if (span != null && !span.isFinished()) {
        span.finish();
      }
    }

    return record;
  }

  @Override
  public void onAcknowledgement(
      final @Nullable RecordMetadata metadata, final @Nullable Exception exception) {}

  private boolean isIgnored() {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), traceOrigin);
  }

  @Override
  public void close() {}

  @Override
  public void configure(final @Nullable Map<String, ?> configs) {}

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
}
