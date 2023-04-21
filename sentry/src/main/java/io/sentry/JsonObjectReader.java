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
public final class JsonObjectReader extends JsonReader {

  public JsonObjectReader(Reader in) {
    super(in);
  }

  public @Nullable String nextStringOrNull() throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextString();
  }

  public @Nullable Double nextDoubleOrNull() throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextDouble();
  }

  public @Nullable Float nextFloatOrNull() throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextFloat();
  }

  public @NotNull Float nextFloat() throws IOException {
    return (float) nextDouble();
  }

  public @Nullable Long nextLongOrNull() throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextLong();
  }

  public @Nullable Integer nextIntegerOrNull() throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextInt();
  }

  public @Nullable Boolean nextBooleanOrNull() throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextBoolean();
  }

  public void nextUnknown(ILogger logger, Map<String, Object> unknown, String name) {
    try {
      unknown.put(name, nextObjectOrNull());
    } catch (Exception exception) {
      logger.log(SentryLevel.ERROR, exception, "Error deserializing unknown key: %s", name);
    }
  }

  public <T> @Nullable List<T> nextList(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    beginArray();
    List<T> list = new ArrayList<>();
    do {
      try {
        list.add(deserializer.deserialize(this, logger));
      } catch (Exception e) {
        logger.log(SentryLevel.ERROR, "Failed to deserialize object in list.", e);
      }
    } while (peek() == JsonToken.BEGIN_OBJECT);
    endArray();
    return list;
  }

  public <T> @Nullable Map<String, T> nextMapOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    beginObject();
    Map<String, T> map = new HashMap<>();
    do {
      try {
        String key = nextName();
        map.put(key, deserializer.deserialize(this, logger));
      } catch (Exception e) {
        logger.log(SentryLevel.ERROR, "Failed to deserialize object in map.", e);
      }
    } while (peek() == JsonToken.BEGIN_OBJECT || peek() == JsonToken.NAME);

    endObject();
    return map;
  }

  public <T> @Nullable T nextOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws Exception {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return deserializer.deserialize(this, logger);
  }

  public @Nullable Date nextDateOrNull(ILogger logger) throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return JsonObjectReader.dateOrNull(nextString(), logger);
  }

  public static @Nullable Date dateOrNull(@Nullable String dateString, ILogger logger) {
    if (dateString == null) {
      return null;
    }
    try {
      return DateUtils.getDateTime(dateString);
    } catch (Exception e) {
      logger.log(
          SentryLevel.DEBUG,
          "Error when deserializing UTC timestamp format, it might be millis timestamp format.",
          e);
    }
    try {
      return DateUtils.getDateTimeWithMillisPrecision(dateString);
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing millis timestamp format.", e);
    }
    return null;
  }

  public @Nullable TimeZone nextTimeZoneOrNull(ILogger logger) throws IOException {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    try {
      return TimeZone.getTimeZone(nextString());
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
  public @Nullable Object nextObjectOrNull() throws IOException {
    return new JsonObjectDeserializer().deserialize(this);
  }
}
