package io.sentry;

import io.sentry.vendor.gson.stream.JsonReader;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.util.Date;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonObjectReader extends JsonReader {

  public JsonObjectReader(Reader in) {
    super(in);
  }

  public @Nullable String nextStringOrNull() throws IOException {
    return peek() == JsonToken.NULL ? null : nextString();
  }

  public @Nullable Double nextDoubleOrNull() throws IOException {
    return peek() == JsonToken.NULL ? null : nextDouble();
  }

  public @Nullable Long nextLongOrNull() throws IOException {
    return peek() == JsonToken.NULL ? null : nextLong();
  }

  public @Nullable Integer nextIntegerOrNull() throws IOException {
    return peek() == JsonToken.NULL ? null : nextInt();
  }

  public @Nullable Boolean nextBooleanOrNull() throws IOException {
    return peek() == JsonToken.NULL ? null : nextBoolean();
  }

  public void nextUnknown(ILogger logger, Map<String, Object> unknown, String name) {
    try {
      unknown.put(name, nextObjectOrNull());
    } catch (Exception exception) {
      logger.log(SentryLevel.ERROR, exception, "Error deserializing unknown key: %s", name);
    }
  }

  public @Nullable Date nextDateOrNull(ILogger logger) throws IOException {
    if (peek() == JsonToken.NULL) {
      return null;
    }
    String dateString = nextString();
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
