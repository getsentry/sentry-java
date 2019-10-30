package io.sentry.android.core.adapters;

import static io.sentry.core.ILogger.logIfNotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.protocol.Device;
import java.lang.reflect.Type;
import java.util.Locale;

public final class OrientationSerializerAdapter
    implements JsonSerializer<Device.DeviceOrientation> {

  private final ILogger logger;

  public OrientationSerializerAdapter(ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(
      Device.DeviceOrientation src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.name().toLowerCase(Locale.ROOT));
    } catch (Exception e) {
      logIfNotNull(logger, SentryLevel.ERROR, "Error when serializing DeviceOrientation", e);
    }
    return null;
  }
}
