package io.sentry.android.core.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.protocol.App;
import io.sentry.core.protocol.Browser;
import io.sentry.core.protocol.Contexts;
import io.sentry.core.protocol.Device;
import io.sentry.core.protocol.Gpu;
import io.sentry.core.protocol.OperatingSystem;
import io.sentry.core.protocol.SentryRuntime;
import java.lang.reflect.Type;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ContextsDeserializerAdapter implements JsonDeserializer<Contexts> {

  private final @NotNull ILogger logger;

  public ContextsDeserializerAdapter(@NotNull final ILogger logger) {
    this.logger = logger;
  }

  @Override
  public Contexts deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      if (json != null && !json.isJsonNull()) {
        Contexts contexts = new Contexts();

        JsonObject jsonObject = json.getAsJsonObject();

        if (jsonObject != null && !jsonObject.isJsonNull()) {
          JsonObject appOjbect = jsonObject.getAsJsonObject(App.TYPE);
          if (appOjbect != null && !appOjbect.isJsonNull()) {
            App app = context.deserialize(appOjbect, App.class);
            contexts.setApp(app);
          }

          JsonObject browserObject = jsonObject.getAsJsonObject(Browser.TYPE);
          if (browserObject != null && !browserObject.isJsonNull()) {
            Browser browser = context.deserialize(browserObject, Browser.class);
            contexts.setBrowser(browser);
          }

          JsonObject deviceObject = jsonObject.getAsJsonObject(Device.TYPE);
          if (deviceObject != null && !deviceObject.isJsonNull()) {
            Device device = context.deserialize(deviceObject, Device.class);
            contexts.setDevice(device);
          }

          JsonObject osObject = jsonObject.getAsJsonObject(OperatingSystem.TYPE);
          if (osObject != null && !osObject.isJsonNull()) {
            OperatingSystem os = context.deserialize(osObject, OperatingSystem.class);
            contexts.setOperatingSystem(os);
          }

          JsonObject runtimeObject = jsonObject.getAsJsonObject(SentryRuntime.TYPE);
          if (runtimeObject != null && !runtimeObject.isJsonNull()) {
            SentryRuntime runtime = context.deserialize(runtimeObject, SentryRuntime.class);
            contexts.setRuntime(runtime);
          }

          JsonObject gpuObject = jsonObject.getAsJsonObject(Gpu.TYPE);
          if (gpuObject != null && !gpuObject.isJsonNull()) {
            Gpu gpu = context.deserialize(runtimeObject, Gpu.class);
            contexts.setGpu(gpu);
          }
        }

        return contexts;
      }
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing Contexts", e);
    }
    return null;
  }
}
