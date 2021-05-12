package io.sentry.adapters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.Collection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serializes collections to JSON. When collection is empty or {@code null}, returns {@code null}.
 */
@ApiStatus.Internal
public final class CollectionAdapter implements JsonSerializer<Collection<?>> {
  @Override
  public @Nullable JsonElement serialize(
      final @Nullable Collection<?> src,
      final @NotNull Type typeOfSrc,
      final @NotNull JsonSerializationContext context) {
    if (src == null || src.isEmpty()) {
      return null;
    }

    final JsonArray array = new JsonArray();

    for (final Object child : src) {
      JsonElement element = context.serialize(child);
      array.add(element);
    }
    return array;
  }
}
