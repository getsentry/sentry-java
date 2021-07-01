package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface JsonDeserializer<T extends JsonSerializable> {
  @NotNull
  T deserialize(@NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception;
}
