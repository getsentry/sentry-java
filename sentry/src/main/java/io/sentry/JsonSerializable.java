package io.sentry;

import java.io.IOException;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface JsonSerializable {
  void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger) throws IOException;

  @Nullable Map<String, Object> getUnknown();
  void setUnknown(@Nullable Map<String, Object> unknown);
}
