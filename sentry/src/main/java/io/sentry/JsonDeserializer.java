package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import io.sentry.vendor.gson.stream.JsonReader;

@ApiStatus.Internal
public interface JsonDeserializer<T> {
   @NotNull T deserialize(JsonReader reader) throws Exception;
}
