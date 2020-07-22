package io.sentry.core.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.lang.reflect.Type;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class TimeZoneDeserializerAdapter implements JsonDeserializer<TimeZone> {

  private final @NotNull ILogger logger;

  public TimeZoneDeserializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public TimeZone deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : TimeZone.getTimeZone(json.getAsString());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing TimeZone", e);
    }
    return null;
  }
}
