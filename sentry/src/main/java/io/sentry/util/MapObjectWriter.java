package io.sentry.util;

import static io.sentry.util.JsonSerializationUtils.atomicIntegerArrayToList;
import static io.sentry.util.JsonSerializationUtils.calendarToMap;

import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.JsonSerializable;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
public final class MapObjectWriter implements ObjectWriter {

  final @NotNull Map<String, Object> root;
  /**
   * The stack for maintaining the hierarchy of a json-like data structure. Possible elements:
   *
   * <pre>
   * Map: A JSON like object
   * List: An array of values.
   * String: The name for the upcoming Map/List
   * </pre>
   *
   * E.g. consider the following stack:
   *
   * <pre>
   * | String ("lastName")
   * | Map (firstName="John")
   * </pre>
   *
   * Once .value("Doe") is called, the top-most stack-element ("lastName") is popped and the
   * key/value pair is stored in top-most stack element.
   *
   * <pre>
   * | Map (firstName="John", lastName="Doe")
   * </pre>
   */
  final @NotNull ArrayDeque<Object> stack;

  public MapObjectWriter(final @NotNull Map<String, Object> root) {
    this.root = root;
    stack = new ArrayDeque<>();
    stack.addLast(root);
  }

  @Override
  public MapObjectWriter name(final @NotNull String name) throws IOException {
    stack.add(name);
    return this;
  }

  @Override
  public MapObjectWriter value(final @NotNull ILogger logger, final @Nullable Object object)
      throws IOException {
    if (object == null) {
      nullValue();
    } else if (object instanceof Character) {
      value(Character.toString((Character) object));
    } else if (object instanceof String) {
      value((String) object);
    } else if (object instanceof Boolean) {
      value((boolean) object);
    } else if (object instanceof Number) {
      value((Number) object);
    } else if (object instanceof Date) {
      serializeDate(logger, (Date) object);
    } else if (object instanceof TimeZone) {
      serializeTimeZone(logger, (TimeZone) object);
    } else if (object instanceof JsonSerializable) {
      ((JsonSerializable) object).serialize(this, logger);
    } else if (object instanceof Collection) {
      serializeCollection(logger, (Collection<?>) object);
    } else if (object.getClass().isArray()) {
      serializeCollection(logger, Arrays.asList((Object[]) object));
    } else if (object instanceof Map) {
      serializeMap(logger, (Map<?, ?>) object);
    } else if (object instanceof Locale) {
      value(object.toString());
    } else if (object instanceof AtomicIntegerArray) {
      serializeCollection(logger, atomicIntegerArrayToList((AtomicIntegerArray) object));
    } else if (object instanceof AtomicBoolean) {
      value(((AtomicBoolean) object).get());
    } else if (object instanceof URI) {
      value(object.toString());
    } else if (object instanceof InetAddress) {
      value(object.toString());
    } else if (object instanceof UUID) {
      value(object.toString());
    } else if (object instanceof Currency) {
      value(object.toString());
    } else if (object instanceof Calendar) {
      serializeMap(logger, calendarToMap((Calendar) object));
    } else if (object.getClass().isEnum()) {
      value(object.toString());
    } else {
      logger.log(SentryLevel.WARNING, "Failed serializing unknown object.", object);
    }
    return this;
  }

  @Override
  public void setLenient(boolean lenient) {
    // no-op
  }

  @Override
  public MapObjectWriter beginArray() throws IOException {
    stack.add(new ArrayList<>());
    return this;
  }

  @Override
  public MapObjectWriter endArray() throws IOException {
    endObject();
    return this;
  }

  @Override
  public MapObjectWriter beginObject() throws IOException {
    stack.addLast(new HashMap<>());
    return this;
  }

  @Override
  public MapObjectWriter endObject() throws IOException {
    final Object value = stack.removeLast();
    postValue(value);
    return this;
  }

  @Override
  public MapObjectWriter value(final @Nullable String value) throws IOException {
    postValue(value);
    return this;
  }

  @Override
  public ObjectWriter jsonValue(@Nullable String value) throws IOException {
    // no-op
    return this;
  }

  @Override
  public MapObjectWriter nullValue() throws IOException {
    postValue((Object) null);
    return this;
  }

  @Override
  public MapObjectWriter value(final boolean value) throws IOException {
    postValue(value);
    return this;
  }

  @Override
  public MapObjectWriter value(final @Nullable Boolean value) throws IOException {
    postValue(value);
    return this;
  }

  @Override
  public MapObjectWriter value(final double value) throws IOException {
    postValue(value);
    return this;
  }

  @Override
  public MapObjectWriter value(final long value) throws IOException {
    postValue(value);
    return this;
  }

  @Override
  public MapObjectWriter value(final @Nullable Number value) throws IOException {
    postValue(value);
    return this;
  }

  private void serializeDate(final @NotNull ILogger logger, final @NotNull Date date)
      throws IOException {
    try {
      value(DateUtils.getTimestamp(date));
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing Date", e);
      nullValue(); // Fallback to setting null when date is malformed.
    }
  }

  private void serializeTimeZone(final @NotNull ILogger logger, final @NotNull TimeZone timeZone)
      throws IOException {
    try {
      value(timeZone.getID());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing TimeZone", e);
      nullValue(); // Fallback.
    }
  }

  private void serializeCollection(
      final @NotNull ILogger logger, final @NotNull Collection<?> collection) throws IOException {
    beginArray();
    for (Object object : collection) {
      value(logger, object);
    }
    endArray();
  }

  private void serializeMap(final @NotNull ILogger logger, final @NotNull Map<?, ?> map)
      throws IOException {
    beginObject();
    for (Object key : map.keySet()) {
      if (key instanceof String) {
        name((String) key);
        value(logger, map.get(key));
      }
    }
    endObject();
  }

  @SuppressWarnings("unchecked")
  private void postValue(final @Nullable Object value) {
    final Object topStackElement = stack.peekLast();
    if (topStackElement instanceof List) {
      // if top stack element is an array, value is an element within the array
      ((List<Object>) topStackElement).add(value);
    } else if (topStackElement instanceof String) {
      // if top stack element is a String, it's the key for the value
      // -> add both (key, value) to underlying map
      final String key = (String) stack.removeLast();
      peekObject().put(key, value);
    } else {
      throw new IllegalStateException("Invalid stack state, expected array or string on top");
    }
  }

  @SuppressWarnings("unchecked")
  private @NotNull Map<String, Object> peekObject() {
    final @Nullable Object item = stack.peekLast();
    if (item == null) {
      throw new IllegalStateException("Stack is empty.");
    } else if ((item instanceof Map)) {
      return (Map<String, Object>) item;
    }
    throw new IllegalStateException("Stack element is not a Map.");
  }
}
