package io.sentry.android.core;

import static io.sentry.core.ILogger.log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.core.DateUtils;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.lang.reflect.Type;
import java.util.Date;

class DateDeserializerAdapter implements JsonDeserializer<Date> {

  private final ILogger logger;

  public DateDeserializerAdapter(ILogger logger) {
    this.logger = logger;
  }

  @Override
  public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : DateUtils.getDateTime(json.getAsString());
    } catch (Exception e) {
      log(logger, SentryLevel.ERROR, "Error when deserializing Date", e);
    }
    return null;
  }
}
