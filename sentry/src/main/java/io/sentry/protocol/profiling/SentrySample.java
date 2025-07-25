package io.sentry.protocol.profiling;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentrySample implements JsonUnknown, JsonSerializable {

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

    writer.name(JsonKeys.TIMESTAMP).value(logger, doubleToBigDecimal(timestamp));
    writer.name(JsonKeys.STACK_ID).value(logger, stackId);

    if (threadId != null) {
      writer.name(JsonKeys.THREAD_ID).value(logger, threadId);
    }

    writer.endObject();
  }

  private @NotNull BigDecimal doubleToBigDecimal(final @NotNull Double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.DOWN);
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return new HashMap<>();
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {}

  public static final class Deserializer implements JsonDeserializer<SentrySample> {

    @Override
    public @NotNull SentrySample deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      SentrySample data = new SentrySample();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TIMESTAMP:
            data.timestamp = reader.nextDouble();
            break;
          case JsonKeys.STACK_ID:
            data.stackId = reader.nextInt();
            break;
          case JsonKeys.THREAD_ID:
            data.threadId = reader.nextStringOrNull();
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
