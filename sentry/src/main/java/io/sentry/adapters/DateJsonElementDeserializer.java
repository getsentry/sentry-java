package io.sentry.adapters;

import io.sentry.DateUtils;
import io.sentry.JsonElementDeserializer;
import io.sentry.JsonObjectReader;
import java.io.IOException;
import java.util.Date;
import org.jetbrains.annotations.NotNull;

public final class DateJsonElementDeserializer implements JsonElementDeserializer<Date> {
  @Override
  public @NotNull Date deserialize(@NotNull JsonObjectReader reader) throws IOException {
    String dateValue = reader.nextString();
    try {
      return DateUtils.getDateTime(dateValue);
    } catch (Exception e) {
      // TODO Logger?
    }
    return DateUtils.getDateTime(dateValue);
  }
}
