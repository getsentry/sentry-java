package io.sentry;

import io.sentry.vendor.gson.stream.JsonReader;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonObjectReader implements ObjectReader {

  private final @NotNull JsonReader jsonReader;
  private int depth = 0;

  public JsonObjectReader(Reader in) {
    this.jsonReader = new JsonReader(in);
  }

  @Override
  public @Nullable String nextStringOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    return jsonReader.nextString();
  }

  @Override
  public @Nullable Double nextDoubleOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    return jsonReader.nextDouble();
  }

  @Override
  public @Nullable Float nextFloatOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    return nextFloat();
  }

  @Override
  public float nextFloat() throws IOException {
    return (float) jsonReader.nextDouble();
  }

  @Override
  public @Nullable Long nextLongOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    return jsonReader.nextLong();
  }

  @Override
  public @Nullable Integer nextIntegerOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    return jsonReader.nextInt();
  }

  @Override
  public @Nullable Boolean nextBooleanOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    return jsonReader.nextBoolean();
  }

  @Override
  public void nextUnknown(ILogger logger, Map<String, Object> unknown, String name) {
    final int startDepth = depth;
    JsonToken startToken = JsonToken.END_DOCUMENT;
    try {
      startToken = peek();
      unknown.put(name, nextObjectOrNull());
    } catch (Exception exception) {
      logger.log(SentryLevel.ERROR, exception, "Error deserializing unknown key: %s", name);
      try {
        recoverValue(startDepth, startToken);
      } catch (Exception ignored) {
        // stream is unrecoverable
      }
    }
  }

  @Override
  public <T> @Nullable List<T> nextListOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    beginArray();
    List<T> list = new ArrayList<>();
    if (jsonReader.hasNext()) {
      do {
        final int startDepth = depth;
        final JsonToken startToken = peek();
        try {
          list.add(deserializer.deserialize(this, logger));
        } catch (Exception e) {
          if (!recoverAfterValueFailure(
              logger,
              e,
              "Failed to deserialize object in list.",
              "Stream unrecoverable, aborting list deserialization.",
              startDepth,
              startToken)) {
            break;
          }
        }
      } while (jsonReader.peek() == JsonToken.BEGIN_OBJECT);
    }
    endArray();
    return list;
  }

  @Override
  public <T> @Nullable Map<String, T> nextMapOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    beginObject();
    Map<String, T> map = new HashMap<>();
    if (jsonReader.hasNext()) {
      do {
        final String key = jsonReader.nextName();
        final int startDepth = depth;
        final JsonToken startToken = peek();
        try {
          map.put(key, deserializer.deserialize(this, logger));
        } catch (Exception e) {
          if (!recoverAfterValueFailure(
              logger,
              e,
              "Failed to deserialize object in map.",
              "Stream unrecoverable, aborting map deserialization.",
              startDepth,
              startToken)) {
            break;
          }
        }
      } while (jsonReader.peek() == JsonToken.BEGIN_OBJECT || jsonReader.peek() == JsonToken.NAME);
    }

    endObject();
    return map;
  }

  @Override
  public <T> @Nullable Map<String, List<T>> nextMapOfListOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException {

    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    final @NotNull Map<String, List<T>> result = new HashMap<>();

    beginObject();
    if (hasNext()) {
      do {
        final @NotNull String key = nextName();
        final int startDepth = depth;
        final JsonToken startToken = peek();
        try {
          final @Nullable List<T> list = nextListOrNull(logger, deserializer);
          if (list != null) {
            result.put(key, list);
          }
        } catch (Exception e) {
          if (!recoverAfterValueFailure(
              logger,
              e,
              "Failed to deserialize list in map.",
              "Stream unrecoverable, aborting map-of-lists deserialization.",
              startDepth,
              startToken)) {
            break;
          }
        }
      } while (peek() == JsonToken.BEGIN_OBJECT || peek() == JsonToken.NAME);
    }
    endObject();

    return result;
  }

  @Override
  public <T> @Nullable T nextOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws Exception {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    return deserializer.deserialize(this, logger);
  }

  @Override
  public @Nullable Date nextDateOrNull(ILogger logger) throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    return ObjectReader.dateOrNull(jsonReader.nextString(), logger);
  }

  @Override
  public @Nullable TimeZone nextTimeZoneOrNull(ILogger logger) throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    }
    try {
      return TimeZone.getTimeZone(jsonReader.nextString());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing TimeZone", e);
    }
    return null;
  }

  /**
   * Decodes JSON into Java primitives/objects (null, boolean, int, long, double, String, Map, List)
   * with full nesting support. To be used at the root level or after calling `nextName()`.
   *
   * @return The deserialized object from json.
   */
  @Override
  public @Nullable Object nextObjectOrNull() throws IOException {
    return new JsonObjectDeserializer().deserialize(this);
  }

  @Override
  public @NotNull JsonToken peek() throws IOException {
    return jsonReader.peek();
  }

  @Override
  public @NotNull String nextName() throws IOException {
    return jsonReader.nextName();
  }

  @Override
  public void beginObject() throws IOException {
    jsonReader.beginObject();
    depth++;
  }

  @Override
  public void endObject() throws IOException {
    jsonReader.endObject();
    depth--;
  }

  @Override
  public void beginArray() throws IOException {
    jsonReader.beginArray();
    depth++;
  }

  @Override
  public void endArray() throws IOException {
    jsonReader.endArray();
    depth--;
  }

  @Override
  public boolean hasNext() throws IOException {
    return jsonReader.hasNext();
  }

  @Override
  public int nextInt() throws IOException {
    return jsonReader.nextInt();
  }

  @Override
  public long nextLong() throws IOException {
    return jsonReader.nextLong();
  }

  @Override
  public String nextString() throws IOException {
    return jsonReader.nextString();
  }

  @Override
  public boolean nextBoolean() throws IOException {
    return jsonReader.nextBoolean();
  }

  @Override
  public double nextDouble() throws IOException {
    return jsonReader.nextDouble();
  }

  @Override
  public void nextNull() throws IOException {
    jsonReader.nextNull();
  }

  @Override
  public void setLenient(boolean lenient) {
    jsonReader.setLenient(lenient);
  }

  @Override
  public void skipValue() throws IOException {
    jsonReader.skipValue();
  }

  private boolean recoverAfterValueFailure(
      final @NotNull ILogger logger,
      final @NotNull Exception error,
      final @NotNull String warningMessage,
      final @NotNull String unrecoverableMessage,
      final int startDepth,
      final @NotNull JsonToken startToken) {
    logger.log(SentryLevel.WARNING, warningMessage, error);
    try {
      recoverValue(startDepth, startToken);
      return true;
    } catch (Exception recoveryException) {
      logger.log(SentryLevel.ERROR, unrecoverableMessage, recoveryException);
      return false;
    }
  }

  private void recoverValue(final int startDepth, final @NotNull JsonToken startToken)
      throws IOException {
    final boolean enteredNestedValue = depth > startDepth;
    while (depth > startDepth) {
      final JsonToken token = peek();
      if (token == JsonToken.END_OBJECT) {
        endObject();
      } else if (token == JsonToken.END_ARRAY) {
        endArray();
      } else {
        skipValue();
      }
    }

    if (!enteredNestedValue && peek() == startToken) {
      skipValue();
    }
  }

  @Override
  public void close() throws IOException {
    jsonReader.close();
  }
}
