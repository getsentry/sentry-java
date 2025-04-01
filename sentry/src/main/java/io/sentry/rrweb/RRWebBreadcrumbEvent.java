package io.sentry.rrweb;

import static io.sentry.SentryLevel.DEBUG;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RRWebBreadcrumbEvent extends RRWebEvent
    implements JsonUnknown, JsonSerializable {
  public static final String EVENT_TAG = "breadcrumb";

  private @NotNull String tag;
  private double breadcrumbTimestamp;
  private @Nullable String breadcrumbType;
  private @Nullable String category;
  private @Nullable String message;
  private @Nullable SentryLevel level;
  private @Nullable Map<String, Object> data;
  // to support unknown json attributes with nesting, we have to have unknown map for each of the
  // nested object in json: { ..., "data": { ..., "payload": { ... } } }
  private @Nullable Map<String, Object> unknown;
  private @Nullable Map<String, Object> payloadUnknown;
  private @Nullable Map<String, Object> dataUnknown;

  public RRWebBreadcrumbEvent() {
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

  public double getBreadcrumbTimestamp() {
    return breadcrumbTimestamp;
  }

  public void setBreadcrumbTimestamp(final double breadcrumbTimestamp) {
    this.breadcrumbTimestamp = breadcrumbTimestamp;
  }

  @Nullable
  public String getBreadcrumbType() {
    return breadcrumbType;
  }

  public void setBreadcrumbType(final @Nullable String breadcrumbType) {
    this.breadcrumbType = breadcrumbType;
  }

  @Nullable
  public String getCategory() {
    return category;
  }

  public void setCategory(final @Nullable String category) {
    this.category = category;
  }

  @Nullable
  public String getMessage() {
    return message;
  }

  public void setMessage(final @Nullable String message) {
    this.message = message;
  }

  @Nullable
  public SentryLevel getLevel() {
    return level;
  }

  public void setLevel(final @Nullable SentryLevel level) {
    this.level = level;
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

  // rrweb uses camelCase hence the json keys are in camelCase here
  public static final class JsonKeys {
    public static final String DATA = "data";
    public static final String PAYLOAD = "payload";
    public static final String TIMESTAMP = "timestamp";
    public static final String TYPE = "type";
    public static final String CATEGORY = "category";
    public static final String MESSAGE = "message";
    public static final String LEVEL = "level";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    new RRWebEvent.Serializer().serialize(this, writer, logger);
    writer.name(JsonKeys.DATA);
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
    writer.name(JsonKeys.PAYLOAD);
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
    if (breadcrumbType != null) {
      writer.name(JsonKeys.TYPE).value(breadcrumbType);
    }
    writer.name(JsonKeys.TIMESTAMP).value(logger, BigDecimal.valueOf(breadcrumbTimestamp));
    if (category != null) {
      writer.name(JsonKeys.CATEGORY).value(category);
    }
    if (message != null) {
      writer.name(JsonKeys.MESSAGE).value(message);
    }
    if (level != null) {
      writer.name(JsonKeys.LEVEL).value(logger, level);
    }
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

  public static final class Deserializer implements JsonDeserializer<RRWebBreadcrumbEvent> {

    @Override
    public @NotNull RRWebBreadcrumbEvent deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      @Nullable Map<String, Object> unknown = null;

      final RRWebBreadcrumbEvent event = new RRWebBreadcrumbEvent();
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
        final @NotNull RRWebBreadcrumbEvent event,
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
        final @NotNull RRWebBreadcrumbEvent event,
        final @NotNull ObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      @Nullable Map<String, Object> payloadUnknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TYPE:
            event.breadcrumbType = reader.nextStringOrNull();
            break;
          case JsonKeys.TIMESTAMP:
            event.breadcrumbTimestamp = reader.nextDouble();
            break;
          case JsonKeys.CATEGORY:
            event.category = reader.nextStringOrNull();
            break;
          case JsonKeys.MESSAGE:
            event.message = reader.nextStringOrNull();
            break;
          case JsonKeys.LEVEL:
            try {
              event.level = new SentryLevel.Deserializer().deserialize(reader, logger);
            } catch (Exception exception) {
              if (logger.isEnabled(DEBUG)) {
                logger.log(SentryLevel.DEBUG, exception, "Error when deserializing SentryLevel");
              }
            }
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
