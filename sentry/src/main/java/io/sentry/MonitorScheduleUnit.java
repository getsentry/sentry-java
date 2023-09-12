package io.sentry;

import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** Status of a CheckIn */
public enum MonitorScheduleUnit implements JsonSerializable {
  MINUTE,
  HOUR,
  DAY,
  WEEK,
  MONTH,
  YEAR;

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(name().toLowerCase(Locale.ROOT));
  }

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }

  static final class Deserializer implements JsonDeserializer<MonitorScheduleUnit> {

    @Override
    public @NotNull MonitorScheduleUnit deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      return MonitorScheduleUnit.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
    }
  }
}
