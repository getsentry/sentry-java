package io.sentry.core.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.protocol.SentryId;
import java.lang.reflect.Type;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryIdDeserializerAdapter implements JsonDeserializer<SentryId> {

  private final @NotNull ILogger logger;

  public SentryIdDeserializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public SentryId deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : new SentryId(json.getAsString());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing SentryId", e);
    }
    return null;
  }
}
