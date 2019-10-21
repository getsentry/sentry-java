package io.sentry.android.core;

import static io.sentry.core.ILogger.log;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.protocol.SentryId;
import java.lang.reflect.Type;

class SentryIdSerializerAdapter implements JsonSerializer<SentryId> {

  private final ILogger logger;

  public SentryIdSerializerAdapter(ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(SentryId src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.toString());
    } catch (Exception e) {
      log(logger, SentryLevel.ERROR, "Error when serializing SentryId", e);
    }
    return null;
  }
}
