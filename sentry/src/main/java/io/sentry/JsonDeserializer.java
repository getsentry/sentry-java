package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface JsonDeserializer<T> {
  @NotNull
  T deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception;
}
