package io.sentry.protocol.profiling;

import io.sentry.JsonDeserializer;
import io.sentry.ObjectReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.sentry.ILogger;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectWriter;

public final class JfrSample implements JsonUnknown, JsonSerializable {

  public double timestamp; // Unix timestamp in seconds with microsecond precision

  public int stackId;

  public @Nullable String threadId;

  public static final class JsonKeys {
    public static final String TIMESTAMP = "timestamp";
    public static final String STACK_ID = "stackId";
    public static final String THREAD_ID = "threadId";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    writer.name(JfrSample.JsonKeys.TIMESTAMP).value(logger, timestamp);
    writer.name(JfrSample.JsonKeys.STACK_ID).value(logger, stackId);

    if(threadId != null) {
      writer.name(JfrFrame.JsonKeys.FILENAME).value(logger, threadId);
    }

    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return new HashMap<>();
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {

  }

  public static final class Deserializer implements JsonDeserializer<JfrSample> {

    @Override
    public @NotNull JfrSample deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      JfrSample data = new JfrSample();
      return data;
    }
  }
}
