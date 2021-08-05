package io.sentry;

import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
interface JsonUnknown {
  @Nullable
  Map<String, Object> getUnknown();

  void setUnknown(@Nullable Map<String, Object> unknown);
}
