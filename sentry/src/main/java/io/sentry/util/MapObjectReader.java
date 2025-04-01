package io.sentry.util;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.ObjectReader;
import io.sentry.SentryLevel;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unchecked")
public final class MapObjectReader implements ObjectReader {

  private final Deque<Map.Entry<String, Object>> stack;

  public MapObjectReader(final Map<String, Object> root) {
    stack = new ArrayDeque<>();
    stack.addLast(new AbstractMap.SimpleEntry<>(null, root));
  }

  @Override
  public void nextUnknown(
      final @NotNull ILogger logger, final Map<String, Object> unknown, final String name) {
    try {
      unknown.put(name, nextObjectOrNull());
    } catch (Exception exception) {
      if (logger.isEnabled(SentryLevel.ERROR)) {
        logger.log(SentryLevel.ERROR, exception, "Error deserializing unknown key: %s", name);
      }
    }
  }

  @Nullable
  @Override
  public <T> List<T> nextListOrNull(
      final @NotNull ILogger logger, final @NotNull JsonDeserializer<T> deserializer)
      throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    try {
      beginArray();
      List<T> list = new ArrayList<>();
      if (hasNext()) {
        do {
          try {
            list.add(deserializer.deserialize(this, logger));
          } catch (Exception e) {
            if (logger.isEnabled(SentryLevel.WARNING)) {
              logger.log(SentryLevel.WARNING, "Failed to deserialize object in list.", e);
            }
          }
        } while (peek() == JsonToken.BEGIN_OBJECT);
      }
      endArray();
      return list;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Nullable
  @Override
  public <T> Map<String, T> nextMapOrNull(
      final @NotNull ILogger logger, final @NotNull JsonDeserializer<T> deserializer)
      throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    try {
      beginObject();
      Map<String, T> map = new HashMap<>();
      if (hasNext()) {
        do {
          try {
            String key = nextName();
            map.put(key, deserializer.deserialize(this, logger));
          } catch (Exception e) {
            if (logger.isEnabled(SentryLevel.WARNING)) {
              logger.log(SentryLevel.WARNING, "Failed to deserialize object in map.", e);
            }
          }
        } while (peek() == JsonToken.BEGIN_OBJECT || peek() == JsonToken.NAME);
      }
      endObject();
      return map;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public @Nullable <T> Map<String, List<T>> nextMapOfListOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    final @NotNull Map<String, List<T>> result = new HashMap<>();

    try {
      beginObject();
      if (hasNext()) {
        do {
          final @NotNull String key = nextName();
          final @Nullable List<T> list = nextListOrNull(logger, deserializer);
          if (list != null) {
            result.put(key, list);
          }
        } while (peek() == JsonToken.BEGIN_OBJECT || peek() == JsonToken.NAME);
      }
      endObject();
      return result;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Nullable
  @Override
  public <T> T nextOrNull(
      final @NotNull ILogger logger, final @NotNull JsonDeserializer<T> deserializer)
      throws Exception {
    return nextValueOrNull(logger, deserializer);
  }

  @Nullable
  @Override
  public Date nextDateOrNull(final @NotNull ILogger logger) throws IOException {
    final String dateString = nextStringOrNull();
    return ObjectReader.dateOrNull(dateString, logger);
  }

  @Nullable
  @Override
  public TimeZone nextTimeZoneOrNull(final @NotNull ILogger logger) throws IOException {
    final String timeZoneId = nextStringOrNull();
    return timeZoneId != null ? TimeZone.getTimeZone(timeZoneId) : null;
  }

  @Nullable
  @Override
  public Object nextObjectOrNull() throws IOException {
    return nextValueOrNull();
  }

  @NotNull
  @Override
  public JsonToken peek() throws IOException {
    if (stack.isEmpty()) {
      return JsonToken.END_DOCUMENT;
    }

    final Map.Entry<String, Object> currentEntry = stack.peekLast();
    if (currentEntry == null) {
      return JsonToken.END_DOCUMENT;
    }

    if (currentEntry.getKey() != null) {
      return JsonToken.NAME;
    }

    final Object value = currentEntry.getValue();

    if (value instanceof Map) {
      return JsonToken.BEGIN_OBJECT;
    } else if (value instanceof List) {
      return JsonToken.BEGIN_ARRAY;
    } else if (value instanceof String) {
      return JsonToken.STRING;
    } else if (value instanceof Number) {
      return JsonToken.NUMBER;
    } else if (value instanceof Boolean) {
      return JsonToken.BOOLEAN;
    } else if (value instanceof JsonToken) {
      return (JsonToken) value;
    } else {
      return JsonToken.END_DOCUMENT;
    }
  }

  @NotNull
  @Override
  public String nextName() throws IOException {
    final Map.Entry<String, Object> currentEntry = stack.peekLast();
    if (currentEntry != null && currentEntry.getKey() != null) {
      return currentEntry.getKey();
    }
    throw new IOException("Expected a name but was " + peek());
  }

  @Override
  public void beginObject() throws IOException {
    final Map.Entry<String, Object> currentEntry = stack.removeLast();
    if (currentEntry == null) {
      throw new IOException("No more entries");
    }
    final Object value = currentEntry.getValue();
    if (value instanceof Map) {
      // insert a dummy entry to indicate end of an object
      stack.addLast(new AbstractMap.SimpleEntry<>(null, JsonToken.END_OBJECT));
      // extract map entries onto the stack
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
        stack.addLast(entry);
      }
    } else {
      throw new IOException("Current token is not an object");
    }
  }

  @Override
  public void endObject() throws IOException {
    if (stack.size() > 1) {
      stack.removeLast(); // Pop the current map from stack
    }
  }

  @Override
  public void beginArray() throws IOException {
    final Map.Entry<String, Object> currentEntry = stack.removeLast();
    if (currentEntry == null) {
      throw new IOException("No more entries");
    }
    final Object value = currentEntry.getValue();
    if (value instanceof List) {
      // insert a dummy entry to indicate end of an object
      stack.addLast(new AbstractMap.SimpleEntry<>(null, JsonToken.END_ARRAY));
      // extract map entries onto the stack
      for (int i = ((List<?>) value).size() - 1; i >= 0; i--) {
        final Object entry = ((List<?>) value).get(i);
        stack.addLast(new AbstractMap.SimpleEntry<>(null, entry));
      }
    } else {
      throw new IOException("Current token is not an object");
    }
  }

  @Override
  public void endArray() throws IOException {
    if (stack.size() > 1) {
      stack.removeLast(); // Pop the current array from stack
    }
  }

  @Override
  public boolean hasNext() throws IOException {
    return !stack.isEmpty();
  }

  @Override
  public int nextInt() throws IOException {
    final Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).intValue();
    } else {
      throw new IOException("Expected int");
    }
  }

  @Nullable
  @Override
  public Integer nextIntegerOrNull() throws IOException {
    final Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return null;
  }

  @Override
  public long nextLong() throws IOException {
    final Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).longValue();
    } else {
      throw new IOException("Expected long");
    }
  }

  @Nullable
  @Override
  public Long nextLongOrNull() throws IOException {
    final Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return null;
  }

  @Override
  public String nextString() throws IOException {
    final String value = nextValueOrNull();
    if (value != null) {
      return value;
    } else {
      throw new IOException("Expected string");
    }
  }

  @Nullable
  @Override
  public String nextStringOrNull() throws IOException {
    return nextValueOrNull();
  }

  @Override
  public boolean nextBoolean() throws IOException {
    final Boolean value = nextValueOrNull();
    if (value != null) {
      return value;
    } else {
      throw new IOException("Expected boolean");
    }
  }

  @Nullable
  @Override
  public Boolean nextBooleanOrNull() throws IOException {
    return nextValueOrNull();
  }

  @Override
  public double nextDouble() throws IOException {
    final Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    } else {
      throw new IOException("Expected double");
    }
  }

  @Nullable
  @Override
  public Double nextDoubleOrNull() throws IOException {
    final Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    return null;
  }

  @Nullable
  @Override
  public Float nextFloatOrNull() throws IOException {
    final Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).floatValue();
    }
    return null;
  }

  @Override
  public float nextFloat() throws IOException {
    final Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).floatValue();
    } else {
      throw new IOException("Expected float");
    }
  }

  @Override
  public void nextNull() throws IOException {
    final Object value = nextValueOrNull();
    if (value != null) {
      throw new IOException("Expected null but was " + peek());
    }
  }

  @Override
  public void setLenient(final boolean lenient) {}

  @Override
  public void skipValue() throws IOException {}

  @SuppressWarnings("TypeParameterUnusedInFormals")
  @Nullable
  private <T> T nextValueOrNull() throws IOException {
    try {
      return nextValueOrNull(null, null);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @SuppressWarnings("TypeParameterUnusedInFormals")
  @Nullable
  private <T> T nextValueOrNull(
      final @Nullable ILogger logger, final @Nullable JsonDeserializer<T> deserializer)
      throws Exception {
    final Map.Entry<String, Object> currentEntry = stack.peekLast();
    if (currentEntry == null) {
      return null;
    }
    final T value = (T) currentEntry.getValue();
    if (deserializer != null && logger != null) {
      return deserializer.deserialize(this, logger);
    }
    stack.removeLast();
    return value;
  }

  @Override
  public void close() throws IOException {
    stack.clear();
  }
}
