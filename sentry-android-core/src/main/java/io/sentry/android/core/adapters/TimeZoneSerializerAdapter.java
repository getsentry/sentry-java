package io.sentry.android.core.adapters;

import static io.sentry.core.ILogger.log;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.lang.reflect.Type;
import java.util.TimeZone;

public class TimeZoneSerializerAdapter implements JsonSerializer<TimeZone> {

  private final ILogger logger;

  public TimeZoneSerializerAdapter(ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(TimeZone src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.getID());
    } catch (Exception e) {
      log(logger, SentryLevel.ERROR, "Error when serializing TimeZone", e);
    }
    return null;
  }
}
