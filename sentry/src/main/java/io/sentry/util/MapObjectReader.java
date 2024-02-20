package io.sentry.util;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.ObjectReader;
import io.sentry.SentryLevel;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
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
  public void nextUnknown(final @NotNull ILogger logger, Map<String, Object> unknown, String name) {
    try {
      unknown.put(name, nextObjectOrNull());
    } catch (Exception exception) {
      logger.log(SentryLevel.ERROR, exception, "Error deserializing unknown key: %s", name);
    }
  }

  @Nullable
  @Override
  public <T> List<T> nextListOrNull(
      final @NotNull ILogger logger, final @NotNull JsonDeserializer<T> deserializer)
      throws IOException {
    return nextValueOrNull();
  }

  @Nullable
  @Override
  public <T> Map<String, T> nextMapOrNull(
      final @NotNull ILogger logger, final @NotNull JsonDeserializer<T> deserializer)
      throws IOException {
    return nextValueOrNull();
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
    String dateString = nextStringOrNull();
    return ObjectReader.dateOrNull(dateString, logger);
  }

  @Nullable
  @Override
  public TimeZone nextTimeZoneOrNull(final @NotNull ILogger logger) throws IOException {
    String timeZoneId = nextStringOrNull();
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

    Map.Entry<String, Object> currentEntry = stack.peekLast();
    if (currentEntry == null) {
      return JsonToken.END_DOCUMENT;
    }

    if (currentEntry.getKey() != null) {
      return JsonToken.NAME;
    }

    Object value = currentEntry.getValue();

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
    Map.Entry<String, Object> currentEntry = stack.peekLast();
    if (currentEntry != null && currentEntry.getKey() != null) {
      return currentEntry.getKey();
    }
    throw new IOException("Expected a name but was " + peek());
  }

  @Override
  public void beginObject() throws IOException {
    Map.Entry<String, Object> currentEntry = stack.removeLast();
    if (currentEntry == null) {
      throw new IOException("No more entries");
    }
    Object value = currentEntry.getValue();
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
    Map.Entry<String, Object> currentEntry = stack.removeLast();
    if (currentEntry == null) {
      throw new IOException("No more entries");
    }
    Object value = currentEntry.getValue();
    if (value instanceof List) {
      // insert a dummy entry to indicate end of an object
      stack.addLast(new AbstractMap.SimpleEntry<>(null, JsonToken.END_ARRAY));
      // extract map entries onto the stack
      for (Object entry : (List<?>) value) {
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
    Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).intValue();
    } else {
      throw new IOException("Expected int");
    }
  }

  @Nullable
  @Override
  public Integer nextIntegerOrNull() throws IOException {
    Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return null;
  }

  @Override
  public long nextLong() throws IOException {
    Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).longValue();
    } else {
      throw new IOException("Expected long");
    }
  }

  @Nullable
  @Override
  public Long nextLongOrNull() throws IOException {
    Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return null;
  }

  @Override
  public String nextString() throws IOException {
    String value = nextValueOrNull();
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
    Boolean value = nextValueOrNull();
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
    Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    } else {
      throw new IOException("Expected double");
    }
  }

  @Nullable
  @Override
  public Double nextDoubleOrNull() throws IOException {
    Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    return null;
  }

  @Nullable
  @Override
  public Float nextFloatOrNull() throws IOException {
    Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).floatValue();
    }
    return null;
  }

  @Override
  public float nextFloat() throws IOException {
    Object value = nextValueOrNull();
    if (value instanceof Number) {
      return ((Number) value).floatValue();
    } else {
      throw new IOException("Expected float");
    }
  }

  @Override
  public void nextNull() throws IOException {
    nextValueOrNull();
  }

  @Override
  public void setLenient(boolean lenient) {}

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
    Map.Entry<String, Object> currentEntry = stack.peekLast();
    if (currentEntry == null) {
      return null;
    }
    T value = (T) currentEntry.getValue();
    if (deserializer != null && logger != null) {
      return deserializer.deserialize(this, logger);
    } else if (value instanceof List) {
      List<Object> list = (List<Object>) value;
      if (!list.isEmpty()) {
        T next = (T) list.remove(0);
        if (next instanceof Map) {
          stack.addLast(new AbstractMap.SimpleEntry<>(null, next));
        }
        return next;
      }
    } else if (value instanceof Map) {
      stack.addLast(new AbstractMap.SimpleEntry<>(null, value));
      return value;
    }
    stack.removeLast();
    return value;
  }

  @Override
  public void close() throws IOException {
    stack.clear();
  }
}
