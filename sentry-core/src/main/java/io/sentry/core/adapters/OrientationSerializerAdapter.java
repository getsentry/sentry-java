package io.sentry.core.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.protocol.Device;
import java.lang.reflect.Type;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OrientationSerializerAdapter
    implements JsonSerializer<Device.DeviceOrientation> {

  private final @NotNull ILogger logger;

  public OrientationSerializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(
      Device.DeviceOrientation src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.name().toLowerCase(Locale.ROOT));
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing DeviceOrientation", e);
    }
    return null;
  }
}
