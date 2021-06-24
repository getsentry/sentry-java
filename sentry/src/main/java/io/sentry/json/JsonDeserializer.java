package io.sentry.json;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import io.sentry.json.stream.JsonReader;

@ApiStatus.Internal
public interface JsonDeserializer<T> {
   @NotNull T deserialize(JsonReader reader) throws Exception;
}
