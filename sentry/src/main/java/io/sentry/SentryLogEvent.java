package io.sentry;

import static io.sentry.DateUtils.doubleToBigDecimal;

import io.sentry.protocol.SentryId;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryLogEvent implements JsonUnknown, JsonSerializable {

  private @NotNull SentryId traceId;
  private @NotNull Double timestamp;
  private @NotNull String body;
  private @NotNull SentryLogLevel level;
  private @Nullable Integer severityNumber;

  private @Nullable Map<String, SentryLogEventAttributeValue> attributes;
  private @Nullable Map<String, Object> unknown;

  public SentryLogEvent(
      final @NotNull SentryId traceId,
      final @NotNull SentryDate timestamp,
      final @NotNull String body,
      final @NotNull SentryLogLevel level) {
    this(traceId, DateUtils.nanosToSeconds(timestamp.nanoTimestamp()), body, level);
  }

  public SentryLogEvent(
      final @NotNull SentryId traceId,
      final @NotNull Double timestamp,
      final @NotNull String body,
      final @NotNull SentryLogLevel level) {
    this.traceId = traceId;
    this.timestamp = timestamp;
    this.body = body;
    this.level = level;
  }

  @NotNull
  public Double getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final @NotNull Double timestamp) {
    this.timestamp = timestamp;
  }

  public @NotNull String getBody() {
    return body;
  }

  public void setBody(final @NotNull String body) {
    this.body = body;
  }

  public @NotNull SentryLogLevel getLevel() {
    return level;
  }

  public void setLevel(final @NotNull SentryLogLevel level) {
    this.level = level;
  }

  public @Nullable Map<String, SentryLogEventAttributeValue> getAttributes() {
    return attributes;
  }

  public void setAttributes(final @Nullable Map<String, SentryLogEventAttributeValue> attributes) {
    this.attributes = attributes;
  }

  public void setAttribute(
      final @Nullable String key, final @Nullable SentryLogEventAttributeValue value) {
    if (key == null) {
      return;
    }
    if (this.attributes == null) {
      this.attributes = new HashMap<>();
    }
    this.attributes.put(key, value);
  }

  public @Nullable Integer getSeverityNumber() {
    return severityNumber;
  }

  public void setSeverityNumber(final @Nullable Integer severityNumber) {
    this.severityNumber = severityNumber;
  }

  // region json
  public static final class JsonKeys {
    public static final String TIMESTAMP = "timestamp";
    public static final String TRACE_ID = "trace_id";
    public static final String LEVEL = "level";
    public static final String SEVERITY_NUMBER = "severity_number";
    public static final String BODY = "body";
    public static final String ATTRIBUTES = "attributes";
  }

  @Override
  @SuppressWarnings("JdkObsolete")
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TIMESTAMP).value(logger, doubleToBigDecimal(timestamp));
    writer.name(JsonKeys.TRACE_ID).value(logger, traceId);
    writer.name(JsonKeys.BODY).value(body);
    writer.name(JsonKeys.LEVEL).value(logger, level);
    if (severityNumber != null) {
      writer.name(JsonKeys.SEVERITY_NUMBER).value(logger, severityNumber);
    }
    if (attributes != null) {
      writer.name(JsonKeys.ATTRIBUTES).value(logger, attributes);
    }

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        final Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<SentryLogEvent> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentryLogEvent deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      @Nullable Map<String, Object> unknown = null;
      @Nullable SentryId traceId = null;
      @Nullable Double timestamp = null;
      @Nullable String body = null;
      @Nullable SentryLogLevel level = null;
      @Nullable Integer severityNumber = null;
      @Nullable Map<String, SentryLogEventAttributeValue> attributes = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TRACE_ID:
            traceId = reader.nextOrNull(logger, new SentryId.Deserializer());
            break;
          case JsonKeys.TIMESTAMP:
            timestamp = reader.nextDoubleOrNull();
            break;
          case JsonKeys.BODY:
            body = reader.nextStringOrNull();
            break;
          case JsonKeys.LEVEL:
            level = reader.nextOrNull(logger, new SentryLogLevel.Deserializer());
            break;
          case JsonKeys.SEVERITY_NUMBER:
            severityNumber = reader.nextIntegerOrNull();
            break;
          case JsonKeys.ATTRIBUTES:
            attributes =
                reader.nextMapOrNull(logger, new SentryLogEventAttributeValue.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      if (traceId == null) {
        final String message = "Missing required field \"" + JsonKeys.TRACE_ID + "\"";
        final Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (timestamp == null) {
        final String message = "Missing required field \"" + JsonKeys.TIMESTAMP + "\"";
        final Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (body == null) {
        final String message = "Missing required field \"" + JsonKeys.BODY + "\"";
        final Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (level == null) {
        final String message = "Missing required field \"" + JsonKeys.LEVEL + "\"";
        final Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      final SentryLogEvent logEvent = new SentryLogEvent(traceId, timestamp, body, level);

      logEvent.setAttributes(attributes);
      logEvent.setSeverityNumber(severityNumber);
      logEvent.setUnknown(unknown);

      return logEvent;
    }
  }
  // endregion json
}
