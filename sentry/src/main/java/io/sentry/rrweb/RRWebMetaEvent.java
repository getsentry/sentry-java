package io.sentry.rrweb;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RRWebMetaEvent extends RRWebEvent implements JsonUnknown, JsonSerializable {

  private @NotNull String href;
  private int height;
  private int width;
  // to support unknown json attributes with nesting, we have to have unknown map for each of the
  // nested object in json: { ..., "data": { ... } }
  private @Nullable Map<String, Object> unknown;
  private @Nullable Map<String, Object> dataUnknown;

  public RRWebMetaEvent() {
    super(RRWebEventType.Meta);
    this.href = "";
  }

  @NotNull
  public String getHref() {
    return href;
  }

  public void setHref(final @NotNull String href) {
    this.href = href;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(final int height) {
    this.height = height;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(final int width) {
    this.width = width;
  }

  @Nullable
  public Map<String, Object> getDataUnknown() {
    return dataUnknown;
  }

  public void setDataUnknown(final @Nullable Map<String, Object> dataUnknown) {
    this.dataUnknown = dataUnknown;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    RRWebMetaEvent metaEvent = (RRWebMetaEvent) o;
    return height == metaEvent.height
        && width == metaEvent.width
        && Objects.equals(href, metaEvent.href);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), href, height, width);
  }

  public static final class JsonKeys {
    public static final String DATA = "data";
    public static final String HREF = "href";
    public static final String HEIGHT = "height";
    public static final String WIDTH = "width";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    new RRWebEvent.Serializer().serialize(this, writer, logger);
    writer.name(JsonKeys.DATA);
    serializeData(writer, logger);
    writer.endObject();
  }

  private void serializeData(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.HREF).value(href);
    writer.name(JsonKeys.HEIGHT).value(height);
    writer.name(JsonKeys.WIDTH).value(width);
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
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

  public static final class Deserializer implements JsonDeserializer<RRWebMetaEvent> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull RRWebMetaEvent deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      @Nullable Map<String, Object> unknown = null;
      final RRWebMetaEvent event = new RRWebMetaEvent();
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
        final @NotNull RRWebMetaEvent event,
        final @NotNull ObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      @Nullable Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.HREF:
            final String href = reader.nextStringOrNull();
            event.href = href == null ? "" : href;
            break;
          case JsonKeys.HEIGHT:
            final Integer height = reader.nextIntegerOrNull();
            event.height = height == null ? 0 : height;
            break;
          case JsonKeys.WIDTH:
            final Integer width = reader.nextIntegerOrNull();
            event.width = width == null ? 0 : width;
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
        }
      }
      event.setDataUnknown(unknown);
      reader.endObject();
    }
  }
}
