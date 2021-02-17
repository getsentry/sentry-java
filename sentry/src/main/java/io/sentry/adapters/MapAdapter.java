package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Serializes maps to JSON. When map is empty or {@code null}, returns {@code null}. */
@ApiStatus.Internal
public final class MapAdapter implements JsonSerializer<Map<String, ?>> {
  @Override
  public JsonElement serialize(
      final @Nullable Map<String, ?> src,
      final @NotNull Type typeOfSrc,
      final @NotNull JsonSerializationContext context) {
    if (src == null || src.isEmpty()) {
      return null;
    }

    final JsonObject jsonObject = new JsonObject();

    for (final Map.Entry<String, ?> entry : src.entrySet()) {
      JsonElement element = context.serialize(entry.getValue());
      jsonObject.add(entry.getKey(), element);
    }
    return jsonObject;
  }
}
