package io.sentry.core.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.protocol.Device;
import java.lang.reflect.Type;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OrientationDeserializerAdapter
    implements JsonDeserializer<Device.DeviceOrientation> {

  private final @NotNull ILogger logger;

  public OrientationDeserializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public Device.DeviceOrientation deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null
          ? null
          : Device.DeviceOrientation.valueOf(json.getAsString().toUpperCase(Locale.ROOT));
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing DeviceOrientation", e);
    }
    return null;
  }
}
