package io.sentry.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.DateUtils;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.lang.reflect.Type;
import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DateDeserializerAdapter implements JsonDeserializer<Date> {

  private final @NotNull SentryOptions options;

  public DateDeserializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : DateUtils.getDateTime(json.getAsString());
    } catch (Exception e) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Error when deserializing UTC timestamp format, it might be millis timestamp format.",
              e);
    }
    try {
      return DateUtils.getDateTimeWithMillisPrecision(json.getAsString());
    } catch (Exception e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Error when deserializing millis timestamp format.", e);
    }
    return null;
  }
}
