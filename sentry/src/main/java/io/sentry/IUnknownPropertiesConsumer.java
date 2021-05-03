package io.sentry;

import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface IUnknownPropertiesConsumer {
  void acceptUnknownProperties(@NotNull Map<String, Object> unknown);
}
