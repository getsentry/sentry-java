package io.sentry.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanContext;
import io.sentry.protocol.App;
import io.sentry.protocol.Browser;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Device;
import io.sentry.protocol.Gpu;
import io.sentry.protocol.OperatingSystem;
import io.sentry.protocol.SentryRuntime;
import java.lang.reflect.Type;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ContextsDeserializerAdapter implements JsonDeserializer<Contexts> {

  private final @NotNull SentryOptions options;

  public ContextsDeserializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @Nullable Contexts deserialize(
      final @Nullable JsonElement json,
      final @NotNull Type typeOfT,
      final @NotNull JsonDeserializationContext context)
      throws JsonParseException {
    try {
      if (json != null && !json.isJsonNull()) {
        final Contexts contexts = new Contexts();
        final JsonObject jsonObject = json.getAsJsonObject();

        if (jsonObject != null && !jsonObject.isJsonNull()) {
          for (final String key : jsonObject.keySet()) {
            switch (key) {
              case App.TYPE:
                App app = parseObject(context, jsonObject, key, App.class);
                if (app != null) {
                  contexts.setApp(app);
                }
                break;
              case Browser.TYPE:
                Browser browser = parseObject(context, jsonObject, key, Browser.class);
                if (browser != null) {
                  contexts.setBrowser(browser);
                }
                break;
              case Device.TYPE:
                Device device = parseObject(context, jsonObject, key, Device.class);
                if (device != null) {
                  contexts.setDevice(device);
                }
                break;
              case OperatingSystem.TYPE:
                OperatingSystem os = parseObject(context, jsonObject, key, OperatingSystem.class);
                if (os != null) {
                  contexts.setOperatingSystem(os);
                }
                break;
              case SentryRuntime.TYPE:
                SentryRuntime runtime = parseObject(context, jsonObject, key, SentryRuntime.class);
                if (runtime != null) {
                  contexts.setRuntime(runtime);
                }
                break;
              case Gpu.TYPE:
                Gpu gpu = parseObject(context, jsonObject, key, Gpu.class);
                if (gpu != null) {
                  contexts.setGpu(gpu);
                }
                break;
              case SpanContext.TYPE:
                SpanContext trace = parseObject(context, jsonObject, key, SpanContext.class);
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
                    options
                        .getLogger()
                        .log(SentryLevel.ERROR, e, "Error when deserializing the %s key.", key);
                  }
                }
                break;
            }
          }
        }
        return contexts;
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when deserializing Contexts", e);
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
