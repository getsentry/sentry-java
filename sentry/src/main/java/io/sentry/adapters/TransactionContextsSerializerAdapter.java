package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.SpanContext;
import io.sentry.TransactionContexts;
import io.sentry.protocol.App;
import io.sentry.protocol.Browser;
import io.sentry.protocol.Device;
import io.sentry.protocol.Gpu;
import io.sentry.protocol.OperatingSystem;
import io.sentry.protocol.SentryRuntime;
import java.lang.reflect.Type;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class TransactionContextsSerializerAdapter
    implements JsonSerializer<TransactionContexts> {

  private final @NotNull ILogger logger;

  public TransactionContextsSerializerAdapter(@NotNull final ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(
      TransactionContexts src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) {
      return null;
    }

    final JsonObject object = new JsonObject();
    object.add(SpanContext.TYPE, context.serialize(src.getTrace(), Object.class));
    object.add(App.TYPE, context.serialize(src.getApp(), Object.class));
    object.add(Browser.TYPE, context.serialize(src.getBrowser(), Object.class));
    object.add(OperatingSystem.TYPE, context.serialize(src.getOperatingSystem(), Object.class));
    object.add(Device.TYPE, context.serialize(src.getDevice(), Object.class));
    object.add(Gpu.TYPE, context.serialize(src.getGpu(), Object.class));
    object.add(SentryRuntime.TYPE, context.serialize(src.getRuntime(), Object.class));
    for (final Map.Entry<String, Object> entry : src.getOther().entrySet()) {
      if (!object.has(entry.getKey())) {
        try {
          final JsonElement element = context.serialize(entry.getValue(), Object.class);
          if (element != null) {
            object.add(entry.getKey(), element);
          }
        } catch (JsonParseException e) {
          logger.log(SentryLevel.ERROR, "%s context key isn't serializable.");
        }
      }
    }
    return object;
  }
}
