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
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(name().toLowerCase(Locale.ROOT));
  }

  public static final class Deserializer implements JsonDeserializer<SentryLevel> {

    @Override
    public @NotNull SentryLevel deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      return SentryLevel.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
    }
  }
}
