package io.sentry.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.Trace;
import io.sentry.TransactionContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

@ApiStatus.Internal
public final class TransactionContextsDeserializerAdapter implements JsonDeserializer<TransactionContexts> {

  private final @NotNull ILogger logger;

  public TransactionContextsDeserializerAdapter(@NotNull final ILogger logger) {
    this.logger = logger;
  }

  @Override
  public TransactionContexts deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      if (json != null && !json.isJsonNull()) {
        final TransactionContexts contexts = new TransactionContexts();
        final JsonObject jsonObject = json.getAsJsonObject();

        if (jsonObject != null && !jsonObject.isJsonNull()) {
          for (final String key : jsonObject.keySet()) {
            switch (key) {
              case Trace.TYPE:
                final Trace trace = parseObject(context, jsonObject, key, Trace.class);
                if (trace != null) {
                  contexts.setTrace(trace);
                }
                break;
              default:
                final JsonElement element = jsonObject.get(key);
                if (element != null && !element.isJsonNull()) {
                  try {
                    final Object object = context.deserialize(element, Object.class);
                    contexts.put(key, object);
                  } catch (JsonParseException e) {
                    logger.log(SentryLevel.ERROR, e, "Error when deserializing the %s key.", key);
                  }
                }
                break;
            }
          }
        }
        return contexts;
      }
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing Contexts", e);
    }
    return null;
  }

  private @Nullable <T> T parseObject(
      final @NotNull JsonDeserializationContext context,
      final @NotNull JsonObject jsonObject,
      final @NotNull String key,
      final @NotNull Class<T> clazz)
      throws JsonParseException {
    final JsonObject object = jsonObject.getAsJsonObject(key);
    if (object != null && !object.isJsonNull()) {
      return context.deserialize(object, clazz);
    }
    return null;
  }
}
