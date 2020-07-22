package io.sentry.core.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.lang.reflect.Type;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryLevelDeserializerAdapter implements JsonDeserializer<SentryLevel> {

  private final @NotNull ILogger logger;

  public SentryLevelDeserializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public SentryLevel deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : SentryLevel.valueOf(json.getAsString().toUpperCase(Locale.ROOT));
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing SentryLevel", e);
    }
    return null;
  }
}
