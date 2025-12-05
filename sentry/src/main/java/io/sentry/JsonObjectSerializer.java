package io.sentry;

import static io.sentry.util.JsonSerializationUtils.atomicIntegerArrayToList;
import static io.sentry.util.JsonSerializationUtils.calendarToMap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonObjectSerializer {

  public static final String OBJECT_PLACEHOLDER = "[OBJECT]";

  public final JsonReflectionObjectSerializer jsonReflectionObjectSerializer;

  public JsonObjectSerializer(int maxDepth) {
    jsonReflectionObjectSerializer = new JsonReflectionObjectSerializer(maxDepth);
  }

  public void serialize(
      @NotNull ObjectWriter writer, @NotNull ILogger logger, @Nullable Object object)
      throws IOException {
    if (object == null) {
      writer.nullValue();
    } else if (object instanceof Character) {
      writer.value(Character.toString((Character) object));
    } else if (object instanceof String) {
      writer.value((String) object);
    } else if (object instanceof Boolean) {
      writer.value((boolean) object);
    } else if (object instanceof Number) {
      writer.value((Number) object);
    } else if (object instanceof Date) {
      serializeDate(writer, logger, (Date) object);
    } else if (object instanceof TimeZone) {
      serializeTimeZone(writer, logger, (TimeZone) object);
    } else if (object instanceof JsonSerializable) {
      ((JsonSerializable) object).serialize(writer, logger);
    } else if (object instanceof Collection) {
      serializeCollection(writer, logger, (Collection<?>) object);
    } else if (object.getClass().isArray()) {
      serializeCollection(writer, logger, Arrays.asList((Object[]) object));
    } else if (object instanceof Map) {
      serializeMap(writer, logger, (Map<?, ?>) object);
    } else if (object instanceof Locale) {
      writer.value(object.toString());
    } else if (object instanceof AtomicIntegerArray) {
      serializeCollection(writer, logger, atomicIntegerArrayToList((AtomicIntegerArray) object));
    } else if (object instanceof AtomicBoolean) {
      writer.value(((AtomicBoolean) object).get());
    } else if (object instanceof URI) {
      writer.value(object.toString());
    } else if (object instanceof InetAddress) {
      writer.value(object.toString());
    } else if (object instanceof UUID) {
      writer.value(object.toString());
    } else if (object instanceof Currency) {
      writer.value(object.toString());
    } else if (object instanceof Calendar) {
      serializeMap(writer, logger, calendarToMap((Calendar) object));
    } else if (object instanceof JsonRawString) {
      writer.jsonValue(((JsonRawString) object).getValue());
    } else if (object.getClass().isEnum()) {
      writer.value(object.toString());
    } else {
      try {
        Object serializableObject = jsonReflectionObjectSerializer.serialize(object, logger);
        serialize(writer, logger, serializableObject);
      } catch (Exception exception) {
        logger.log(SentryLevel.ERROR, "Failed serializing unknown object.", exception);
        writer.value(OBJECT_PLACEHOLDER);
      }
    }
  }

  // Helper

  private void serializeDate(
      @NotNull ObjectWriter writer, @NotNull ILogger logger, @NotNull Date date)
      throws IOException {
    try {
      writer.value(DateUtils.getTimestamp(date));
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing Date", e);
      writer.nullValue(); // Fallback to setting null when date is malformed.
    }
  }

  private void serializeTimeZone(
      @NotNull ObjectWriter writer, @NotNull ILogger logger, @NotNull TimeZone timeZone)
      throws IOException {
    try {
      writer.value(timeZone.getID());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing TimeZone", e);
      writer.nullValue(); // Fallback.
    }
  }

  private void serializeCollection(
      @NotNull ObjectWriter writer, @NotNull ILogger logger, @NotNull Collection<?> collection)
      throws IOException {
    writer.beginArray();
    for (Object object : collection) {
      serialize(writer, logger, object);
    }
    writer.endArray();
  }

  private void serializeMap(
      @NotNull ObjectWriter writer, @NotNull ILogger logger, @NotNull Map<?, ?> map)
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
