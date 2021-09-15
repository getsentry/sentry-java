package io.sentry.protocol;

import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryTracer;
import io.sentry.Span;
import io.sentry.SpanContext;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryTransaction extends SentryBaseEvent
    implements JsonUnknown, JsonSerializable {
  /** The transaction name. */
  @SuppressWarnings("UnusedVariable")
  private @Nullable String transaction;

  /** The moment in time when span was started. */
  private @NotNull Date startTimestamp;

  /** The moment in time when span has ended. */
  private @Nullable Date timestamp;

  /** A list of spans within this transaction. Can be empty. */
  private final @NotNull List<SentrySpan> spans = new ArrayList<>();

  /** The {@code type} property is required in JSON payload sent to Sentry. */
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";

  private @NotNull final Map<String, @NotNull MeasurementValue> measurements = new HashMap<>();

  private @Nullable Map<String, Object> unknown;

  @SuppressWarnings("deprecation")
  public SentryTransaction(final @NotNull SentryTracer sentryTracer) {
    super(sentryTracer.getEventId());
    Objects.requireNonNull(sentryTracer, "sentryTracer is required");
    this.startTimestamp = sentryTracer.getStartTimestamp();
    this.timestamp = DateUtils.getCurrentDateTime();
    this.transaction = sentryTracer.getName();
    for (final Span span : sentryTracer.getChildren()) {
      this.spans.add(new SentrySpan(span));
    }
    final Contexts contexts = this.getContexts();
    for (Map.Entry<String, Object> entry : sentryTracer.getContexts().entrySet()) {
      contexts.put(entry.getKey(), entry.getValue());
    }
    contexts.setTrace(sentryTracer.getSpanContext());
    this.setRequest(sentryTracer.getRequest());
  }

  @ApiStatus.Internal
  public SentryTransaction(
      @Nullable String transaction,
      @NotNull Date startTimestamp,
      @Nullable Date timestamp,
      @NotNull List<SentrySpan> spans,
      @NotNull final Map<String, @NotNull MeasurementValue> measurements) {
    this.transaction = transaction;
    this.startTimestamp = startTimestamp;
    this.timestamp = timestamp;
    this.spans.addAll(spans);
    this.measurements.putAll(measurements);
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

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
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
    final SpanContext trace = this.getContexts().getTrace();
    return trace != null && Boolean.TRUE.equals(trace.getSampled());
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
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (transaction != null) {
      writer.name(JsonKeys.TRANSACTION).value(transaction);
    }
    writer.name(JsonKeys.START_TIMESTAMP).value(logger, startTimestamp);
    if (timestamp != null) {
      writer.name(JsonKeys.TIMESTAMP).value(logger, timestamp);
    }
    if (!spans.isEmpty()) {
      writer.name(JsonKeys.SPANS).value(logger, spans);
    }
    writer.name(JsonKeys.TYPE).value(type);
    if (!measurements.isEmpty()) {
      writer.name(JsonKeys.MEASUREMENTS).value(logger, measurements);
    }
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
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      // Init with placeholders.
      SentryTransaction transaction =
          new SentryTransaction("", new Date(), new Date(), new ArrayList<>(), new HashMap<>());
      Map<String, Object> unknown = null;

      SentryBaseEvent.Deserializer baseEventDeserializer = new SentryBaseEvent.Deserializer();

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TRANSACTION:
            transaction.transaction = reader.nextStringOrNull();
            break;
          case JsonKeys.START_TIMESTAMP:
            Date deserializedStartTimestamp = reader.nextDateOrNull(logger);
            if (deserializedStartTimestamp != null) {
              transaction.startTimestamp = deserializedStartTimestamp;
            }
            break;
          case JsonKeys.TIMESTAMP:
            Date deserializedTimestamp = reader.nextDateOrNull(logger);
            if (deserializedTimestamp != null) {
              transaction.timestamp = deserializedTimestamp;
            }
            break;
          case JsonKeys.SPANS:
            List<SentrySpan> deserializedSpans =
                reader.nextList(logger, new SentrySpan.Deserializer());
            if (deserializedSpans != null) {
              transaction.spans.addAll(deserializedSpans);
            }
            break;
          case JsonKeys.TYPE:
            reader.nextString(); // No need to assign, as it is final.
            break;
          case JsonKeys.MEASUREMENTS:
            Map<String, @NotNull MeasurementValue> deserializedMeasurements =
                (Map<String, @NotNull MeasurementValue>) reader.nextObjectOrNull();
            if (deserializedMeasurements != null) {
              transaction.measurements.putAll(deserializedMeasurements);
            }
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
