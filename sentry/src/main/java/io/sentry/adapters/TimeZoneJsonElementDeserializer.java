package io.sentry.adapters;

import io.sentry.JsonElementDeserializer;
import io.sentry.JsonObjectReader;
import java.io.IOException;
import java.util.TimeZone;
import org.jetbrains.annotations.NotNull;

public final class TimeZoneJsonElementDeserializer implements JsonElementDeserializer<TimeZone> {
  @Override
  public @NotNull TimeZone deserialize(@NotNull JsonObjectReader reader) throws IOException {
    String dateValue = reader.nextString();
    return TimeZone.getTimeZone(dateValue);
  }
}
