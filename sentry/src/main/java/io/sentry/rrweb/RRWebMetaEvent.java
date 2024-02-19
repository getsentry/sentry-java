package io.sentry.rrweb;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RRWebMetaEvent extends RRWebEvent implements JsonUnknown, JsonSerializable {

  private @NotNull String href;
  private int height;
  private int width;
  private @Nullable Map<String, Object> unknown;

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
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      RRWebMetaEvent event = new RRWebMetaEvent();
      RRWebEvent.Deserializer baseEventDeserializer = new RRWebEvent.Deserializer();

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.DATA:
            deserializeData(event, reader, logger);
            break;
          default:
            baseEventDeserializer.deserializeValue(event, nextName, reader, logger);
            break;
        }
      }
      reader.endObject();
      return event;
    }

    private void deserializeData(
        final @NotNull RRWebMetaEvent event,
        final @NotNull JsonObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      Map<String, Object> unknown = null;

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
      event.setUnknown(unknown);
      reader.endObject();
    }
  }
}