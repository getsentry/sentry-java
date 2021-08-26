package io.sentry;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonObjectSerializer {

  public static final String OBJECT_PLACEHOLDER = "[OBJECT]";

  public void serialize(
      @NotNull JsonObjectWriter writer, @NotNull ILogger logger, @Nullable Object object)
      throws IOException {
    if (object == null) {
      writer.nullValue();
    } else if (object instanceof String) {
      writer.value((String) object);
    } else if (object instanceof Boolean) {
      writer.value((boolean) object);
    } else if (object instanceof Number) {
      writer.value((Number) object);
    } else if (object instanceof Date) {
      serializeDate(writer, logger, (Date) object);
    } else if (object instanceof Collection) {
      serializeCollection(writer, logger, (Collection<?>) object);
    } else if (object.getClass().isArray()) {
      serializeCollection(writer, logger, Arrays.asList((Object[]) object));
    } else if (object instanceof Map) {
      serializeMap(writer, logger, (Map<?, ?>) object);
    } else if (object instanceof JsonSerializable) {
      ((JsonSerializable) object).serialize(writer, logger);
    } else {
      // TODO: Use reflection to support object serialization.
      writer.value(OBJECT_PLACEHOLDER);
    }
  }

  // Helper

  private void serializeDate(
      @NotNull JsonObjectWriter writer, @NotNull ILogger logger, @NotNull Date date)
      throws IOException {
    try {
      writer.value(DateUtils.getTimestamp(date));
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing Date", e);
      writer.nullValue(); // Fallback to setting null when date is malformed.
    }
  }

  private void serializeCollection(
      @NotNull JsonObjectWriter writer, @NotNull ILogger logger, @NotNull Collection<?> collection)
      throws IOException {
    writer.beginArray();
    for (Object object : collection) {
      serialize(writer, logger, object);
    }
    writer.endArray();
  }

  private void serializeMap(
      @NotNull JsonObjectWriter writer, @NotNull ILogger logger, @NotNull Map<?, ?> map)
      throws IOException {
    writer.beginObject();
    for (Object key : map.keySet()) {
      if (key instanceof String) {
        writer.name((String) key);
        serialize(writer, logger, map.get(key));
      }
    }
    writer.endObject();
  }
}
