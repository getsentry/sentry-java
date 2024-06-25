package io.sentry;

import io.sentry.vendor.gson.stream.JsonToken;
import java.io.Closeable;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ObjectReader extends Closeable {
  static @Nullable Date dateOrNull(
      final @Nullable String dateString, final @NotNull ILogger logger) {
    if (dateString == null) {
      return null;
    }
    try {
      return DateUtils.getDateTime(dateString);
    } catch (Exception ignored) {
      try {
        return DateUtils.getDateTimeWithMillisPrecision(dateString);
      } catch (Exception e) {
        logger.log(SentryLevel.ERROR, "Error when deserializing millis timestamp format.", e);
      }
    }
    return null;
  }

  void nextUnknown(ILogger logger, Map<String, Object> unknown, String name);

  <T> @Nullable List<T> nextListOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException;

  <T> @Nullable Map<String, T> nextMapOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException;

  <T> @Nullable Map<String, List<T>> nextMapOfListOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException;

  <T> @Nullable T nextOrNull(@NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer)
      throws Exception;

  @Nullable
  Date nextDateOrNull(ILogger logger) throws IOException;

  @Nullable
  TimeZone nextTimeZoneOrNull(ILogger logger) throws IOException;

  @Nullable
  Object nextObjectOrNull() throws IOException;

  @NotNull
  JsonToken peek() throws IOException;

  @NotNull
  String nextName() throws IOException;

  void beginObject() throws IOException;

  void endObject() throws IOException;

  void beginArray() throws IOException;

  void endArray() throws IOException;

  boolean hasNext() throws IOException;

  int nextInt() throws IOException;

  @Nullable
  Integer nextIntegerOrNull() throws IOException;

  long nextLong() throws IOException;

  @Nullable
  Long nextLongOrNull() throws IOException;

  String nextString() throws IOException;

  @Nullable
  String nextStringOrNull() throws IOException;

  boolean nextBoolean() throws IOException;

  @Nullable
  Boolean nextBooleanOrNull() throws IOException;

  double nextDouble() throws IOException;

  @Nullable
  Double nextDoubleOrNull() throws IOException;

  float nextFloat() throws IOException;

  @Nullable
  Float nextFloatOrNull() throws IOException;

  void nextNull() throws IOException;

  void setLenient(boolean lenient);

  void skipValue() throws IOException;
}
