package io.sentry.protocol.profiling;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JfrSample implements JsonUnknown, JsonSerializable {

  public double timestamp; // Unix timestamp in seconds with microsecond precision

  public int stackId;

  public @Nullable String threadId;

  public static final class JsonKeys {
    public static final String TIMESTAMP = "timestamp";
    public static final String STACK_ID = "stack_id";
    public static final String THREAD_ID = "thread_id";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TIMESTAMP).value(logger, timestamp);
    writer.name(JsonKeys.STACK_ID).value(logger, stackId);

    if (threadId != null) {
      writer.name(JsonKeys.THREAD_ID).value(logger, threadId);
    }

    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return new HashMap<>();
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {}

  public static final class Deserializer implements JsonDeserializer<JfrSample> {

    @Override
    public @NotNull JfrSample deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      JfrSample data = new JfrSample();
      return data;
    }
  }
}
