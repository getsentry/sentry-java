package io.sentry.android.core.adapters;

import static io.sentry.core.ILogger.logIfNotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.protocol.SentryId;
import java.lang.reflect.Type;

public final class SentryIdSerializerAdapter implements JsonSerializer<SentryId> {

  private final ILogger logger;

  public SentryIdSerializerAdapter(ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(SentryId src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.toString());
    } catch (Exception e) {
      logIfNotNull(logger, SentryLevel.ERROR, "Error when serializing SentryId", e);
    }
    return null;
  }
}
