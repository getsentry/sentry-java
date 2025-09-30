package io.sentry.protocol.profiling;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryThreadMetadata implements JsonUnknown, JsonSerializable {
  private @Nullable String name;

  private int priority;

  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public static final class JsonKeys {
    public static final String NAME = "name";
    public static final String PRIORITY = "priority";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    if (name != null) {
      writer.name(JsonKeys.NAME).value(logger, name);
    }
    writer.name(JsonKeys.PRIORITY).value(logger, priority);
    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return new HashMap<>();
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {}

  public static final class Deserializer implements JsonDeserializer<SentryThreadMetadata> {

    @Override
    public @NotNull SentryThreadMetadata deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      SentryThreadMetadata data = new SentryThreadMetadata();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.NAME:
            data.name = reader.nextStringOrNull();
            break;
          case JsonKeys.PRIORITY:
            data.priority = reader.nextInt();
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      data.setUnknown(unknown);
      reader.endObject();
      return data;
    }
  }
}
