package io.sentry;

import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** the SentryLevel */
public enum SentryLevel implements JsonSerializable {
  DEBUG,
  INFO,
  WARNING,
  ERROR,
  FATAL;

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.value(name().toLowerCase(Locale.ROOT));
  }

  static final class Deserializer implements JsonDeserializer<SentryLevel> {

    @Override
    public @NotNull SentryLevel deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      return SentryLevel.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
    }
  }
}
