package io.sentry.adapters;

import io.sentry.DateUtils;
import io.sentry.JsonElementSerializer;
import io.sentry.JsonObjectWriter;
import java.io.IOException;
import java.util.Date;
import org.jetbrains.annotations.NotNull;

public final class DateJsonElementSerializer implements JsonElementSerializer<Date> {
  @Override
  public void serialize(Date src, @NotNull JsonObjectWriter writer) throws IOException {
    writer.value(DateUtils.getTimestamp(src));
  }
}
