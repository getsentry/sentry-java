package io.sentry.protocol.profiling;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ThreadMetadata implements JsonUnknown, JsonSerializable {
  public @Nullable String name; // e.g., "com.example.MyClass.myMethod"

  public int priority; // e.g., "com.example" (package name)

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
  public void setUnknown(@Nullable Map<String, Object> unknown) {

  }

  public static final class Deserializer implements JsonDeserializer<ThreadMetadata> {

    @Override
    public @NotNull ThreadMetadata deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      ThreadMetadata data = new ThreadMetadata();
      return data;
    }
  }
}

