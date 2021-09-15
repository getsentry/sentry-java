package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MeasurementValue implements JsonSerializable {
  @SuppressWarnings("UnusedVariable")
  private final float value;

  public MeasurementValue(final float value) {
    this.value = value;
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String VALUE = "value";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.VALUE).value((double) value);
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<MeasurementValue> {
    @Override
    public @NotNull MeasurementValue deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      reader.nextName();
      MeasurementValue measurementValue = new MeasurementValue(reader.nextFloat());
      reader.endObject();
      return measurementValue;
    }
  }
}
