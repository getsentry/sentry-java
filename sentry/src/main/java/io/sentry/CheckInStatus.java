package io.sentry;

import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** Status of a CheckIn */
public enum CheckInStatus implements JsonSerializable {
  IN_PROGRESS,
  OK,
  ERROR;

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(name().toLowerCase(Locale.ROOT));
  }

  static final class Deserializer implements JsonDeserializer<CheckInStatus> {

    @Override
    public @NotNull CheckInStatus deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      return CheckInStatus.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
    }
  }
}
