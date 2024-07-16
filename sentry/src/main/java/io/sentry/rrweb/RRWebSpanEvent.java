package io.sentry.rrweb;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RRWebSpanEvent extends RRWebEvent implements JsonSerializable, JsonUnknown {
  public static final String EVENT_TAG = "performanceSpan";

  private @NotNull String tag;
  private @Nullable String op;
  private @Nullable String description;
  private double startTimestamp;
  private double endTimestamp;
  private @Nullable Map<String, Object> data;
  // to support unknown json attributes with nesting, we have to have unknown map for each of the
  // nested object in json: { ..., "data": { ..., "payload": { ... } } }
  private @Nullable Map<String, Object> unknown;
  private @Nullable Map<String, Object> payloadUnknown;
  private @Nullable Map<String, Object> dataUnknown;

  public RRWebSpanEvent() {
    super(RRWebEventType.Custom);
    tag = EVENT_TAG;
  }

  @NotNull
  public String getTag() {
    return tag;
  }

  public void setTag(final @NotNull String tag) {
    this.tag = tag;
  }

  @Nullable
  public String getOp() {
    return op;
  }

  public void setOp(final @Nullable String op) {
    this.op = op;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public void setDescription(final @Nullable String description) {
    this.description = description;
  }

  public double getStartTimestamp() {
    return startTimestamp;
  }

  public void setStartTimestamp(final double startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  public double getEndTimestamp() {
    return endTimestamp;
  }

  public void setEndTimestamp(final double endTimestamp) {
    this.endTimestamp = endTimestamp;
  }

  @Nullable
  public Map<String, Object> getData() {
    return data;
  }

  public void setData(final @Nullable Map<String, Object> data) {
    this.data = data == null ? null : new ConcurrentHashMap<>(data);
  }

  public @Nullable Map<String, Object> getPayloadUnknown() {
    return payloadUnknown;
  }

  public void setPayloadUnknown(final @Nullable Map<String, Object> payloadUnknown) {
    this.payloadUnknown = payloadUnknown;
  }

  public @Nullable Map<String, Object> getDataUnknown() {
    return dataUnknown;
  }

  public void setDataUnknown(final @Nullable Map<String, Object> dataUnknown) {
    this.dataUnknown = dataUnknown;
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  // region json
  public static final class JsonKeys {
    public static final String DATA = "data";
    public static final String PAYLOAD = "payload";
    public static final String OP = "op";
    public static final String DESCRIPTION = "description";
    public static final String START_TIMESTAMP = "startTimestamp";
    public static final String END_TIMESTAMP = "endTimestamp";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    new RRWebEvent.Serializer().serialize(this, writer, logger);
    writer.name(RRWebBreadcrumbEvent.JsonKeys.DATA);
    serializeData(writer, logger);
    if (unknown != null) {
      for (final String key : unknown.keySet()) {
        final Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  private void serializeData(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(RRWebEvent.JsonKeys.TAG).value(tag);
    writer.name(RRWebBreadcrumbEvent.JsonKeys.PAYLOAD);
    serializePayload(writer, logger);
    if (dataUnknown != null) {
      for (String key : dataUnknown.keySet()) {
        Object value = dataUnknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  private void serializePayload(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (op != null) {
      writer.name(JsonKeys.OP).value(op);
    }
    if (description != null) {
      writer.name(JsonKeys.DESCRIPTION).value(description);
    }
    writer.name(JsonKeys.START_TIMESTAMP).value(logger, BigDecimal.valueOf(startTimestamp));
    writer.name(JsonKeys.END_TIMESTAMP).value(logger, BigDecimal.valueOf(endTimestamp));
    if (data != null) {
      writer.name(JsonKeys.DATA).value(logger, data);
    }
    if (payloadUnknown != null) {
      for (final String key : payloadUnknown.keySet()) {
        final Object value = payloadUnknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<RRWebSpanEvent> {

    @Override
    public @NotNull RRWebSpanEvent deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      @Nullable Map<String, Object> unknown = null;

      final RRWebSpanEvent event = new RRWebSpanEvent();
      final RRWebEvent.Deserializer baseEventDeserializer = new RRWebEvent.Deserializer();

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.DATA:
            deserializeData(event, reader, logger);
            break;
          default:
            if (!baseEventDeserializer.deserializeValue(event, nextName, reader, logger)) {
              if (unknown == null) {
                unknown = new HashMap<>();
              }
              reader.nextUnknown(logger, unknown, nextName);
            }
            break;
        }
      }

      event.setUnknown(unknown);
      reader.endObject();
      return event;
    }

    private void deserializeData(
        final @NotNull RRWebSpanEvent event,
        final @NotNull ObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      @Nullable Map<String, Object> dataUnknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case RRWebEvent.JsonKeys.TAG:
            final String tag = reader.nextStringOrNull();
            event.tag = tag == null ? "" : tag;
            break;
          case JsonKeys.PAYLOAD:
            deserializePayload(event, reader, logger);
            break;
          default:
            if (dataUnknown == null) {
              dataUnknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, dataUnknown, nextName);
        }
      }
      event.setDataUnknown(dataUnknown);
      reader.endObject();
    }

    @SuppressWarnings("unchecked")
    private void deserializePayload(
        final @NotNull RRWebSpanEvent event,
        final @NotNull ObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      @Nullable Map<String, Object> payloadUnknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.OP:
            event.op = reader.nextStringOrNull();
            break;
          case JsonKeys.DESCRIPTION:
            event.description = reader.nextStringOrNull();
            break;
          case JsonKeys.START_TIMESTAMP:
            event.startTimestamp = reader.nextDouble();
            break;
          case JsonKeys.END_TIMESTAMP:
            event.endTimestamp = reader.nextDouble();
            break;
          case JsonKeys.DATA:
            Map<String, Object> deserializedData =
                CollectionUtils.newConcurrentHashMap(
                    (Map<String, Object>) reader.nextObjectOrNull());
            if (deserializedData != null) {
              event.data = deserializedData;
            }
            break;
          default:
            if (payloadUnknown == null) {
              payloadUnknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, payloadUnknown, nextName);
        }
      }
      event.setPayloadUnknown(payloadUnknown);
      reader.endObject();
    }
  }
  // endregion json
}
