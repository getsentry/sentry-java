package io.sentry;

import io.sentry.vendor.gson.stream.JsonReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface JsonDeserializer<T> {
  @NotNull
  T deserialize(JsonReader reader) throws Exception;
}
