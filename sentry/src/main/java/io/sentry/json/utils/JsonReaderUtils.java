package io.sentry.json.utils;

import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import io.sentry.json.stream.JsonReader;
import io.sentry.json.stream.JsonToken;

public final class JsonReaderUtils {

  public static @Nullable  String nextStringOrNull(JsonReader reader) throws IOException {
    return reader.peek() == JsonToken.NULL ? null : reader.nextString();
  }

  public static @Nullable Double nextDoubleOrNull(JsonReader reader) throws IOException {
    return reader.peek() == JsonToken.NULL ? null : reader.nextDouble();
  }

  public static @Nullable Long nextLongOrNull(JsonReader reader) throws IOException {
    return reader.peek() == JsonToken.NULL ? null : reader.nextLong();
  }

  public static @Nullable Integer nextIntegerOrNull(JsonReader reader) throws IOException {
    return reader.peek() == JsonToken.NULL ? null : reader.nextInt();
  }

  public static @Nullable Boolean nextBooleanOrNull(JsonReader reader) throws IOException {
    return reader.peek() == JsonToken.NULL ? null : reader.nextBoolean();
  }
}
