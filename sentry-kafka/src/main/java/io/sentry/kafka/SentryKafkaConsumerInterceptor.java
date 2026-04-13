package io.sentry.kafka;

import io.sentry.BaggageHeader;
import io.sentry.IScopes;
import io.sentry.ITransaction;
import io.sentry.ScopesAdapter;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryKafkaConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

  public static final @NotNull String TRACE_ORIGIN = "auto.queue.kafka.consumer";

  private final @NotNull IScopes scopes;

  public SentryKafkaConsumerInterceptor() {
    this(ScopesAdapter.getInstance());
  }

  public SentryKafkaConsumerInterceptor(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public @NotNull ConsumerRecords<K, V> onConsume(final @NotNull ConsumerRecords<K, V> records) {
    if (!scopes.getOptions().isEnableQueueTracing() || records.isEmpty()) {
      return records;
    }

    final @NotNull ConsumerRecord<K, V> firstRecord = records.iterator().next();

    try {
      final @Nullable TransactionContext continued = continueTrace(firstRecord);
      final @NotNull TransactionContext txContext =
          continued != null ? continued : new TransactionContext("queue.receive", "queue.receive");
      txContext.setName("queue.receive");
      txContext.setOperation("queue.receive");

      final @NotNull TransactionOptions txOptions = new TransactionOptions();
      txOptions.setOrigin(TRACE_ORIGIN);
      txOptions.setBindToScope(false);

      final @NotNull ITransaction transaction = scopes.startTransaction(txContext, txOptions);
      if (!transaction.isNoOp()) {
        transaction.setData(SpanDataConvention.MESSAGING_SYSTEM, "kafka");
        transaction.setData(SpanDataConvention.MESSAGING_DESTINATION_NAME, firstRecord.topic());
        transaction.setData("messaging.batch.message.count", records.count());
        transaction.setStatus(SpanStatus.OK);
        transaction.finish();
      }
    } catch (Throwable ignored) {
      // Instrumentation must never break the customer's Kafka poll loop.
    }

    return records;
  }

  @Override
  public void onCommit(final @NotNull Map<TopicPartition, OffsetAndMetadata> offsets) {}

  @Override
  public void close() {}

  @Override
  public void configure(final @Nullable Map<String, ?> configs) {}

  private @Nullable TransactionContext continueTrace(final @NotNull ConsumerRecord<K, V> record) {
    final @Nullable String sentryTrace = headerValue(record, SentryTraceHeader.SENTRY_TRACE_HEADER);
    final @Nullable String baggage = headerValue(record, BaggageHeader.BAGGAGE_HEADER);
    final @Nullable List<String> baggageHeaders =
        baggage != null ? Collections.singletonList(baggage) : null;
    return scopes.continueTrace(sentryTrace, baggageHeaders);
  }

  private @Nullable String headerValue(
      final @NotNull ConsumerRecord<K, V> record, final @NotNull String headerName) {
    final @Nullable Header header = record.headers().lastHeader(headerName);
    if (header == null || header.value() == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }
}
