package io.sentry.protocol;

import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryTracer;
import io.sentry.Span;
import io.sentry.SpanContext;
import io.sentry.SpanStatus;
import io.sentry.TracesSamplingDecision;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryTransaction extends SentryBaseEvent
    implements JsonUnknown, JsonSerializable {
  /** The transaction name. */
  @SuppressWarnings("UnusedVariable")
  private @Nullable String transaction;

  /** The moment in time when span was started. */
  private @NotNull Double startTimestamp;

  /** The moment in time when span has ended. */
  private @Nullable Double timestamp;

  /** A list of spans within this transaction. Can be empty. */
  private final @NotNull List<SentrySpan> spans = new ArrayList<>();

  /** The {@code type} property is required in JSON payload sent to Sentry. */
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";

  private @NotNull final Map<String, @NotNull MeasurementValue> measurements = new HashMap<>();

  private @NotNull TransactionInfo transactionInfo;

  private @Nullable Map<String, Object> unknown;

  @SuppressWarnings("deprecation")
  public SentryTransaction(final @NotNull SentryTracer sentryTracer) {
    super(sentryTracer.getEventId());
    Objects.requireNonNull(sentryTracer, "sentryTracer is required");
    // we lose precision here, from potential nanosecond precision down to 10 microsecond precision
    this.startTimestamp = DateUtils.nanosToSeconds(sentryTracer.getStartDate().nanoTimestamp());
    // we lose precision here, from potential nanosecond precision down to 10 microsecond precision
    this.timestamp =
        DateUtils.nanosToSeconds(
            sentryTracer
                .getStartDate()
                .laterDateNanosTimestampByDiff(sentryTracer.getFinishDate()));
    this.transaction = sentryTracer.getName();
    for (final Span span : sentryTracer.getChildren()) {
      if (Boolean.TRUE.equals(span.isSampled())) {
        this.spans.add(new SentrySpan(span));
      }
    }
    final Contexts contexts = this.getContexts();

    contexts.putAll(sentryTracer.getContexts());

    final SpanContext tracerContext = sentryTracer.getSpanContext();
    Map<String, Object> data = sentryTracer.getData();
    // tags must be placed on the root of the transaction instead of contexts.trace.tags
    final @NotNull SpanContext tracerContextToSend =
        new SpanContext(
            tracerContext.getTraceId(),
            tracerContext.getSpanId(),
            tracerContext.getParentSpanId(),
            tracerContext.getOperation(),
            tracerContext.getDescription(),
            tracerContext.getSamplingDecision(),
            tracerContext.getStatus(),
            tracerContext.getOrigin());

    for (final Map.Entry<String, String> tag : tracerContext.getTags().entrySet()) {
      this.setTag(tag.getKey(), tag.getValue());
    }

    if (data != null) {
      for (final Map.Entry<String, Object> tag : data.entrySet()) {
        tracerContextToSend.setData(tag.getKey(), tag.getValue());
      }
    }

    contexts.setTrace(tracerContextToSend);

    this.transactionInfo = new TransactionInfo(sentryTracer.getTransactionNameSource().apiName());
  }

  @ApiStatus.Internal
  public SentryTransaction(
      @Nullable String transaction,
      @NotNull Double startTimestamp,
      @Nullable Double timestamp,
      @NotNull List<SentrySpan> spans,
      @NotNull final Map<String, @NotNull MeasurementValue> measurements,
      @NotNull final TransactionInfo transactionInfo) {
    this.transaction = transaction;
    this.startTimestamp = startTimestamp;
    this.timestamp = timestamp;
    this.spans.addAll(spans);
    this.measurements.putAll(measurements);
    for (SentrySpan span : spans) {
      this.measurements.putAll(span.getMeasurements());
    }
    this.transactionInfo = transactionInfo;
  }

  public @NotNull List<SentrySpan> getSpans() {
    return spans;
  }

  public boolean isFinished() {
    return this.timestamp != null;
  }

  public @Nullable String getTransaction() {
    return transaction;
  }

  public @NotNull Double getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Double getTimestamp() {
    return timestamp;
  }

  public @NotNull String getType() {
    return type;
  }

  public @Nullable SpanStatus getStatus() {
    final SpanContext trace = this.getContexts().getTrace();
    return trace != null ? trace.getStatus() : null;
  }

  public boolean isSampled() {
    final @Nullable TracesSamplingDecision samplingDecsion = getSamplingDecision();
    if (samplingDecsion == null) {
      return false;
    }

    return samplingDecsion.getSampled();
  }

  public @Nullable TracesSamplingDecision getSamplingDecision() {
    final SpanContext trace = this.getContexts().getTrace();
    if (trace == null) {
      return null;
    }

    return trace.getSamplingDecision();
  }

  public @NotNull Map<String, @NotNull MeasurementValue> getMeasurements() {
    return measurements;
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String TRANSACTION = "transaction";
    public static final String START_TIMESTAMP = "start_timestamp";
    public static final String TIMESTAMP = "timestamp";
    public static final String SPANS = "spans";
    public static final String TYPE = "type";
    public static final String MEASUREMENTS = "measurements";
    public static final String TRANSACTION_INFO = "transaction_info";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (transaction != null) {
      writer.name(JsonKeys.TRANSACTION).value(transaction);
    }
    writer.name(JsonKeys.START_TIMESTAMP).value(logger, doubleToBigDecimal(startTimestamp));
    if (timestamp != null) {
      writer.name(JsonKeys.TIMESTAMP).value(logger, doubleToBigDecimal(timestamp));
    }
    if (!spans.isEmpty()) {
      writer.name(JsonKeys.SPANS).value(logger, spans);
    }
    writer.name(JsonKeys.TYPE).value(type);
    if (!measurements.isEmpty()) {
      writer.name(JsonKeys.MEASUREMENTS).value(logger, measurements);
    }
    writer.name(JsonKeys.TRANSACTION_INFO).value(logger, transactionInfo);
    new SentryBaseEvent.Serializer().serialize(this, writer, logger);
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  private @NotNull BigDecimal doubleToBigDecimal(final @NotNull Double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.DOWN);
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<SentryTransaction> {

    @SuppressWarnings({"unchecked", "JavaUtilDate"})
    @Override
    public @NotNull SentryTransaction deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      // Init with placeholders.
      SentryTransaction transaction =
          new SentryTransaction(
              "",
              0d,
              null,
              new ArrayList<>(),
              new HashMap<>(),
              new TransactionInfo(TransactionNameSource.CUSTOM.apiName()));
      Map<String, Object> unknown = null;

      SentryBaseEvent.Deserializer baseEventDeserializer = new SentryBaseEvent.Deserializer();

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TRANSACTION:
            transaction.transaction = reader.nextStringOrNull();
            break;
          case JsonKeys.START_TIMESTAMP:
            try {
              final Double deserializedStartTimestamp = reader.nextDoubleOrNull();
              if (deserializedStartTimestamp != null) {
                transaction.startTimestamp = deserializedStartTimestamp;
              }
            } catch (NumberFormatException e) {
              final Date date = reader.nextDateOrNull(logger);
              if (date != null) {
                transaction.startTimestamp = DateUtils.dateToSeconds(date);
              }
            }
            break;
          case JsonKeys.TIMESTAMP:
            try {
              final Double deserializedTimestamp = reader.nextDoubleOrNull();
              if (deserializedTimestamp != null) {
                transaction.timestamp = deserializedTimestamp;
              }
            } catch (NumberFormatException e) {
              final Date date = reader.nextDateOrNull(logger);
              if (date != null) {
                transaction.timestamp = DateUtils.dateToSeconds(date);
              }
            }
            break;
          case JsonKeys.SPANS:
            List<SentrySpan> deserializedSpans =
                reader.nextListOrNull(logger, new SentrySpan.Deserializer());
            if (deserializedSpans != null) {
              transaction.spans.addAll(deserializedSpans);
            }
            break;
          case JsonKeys.TYPE:
            reader.nextString(); // No need to assign, as it is final.
            break;
          case JsonKeys.MEASUREMENTS:
            Map<String, MeasurementValue> deserializedMeasurements =
                reader.nextMapOrNull(logger, new MeasurementValue.Deserializer());
            if (deserializedMeasurements != null) {
              transaction.measurements.putAll(deserializedMeasurements);
            }
            break;
          case JsonKeys.TRANSACTION_INFO:
            transaction.transactionInfo =
                new TransactionInfo.Deserializer().deserialize(reader, logger);
            break;
          default:
            if (!baseEventDeserializer.deserializeValue(transaction, nextName, reader, logger)) {
              if (unknown == null) {
                unknown = new ConcurrentHashMap<>();
              }
              reader.nextUnknown(logger, unknown, nextName);
            }
            break;
        }
      }
      transaction.setUnknown(unknown);
      reader.endObject();
      return transaction;
    }
  }
}
