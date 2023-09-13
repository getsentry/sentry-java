package io.sentry;

import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** Type of a monitor schedule */
public enum MonitorScheduleType implements JsonSerializable {
  CRONTAB,
  INTERVAL;

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(name().toLowerCase(Locale.ROOT));
  }

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }

  static final class Deserializer implements JsonDeserializer<MonitorScheduleType> {

    @Override
    public @NotNull MonitorScheduleType deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      return MonitorScheduleType.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
    }
  }
}
