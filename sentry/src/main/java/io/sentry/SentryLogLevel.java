package io.sentry;

import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** the SentryLevel for Logs */
public enum SentryLogLevel implements JsonSerializable {
  TRACE(1),
  DEBUG(5),
  INFO(9),
  WARN(13),
  ERROR(17),
  FATAL(21);

  private final int severityNumber;

  private SentryLogLevel(int severityNumber) {
    this.severityNumber = severityNumber;
  }

  public int getSeverityNumber() {
    return severityNumber;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(name().toLowerCase(Locale.ROOT));
  }

  public static final class Deserializer implements JsonDeserializer<SentryLogLevel> {

    @Override
    public @NotNull SentryLogLevel deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      return SentryLogLevel.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
    }
  }
}
