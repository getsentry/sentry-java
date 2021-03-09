package io.sentry.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.Device;
import java.lang.reflect.Type;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OrientationDeserializerAdapter
    implements JsonDeserializer<Device.DeviceOrientation> {

  private final @NotNull SentryOptions options;

  public OrientationDeserializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
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
      options.getLogger().log(SentryLevel.ERROR, "Error when deserializing DeviceOrientation", e);
    }
    return null;
  }
}
