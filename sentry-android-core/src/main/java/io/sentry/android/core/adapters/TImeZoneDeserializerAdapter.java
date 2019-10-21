package io.sentry.android.core.adapters;

import static io.sentry.core.ILogger.log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.lang.reflect.Type;
import java.util.TimeZone;

public class TImeZoneDeserializerAdapter implements JsonDeserializer<TimeZone> {

  private final ILogger logger;

  public TImeZoneDeserializerAdapter(ILogger logger) {
    this.logger = logger;
  }

  @Override
  public TimeZone deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : TimeZone.getTimeZone(json.getAsString());
    } catch (Exception e) {
      log(logger, SentryLevel.ERROR, "Error when deserializing TimeZone", e);
    }
    return null;
  }
}
