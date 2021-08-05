package io.sentry.adapters;

import io.sentry.JsonElementSerializer;
import io.sentry.JsonObjectWriter;
import java.io.IOException;
import java.util.TimeZone;
import org.jetbrains.annotations.NotNull;

public final class TimeZoneJsonElementSerializer implements JsonElementSerializer<TimeZone> {
  @Override
  public void serialize(TimeZone src, @NotNull JsonObjectWriter writer) throws IOException {
    writer.value(src.getID());
  }
}
